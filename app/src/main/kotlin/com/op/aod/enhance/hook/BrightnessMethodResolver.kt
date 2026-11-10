package com.op.aod.enhance.hook

import java.lang.reflect.Method

/** Prefer the host override over a different numeric API inherited from DreamService. */
internal object BrightnessMethodResolver {
    fun find(clazz: Class<*>, name: String): Method? {
        var owner: Class<*>? = clazz
        while (owner != null) {
            for (type in listOf(Float::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)) {
                val found = owner.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.contentEquals(arrayOf(type)) }
                if (found != null) return found
            }
            owner = owner.superclass
        }
        return null
    }
}
