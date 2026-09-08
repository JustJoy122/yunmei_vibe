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
    var enableBlur: Boolean
    var enableFloatingBottomBar: Boolean
    var enableFloatingBottomBarBlur: Boolean
    var pageScale: Float
}
