package com.yunmei.vibe.data.repository

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yunmei.vibe.data.ble.BLE_PERMISSIONS
import com.yunmei.vibe.data.ble.UnlockCooldown
import com.yunmei.vibe.data.local.AccountStore
import com.yunmei.vibe.data.local.LockStore
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 开门前置检查的统一结果：**阻塞原因**。
 *
 * 首页与 [com.yunmei.vibe.ui.unlock.UnlockService] 拿到的是同一个枚举，
 * 但后续动作不同——首页据此拉起系统蓝牙对话框或写状态文案，
 * 服务层据此直接发失败通知。
 */
sealed interface UnlockBlocker {
    /** 蓝牙总开关关闭：首页拉起 `ACTION_REQUEST_ENABLE` 对话框，服务层发失败通知。 */
    data object BluetoothOff : UnlockBlocker

    /** 没有可用门锁（服务层用默认门锁，取不到即视为没有）。 */
    data object NoDefaultLock : UnlockBlocker

    /** 缺少 BLE 运行时权限（正常情况下入口层已申请，这里是兜底）。 */
    data object MissingPermissions : UnlockBlocker

    /** 门锁数据不完整（缺 MAC / secret 等），不可开门。 */
    data object LockUnusable : UnlockBlocker

    /** 8 秒开门冷却中（官方 openDelay），[seconds] 为向上取整后的剩余秒数。 */
    data class CoolingDown(val seconds: Int) : UnlockBlocker
}

/** 前置检查通过后的产物：已刷新过的门锁、快连开关、本次使用的账号。 */
sealed interface UnlockPreparation {
    data class Ready(
        val lock: Lock,
        val quickConnect: Boolean,
        val account: StoredUser?,
    ) : UnlockPreparation

    data class Blocked(val blocker: UnlockBlocker) : UnlockPreparation
}

/**
 * 开门流程中「与入口无关」的那部分：蓝牙状态、门锁可用性、权限、冷却、secret 刷新。
 *
 * 首页按钮、桌面快捷方式、通知动作、控制中心磁贴最终都落到
 * `UnlockManager.openDoor(...)`；此前这些前置检查在
 * `HomeViewModel.doOpenDoor` 与 `UnlockService.runUnlock` 里各写了一份，
 * 顺序与文案也不完全一致。统一到这里后：
 *
 * - 调用方只负责「把 [UnlockBlocker] 翻译成 UI 文案 / 通知」；
 * - 真正的蓝牙连接与帧下发仍由 `UnlockManager` 完成，本类不碰 BLE。
 *
 * 注意：本类**不做**权限申请、不弹任何对话框，只做判定。
 */
class UnlockFlow(
    private val context: Context,
    private val lockStore: LockStore,
    private val accountStore: AccountStore,
    private val appPreferences: AppPreferences,
    private val repository: YunMeiRepository,
) {
    private val appContext: Context = context.applicationContext

    /**
     * 依次检查：蓝牙开关 → 门锁 → 权限 → 可用性 → 冷却 → 刷新 secret。
     *
     * @param lock 首页传入当前门锁；传 `null` 时（如服务层）回落到默认门锁。
     * @param account 调用方已确定的账号；为空时按 `usernameMd5` 查找，找不到再用
     *   「未保存账号时使用首个账号」开关兜底。
     */
    suspend fun prepare(lock: Lock?, account: StoredUser? = null): UnlockPreparation {
        if (!isBluetoothEnabled()) return UnlockPreparation.Blocked(UnlockBlocker.BluetoothOff)

        val target = lock ?: withContext(Dispatchers.IO) { lockStore.getDefault() }
        ?: return UnlockPreparation.Blocked(UnlockBlocker.NoDefaultLock)

        if (!hasBlePermissions()) return UnlockPreparation.Blocked(UnlockBlocker.MissingPermissions)

        if (!target.isUsable) return UnlockPreparation.Blocked(UnlockBlocker.LockUnusable)

        val cooldownMs = UnlockCooldown.remainingMs()
        if (cooldownMs > 0L) {
            val seconds = ((cooldownMs + 999L) / 1000L).toInt()
            return UnlockPreparation.Blocked(UnlockBlocker.CoolingDown(seconds))
        }

        val settings = withContext(Dispatchers.IO) { appPreferences.settings.first() }
        val resolvedAccount = account ?: withContext(Dispatchers.IO) { pickAccount(target, settings.alwaysCode) }

        // 开门前刷新一次 secret 与型号能力：本地可能是首次添加时的旧快照，服务端会轮换。
        // 未登录时 refreshLock 内部会静默重登；超时 / 离线直接沿用旧 secret，不阻塞开锁。
        val freshLock = refreshSecret(target, resolvedAccount)
        return UnlockPreparation.Ready(
            lock = freshLock,
            quickConnect = settings.quickConnect,
            account = resolvedAccount,
        )
    }

    /**
     * 用服务端最新信息覆盖本地快照；拿不到（未登录 / 无网络 / 超时）时原样返回。
     * AES 型号的拦截在 `UnlockManager.openDoor` 里按刷新后的 `aesSupport` 判断。
     */
    suspend fun refreshSecret(lock: Lock, account: StoredUser?): Lock {
        val refreshed = withTimeoutOrNull(SECRET_REFRESH_TIMEOUT_MS) {
            repository.refreshLock(lock, account)
        } ?: return lock
        withContext(Dispatchers.IO) { lockStore.updateFromServer(refreshed) }
        return refreshed
    }

    private suspend fun pickAccount(lock: Lock, alwaysCode: Boolean): StoredUser? =
        accountStore.getByUsernameMd5(lock.usernameMd5)
            ?: if (alwaysCode) accountStore.getAll().firstOrNull() else null

    private fun isBluetoothEnabled(): Boolean = runCatching {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    }.getOrDefault(false)

    private fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        /** 刷新 secret 的等待上限：超时直接按旧 secret 开门。 */
        const val SECRET_REFRESH_TIMEOUT_MS = 8_000L
    }
}
