package com.yunmei.vibe.ui.unlock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.xzakota.hyper.notification.focus.FocusNotification
import com.yunmei.vibe.R

/**
 * 小米超级岛（澎湃 OS 焦点通知 V3）适配。
 *
 * 设备判定与参数结构整体移植自 InstallerX Revived：
 *  - framework/notification/builder/MiIslandNotificationBuilder.kt（岛参数构建）
 *  - data/device/provider/DeviceCapabilityProviderImpl.kt（isSupportMiIsland 判定）
 * 参数生成不自行拼 JSON，交给 InstallerX 同款依赖
 * `com.xzakota.hyper.notification:focus-api`（`FocusNotification.buildV3`），
 * 由它负责写入 `miui.focus.param` 与 `miui.focus.pic_*`。
 */
object MiIslandNotification {

    /** 焦点通知协议版本：3 才是 OS3 超级岛模板。 */
    private const val FOCUS_PROTOCOL_ISLAND = 3

    private const val KEY_FOCUS_PROTOCOL = "notification_focus_protocol"
    private const val KEY_MIUI_VERSION = "ro.miui.ui.version.name"
    private const val KEY_MI_OS_VERSION = "ro.mi.os.version.name"

    @Volatile
    private var supportedCache: Boolean? = null

    /**
     * 是否为「小米机型 + 超级岛协议可用」。
     *
     * 判定方式与 InstallerX 一致：以官方查询接口
     * `Settings.System.getInt(contentResolver, "notification_focus_protocol", 0) == 3` 为准，
     * 再用厂商（Xiaomi，Redmi / POCO 同样上报 Xiaomi）与 HyperOS / MIUI 系统属性佐证。
     * 属性读取可能被隐藏 API 策略拦截，因此只要厂商命中就不影响判定结果。
     */
    fun isSupported(context: Context): Boolean =
        supportedCache ?: computeSupported(context).also { supportedCache = it }

    private fun computeSupported(context: Context): Boolean {
        if (focusProtocolVersion(context) != FOCUS_PROTOCOL_ISLAND) return false
        return isXiaomiDevice() || isMiuiOrHyperOs()
    }

    private fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER?.equals("xiaomi", ignoreCase = true) == true

    private fun isMiuiOrHyperOs(): Boolean {
        val hyperOs = systemProperty(KEY_MI_OS_VERSION)
        if (!hyperOs.isNullOrEmpty() && hyperOs.startsWith("OS")) return true
        val miui = systemProperty(KEY_MIUI_VERSION)
        return !miui.isNullOrEmpty()
    }

    private fun focusProtocolVersion(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, KEY_FOCUS_PROTOCOL, 0)
    }.getOrDefault(0)

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * 生成超级岛参数（内部即 `miui.focus.param` + 图片参数）。
     *
     * [inProgress] 为 true 时呈现进度态（胶囊进度环 + 焦点态多段进度），否则为结果态。
     */
    fun extras(
        context: Context,
        title: String,
        content: String,
        percent: Int?,
        inProgress: Boolean,
    ): Bundle {
        val lightIcon = Icon.createWithResource(context, R.drawable.ic_notification_unlock).setTint(Color.BLACK)
        val darkIcon = Icon.createWithResource(context, R.drawable.ic_notification_unlock).setTint(Color.WHITE)
        val safeContent = content.ifEmpty { " " }
        val progressValue = (percent ?: 0).coerceIn(0, 100)

        return FocusNotification.buildV3 {
            val lightLogoKey = createPicture("key_logo_light", lightIcon)
            val darkLogoKey = createPicture("key_logo_dark", darkIcon)

            islandFirstFloat = true
            enableFloat = !inProgress
            updatable = true
            ticker = title
            tickerPic = lightLogoKey
            // 外发光：与 InstallerX 的默认设置一致。
            outEffectSrc = "outer_glow"

            // 1. 胶囊摘要态 + 大岛展开态
            island {
                islandProperty = 1

                bigIslandArea {
                    imageTextInfoLeft {
                        type = 1
                        picInfo {
                            type = 1
                            pic = darkLogoKey
                        }
                    }

                    if (inProgress) {
                        progressTextInfo {
                            progressInfo {
                                isCCW = true
                                this.progress = progressValue
                            }
                            textInfo {
                                this.title = title
                                this.content = safeContent
                            }
                        }
                    } else {
                        imageTextInfoRight {
                            type = 3
                            textInfo {
                                this.title = title
                            }
                        }
                    }
                }

                smallIslandArea {
                    picInfo {
                        type = 1
                        pic = darkLogoKey
                    }
                }
            }

            // 2. 焦点通知下拉展开态：进行中用官方「文本 + 多段进度」模板，结束用图标文本模板。
            if (inProgress) {
                baseInfo {
                    type = 2
                    this.title = title
                    this.content = safeContent
                }
                multiProgressInfo {
                    this.progress = progressValue
                }
            } else {
                iconTextInfo {
                    this.title = title
                    this.content = safeContent
                    animIconInfo {
                        type = 0
                        src = lightLogoKey
                    }
                }
            }

            picInfo {
                type = 1
                pic = lightLogoKey
                picDark = darkLogoKey
            }
        }
    }
}
