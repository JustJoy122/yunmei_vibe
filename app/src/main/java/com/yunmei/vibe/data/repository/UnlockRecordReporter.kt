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
 * 开门记录上报。
 *
 * 官方客户端在收到门锁 A7 确认后立即异步上报 `lockrecord/savelockrecord`，
 * 本类按同样的时机与字段结构上报，差异有两点（都在报告里写明了原因）：
 * - `newPwd` 恒为空串：本应用不生成一次性临时密码；
 * - 楼栋/房间等字段本应用没有，交给仓库层传空串。
 *
 * 失败只打日志、不做本地补传（与「不引入离线队列」的取舍一致）；
 * 用户在设置里关掉开关后直接跳过，不影响开门本身。
 */
class UnlockRecordReporter(context: Context) {

    private val appContext = context.applicationContext

    /** 上报不占用界面生命周期：页面退出、通知服务结束时仍需把已经成功的那次记录发出去。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 上报一次成功开门。
     *
     * @param startedAtMillis 发起本次开门的时刻（[System.currentTimeMillis]），用于算时长。
     * @param battery 本次从门锁回传的 AA/AB 电量；没收到时为 null，上报时按 0 处理（与官方一致）。
     */
    fun report(lock: Lock, startedAtMillis: Long, battery: Int?) {
        val container = YunMeiApp.app.container
        if (!container.settingsRepository.enableRecordUpload) return
        // 没有学校/门锁号时服务端无法登记，直接跳过，避免刷无效请求。
        if (lock.schoolNo.isBlank() || lock.lockNo.isBlank()) return

        scope.launch {
            runCatching {
                val deviceId = container.lockStore.getOrCreateDeviceId()
                val userName = container.accountStore.getByUsernameMd5(lock.usernameMd5)?.username.orEmpty()
                val now = System.currentTimeMillis()
                val record = UnlockRecord(
                    schoolNo = lock.schoolNo,
                    lockNo = lock.lockNo,
                    userName = userName,
                    success = true,
                    // 官方把 AA/AB 解析结果塞进 lockResult；本应用只解析出电量，保持同样的 JSON 形状。
                    resultJson = "{\"aa\":" + (battery ?: 0) + "}",
                    durationSeconds = ((now - startedAtMillis) / 1000L).toInt().coerceAtLeast(0),
                    battery = battery ?: 0,
                    lockTime = SimpleDateFormat(TIME_PATTERN, Locale.CHINA).format(Date(now)),
                    newPwd = "",
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
