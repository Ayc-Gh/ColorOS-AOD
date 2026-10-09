package com.op.aod.enhance.hook

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal class HookRuntime(
    val module: XposedModule,
    val classLoader: ClassLoader,
) {
    fun findClass(name: String): Class<*> = Class.forName(name, false, classLoader)

    fun findMethod(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method = findMethod(findClass(className), methodName, *parameterTypes)

    fun findMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = if (parameterTypes.isEmpty()) {
                current.declaredMethods.filter { it.name == methodName }.let { candidates ->
                    check(candidates.size <= 1) { "Ambiguous method: ${clazz.name}#$methodName; specify parameter types" }
                    candidates.singleOrNull()
                }
            } else {
                runCatching { current.getDeclaredMethod(methodName, *parameterTypes) }.getOrNull()
            }
            if (method != null) {
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        error("Method not found: ${clazz.name}#$methodName(${parameterTypes.joinToString { it.simpleName }})")
    }

    fun intercept(
        id: String,
        method: Method,
        block: (XposedInterface.Chain) -> Any?,
    ): XposedInterface.HookHandle {
        val firstHit = AtomicBoolean(true)
        return module.hook(method)
        .setId(id)
        .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
        .intercept { chain ->
            if (firstHit.getAndSet(false)) AodLog.i("HOOK_HIT", "id=$id method=${method.declaringClass.name}#${method.name}")
            block(chain)
        }
    }
}
