package com.yunmei.vibe.data.ble

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleScanAndConnectCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.Lock
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * BLE 开门逻辑，1:1 复刻 zxy19/yunmei_unintelligent 的 UnlockService：
 * 快速连接（有 MAC）→ 扫描（服务 UUID）→ 连接 → 订阅通知 → 写入开门帧。
 */
/** 开门流程进度（百分比）。集中定义，避免魔法数字散落在各回调里。 */
private object UnlockProgress {
    const val RESET = 0
    const val START = 20
    const val DEVICE_FOUND = 30
    const val CONNECTED = 40
    const val CONNECTING = 43
    const val SUBSCRIBED = 50
    const val SENDING = 75
    const val DONE = 100
}
class UnlockManager(context: Context) {

    /** 应用 Context，仅用于读取界面文案资源（数据层不再硬编码中文）。 */
    private val appContext = context.applicationContext

    interface Listener {
        fun onProgress(percent: Int, message: String)
        fun onBattery(percent: Int)
        fun onSuccess()
        fun onFailure(message: String)
    }

    /** 可选回调：扫描模式开门成功后，把学到的真实 MAC 写回门锁（与原项目行为一致）。 */
    var onMacDiscovered: ((Lock, String) -> Unit)? = null

    // 冷启动不初始化 FastBle；首次访问时初始化，并显式设置连接与读写超时（不依赖库默认值）。
    private val bleManager: BleManager by lazy {
        BleManager.getInstance().apply {
            init(appContext as Application)
            setConnectOverTime(CONNECT_TIMEOUT_MS)
            setOperateTimeout(OPERATE_TIMEOUT_MS.toInt())
        }
    }
    private var connectedDevice: BleDevice? = null
    private var scanMode = false

    // ── 看门狗 ───────────────────────────────────────────────────────────────
    // 卡在某个百分比（最常见是 20%）的本质是「该阶段之后再也没有回调」：蓝牙连接回调丢失、
    // GATT 静默失败、系统回收、ROM 限制扫描等都会造成这种结果。这里在每个阶段的起点重新计时，
    // 超过 STALL_TIMEOUT_MS 没有新的进度或结果，就主动结束流程并回调失败。
    private val handler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null
    private var completed = false
    private var activeGuarded: GuardedListener? = null

    /** 内部包装：统一处理「流程已结束后忽略迟到回调」与「每个阶段重新计时」。 */
    private inner class GuardedListener(val delegate: Listener) : Listener {
        override fun onProgress(percent: Int, message: String) {
            if (completed) return
            armWatchdog()
            delegate.onProgress(percent, message)
        }

        override fun onBattery(percent: Int) {
            if (completed) return
            delegate.onBattery(percent)
        }

        override fun onSuccess() {
            if (completed) return
            completed = true
            cancelWatchdog()
            releaseBle()
            delegate.onSuccess()
        }

        override fun onFailure(message: String) {
            if (completed) return
            completed = true
            cancelWatchdog()
            releaseBle()
            delegate.onFailure(message)
        }
    }

    private fun armWatchdog() {
        cancelWatchdog()
        val guarded = activeGuarded ?: return
        val task = Runnable {
            if (completed) return@Runnable
            // 通过 GuardedListener 收口：置位完成标志、清理 BLE、再回调业务失败。
            guarded.onFailure(appContext.getString(R.string.unlock_timeout))
        }
        watchdog = task
        handler.postDelayed(task, STALL_TIMEOUT_MS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    /** 结束流程时停掉扫描并断开连接，避免残留 GATT 影响下一次开门。 */
    private fun releaseBle() {
        runCatching { bleManager.cancelScan() }
        connectedDevice?.let { device -> runCatching { bleManager.disconnect(device) } }
        connectedDevice = null
    }

    fun openDoor(lock: Lock, quickConnect: Boolean, listener: Listener) {
        if (!lock.isUsable) {
            listener.onFailure(appContext.getString(R.string.unlock_lock_unusable_need_login))
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            listener.onFailure(appContext.getString(R.string.unlock_bt_unsupported))
            return
        }
        if (!adapter.isEnabled) {
            listener.onProgress(UnlockProgress.RESET, appContext.getString(R.string.unlock_bluetooth_off))
            listener.onFailure(appContext.getString(R.string.unlock_bluetooth_disabled))
            return
        }

        completed = false
        val guarded = GuardedListener(listener)
        activeGuarded = guarded
        // 起点即计时：即使第一个回调就丢，也不会永久停在 20%。
        armWatchdog()

        if (lock.mac.isNotBlank() && quickConnect) {
            guarded.onProgress(UnlockProgress.START, appContext.getString(R.string.unlock_progress_quick_connect))
            connect(lock, lock.mac, guarded)
        } else {
            guarded.onProgress(UnlockProgress.START, appContext.getString(R.string.unlock_progress_scan_start))
            scanAndConnect(lock, guarded)
        }
    }

    private fun connect(lock: Lock, mac: String, listener: Listener) {
        bleManager.connect(mac, object : BleGattCallback() {
            override fun onStartConnect() = Unit

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                listener.onProgress(UnlockProgress.RESET, appContext.getString(R.string.unlock_progress_quick_connect_fallback))
                scanAndConnect(lock, listener)
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                connectedDevice = bleDevice
                listener.onProgress(UnlockProgress.CONNECTED, appContext.getString(R.string.unlock_connected))
                notifyAndSend(lock, bleDevice, listener)
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice,
                gatt: BluetoothGatt,
                status: Int,
            ) {
                // 原实现是空实现：一旦连接中途断开且没有后续回调，流程就会永久卡住。
                listener.onFailure(appContext.getString(R.string.unlock_disconnected))
            }
        })
    }

    private fun scanAndConnect(lock: Lock, listener: Listener) {
        scanMode = true
        val serviceUuid = runCatching { UUID.fromString(lock.serviceUuid) }.getOrNull()
        if (serviceUuid == null) {
            listener.onFailure(appContext.getString(R.string.unlock_error_service_uuid))
            return
        }
        bleManager.initScanRule(
            BleScanRuleConfig.Builder()
                .setServiceUuids(arrayOf(serviceUuid))
                // 显式声明扫描超时（FastBle 默认同为 10s），避免依赖默认值导致扫描不结束。
                .setScanTimeOut(SCAN_TIMEOUT_MS)
                .build()
        )
        bleManager.scanAndConnect(object : BleScanAndConnectCallback() {
            override fun onScanStarted(success: Boolean) {
                if (!success) listener.onFailure(appContext.getString(R.string.unlock_error_scan_start))
            }

            override fun onScanning(bleDevice: BleDevice) = Unit

            override fun onScanFinished(scanResult: BleDevice?) {
                if (scanResult == null) {
                    listener.onFailure(appContext.getString(R.string.unlock_device_not_found))
                } else {
                    listener.onProgress(UnlockProgress.DEVICE_FOUND, appContext.getString(R.string.unlock_progress_device_found))
                }
            }

            override fun onStartConnect() {
                listener.onProgress(UnlockProgress.CONNECTING, appContext.getString(R.string.unlock_connecting))
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                listener.onFailure(appContext.getString(R.string.unlock_error_connect))
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                connectedDevice = bleDevice
                listener.onProgress(UnlockProgress.CONNECTED, appContext.getString(R.string.unlock_connected))
                notifyAndSend(lock, bleDevice, listener)
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice,
                gatt: BluetoothGatt,
                status: Int,
            ) {
                listener.onFailure(appContext.getString(R.string.unlock_disconnected))
            }
        })
    }

    private fun notifyAndSend(lock: Lock, device: BleDevice, listener: Listener) {
        val notifyUuid = lock.writeUuid.replace("6E400002", "6E400003")
        bleManager.notify(device, lock.serviceUuid, notifyUuid, object : BleNotifyCallback() {
            override fun onNotifySuccess() {
                listener.onProgress(UnlockProgress.SUBSCRIBED, appContext.getString(R.string.unlock_connected))
                sendMessage(lock, device, listener)
            }

            override fun onNotifyFailure(exception: BleException) {
                listener.onFailure(appContext.getString(R.string.unlock_error_subscribe))
            }

            override fun onCharacteristicChanged(data: ByteArray) {
                parseBattery(data)?.let { listener.onBattery(it) }
            }
        })
    }

    private fun sendMessage(lock: Lock, device: BleDevice, listener: Listener) {
        bleManager.write(device, lock.serviceUuid, lock.writeUuid, buildUnlockFrame(lock.secret), object : BleWriteCallback() {
            override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
                if (current == total) {
                    // 扫描模式开门成功后把学到的真实 MAC 写回（原项目行为），下次即可快速连接。
                    if (scanMode) {
                        connectedDevice?.let { device ->
                            onMacDiscovered?.invoke(lock, device.mac)
                        }
                    }
                    listener.onProgress(UnlockProgress.DONE, appContext.getString(R.string.unlock_progress_done))
                    listener.onSuccess()
                } else {
                    listener.onProgress(UnlockProgress.SENDING, appContext.getString(R.string.unlock_sending))
                }
            }

            override fun onWriteFailure(exception: BleException) {
                listener.onFailure(appContext.getString(R.string.unlock_failed))
            }
        })
    }

    private fun buildUnlockFrame(secret: String): ByteArray {
        val secretBytes = secret.toByteArray(Charsets.UTF_8)
        val totalLength = secretBytes.size + 14
        var password = Random.nextInt(1_000_000)
        val out = ByteArrayOutputStream()
        out.write(0xD0)
        out.write(totalLength)
        out.write(secretBytes)
        out.write(0xA5)
        repeat(6) {
            out.write(password % 10)
            password /= 10
        }
        out.write('I'.code)
        out.write('D'.code)
        out.write('0'.code)
        out.write('1'.code)
        out.write(0xA7)
        return out.toByteArray()
    }

    private fun parseBattery(data: ByteArray): Int? {
        var posAA = -1
        var posAB = -1
        data.forEachIndexed { index, byte ->
            when (byte) {
                0xAA.toByte() -> posAA = index
                0xAB.toByte() -> posAB = index
            }
        }
        val aa = if (posAA == -1) -1 else parseAsciiInt(data, posAA + 1)
        val ab = if (posAB == -1) -1 else parseAsciiInt(data, posAB + 1)
        if (aa == -1 && ab == -1) return null

        var battery = aa
        if (ab != -1) {
            battery = ((100.0 * (ab - 40)) / 24.0).roundToInt()
        }
        return battery.coerceIn(0, 100)
    }

    private fun parseAsciiInt(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size) return -1
        return String(data, offset, 2, Charsets.US_ASCII).toIntOrNull() ?: -1
    }

    private companion object {
        /** 某阶段超过该时间没有任何回调即判定失败：扫描本身 10s，这里留足余量。 */
        const val STALL_TIMEOUT_MS = 15_000L

        /** 扫描超时（FastBle 默认同为 10s，显式声明避免依赖默认值）。 */
        const val SCAN_TIMEOUT_MS = 10_000L

        /** 连接超时（FastBle setConnectOverTime）。 */
        const val CONNECT_TIMEOUT_MS = 15_000L

        /** 读写/订阅操作超时（FastBle setOperateTimeout）。 */
        const val OPERATE_TIMEOUT_MS = 10_000L
    }
}
