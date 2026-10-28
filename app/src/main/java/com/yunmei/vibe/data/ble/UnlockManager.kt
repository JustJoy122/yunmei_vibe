package com.yunmei.vibe.data.ble

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
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
    const val CONNECTING = 35
    const val SUBSCRIBED = 50
    const val SENDING = 75
    const val SENT = 80
    const val ACK = 95
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
    // ── 分片写入与上行帧重组 ─────────────────────────────────────────────────

    // 官方客户端的三条纪律：订阅成功后才允许写、按 20 字节分片限速、用门锁回传的
    // A7 动作帧判定成功（写回调只代表数据交给了蓝牙栈）。
    private var writeLock: Lock? = null
    private var writeDevice: BleDevice? = null
    private var pendingFrame: ByteArray = ByteArray(0)
    private var pendingOffset = 0
    private val rxBuffer = ByteArrayOutputStream()
    private var responseTimer: Runnable? = null

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
            cancelResponseTimer()
            releaseBle()
            delegate.onSuccess()
        }

        override fun onFailure(message: String) {
            if (completed) return
            completed = true
            cancelWatchdog()
            cancelResponseTimer()
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
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
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
        resetIoState()
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
                handleIncoming(data, listener)
            }
        })
    }

    private fun sendMessage(lock: Lock, device: BleDevice, listener: Listener) {
        writeLock = lock
        writeDevice = device
        pendingFrame = buildUnlockFrame(lock.secret)
        pendingOffset = 0
        listener.onProgress(UnlockProgress.SENDING, appContext.getString(R.string.unlock_sending))
        sendNextChunk(listener)
    }

    /**
     * 逐包发送：每包最多 [WRITE_CHUNK_BYTES] 字节，等上一包的写入回调之后再间隔
     * [WRITE_INTERVAL_MS] 发下一包。官方客户端同样是 20 字节 + 100ms 的节奏，
     * 一次性把整帧推给 GATT 容易被门锁丢弃。
     */
    private fun sendNextChunk(listener: Listener) {
        if (completed) return
        val device = writeDevice ?: return
        val lock = writeLock ?: return
        if (pendingOffset >= pendingFrame.size) {
            // 最后一包发出去只说明数据到了门锁，动作是否执行由回传帧决定。
            listener.onProgress(UnlockProgress.SENT, appContext.getString(R.string.unlock_progress_sent))
            armResponseTimer(listener)
            return
        }
        val end = minOf(pendingOffset + WRITE_CHUNK_BYTES, pendingFrame.size)
        val chunk = pendingFrame.copyOfRange(pendingOffset, end)
        bleManager.write(device, lock.serviceUuid, lock.writeUuid, chunk, object : BleWriteCallback() {
            override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
                if (completed) return
                pendingOffset = end
                handler.postDelayed({ sendNextChunk(listener) }, WRITE_INTERVAL_MS)
            }

            override fun onWriteFailure(exception: BleException) {
                if (completed) return
                listener.onFailure(appContext.getString(R.string.unlock_failed))
            }
        })
    }

    /** 发完数据后的等待窗口：窗口内没有 A7 / 密钥错误帧，就按开锁超时收尾。 */
    private fun armResponseTimer(listener: Listener) {
        cancelResponseTimer()
        val task = Runnable {
            responseTimer = null
            if (completed) return@Runnable
            listener.onFailure(appContext.getString(R.string.unlock_timeout))
        }
        responseTimer = task
        handler.postDelayed(task, RESPONSE_TIMEOUT_MS)
    }

    private fun cancelResponseTimer() {
        responseTimer?.let { handler.removeCallbacks(it) }
        responseTimer = null
    }

    /** 每次开门前清空上一轮的分片状态与重组缓冲，避免迟到的数据混进本轮。 */
    private fun resetIoState() {
        pendingFrame = ByteArray(0)
        pendingOffset = 0
        writeLock = null
        writeDevice = null
        rxBuffer.reset()
        cancelResponseTimer()
    }

    /**
     * 上行数据按帧头累积重组：一帧可能跨多包到达，收齐帧头声明的长度后才分发。
     * 支持 D0（普通帧）与 D2D3（AES 外层）两种帧头，长度位都包含帧头自身。
     */
    private fun handleIncoming(data: ByteArray, listener: Listener) {
        if (completed) return
        rxBuffer.write(data)
        val bytes = rxBuffer.toByteArray()
        var consumed = 0
        while (consumed < bytes.size) {
            val first = bytes[consumed]
            val headerSize: Int
            val frameLength: Int
            if (first == 0xD0.toByte()) {
                if (bytes.size - consumed < 2) break
                headerSize = 2
                frameLength = bytes[consumed + 1].toInt() and 0xFF
            } else if (first == 0xD2.toByte()) {
                if (bytes.size - consumed < 3) break
                if (bytes[consumed + 1] != 0xD3.toByte()) {
                    consumed++
                    continue
                }
                headerSize = 3
                frameLength = bytes[consumed + 2].toInt() and 0xFF
            } else {
                // 不是帧头：丢掉一个字节后继续找下一帧起点。
                consumed++
                continue
            }
            if (frameLength <= headerSize) {
                consumed++
                continue
            }
            if (bytes.size - consumed < frameLength) break
            dispatchFrame(bytes.copyOfRange(consumed, consumed + frameLength), listener)
            consumed += frameLength
        }
        rxBuffer.reset()
        if (consumed < bytes.size) rxBuffer.write(bytes, consumed, bytes.size - consumed)
    }

    /** 解析一帧完整回传：只有 A7 动作确认与密钥失效是可识别的明确结果。 */
    private fun dispatchFrame(frame: ByteArray, listener: Listener) {
        if (completed) return
        if ((frame[0].toInt() and 0xFF) != 0xD0) {
            // 只发普通帧却收到加密响应，说明这次开门的能力判断与实际不符。
            listener.onFailure(appContext.getString(R.string.unlock_error_encrypted_response))
            return
        }
        val action = frame[2].toInt() and 0xFF
        val payload = String(frame, 3, frame.size - 3, Charsets.US_ASCII)
        when {
            payload == "SECRET_KEY_FAIL" || payload == "SECRET_KEY_FAI" ->
                listener.onFailure(appContext.getString(R.string.unlock_secret_invalid))

            action == 0xA7 -> {
                // 扫描模式开门成功后把学到的真实 MAC 写回（原项目行为），下次即可快速连接。
                val device = writeDevice
                val opened = writeLock
                if (scanMode && device != null && opened != null) {
                    onMacDiscovered?.invoke(opened, device.mac)
                }
                listener.onProgress(UnlockProgress.ACK, appContext.getString(R.string.unlock_progress_ack))
                listener.onSuccess()
            }
            // A5 是密码新增回应，其它未知动作都不算开门结果，继续等待。
        }
    }

    private fun buildUnlockFrame(secret: String): ByteArray {
        val secretBytes = secret.toByteArray(Charsets.UTF_8)
        val frame = ByteArray(secretBytes.size + 3)
        frame[0] = 0xD0.toByte()
        frame[1] = frame.size.toByte()
        System.arraycopy(secretBytes, 0, frame, 2, secretBytes.size)
        frame[frame.lastIndex] = 0xA7.toByte()
        return frame
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

        /** 单包写入字节数：官方客户端按 20 字节分片。 */
        const val WRITE_CHUNK_BYTES = 20

        /** 相邻两包之间的间隔：官方客户端为 100ms。 */
        const val WRITE_INTERVAL_MS = 100L

        /** 数据发完后等待门锁回传的时间窗口。 */
        const val RESPONSE_TIMEOUT_MS = 5_000L
    }
}
