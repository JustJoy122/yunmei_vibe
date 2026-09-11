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
        locks.add(lock)
        secureStore.putJson(KEY_LOCKS, locks)
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

    private fun clearDefault() {
        secureStore.putString(KEY_DEFAULT, "")
    }

    private fun notifyChanged() {
        _revision.value += 1
    }

    private companion object {
        const val KEY_LOCKS = "locks"
        const val KEY_DEFAULT = "lock_default"
    }
}
