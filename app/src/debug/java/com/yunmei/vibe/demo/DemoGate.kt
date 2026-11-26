package com.yunmei.vibe.demo

import com.yunmei.vibe.BuildConfig
import com.yunmei.vibe.core.di.AppContainer

/**
 * Demo 模式的开关与入口（**仅 debug 源集存在**）。
 *
 * 主源集不能直接引用这里的类（release 变体编译时看不到 debug 源集），所以
 * `AppContainer` / `YunMeiApp` 用反射按类名查找本对象：
 * - `AppContainer` 启动时调 [enabled] 决定是否注入 [DemoMockInterceptor]；
 * - `YunMeiApp.onCreate` 调 [seed] 灌入演示数据。
 *
 * 反射失败（release 包、或类被 R8 移除）一律当作「未开启」，对正式包零影响。
 */
object DemoGate {

    /** 由 `buildTypes.debug` 的 `buildConfigField("boolean", "DEMO_MODE", "true")` 生成。 */
    @JvmStatic
    fun enabled(): Boolean = BuildConfig.DEMO_MODE

    /** 灌入演示数据；重复调用无副作用（已存在演示账号时直接返回）。 */
    @JvmStatic
    fun seed(container: AppContainer) {
        if (!enabled()) return
        DemoSeeder.seed(container)
    }
}
