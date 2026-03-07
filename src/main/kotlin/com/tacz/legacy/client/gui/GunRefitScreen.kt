package com.tacz.legacy.client.gui

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IAttachment
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.client.input.LegacyKeyBindings
import com.tacz.legacy.common.application.refit.LegacyGunRefitRuntime
import com.tacz.legacy.common.application.refit.LegacyRefitInventorySlot
import com.tacz.legacy.common.item.LegacyRuntimeTooltipSupport
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessageLaserColor
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerFireSelect
import com.tacz.legacy.common.network.message.client.ClientMessageRefitGun
import com.tacz.legacy.common.network.message.client.ClientMessageUnloadAttachment
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZAttachmentLaserConfigDefinition
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.resources.I18n
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentTranslation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Color
import java.util.Locale

@SideOnly(Side.CLIENT)
internal class GunRefitScreen : GuiScreen() {
    private var currentPage: Int = 0
    private var selectedType: AttachmentType = AttachmentType.NONE

    private val slotButtons: MutableList<AttachmentSlotButton> = mutableListOf()
    private val inventoryButtons: MutableList<InventoryAttachmentButton> = mutableListOf()
    private var unloadButton: UnloadButton? = null
    private var hueSlider: ColorSliderButton? = null
    private var saturationSlider: ColorSliderButton? = null

    override fun initGui() {
        super.initGui()
        buttonList.clear()
        slotButtons.clear()
        inventoryButtons.clear()
        unloadButton = null
        hueSlider = null
        saturationSlider = null

        if (!canStayOpen()) {
            mc.displayGuiScreen(null)
            return
        }

        normalizeSelection()
        addPropertyButtons()
        addAttachmentTypeButtons()
        addInventoryButtons()
        addLaserControls()
    }

    override fun doesGuiPauseGame(): Boolean = false

    override fun updateScreen() {
        super.updateScreen()
        if (!canStayOpen()) {
            mc.displayGuiScreen(null)
        }
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel == 0) {
            return
        }
        val maxPage = inventoryMaxPage()
        if (maxPage <= 0) {
            return
        }
        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        if (!isInsideInventoryArea(mouseX, mouseY)) {
            return
        }
        currentPage = when {
            wheel > 0 -> (currentPage - 1).coerceAtLeast(0)
            wheel < 0 -> (currentPage + 1).coerceAtMost(maxPage)
            else -> currentPage
        }
        initGui()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == LegacyKeyBindings.REFIT.keyCode) {
            mc.displayGuiScreen(null)
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        val player = currentPlayer()
        val gunStack = currentGunStack()
        if (player != null && !gunStack.isEmpty && gunStack.item is IGun) {
            TACZNetworkHandler.sendToServer(ClientMessageLaserColor(gunStack, player.inventory.currentItem))
        }
        super.onGuiClosed()
    }

    override fun actionPerformed(button: GuiButton) {
        when {
            button.id == BUTTON_PAGE_UP -> {
                currentPage = (currentPage - 1).coerceAtLeast(0)
                initGui()
            }
            button.id == BUTTON_PAGE_DOWN -> {
                currentPage = (currentPage + 1).coerceAtMost(inventoryMaxPage())
                initGui()
            }
            button.id == BUTTON_SHOW_PROPERTIES || button.id == BUTTON_HIDE_PROPERTIES -> {
                HIDE_GUN_PROPERTY_PANEL = !HIDE_GUN_PROPERTY_PANEL
                initGui()
            }
            button.id == BUTTON_FIRE_SELECT -> {
                val player = currentPlayer() ?: return
                val gunStack = currentGunStack()
                val iGun = gunStack.item as? IGun ?: return
                val before = iGun.getFireMode(gunStack)
                IGunOperator.fromLivingEntity(player).fireSelect()
                if (before != iGun.getFireMode(gunStack)) {
                    TACZNetworkHandler.sendToServer(ClientMessagePlayerFireSelect())
                }
                initGui()
            }
            button.id == BUTTON_UNLOAD -> tryUnloadSelectedAttachment()
            button.id in BUTTON_SLOT_BASE until BUTTON_SLOT_BASE + AttachmentType.values().size -> {
                val slot = slotButtons.firstOrNull { it.id == button.id } ?: return
                if (!slot.isAllowed()) {
                    selectedType = AttachmentType.NONE
                } else {
                    selectedType = if (selectedType == slot.type) AttachmentType.NONE else slot.type
                }
                currentPage = 0
                initGui()
            }
            button.id in BUTTON_INVENTORY_BASE until BUTTON_INVENTORY_BASE + INVENTORY_ATTACHMENT_SLOT_COUNT -> {
                val inventoryButton = inventoryButtons.firstOrNull { it.id == button.id } ?: return
                val player = currentPlayer() ?: return
                TACZNetworkHandler.sendToServer(
                    ClientMessageRefitGun(
                        inventoryButton.refitSlot.slotIndex,
                        player.inventory.currentItem,
                        selectedType,
                    ),
                )
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        drawGradientRect(0, 0, width, height, 0xB0101010.toInt(), 0xD0101010.toInt())
        drawPanels()
        drawTitle()
        drawGunPreview(mouseX, mouseY)
        if (!HIDE_GUN_PROPERTY_PANEL) {
            drawPropertyPanel()
        }
        drawInventoryHints()
        super.drawScreen(mouseX, mouseY, partialTicks)
        drawColorPreview()
        renderHoveredTooltips(mouseX, mouseY)
    }

    fun refreshFromServer() {
        initGui()
    }

    private fun canStayOpen(): Boolean {
        val player = currentPlayer() ?: return false
        return !player.isSpectator && IGun.mainHandHoldGun(player)
    }

    private fun currentPlayer(): EntityPlayerSP? = mc.player

    private fun currentGunStack(): ItemStack = currentPlayer()?.heldItemMainhand ?: ItemStack.EMPTY

    private fun normalizeSelection() {
        val gunStack = currentGunStack()
        val iGun = IGun.getIGunOrNull(gunStack)
        if (iGun == null) {
            selectedType = AttachmentType.NONE
            currentPage = 0
            return
        }
        if (selectedType != AttachmentType.NONE && !iGun.allowAttachmentType(gunStack, selectedType)) {
            selectedType = AttachmentType.NONE
            currentPage = 0
        }
        currentPage = currentPage.coerceIn(0, inventoryMaxPage())
    }

    private fun drawPanels() {
        drawPanel(8, 8, 8 + PROPERTY_PANEL_WIDTH, 28)
        if (!HIDE_GUN_PROPERTY_PANEL) {
            drawPanel(8, 30, 8 + PROPERTY_PANEL_WIDTH, 8 + PROPERTY_PANEL_HEIGHT)
        }
        drawPanel(width - 44 - SLOT_SIZE * 5, 8, width - 8, 28, 0x7A1B1B1B.toInt())
        drawPanel(width - 44, 48, width - 8, 48 + SLOT_SIZE * INVENTORY_ATTACHMENT_SLOT_COUNT + 18, 0x7A1B1B1B.toInt())
    }

    private fun drawTitle() {
        val gunStack = currentGunStack()
        val title = if (!gunStack.isEmpty) gunStack.displayName else I18n.format("key.tacz.refit.desc")
        drawCenteredString(fontRenderer, title, width / 2, 12, 0xF3EFE0)
    }

    private fun drawGunPreview(mouseX: Int, mouseY: Int) {
        val gunStack = currentGunStack()
        if (gunStack.isEmpty) {
            return
        }
        val previewYaw = -32.0f + ((((width / 2) - mouseX) * 0.08f)).coerceIn(-18.0f, 18.0f)
        val previewPitch = 10.0f + ((((height / 2) - mouseY) * 0.04f)).coerceIn(-10.0f, 10.0f)
        val gunPreviewRendered = TACZGuiModelPreviewRenderer.renderStackPreview(
            stack = gunStack,
            centerX = width / 2.0f - 44.0f,
            centerY = height / 2.0f + 44.0f,
            scale = 34.0f,
            yaw = previewYaw,
            pitch = previewPitch,
        )
        if (!gunPreviewRendered) {
            GlStateManager.pushMatrix()
            GlStateManager.translate((width / 2 - 30).toFloat(), (height / 2 - 10).toFloat(), 300f)
            GlStateManager.scale(4.0f, 4.0f, 1.0f)
            RenderHelper.enableGUIStandardItemLighting()
            itemRender.renderItemAndEffectIntoGUI(gunStack, 0, 0)
            itemRender.renderItemOverlayIntoGUI(fontRenderer, gunStack, 0, 0, null)
            RenderHelper.disableStandardItemLighting()
            GlStateManager.popMatrix()
        }

        if (selectedType == AttachmentType.NONE) {
            return
        }
        val selectedAttachment = LegacyGunRefitRuntime.displayedAttachment(gunStack, selectedType)
        if (selectedAttachment.isEmpty) {
            return
        }
        val attachmentRendered = TACZGuiModelPreviewRenderer.renderStackPreview(
            stack = selectedAttachment,
            centerX = width / 2.0f + 98.0f,
            centerY = height / 2.0f + 18.0f,
            scale = 24.0f,
            yaw = previewYaw - 18.0f,
            pitch = previewPitch + 4.0f,
        )
        if (!attachmentRendered) {
            GlStateManager.pushMatrix()
            GlStateManager.translate((width / 2 + 40).toFloat(), (height / 2 - 10).toFloat(), 300f)
            GlStateManager.scale(2.5f, 2.5f, 1.0f)
            RenderHelper.enableGUIStandardItemLighting()
            itemRender.renderItemAndEffectIntoGUI(selectedAttachment, 0, 0)
            itemRender.renderItemOverlayIntoGUI(fontRenderer, selectedAttachment, 0, 0, null)
            RenderHelper.disableStandardItemLighting()
            GlStateManager.popMatrix()
        }
    }

    private fun drawPropertyPanel() {
        val gunStack = currentGunStack()
        val iGun = gunStack.item as? IGun ?: return
        val gunData = GunDataAccessor.getGunData(iGun.getGunId(gunStack)) ?: return

        var y = 36
        val x = 14
        val currentAmmo = LegacyRuntimeTooltipSupport.getCurrentAmmoWithBarrel(gunStack, iGun, gunData)
        val maxAmmo = LegacyGunRefitRuntime.computeAmmoCapacity(gunStack) + if (gunData.boltType == BoltType.OPEN_BOLT) 0 else 1

        fontRenderer.drawString(
            "${I18n.format("gui.tacz.gun_refit.property_diagrams.fire_mode")}${fireModeText(iGun.getFireMode(gunStack))}",
            x,
            y,
            0xF3EFE0,
        )
        y += 12
        fontRenderer.drawString(
            "${I18n.format("gui.tacz.gun_refit.property_diagrams.ammo_capacity")}: ${currentAmmo}/${maxAmmo}",
            x,
            y,
            0xD8D8D8,
        )
        y += 12
        fontRenderer.drawString(
            I18n.format("tooltip.tacz.attachment.zoom", String.format(Locale.ROOT, "%.2f", iGun.getAimingZoom(gunStack))),
            x,
            y,
            0xD8D8D8,
        )
        y += 12

        if (selectedType != AttachmentType.NONE) {
            val selectedAttachment = LegacyGunRefitRuntime.displayedAttachment(gunStack, selectedType)
            val label = attachmentTypeLabel(selectedType)
            val detail = if (!selectedAttachment.isEmpty) "${label}: ${selectedAttachment.displayName}" else label
            fontRenderer.drawString(detail, x, y, 0xD8D8D8)
        }
    }

    private fun drawInventoryHints() {
        if (selectedType == AttachmentType.NONE) {
            val keyName = Keyboard.getKeyName(LegacyKeyBindings.REFIT.keyCode)
            val text = I18n.format("tooltip.tacz.gun.tips", keyName)
            drawCenteredString(fontRenderer, text, width / 2, height - 18, 0x9F9F9F)
            return
        }
        if (inventoryMaxPage() > 0) {
            val pageText = "${currentPage + 1}/${inventoryMaxPage() + 1}"
            drawCenteredString(fontRenderer, pageText, width - 26, 40, 0xF3EFE0)
        }
    }

    private fun drawColorPreview() {
        val currentColor = currentEditableLaserColor() ?: return
        drawPanel(width - 148, height - 66, width - 18, height - 12, 0x7A1B1B1B.toInt())
        drawRect(width - 44, height - 62, width - 22, height - 40, 0xFF000000.toInt() or (currentColor and 0xFFFFFF))
        fontRenderer.drawString("H", width - 144, height - 58, 0xF3EFE0)
        fontRenderer.drawString("S", width - 144, height - 40, 0xF3EFE0)
    }

    private fun renderHoveredTooltips(mouseX: Int, mouseY: Int) {
        buttonList.asSequence()
            .filterIsInstance<RefitTooltipButton>()
            .firstOrNull { it.contains(mouseX, mouseY) && it.tooltipLines.isNotEmpty() }
            ?.let { button ->
                drawHoveringText(button.tooltipLines, mouseX, mouseY)
                return
            }
        slotButtons.firstOrNull { it.contains(mouseX, mouseY) }?.let { button ->
            val stack = button.displayedAttachment()
            if (!stack.isEmpty) {
                renderToolTip(stack, mouseX, mouseY)
            } else {
                drawHoveringText(listOf(attachmentTypeLabel(button.type)), mouseX, mouseY)
            }
            return
        }
        inventoryButtons.firstOrNull { it.contains(mouseX, mouseY) }?.let { button ->
            renderToolTip(button.refitSlot.stack, mouseX, mouseY)
            return
        }
        unloadButton?.takeIf { it.contains(mouseX, mouseY) }?.let {
            drawHoveringText(listOf(I18n.format("tooltip.tacz.refit.unload")), mouseX, mouseY)
        }
    }

    private fun addPropertyButtons() {
        if (HIDE_GUN_PROPERTY_PANEL) {
            addButton(
                RefitFlatButton(
                    BUTTON_SHOW_PROPERTIES,
                    11,
                    11,
                    PROPERTY_PANEL_WIDTH - 6,
                    16,
                    I18n.format("gui.tacz.gun_refit.property_diagrams.show"),
                ),
            )
        } else {
            addButton(
                RefitFlatButton(
                    BUTTON_FIRE_SELECT,
                    14,
                    11,
                    16,
                    16,
                    "S",
                    listOf(I18n.format("gui.tacz.gun_refit.property_diagrams.fire_mode.switch")),
                ),
            )
            addButton(
                RefitFlatButton(
                    BUTTON_HIDE_PROPERTIES,
                    34,
                    11,
                    PROPERTY_PANEL_WIDTH - 29,
                    16,
                    I18n.format("gui.tacz.gun_refit.property_diagrams.hide"),
                ),
            )
        }
    }

    private fun addAttachmentTypeButtons() {
        val gunStack = currentGunStack()
        val iGun = IGun.getIGunOrNull(gunStack) ?: return
        var x = width - 30
        val y = 10
        AttachmentType.values().filterNot { it == AttachmentType.NONE }.forEach { type ->
            val button = AttachmentSlotButton(BUTTON_SLOT_BASE + type.ordinal, x, y, type)
            button.selected = selectedType == type
            slotButtons += button
            addButton(button)

            if (selectedType == type && !iGun.getAttachment(gunStack, type).isEmpty) {
                val unload = UnloadButton(BUTTON_UNLOAD, x + 5, y + SLOT_SIZE + 2)
                unloadButton = unload
                addButton(unload)
            }
            x -= SLOT_SIZE
        }
    }

    private fun addInventoryButtons() {
        if (selectedType == AttachmentType.NONE) {
            return
        }
        val player = currentPlayer() ?: return
        val compatibleSlots = compatibleSlots(player)
        val pageStart = currentPage * INVENTORY_ATTACHMENT_SLOT_COUNT
        val pageItems = compatibleSlots.drop(pageStart).take(INVENTORY_ATTACHMENT_SLOT_COUNT)

        var y = 50
        pageItems.forEachIndexed { index, slot ->
            val button = InventoryAttachmentButton(BUTTON_INVENTORY_BASE + index, width - 30, y, slot)
            inventoryButtons += button
            addButton(button)
            y += SLOT_SIZE
        }

        if (currentPage > 0) {
            addButton(
                TurnPageButton(
                    BUTTON_PAGE_UP,
                    width - 30,
                    40,
                    true,
                    listOf(I18n.format("tooltip.tacz.page.previous")),
                ),
            )
        }
        if (currentPage < inventoryMaxPage()) {
            addButton(
                TurnPageButton(
                    BUTTON_PAGE_DOWN,
                    width - 30,
                    50 + SLOT_SIZE * INVENTORY_ATTACHMENT_SLOT_COUNT + 6,
                    false,
                    listOf(I18n.format("tooltip.tacz.page.next")),
                ),
            )
        }
    }

    private fun addLaserControls() {
        val color = currentEditableLaserColor() ?: return
        val hsb = Color.RGBtoHSB((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, null)
        val hue = ColorSliderButton(BUTTON_HUE_SLIDER, width - 134, height - 60, 84, hsb[0].toDouble()) { applyLaserPreview() }
        val saturation = ColorSliderButton(BUTTON_SATURATION_SLIDER, width - 134, height - 42, 84, hsb[1].toDouble()) { applyLaserPreview() }
        hueSlider = hue
        saturationSlider = saturation
        addButton(hue)
        addButton(saturation)
    }

    private fun tryUnloadSelectedAttachment() {
        val player = currentPlayer() ?: return
        val gunStack = currentGunStack()
        val iGun = IGun.getIGunOrNull(gunStack) ?: return
        val currentAttachment = iGun.getAttachment(gunStack, selectedType)
        if (currentAttachment.isEmpty) {
            return
        }
        if (player.inventory.getFirstEmptyStack() == -1) {
            player.sendMessage(TextComponentTranslation("gui.tacz.gun_refit.unload.no_space"))
            return
        }
        TACZNetworkHandler.sendToServer(ClientMessageUnloadAttachment(player.inventory.currentItem, selectedType))
    }

    private fun compatibleSlots(player: EntityPlayerSP): List<LegacyRefitInventorySlot> {
        val slots = (0 until player.inventory.sizeInventory).map { slotIndex ->
            LegacyRefitInventorySlot(slotIndex, player.inventory.getStackInSlot(slotIndex))
        }
        return LegacyGunRefitRuntime.compatibleInventorySlots(currentGunStack(), selectedType, slots)
    }

    private fun inventoryMaxPage(): Int {
        val player = currentPlayer() ?: return 0
        val total = compatibleSlots(player).size
        return ((total - 1).coerceAtLeast(0)) / INVENTORY_ATTACHMENT_SLOT_COUNT
    }

    private fun attachmentTypeLabel(type: AttachmentType): String = I18n.format("tooltip.tacz.attachment.${type.serializedName}")

    private fun currentLaserConfig(): TACZAttachmentLaserConfigDefinition? {
        val gunStack = currentGunStack()
        val iGun = IGun.getIGunOrNull(gunStack) ?: return null
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        if (selectedType == AttachmentType.NONE) {
            return TACZGunPackPresentation.resolveGunLaserConfig(snapshot, iGun.getGunId(gunStack))
        }
        val installed = iGun.getAttachment(gunStack, selectedType)
        val iAttachment = IAttachment.getIAttachmentOrNull(installed) ?: return null
        return TACZGunPackPresentation.resolveAttachmentLaserConfig(snapshot, iAttachment.getAttachmentId(installed))
    }

    private fun currentEditableLaserColor(): Int? {
        val config = currentLaserConfig()?.takeIf(TACZAttachmentLaserConfigDefinition::canEdit) ?: return null
        val gunStack = currentGunStack()
        val iGun = IGun.getIGunOrNull(gunStack) ?: return null
        if (selectedType == AttachmentType.NONE) {
            return if (iGun.hasCustomLaserColor(gunStack)) iGun.getLaserColor(gunStack) else config.defaultColor
        }
        val installed = iGun.getAttachment(gunStack, selectedType)
        val iAttachment = IAttachment.getIAttachmentOrNull(installed) ?: return null
        return if (iAttachment.hasCustomLaserColor(installed)) iAttachment.getLaserColor(installed) else config.defaultColor
    }

    private fun applyLaserPreview() {
        val gunStack = currentGunStack()
        val iGun = IGun.getIGunOrNull(gunStack) ?: return
        val hue = hueSlider?.sliderValue ?: return
        val saturation = saturationSlider?.sliderValue ?: return
        val color = Color.HSBtoRGB(hue.toFloat(), saturation.toFloat(), 1.0f)
        if (selectedType == AttachmentType.NONE) {
            iGun.setLaserColor(gunStack, color)
            return
        }
        val attachment = iGun.getAttachment(gunStack, selectedType)
        val iAttachment = IAttachment.getIAttachmentOrNull(attachment) ?: return
        iAttachment.setLaserColor(attachment, color)
        iGun.installAttachment(gunStack, attachment)
    }

    private fun isInsideInventoryArea(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in (width - 30)..(width - 12) && mouseY in 50..(50 + SLOT_SIZE * INVENTORY_ATTACHMENT_SLOT_COUNT)
    }

    private fun drawPanel(left: Int, top: Int, right: Int, bottom: Int, fillColor: Int = 0x7A161616.toInt()) {
        drawRect(left, top, right, bottom, fillColor)
        drawHorizontalLine(left, right - 1, top, 0xFFF3EFE0.toInt())
        drawHorizontalLine(left, right - 1, bottom - 1, 0xAA4A4A4A.toInt())
        drawVerticalLine(left, top, bottom - 1, 0xFFF3EFE0.toInt())
        drawVerticalLine(right - 1, top, bottom - 1, 0xAA4A4A4A.toInt())
    }

    private fun fireModeText(fireMode: FireMode): String {
        val key = when (fireMode) {
            FireMode.AUTO -> "gui.tacz.gun_refit.property_diagrams.auto"
            FireMode.BURST -> "gui.tacz.gun_refit.property_diagrams.burst"
            FireMode.SEMI -> "gui.tacz.gun_refit.property_diagrams.semi"
            FireMode.UNKNOWN -> "gui.tacz.gun_refit.property_diagrams.unknown"
        }
        return I18n.format(key)
    }

    private inner class AttachmentSlotButton(
        id: Int,
        x: Int,
        y: Int,
        val type: AttachmentType,
    ) : GuiButton(id, x, y, SLOT_SIZE, SLOT_SIZE, "") {
        var selected: Boolean = false

        override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = contains(mouseX, mouseY)
            val gunStack = currentGunStack()
            val iGun = IGun.getIGunOrNull(gunStack) ?: return

            GlStateManager.color(1f, 1f, 1f, 1f)
            mc.textureManager.bindTexture(SLOT_TEXTURE)
            if (hovered || selected) {
                drawTexturedModalRect(x, y, 0, 0, width, height)
            } else {
                drawTexturedModalRect(x + 1, y + 1, 1, 1, width - 2, height - 2)
            }

            val displayStack = displayedAttachment()
            RenderHelper.enableGUIStandardItemLighting()
            if (!displayStack.isEmpty) {
                itemRender.renderItemAndEffectIntoGUI(displayStack, x + 1, y + 1)
                itemRender.renderItemOverlayIntoGUI(fontRenderer, displayStack, x + 1, y + 1, null)
            } else {
                mc.textureManager.bindTexture(ICONS_TEXTURE)
                drawTexturedModalRect(x + 2, y + 2, getSlotTextureXOffset(gunStack, iGun, type), 0, width - 4, height - 4)
            }
            RenderHelper.disableStandardItemLighting()

            if (hovered) {
                val labelY = if (selected && !displayStack.isEmpty) y + 30 else y + 20
                drawCenteredString(fontRenderer, attachmentTypeLabel(type), x + width / 2, labelY, 0xFFFFFF)
            }
        }

        fun displayedAttachment(): ItemStack = LegacyGunRefitRuntime.displayedAttachment(currentGunStack(), type)

        fun isAllowed(): Boolean {
            val gunStack = currentGunStack()
            val iGun = IGun.getIGunOrNull(gunStack) ?: return false
            return iGun.allowAttachmentType(gunStack, type)
        }

        fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private inner class InventoryAttachmentButton(
        id: Int,
        x: Int,
        y: Int,
        val refitSlot: LegacyRefitInventorySlot,
    ) : GuiButton(id, x, y, SLOT_SIZE, SLOT_SIZE, "") {
        override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = contains(mouseX, mouseY)
            GlStateManager.color(1f, 1f, 1f, 1f)
            mc.textureManager.bindTexture(SLOT_TEXTURE)
            if (hovered) {
                drawTexturedModalRect(x, y, 0, 0, width, height)
            } else {
                drawTexturedModalRect(x + 1, y + 1, 1, 1, width - 2, height - 2)
            }
            RenderHelper.enableGUIStandardItemLighting()
            itemRender.renderItemAndEffectIntoGUI(refitSlot.stack, x + 1, y + 1)
            itemRender.renderItemOverlayIntoGUI(fontRenderer, refitSlot.stack, x + 1, y + 1, null)
            RenderHelper.disableStandardItemLighting()
        }

        fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private inner class UnloadButton(id: Int, x: Int, y: Int) : GuiButton(id, x, y, 8, 8, "") {
        override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = contains(mouseX, mouseY)
            GlStateManager.color(1f, 1f, 1f, 1f)
            mc.textureManager.bindTexture(UNLOAD_TEXTURE)
            val u = if (hovered) 0f else 80f
            drawModalRectWithCustomSizedTexture(x, y, u, 0f, width, height, 160f, 80f)
        }

        fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private interface RefitTooltipButton {
        val tooltipLines: List<String>
        fun contains(mouseX: Int, mouseY: Int): Boolean
    }

    private inner class RefitFlatButton(
        id: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        displayString: String,
        override val tooltipLines: List<String> = emptyList(),
    ) : GuiButton(id, x, y, width, height, displayString), RefitTooltipButton {
        override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = contains(mouseX, mouseY)
            val fill = if (hovered) 0xAF303030.toInt() else 0xAF222222.toInt()
            drawRect(x, y, x + width, y + height, fill)
            val border = if (hovered) 0xFFF3EFE0.toInt() else 0xFF5A5A5A.toInt()
            drawHorizontalLine(x, x + width - 1, y, border)
            drawHorizontalLine(x, x + width - 1, y + height - 1, border)
            drawVerticalLine(x, y, y + height - 1, border)
            drawVerticalLine(x + width - 1, y, y + height - 1, border)
            drawCenteredString(fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, 0xF3EFE0)
        }

        override fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private inner class TurnPageButton(
        id: Int,
        x: Int,
        y: Int,
        private val upPage: Boolean,
        override val tooltipLines: List<String>,
    ) : GuiButton(id, x, y, 18, 8, ""), RefitTooltipButton {
        override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = contains(mouseX, mouseY)
            GlStateManager.color(1f, 1f, 1f, 1f)
            mc.textureManager.bindTexture(TURN_PAGE_TEXTURE)
            val yOffset = if (upPage) 0 else 80
            if (hovered) {
                drawModalRectWithCustomSizedTexture(x, y, 0f, yOffset.toFloat(), width, height, 180f, 160f)
            } else {
                drawModalRectWithCustomSizedTexture(x + 1, y + 1, 10f, (yOffset + 10).toFloat(), width - 2, height - 2, 180f, 160f)
            }
        }

        override fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private inner class ColorSliderButton(
        id: Int,
        x: Int,
        y: Int,
        width: Int,
        value: Double,
        private val onValueChanged: () -> Unit,
    ) : GuiButton(id, x, y, width, 12, "") {
        var sliderValue: Double = value.coerceIn(0.0, 1.0)
            private set
        private var dragging: Boolean = false

        override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = contains(mouseX, mouseY)
            drawRect(x, y + 5, x + width, y + 7, 0x66FFFFFF)
            val knobX = x + (sliderValue * (width - 6)).toInt()
            drawRect(knobX, y + 2, knobX + 6, y + 10, if (hovered || dragging) 0xFFF3EFE0.toInt() else 0xFFAAAAAA.toInt())
        }

        override fun mousePressed(mc: Minecraft, mouseX: Int, mouseY: Int): Boolean {
            val pressed = super.mousePressed(mc, mouseX, mouseY)
            if (pressed) {
                dragging = true
                updateValue(mouseX)
            }
            return pressed
        }

        override fun mouseDragged(mc: Minecraft, mouseX: Int, mouseY: Int) {
            if (visible && dragging) {
                updateValue(mouseX)
            }
            super.mouseDragged(mc, mouseX, mouseY)
        }

        override fun mouseReleased(mouseX: Int, mouseY: Int) {
            dragging = false
            super.mouseReleased(mouseX, mouseY)
        }

        private fun updateValue(mouseX: Int) {
            sliderValue = ((mouseX - x).toDouble() / (width - 6).toDouble()).coerceIn(0.0, 1.0)
            onValueChanged()
        }

        private fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private companion object {
        val SLOT_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_slot.png")
        val TURN_PAGE_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_turn_page.png")
        val UNLOAD_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_unload.png")
        val ICONS_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_slot_icons.png")

        const val SLOT_SIZE: Int = 18
        const val ICON_UV_SIZE: Int = 32
        const val INVENTORY_ATTACHMENT_SLOT_COUNT: Int = 8
        const val PROPERTY_PANEL_WIDTH: Int = 288
        const val PROPERTY_PANEL_HEIGHT: Int = 92

        const val BUTTON_PAGE_UP: Int = 1
        const val BUTTON_PAGE_DOWN: Int = 2
        const val BUTTON_SHOW_PROPERTIES: Int = 3
        const val BUTTON_HIDE_PROPERTIES: Int = 4
        const val BUTTON_FIRE_SELECT: Int = 5
        const val BUTTON_UNLOAD: Int = 6
        const val BUTTON_HUE_SLIDER: Int = 7
        const val BUTTON_SATURATION_SLIDER: Int = 8
        const val BUTTON_SLOT_BASE: Int = 1000
        const val BUTTON_INVENTORY_BASE: Int = 2000

        var HIDE_GUN_PROPERTY_PANEL: Boolean = true

        fun getSlotTextureXOffset(gunItem: ItemStack, iGun: IGun, attachmentType: AttachmentType): Int {
            if (!iGun.allowAttachmentType(gunItem, attachmentType)) {
                return ICON_UV_SIZE * 6
            }
            return when (attachmentType) {
                AttachmentType.GRIP -> 0
                AttachmentType.LASER -> ICON_UV_SIZE
                AttachmentType.MUZZLE -> ICON_UV_SIZE * 2
                AttachmentType.SCOPE -> ICON_UV_SIZE * 3
                AttachmentType.STOCK -> ICON_UV_SIZE * 4
                AttachmentType.EXTENDED_MAG -> ICON_UV_SIZE * 5
                AttachmentType.NONE -> ICON_UV_SIZE * 6
            }
        }
    }
}
