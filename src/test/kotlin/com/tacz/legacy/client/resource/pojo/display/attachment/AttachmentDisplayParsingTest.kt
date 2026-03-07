package com.tacz.legacy.client.resource.pojo.display.attachment

import com.google.gson.GsonBuilder
import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for AttachmentDisplay POJO deserialization and texture path expansion.
 */
class AttachmentDisplayParsingTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java,
            com.google.gson.JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) })
        .create()

    @Test
    fun `parse minimal attachment display JSON`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope_geo",
          "texture": "tacz:attachment/uv/scope"
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        assertNotNull(display)
        assertEquals(ResourceLocation("tacz", "attachment/model/scope_geo"), display.model)
        assertEquals(ResourceLocation("tacz", "attachment/uv/scope"), display.texture)
    }

    @Test
    fun `init expands texture paths`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope_geo",
          "texture": "tacz:attachment/uv/scope",
          "slot": "tacz:attachment/slot/scope"
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        display.init()

        assertEquals(ResourceLocation("tacz", "textures/attachment/uv/scope.png"), display.texture)
        assertEquals(ResourceLocation("tacz", "textures/attachment/slot/scope.png"), display.slotTextureLocation)
    }

    @Test
    fun `parse attachment display keeps scope metadata`() {
        val json = """
        {
          "slot": "tacz:attachment/slot/test_scope",
          "model": "tacz:attachment/model/test_scope_geo",
          "texture": "tacz:attachment/uv/test_scope",
          "adapter": "scope_adapter",
          "show_muzzle": true,
          "zoom": [2.0, 4.0],
          "views": [1, 4],
          "scope": true,
          "sight": true,
          "fov": 55,
          "views_fov": [60.0, 40.0]
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        display.init()

        assertEquals(ResourceLocation("tacz", "textures/attachment/slot/test_scope.png"), display.slotTextureLocation)
        assertEquals(ResourceLocation("tacz", "attachment/model/test_scope_geo"), display.model)
        assertEquals(ResourceLocation("tacz", "textures/attachment/uv/test_scope.png"), display.texture)
        assertEquals("scope_adapter", display.adapterNodeName)
        assertTrue(display.isShowMuzzle)
        assertArrayEquals(floatArrayOf(2.0f, 4.0f), display.zoom, 0.001f)
        assertArrayEquals(intArrayOf(1, 4), display.views)
        assertTrue(display.isScope)
        assertTrue(display.isSight)
        assertEquals(55.0f, display.fov, 0.001f)
        assertArrayEquals(floatArrayOf(60.0f, 40.0f), display.viewsFov, 0.001f)
    }

    @Test
    fun `parse attachment display with lod`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope_geo",
          "texture": "tacz:attachment/uv/scope",
          "lod": {
            "model": "tacz:attachment/model/scope_lod_geo",
            "texture": "tacz:attachment/uv/scope_lod"
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        assertNotNull(display.attachmentLod)
        assertEquals(ResourceLocation("tacz", "attachment/model/scope_lod_geo"), display.attachmentLod!!.modelLocation)
        assertEquals(ResourceLocation("tacz", "attachment/uv/scope_lod"), display.attachmentLod!!.modelTexture)
    }

    @Test
    fun `attachment display defaults stay compatible when optional fields are absent`() {
        val json = """
        {
          "model": "tacz:attachment/model/test_scope_geo",
          "texture": "tacz:attachment/uv/test_scope"
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)

        assertNull(display.slotTextureLocation)
        assertNull(display.adapterNodeName)
        assertNull(display.zoom)
        assertNull(display.views)
        assertFalse(display.isShowMuzzle)
        assertFalse(display.isScope)
        assertFalse(display.isSight)
        assertEquals(70.0f, display.fov, 0.001f)
        assertNull(display.viewsFov)
        assertNotNull(display.model)
        assertNotNull(display.texture)
    }
}
