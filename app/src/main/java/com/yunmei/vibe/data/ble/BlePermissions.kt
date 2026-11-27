package com.yunmei.vibe.data.ble

import android.Manifest
import android.os.Build

/**
 * BLE 开门所需权限（Android 12+ 用蓝牙权限，旧版本用定位权限）。
 *
 * 定义在数据层，供 [com.yunmei.vibe.data.repository.UnlockFlow] 与服务层共用；
 * UI 层仍通过 `com.yunmei.vibe.ui.util.BLE_PERMISSIONS` 引用同一份数组。
 */
val BLE_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
