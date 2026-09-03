package com.yunmei.client.core.di

import android.content.Context
import com.yunmei.client.data.ble.UnlockManager
import com.yunmei.client.data.local.AccountStore
import com.yunmei.client.data.local.LockStore
import com.yunmei.client.data.local.SecureStore
import com.yunmei.client.data.location.LocationProvider
import com.yunmei.client.data.network.YunMeiApiClient
import com.yunmei.client.data.preferences.AppPreferences
import com.yunmei.client.data.repository.YunMeiRepository

/**
 * 轻量手动依赖容器。规模变大后可平滑替换为 Hilt：
 * 这里只集中暴露单例，避免在 Compose/ViewModel 里直接 new 依赖。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appPreferences: AppPreferences by lazy {
        AppPreferences(appContext)
    }

    private val secureStore: SecureStore by lazy {
        SecureStore(appContext)
    }

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
        YunMeiRepository(apiClient)
    }

    val unlockManager: UnlockManager by lazy {
        UnlockManager(appContext)
    }

    val locationProvider: LocationProvider by lazy {
        LocationProvider(appContext)
    }
}
