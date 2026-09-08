package com.yunmei.vibe.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.content.Context
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleScanAndConnectCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.yunmei.vibe.data.model.Lock
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * BLE 开门逻辑，1:1 复刻 zxy19/yunmei_unintelligent 的 UnlockService：
 * 快速连接（有 MAC）→ 扫描（服务 UUID）→ 连接 → 订阅通知 → 写入开门帧。
 */
class UnlockManager(context: Context) {

    interface Listener {
        fun onProgress(percent: Int, message: String)
        fun onBattery(percent: Int)
        fun onSuccess()
        fun onFailure(message: String)
    }

    /** 可选回调：扫描模式开门成功后，把学到的真实 MAC 写回门锁（与原项目行为一致）。 */
    var onMacDiscovered: ((Lock, String) -> Unit)? = null

    private val bleManager: BleManager = BleManager.getInstance()
    private var connectedDevice: BleDevice? = null
    private var scanMode = false

    fun openDoor(lock: Lock, quickConnect: Boolean, listener: Listener) {
        if (!lock.isUsable) {
            listener.onFailure("当前门锁不可用，请先登录或选择其他门锁")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            listener.onFailure("设备不支持蓝牙")
            return
        }
        if (!adapter.isEnabled) {
            listener.onProgress(0, "请先开启蓝牙")
            listener.onFailure("蓝牙未开启")
            return
        }

        if (lock.mac.isNotBlank() && quickConnect) {
            listener.onProgress(20, "开始快速连接")
            connect(lock, lock.mac, listener)
        } else {
            listener.onProgress(20, "开始扫描")
            scanAndConnect(lock, listener)
        }
    }

    private fun connect(lock: Lock, mac: String, listener: Listener) {
        bleManager.connect(mac, object : BleGattCallback() {
            override fun onStartConnect() = Unit

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                listener.onProgress(0, "快速连接失败，使用常规模式尝试")
                scanAndConnect(lock, listener)
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                connectedDevice = bleDevice
                listener.onProgress(40, "连接成功")
                notifyAndSend(lock, bleDevice, listener)
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice,
                gatt: BluetoothGatt,
                status: Int,
            ) = Unit
        })
    }

    private fun scanAndConnect(lock: Lock, listener: Listener) {
        scanMode = true
        val serviceUuid = runCatching { UUID.fromString(lock.serviceUuid) }.getOrNull()
        if (serviceUuid == null) {
            listener.onFailure("门锁服务 UUID 无效")
            return
        }
        bleManager.initScanRule(
            BleScanRuleConfig.Builder()
                .setServiceUuids(arrayOf(serviceUuid))
                .build()
        )
        bleManager.scanAndConnect(object : BleScanAndConnectCallback() {
            override fun onScanStarted(success: Boolean) {
                if (!success) listener.onFailure("扫描启动失败")
            }

            override fun onScanning(bleDevice: BleDevice) = Unit

            override fun onScanFinished(scanResult: BleDevice?) {
                if (scanResult == null) {
                    listener.onFailure("设备未找到")
                } else {
                    listener.onProgress(30, "设备已找到")
                }
            }

            override fun onStartConnect() {
                listener.onProgress(43, "正在连接")
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                listener.onFailure("连接失败")
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                connectedDevice = bleDevice
                listener.onProgress(40, "连接成功")
                notifyAndSend(lock, bleDevice, listener)
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                device: BleDevice,
                gatt: BluetoothGatt,
                status: Int,
            ) = Unit
        })
    }

    private fun notifyAndSend(lock: Lock, device: BleDevice, listener: Listener) {
        val notifyUuid = lock.writeUuid.replace("6E400002", "6E400003")
        bleManager.notify(device, lock.serviceUuid, notifyUuid, object : BleNotifyCallback() {
            override fun onNotifySuccess() {
                listener.onProgress(50, "连接成功")
                sendMessage(lock, device, listener)
            }

            override fun onNotifyFailure(exception: BleException) {
                listener.onFailure("订阅通知失败")
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
                    listener.onProgress(100, "开门完成")
                    listener.onSuccess()
                } else {
                    listener.onProgress(75, "正在发送数据")
                }
            }

            override fun onWriteFailure(exception: BleException) {
                listener.onFailure("开门失败")
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
}
