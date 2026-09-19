# 项目混淆规则（R8）。
#
# 当前状态：release 已开启 isMinifyEnabled；资源压缩 isShrinkResources 尚未开启
# （按计划待本步真机回归通过后再单独开启）。
#
# 规则分两类：
#   1) 补充保留：在库自带 consumer 规则之外，仍需显式保住的部分（序列化、反射、回调）
#   2) 诊断辅助：保留行号与源文件名，便于线上崩溃栈定位
#
# 说明：Retrofit / OkHttp / kotlinx-serialization / AboutLibraries 的 artifact 自带
# consumer proguard 规则，R8 会自动应用；下列规则是对它们的补充与兜底。

# ---- 诊断：保留行号信息（崩溃栈可读），隐藏原始源文件名 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 注解与泛型签名：序列化与 Retrofit 接口的泛型解析都依赖它们
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# ---- kotlinx-serialization ----
# 本项目模型与导航键（Route 密封接口）都走 kotlinx.serialization；miuix-nav 保存返回栈时
# 会在运行时按 typeOf<List<Route>>() 反射查找序列化器，因此生成的 Companion.serializer()
# 与 **$$serializer 必须保留，否则会重现运行时异常：
# "Serializer for subclass 'Main' is not found in the polymorphic scope of 'Route'"。
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.yunmei.vibe.**$$serializer { *; }
-keepclassmembers class com.yunmei.vibe.** { *** Companion; }
-keepclasseswithmembers class com.yunmei.vibe.** { kotlinx.serialization.KSerializer serializer(...); }

# ---- Retrofit / OkHttp ----
# 接口方法上的 @GET/@POST/@Body 等注解在运行时读取，接口需保留（允许混淆名称）
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
# OkHttp 的可选 TLS 平台实现缺失时不告警
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# ---- FastBle（本地 AAR：回调对象与设备对象经系统回调/反射传递）----
-keep class com.clj.fastble.** { *; }
-dontwarn com.clj.fastble.**

# ---- HiddenApiBypass（通过反射调用隐藏 API）----
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# ---- AboutLibraries（库清单由构建期生成的数据反序列化而来）----
-keep class com.mikepenz.aboutlibraries.entity.** { *; }
-keep class com.mikepenz.aboutlibraries.Libs** { *; }
-dontwarn com.mikepenz.aboutlibraries.**

# ---- 枚举与 Parcelable ----
# 多处用 valueOf(name) 反查（PaletteStyle / ColorSpec.SpecVersion / ThemeMode / ColorMode 等）
-keepclassmembers enum * { *; }
-keep class * implements android.os.Parcelable { public static final ** CREATOR; }

# ---- Compose / Miuix ----
# 两者对 R8 友好，无需额外 keep 规则；保留此注释避免后续误加过宽规则。
-dontwarn top.yukonga.miuix.**

# ---- 编译期注解依赖（R8 missing classes 修复）----
# androidx.security:security-crypto 传递引入 com.google.crypto.tink，其类上引用了 errorprone 的
# 编译期注解（不进运行时）。下面 4 条即 R8 生成的 missing_rules.txt 内容，按最小化原则照录，
# 不使用 -dontobfuscate / -dontshrink 之类的一刀切手段。
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
