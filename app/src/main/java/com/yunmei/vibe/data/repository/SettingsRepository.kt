package com.yunmei.vibe.data.repository

interface SettingsRepository {
    var uiMode: String
    var checkUpdate: Boolean
    var themeMode: Int
    var miuixMonet: Boolean
    var amoled: Boolean
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var enablePredictiveBack: Boolean
    var enableSwipeDismiss: Boolean
    var pagerInterceptionMode: Int
    var predictiveBackAnimation: String
    var predictiveBackExitDirection: String
    var enableBlur: Boolean
    /** 顶栏模糊样式：gaussian / progressive。 */
    var blurStyle: String
    var enableFloatingBottomBar: Boolean
    var enableFloatingBottomBarBlur: Boolean
    var pageScale: Float

    /** 开门成功后是否上传开门记录（默认 true；关掉只影响上传，不影响开门本身）。 */
    var enableRecordUpload: Boolean

    companion object {
        /** 顶栏模糊样式取值：均匀高斯。 */
        const val BLUR_STYLE_GAUSSIAN = "gaussian"

        /** 顶栏模糊样式取值：自顶向下的渐进模糊（内部依赖 RuntimeShader，仅 API 33+ 生效）。 */
        const val BLUR_STYLE_PROGRESSIVE = "progressive"
    }
}
