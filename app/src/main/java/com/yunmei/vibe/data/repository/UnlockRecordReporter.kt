package com.yunmei.vibe.data.repository

import android.content.Context
import android.util.Log
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.UnlockRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 开门记录上报与本地电量回写。
 *
 * 官方客户端在收到门锁 A7 确认后立即异步上报 `lockrecord/savelockrecord`，
 * 本类按同样的时机与字段结构上报；失败只打日志、不做本地补传（与「不引入离线队列」的取舍一致），
 * 用户在设置里关掉开关后直接跳过，不影响开门本身。
 *
 * 无论是否上报成功，都会把这次回传的电量与时间写回本地门锁，界面据此显示「上次更新」。
 */
class UnlockRecordReporter(context: Context) {

    private val appContext = context.applicationContext

    /** 上报不占用界面生命周期：页面退出、通知服务结束时仍需把已经成功的那次记录发出去。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 上报一次成功开门，并把电量写回本地。
     *
     * @param battery 本次从门锁回传的 AA/AB 电量；没收到时为 null，上报时按 0 处理（与官方一致）。
     * @param voltage AB 回传的原始电压值；没收到时为 null，上报时按 0 处理。
     * @param lockLog 本次开门的阶段流水，官方原样作为 lockLog 上报。
     * @param newPwd 本次生成的一次性临时密码。
     */
    fun report(
        lock: Lock,
        startedAtMillis: Long,
        battery: Int?,
        voltage: Int?,
        lockLog: String,
        newPwd: String,
    ) {
        val container = YunMeiApp.app.container
        val now = System.currentTimeMillis()
        val lockTime = SimpleDateFormat(TIME_PATTERN, Locale.CHINA).format(Date(now))

        // 电量回写与会话无关，先做本地更新，保证关掉上报开关时界面也能刷新。
        scope.launch {
            runCatching { container.lockStore.setBattery(lock.label, battery, lockTime) }
                .onFailure { error -> Log.d(TAG, "unlock battery save failed=" + error.message) }
        }

        if (!container.settingsRepository.enableRecordUpload) return
        // 没有学校/门锁号时服务端无法登记，直接跳过，避免刷无效请求。
        if (lock.schoolNo.isBlank() || lock.lockNo.isBlank()) return

        scope.launch {
            runCatching {
                val deviceId = container.lockStore.getOrCreateDeviceId()
                val record = UnlockRecord(
                    schoolNo = lock.schoolNo,
                    lockNo = lock.lockNo,
                    areaNo = lock.areaNo,
                    buildNo = lock.buildNo,
                    dormNo = lock.dormNo,
                    success = true,
                    // 官方用 resultToJson 的输出：{"aa":电量, "ab":电压}。
                    resultJson = "{\"aa\":" + (battery ?: 0) + ",\"ab\":" + (voltage ?: 0) + "}",
                    durationSeconds = ((now - startedAtMillis) / 1000L).toInt().coerceAtLeast(0),
                    battery = battery ?: 0,
                    voltage = voltage ?: 0,
                    lockLog = lockLog,
                    lockTime = lockTime,
                    newPwd = newPwd,
                    location = "",
                )
                val uploaded = container.repository.saveUnlockRecord(record, deviceId)
                Log.d(TAG, "unlock record lock=" + lock.label + " uploaded=" + uploaded)
            }.onFailure { error ->
                // 网络失败属预期情况（离线开门），只记录不打扰用户。
                Log.d(TAG, "unlock record lock=" + lock.label + " failed=" + error.message)
            }
        }
    }

    private companion object {
        const val TAG = "YunMei"
        const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
    }
}
