# 插件元信息通过反射读取，保留相关类与字段
-keep class com.mtopensource.plugin.bridge.PluginManifest { *; }
-keepclassmembers class * implements com.mtopensource.plugin.api.Plugin {
    public <init>();
}
