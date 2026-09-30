# libxposed API 102 module entry.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Miuix UI components used by Compose.
-keep class top.yukonga.miuix.** { *; }

# Configuration model accessed across manager/hook code.
-keep class com.op.aod.enhance.data.** { *; }
-keep class com.op.aod.enhance.hook.AodConfig { *; }
-keep class com.op.aod.enhance.hook.AodConfigReader { *; }

-dontwarn top.yukonga.miuix.**
