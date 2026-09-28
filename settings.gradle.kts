pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // JitPack：小米超级岛参数库 com.xzakota.hyper.notification:focus-api 只在此发布
        // （Maven Central 无该坐标，InstallerX Revived 的通知实现同样依赖它）。
        maven { url = uri("https://jitpack.io") }

        // GitHub Packages（compose-miuix-ui/miuix）。
        // miuix-nav 只在该仓库发布（InstallerX Revived 使用的精确版本为 0.9.4-103b737b-SNAPSHOT），
        // GitHub Packages 不支持匿名访问：公开仓库同样需要鉴权。
        // 凭据来源与 InstallerX 保持一致：
        //   - 环境变量 GITHUB_ACTOR / GITHUB_TOKEN（CI 使用，token 需具备 read:packages）
        //   - 或 ~/.gradle/gradle.properties 中的 gpr.user / gpr.key（不要写进本仓库）
        val gprUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        val gprKey = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))

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
    }
}

rootProject.name = "YunmeiVibe"
include(":app")
