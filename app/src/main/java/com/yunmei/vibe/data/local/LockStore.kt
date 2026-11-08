package com.yunmei.vibe.data.local

import com.yunmei.vibe.data.model.Lock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 添加门锁时发现同标签门锁已存在。
 * UI 层据此把提示映射到 `R.string.locks_duplicate`，
 * 其余 `IllegalStateException` 才归为 `R.string.locks_add_failed`。
 */
class DuplicateLockException : IllegalStateException()

/** 门锁列表与默认门锁的持久化。 */
class LockStore(private val secureStore: SecureStore) {

    private val _revision = MutableStateFlow(0)

    /**
     * 门锁数据（列表 / 默认门锁）变更信号。
     * add / remove / setDefault / setMac 成功后自增一次，UI 层据此响应式刷新，
     * 无需轮询或定时器。
     */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun getAll(): List<Lock> = secureStore.getJson<List<Lock>>(KEY_LOCKS, emptyList())

    fun getDefault(): Lock? = secureStore.getJson<Lock?>(KEY_DEFAULT, null)

    fun add(lock: Lock) {
        val locks = getAll().toMutableList()
        if (locks.any { it.label == lock.label }) {
            throw DuplicateLockException()
        }
        // 第一个门锁自动成为默认门锁；之后添加的门锁不自动设默认，
        // 用户可在门锁详情页手动切换默认。
        val isFirstLock = locks.isEmpty()
        locks.add(lock)
        secureStore.putJson(KEY_LOCKS, locks)
        if (isFirstLock) {
            secureStore.putJson(KEY_DEFAULT, lock)
        }
        notifyChanged()
    }

    fun remove(label: String) {
        val locks = getAll().filterNot { it.label == label }
        secureStore.putJson(KEY_LOCKS, locks)
        if (getDefault()?.label == label) {
            clearDefault()
        }
        notifyChanged()
    }

    /** 还原：整表替换（备份还原专用）。 */
    fun replaceAll(locks: List<Lock>, default: Lock?) {
        secureStore.putJson(KEY_LOCKS, locks)
        if (default == null) clearDefault() else secureStore.putJson(KEY_DEFAULT, default)
        notifyChanged()
    }

    fun setDefault(lock: Lock?) {
        if (lock == null) {
            clearDefault()
        } else {
            secureStore.putJson(KEY_DEFAULT, lock)
        }
        notifyChanged()
    }

    fun setMac(label: String, mac: String) {
        val locks = getAll().map { lock ->
            if (lock.label == label) lock.copy(mac = mac) else lock
        }
        secureStore.putJson(KEY_LOCKS, locks)
        getDefault()?.let { def ->
            if (def.label == label) secureStore.putJson(KEY_DEFAULT, def.copy(mac = mac))
        }
        notifyChanged()
    }

    /**
     * 写入 AES 型号门锁的自定义密钥（传空串等于清除）。
     *
     * 与 [setMac] 一样同时更新列表与默认门锁，避免首页读到旧快照。
     */
    fun setCustomAesKey(label: String, key: String) {
        val normalized = key.trim()
        val locks = getAll().map { lock ->
            if (lock.label == label) lock.copy(customAesKey = normalized.ifBlank { null }) else lock
        }
        secureStore.putJson(KEY_LOCKS, locks)
        getDefault()?.let { def ->
            if (def.label == label) {
                secureStore.putJson(KEY_DEFAULT, def.copy(customAesKey = normalized.ifBlank { null }))
            }
        }
        notifyChanged()
    }

    /**
     * 上报开门记录用的本机标识：首次调用随机生成 8 位小写字母数字并持久化。
     *
     * 官方客户端也是本地生成后随记录一起上报，不参与鉴权，因此不写入账号相关字段。
     */
    fun getOrCreateDeviceId(): String {
        val existing = secureStore.getString(KEY_DEVICE_ID, "")
        if (existing.isNotBlank()) return existing
        val generated = buildString(DEVICE_ID_LENGTH) {
            repeat(DEVICE_ID_LENGTH) { append(DEVICE_ID_ALPHABET.random()) }
        }
        secureStore.putString(KEY_DEVICE_ID, generated)
        return generated
    }

    /**
     * 开门前从服务端刷新到的门锁信息写回（列表与默认门锁同步）。
     *
     * 只合并 secret 与型号能力，不动 MAC 等本地学习到的字段。
     * 与 [setMac] 不同：值没变化时不写盘、不发变更信号，避免每次开门都触发一次首页重载。
     */
    fun updateFromServer(fresh: Lock) {
        val all = getAll()
        val current = all.firstOrNull { it.label == fresh.label } ?: return
        val merged = current.copy(
            secret = fresh.secret.ifBlank { current.secret },
            lockTypeId = fresh.lockTypeId ?: current.lockTypeId,
            aesSupport = fresh.aesSupport ?: current.aesSupport,
            lockElectricity = fresh.lockElectricity ?: current.lockElectricity,
            updateTime = fresh.updateTime ?: current.updateTime,
            schoolName = fresh.schoolName.ifBlank { current.schoolName },
            areaNo = fresh.areaNo.ifBlank { current.areaNo },
            buildNo = fresh.buildNo.ifBlank { current.buildNo },
            dormNo = fresh.dormNo.ifBlank { current.dormNo },
        )
        if (merged == current) return
        secureStore.putJson(KEY_LOCKS, all.map { lock -> if (lock.label == fresh.label) merged else lock })
        getDefault()?.let { def ->
            if (def.label == fresh.label) secureStore.putJson(KEY_DEFAULT, merged)
        }
        notifyChanged()
    }

    /**
     * 蓝牙开门成功后把回传的电量与时间写回本地（列表与默认门锁同步）。
     *
     * @param percent 本次解析出的电量百分比；null 表示门锁没回传 AA/AB，此时只更新时间。
     * @param updateTime 本次开门时刻（`yyyy-MM-dd HH:mm:ss`），界面显示成「上次更新」。
     */
    fun setBattery(label: String, percent: Int?, updateTime: String) {
        val locks = getAll()
        if (locks.none { it.label == label }) return
        val updated = locks.map { lock ->
            if (lock.label == label) lock.copy(lockElectricity = percent ?: lock.lockElectricity, updateTime = updateTime) else lock
        }
        secureStore.putJson(KEY_LOCKS, updated)
        getDefault()?.let { def ->
            if (def.label == label) {
                secureStore.putJson(KEY_DEFAULT, def.copy(lockElectricity = percent ?: def.lockElectricity, updateTime = updateTime))
            }
        }
        notifyChanged()
    }

    private fun clearDefault() {
        secureStore.putString(KEY_DEFAULT, "")
    }

    private fun notifyChanged() {
        _revision.value += 1
    }

    private companion object {
        const val KEY_LOCKS = "locks"
        const val KEY_DEFAULT = "lock_default"

        /** 开门记录上报用的本机标识（随机 8 位）。 */
        const val KEY_DEVICE_ID = "unlock_device_id"

        const val DEVICE_ID_LENGTH = 8
        const val DEVICE_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    }
}
