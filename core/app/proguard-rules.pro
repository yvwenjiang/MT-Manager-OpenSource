# ---------------------------------------------------------------------------
# MT-Manager-OpenSource — R8/ProGuard 规则
# ---------------------------------------------------------------------------

# 保留行号信息，便于崩溃栈定位；同时隐藏源文件名
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# 插件系统：插件通过反射实例化入口类，类名来自插件自己的 manifest，
# 因此任何外部插件相关类型都不能被裁剪或混淆。
# ---------------------------------------------------------------------------
-keep class com.mtopensource.plugin.** { *; }
-keep interface com.mtopensource.plugin.** { *; }

# 插件 API 需要被插件以「接口 + 原方法名」的方式实现，保留成员名
-keepnames class com.mtopensource.plugin.api.** { *; }

# 宿主内置插件入口同样按类名反射加载
-keep class com.mtopensource.mtmanager.plugin.** { *; }

# 插件描述文件（JSON）由序列化框架读取，保留其模型类
-keep class com.mtopensource.plugin.model.** { *; }

# ---------------------------------------------------------------------------
# Kotlin 元数据与协程
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin 反射在插件加载路径中依赖 TypeToken 等实现细节
-dontwarn kotlin.reflect.**
-keepclassmembers class kotlin.reflect.jvm.internal.** { *; }

# ---------------------------------------------------------------------------
# 关闭对 Kotlin 空安全等「无实际影响」检查的告警
# ---------------------------------------------------------------------------
-dontwarn org.jetbrains.annotations.**

# ---------------------------------------------------------------------------
# Android / AndroidX 常规保留规则
# ---------------------------------------------------------------------------
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.**

# Compose 运行时依赖的注解在运行期可见
-keep @androidx.compose.runtime.Composable class * { *; }
