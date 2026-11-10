package com.op.aod.enhance.hook

import org.junit.Assert.*
import org.junit.Test

class BrightnessMethodResolverTest {
    open class FloatBase { fun brightness(value: Float) = value }
    class LegacyHost : FloatBase() { fun brightness(value: Int) = value }
    open class IntBase { fun brightness(value: Int) = value }
    class ModernHost : IntBase() { fun brightness(value: Float) = value }
    @Test fun legacyOverrideWinsOverInheritedFloatApi() {
        val m = BrightnessMethodResolver.find(LegacyHost::class.java, "brightness")!!
        assertEquals(LegacyHost::class.java, m.declaringClass)
        assertEquals(Int::class.javaPrimitiveType, m.parameterTypes.single())
    }
    @Test fun modernOverrideWinsOverInheritedLegacyApi() {
        val m = BrightnessMethodResolver.find(ModernHost::class.java, "brightness")!!
        assertEquals(ModernHost::class.java, m.declaringClass)
        assertEquals(Float::class.javaPrimitiveType, m.parameterTypes.single())
    }
    @Test fun inheritedApiStillWorksWithoutAHostOverride() {
        assertEquals(FloatBase::class.java, BrightnessMethodResolver.find(FloatBase::class.java, "brightness")!!.declaringClass)
    }
    @Test fun missingApiIsUnavailable() { assertNull(BrightnessMethodResolver.find(ModernHost::class.java, "missing")) }
}
