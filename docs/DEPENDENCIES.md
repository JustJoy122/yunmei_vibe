# 项目依赖

本项目使用了以下的第三方库和框架：

- [Android Gradle Plugin](https://developer.android.com/build)
- [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)
- [AndroidX（Compose / Lifecycle / Navigation / DataStore / Security）](https://github.com/androidx/androidx)
- [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)
- [FastBle](https://github.com/Jasonchenlijian/FastBle)
- [InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived)
- [KernelSU](https://github.com/tiann/KernelSU)
- [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit)
- [Kotlin Gradle Plugin](https://github.com/JetBrains/kotlin)
- [Kotlin & kotlinx（Coroutines / Serialization）](https://github.com/Kotlin/kotlinx.coroutines)
- [materialKolor](https://github.com/jordond/materialkolor)
- [Miuix](https://github.com/compose-miuix-ui/miuix)
- [OkHttp](https://github.com/square/okhttp)
- [Retrofit](https://github.com/square/retrofit)
- [yunmei_unintelligent-master](https://github.com/zxy19/yunmei_unintelligent)
- [ZXing](https://github.com/zxing/zxing)

> 各库对应的许可证类型（GPL-3.0 / MIT / Apache-2.0）已在应用内「设置 → 关于 → 开放源代码许可」页中展示。

---

## GitHub Packages 令牌（miuix 依赖）

### 依赖来源

官方 Miuix 组件（`top.yukonga.miuix.kmp:*`，含 `miuix-ui` / `miuix-icons` / `miuix-blur` / `miuix-preference` / `miuix-nav`）**不在 Maven Central 发布**，只能从 GitHub Packages 拉取。仓库声明见 `settings.gradle.kts`：

    maven {
        name = "GitHubPackagesMiuix"
        url = uri("https://maven.pkg.github.com/compose-miuix-ui/miuix")
        if (gprUser.isPresent && gprKey.isPresent) {
            credentials {
                username = gprUser.get()
                password = gprKey.get()
            }
        }
    }

其中凭据的取值顺序为：

1. Gradle 属性 `gpr.user` / `gpr.key`（推荐写入本机 `%USERPROFILE%\.gradle\gradle.properties`，**不要写进项目**）；
2. 环境变量 `GITHUB_ACTOR` / `GITHUB_TOKEN`（CI 环境用这个，见 `.github/workflows/*.yml`）。

> 注意：GitHub Packages 要求令牌具备 `read:packages` 权限；仅 `repo` 权限的经典令牌在读取他人组织的包时可能被拒。

### 令牌失效时的表现

- 依赖解析阶段失败，构建直接中断，典型报错：
  - `Could not resolve top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-103b737b-SNAPSHOT`；
  - `Received status code 401 from server: Unauthorized`（令牌缺失、拼写错误或已过期/被撤销）；
  - `Received status code 403 from server: Forbidden`（令牌有效但**没有 `read:packages` 权限**，或不属于有权访问该仓库的账号）；
  - 偶尔表现为 `Could not GET 'https://maven.pkg.github.com/...'` 后跟上述状态码。
- 若使用 `--offline` 或本机缓存仍然完整，可能暂时构建成功，但**缓存清理后必然复现**——不要据此判断令牌正常。
- 与其它失败区分：`401` / `403` 一定是凭据问题；`UnknownHostException` / 超时是网络问题；`Could not find ...:0.9.4-103b737b-SNAPSHOT`（无状态码）通常是**该快照已在服务器端被替换/清理**，属于依赖可用性问题而非令牌问题。

### 重新配置步骤

1. 新建令牌：GitHub → Settings → Developer settings → Personal access tokens，勾选 **`read:packages`**（如需访问组织包，还要确保该账号已被授予包的读取权限）。
2. 本机配置（推荐，一次性）：

       # %USERPROFILE%\.gradle\gradle.properties
       gpr.user=<GitHub 用户名>
       gpr.key=<上面新建的令牌>

3. CI 配置：在仓库 Settings → Secrets and variables → Actions 中添加 `GITHUB_TOKEN`（GitHub 自带的 `secrets.GITHUB_TOKEN` 通常已具备读包权限，工作流里已按此传入）；若使用自定义令牌，则通过 `GITHUB_ACTOR` / `GITHUB_TOKEN` 环境变量注入。
4. 清理可能被污染的缓存后重试（仅在报错持续时执行）：

       ./gradlew --refresh-dependencies :app:assembleDebug

   必要时再删除 `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\top.yukonga.miuix.kmp` 及其 `metadata-*` 下的对应条目。
5. 验证：执行一次 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`（或直接 `:app:assembleRelease`），确认不再出现 401/403。

### 排错速查

| 现象 | 最可能原因 | 处理 |
| --- | --- | --- |
| `401 Unauthorized` | 未配置凭据 / 令牌已撤销 | 按上面第 1-2 步重建并写入 `gpr.key` |
| `403 Forbidden` | 令牌缺少 `read:packages` 或无权访问该包 | 重新签发令牌并确认包权限 |
| 本机可构建、CI 失败 | CI 未注入 `GITHUB_ACTOR`/`GITHUB_TOKEN` | 检查工作流的 env 段与仓库 secrets |
| 清理缓存后才失败 | 缓存掩盖了凭据问题 | 按凭据问题处理，不要只重试 |