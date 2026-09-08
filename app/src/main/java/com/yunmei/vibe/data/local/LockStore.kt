package com.yunmei.vibe.data.local

import com.yunmei.vibe.data.model.Lock

/** 门锁列表与默认门锁的持久化。 */
class LockStore(private val secureStore: SecureStore) {

    fun getAll(): List<Lock> = secureStore.getJson<List<Lock>>(KEY_LOCKS, emptyList())

    fun getDefault(): Lock? = secureStore.getJson<Lock?>(KEY_DEFAULT, null)

    fun add(lock: Lock) {
        val locks = getAll().toMutableList()
        if (locks.any { it.label == lock.label }) {
            throw IllegalStateException("${lock.label} 已存在")
        }
        locks.add(lock)
        secureStore.putJson(KEY_LOCKS, locks)
    }

    fun remove(label: String) {
        val locks = getAll().filterNot { it.label == label }
        secureStore.putJson(KEY_LOCKS, locks)
        if (getDefault()?.label == label) {
            clearDefault()
        }
    }

    fun setDefault(lock: Lock?) {
        if (lock == null) {
            clearDefault()
        } else {
            secureStore.putJson(KEY_DEFAULT, lock)
        }
    }

    fun setMac(label: String, mac: String) {
        val locks = getAll().map { lock ->
            if (lock.label == label) lock.copy(mac = mac) else lock
        }
        secureStore.putJson(KEY_LOCKS, locks)
        getDefault()?.let { def ->
            if (def.label == label) secureStore.putJson(KEY_DEFAULT, def.copy(mac = mac))
        }
    }

    private fun clearDefault() {
        secureStore.putString(KEY_DEFAULT, "")
    }

    private companion object {
        const val KEY_LOCKS = "locks"
        const val KEY_DEFAULT = "lock_default"
    }
}
