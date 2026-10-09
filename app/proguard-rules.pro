# libxposed API 102 module entry.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# UI and configuration code use direct calls; library consumer rules cover their own reflection.
-dontwarn top.yukonga.miuix.**
