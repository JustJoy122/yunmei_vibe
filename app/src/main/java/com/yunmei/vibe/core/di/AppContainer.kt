package com.yunmei.vibe.core.di

import com.yunmei.vibe.data.repository.SettingsRepositoryImpl
import com.yunmei.vibe.data.repository.SettingsRepository
import android.content.Context
import com.yunmei.vibe.data.ble.UnlockManager
import com.yunmei.vibe.data.local.AccountStore
import com.yunmei.vibe.data.local.LockStore
import com.yunmei.vibe.data.local.SecureStore
import com.yunmei.vibe.data.local.SessionStore
import com.yunmei.vibe.data.location.LocationProvider
import com.yunmei.vibe.data.network.YunMeiApiClient
import com.yunmei.vibe.data.preferences.AppPreferences
import com.yunmei.vibe.data.repository.UnlockFlow
import com.yunmei.vibe.data.repository.YunMeiRepository
import okhttp3.Interceptor

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

    /**
     * 会话持久化。
     *
     * 仓储内部也持有一个实例（同一个 [SecureStore] 单例，读写的是同一份加密偏好），
     * 这里额外暴露一个引用，供演示数据灌入等启动期场景使用。
     */
    val sessionStore: SessionStore by lazy {
        SessionStore(secureStore)
    }

    private val apiClient: YunMeiApiClient by lazy {
        YunMeiApiClient(extraInterceptors = demoInterceptors())
    }

    /**
     * 演示模式的拦截器。
     *
     * `DemoMockInterceptor` 只存在于 debug 源集，主源集编译 release 时看不到它，
     * 因此按类名反射加载；任何一步失败（release、类被移除、参数变化）都退化成空列表，
     * 正式包的请求链路与之前完全一致。
     */
    private fun demoInterceptors(): List<Interceptor> {
        if (!com.yunmei.vibe.BuildConfig.DEBUG) return emptyList()
        return runCatching {
            val gate = Class.forName("com.yunmei.vibe.demo.DemoGate")
            if (gate.getMethod("enabled").invoke(null) != true) return emptyList()
            val interceptor = Class.forName("com.yunmei.vibe.demo.DemoMockInterceptor")
                .getDeclaredConstructor()
                .newInstance() as Interceptor
            listOf(interceptor)
        }.getOrElse { emptyList() }
    }

    val repository: YunMeiRepository by lazy {
        // AccountStore 一并注入：会话续期时要按 usernameMd5 取回密码 MD5 静默重登。
        YunMeiRepository(appContext, apiClient, accountStore, lockStore)
    }

    val unlockManager: UnlockManager by lazy {
        UnlockManager(appContext)
    }

    /**
     * 开门前置检查（蓝牙 / 权限 / 冷却 / secret 刷新）。
     *
     * 首页与 [com.yunmei.vibe.ui.unlock.UnlockService] 共用同一份实现，
     * 避免两处校验顺序与文案继续分叉。
     */
    val unlockFlow: UnlockFlow by lazy {
        UnlockFlow(appContext, lockStore, accountStore, appPreferences, repository)
    }

    val locationProvider: LocationProvider by lazy {
        LocationProvider(appContext)
    }

    /** 设置仓库：以接口类型暴露，ViewModel 只依赖 [SettingsRepository]，不 import 实现类。 */
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl()
    }
}
