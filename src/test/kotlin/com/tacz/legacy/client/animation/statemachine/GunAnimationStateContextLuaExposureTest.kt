package com.tacz.legacy.client.animation.statemachine

import org.junit.Assert.assertFalse
import org.junit.Test
import org.luaj.vm2.lib.jse.CoerceJavaToLua

class GunAnimationStateContextLuaExposureTest {
    @Test
    fun `default state machine context methods are exposed to lua`() {
        val scriptText = javaClass.classLoader
            .getResourceAsStream("assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/default_state_machine.lua")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("default_state_machine.lua not found on test classpath")

        val methods = Regex("context:(\\w+)\\(")
            .findAll(scriptText)
            .map { it.groupValues[1] }
            .toSortedSet()

        val luaContext = CoerceJavaToLua.coerce(GunAnimationStateContext())

        methods.forEach { methodName ->
            assertFalse(
                "Lua context method '$methodName' is missing from GunAnimationStateContext exposure",
                luaContext.get(methodName).isnil(),
            )
        }
    }
}