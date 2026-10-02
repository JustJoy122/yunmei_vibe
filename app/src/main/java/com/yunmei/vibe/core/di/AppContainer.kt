package com.yunmei.vibe.core.di

import android.content.Context
import com.yunmei.vibe.data.ble.UnlockManager
import com.yunmei.vibe.data.local.AccountStore
import com.yunmei.vibe.data.local.LockStore
import com.yunmei.vibe.data.local.SecureStore
import com.yunmei.vibe.data.location.LocationProvider
import com.yunmei.vibe.data.network.YunMeiApiClient
import com.yunmei.vibe.data.preferences.AppPreferences
import com.yunmei.vibe.data.repository.YunMeiRepository

/**
 * 轻量手动依赖容器。规模变大后可平滑替换为 Hilt：
 * 这里只集中暴露单例，避免在 Compose/ViewModel 里直接 new 依赖。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appPreferences: AppPreferences by lazy {
        AppPreferences(appContext, secureStore)
    }

    private val secureStore: SecureStore by lazy {
        // 进程内单例：与快捷方式令牌等其它调用方共用同一个实例
        SecureStore.get(appContext)
    }

    /** 安全存储是否可用（供设置页做内联提示）。 */
    val secureStoreAvailable: Boolean get() = secureStore.isAvailable

    val accountStore: AccountStore by lazy {
        AccountStore(secureStore)
    }

    val lockStore: LockStore by lazy {
        LockStore(secureStore)
    }

    private val apiClient: YunMeiApiClient by lazy {
        YunMeiApiClient()
    }

    val repository: YunMeiRepository by lazy {
        YunMeiRepository(appContext, apiClient)
    }

    val unlockManager: UnlockManager by lazy {
        UnlockManager(appContext)
    }

    val locationProvider: LocationProvider by lazy {
        LocationProvider(appContext)
    }
}
