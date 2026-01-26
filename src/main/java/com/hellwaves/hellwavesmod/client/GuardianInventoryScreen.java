package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import com.hellwaves.hellwavesmod.inventory.GuardianInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class GuardianInventoryScreen extends AbstractContainerScreen<GuardianInventoryMenu> {

    public GuardianInventoryScreen(GuardianInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 220;
        this.imageHeight = 256;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Fundo principal
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        drawThickBorder(guiGraphics, x, y, imageWidth, imageHeight, 0xFF8B7355, 2);

        // Seção Guardian Equipment
        int guardianSectionX = x + 7;
        int guardianSectionY = y + 17;
        int guardianSectionWidth = 140;
        int guardianSectionHeight = 95;

        // Fundo da seção Guardian
        guiGraphics.fill(guardianSectionX, guardianSectionY,
                guardianSectionX + guardianSectionWidth,
                guardianSectionY + guardianSectionHeight,
                0xFFB0B0B0);
        drawThickBorder(guiGraphics, guardianSectionX, guardianSectionY,
                guardianSectionWidth, guardianSectionHeight,
                0xFF707070, 1);

        // Título Guardian
        String guardianTitle = "Guardian Equipment";
        guiGraphics.drawString(this.font, guardianTitle, guardianSectionX + 3, guardianSectionY - 10, 0x404040, false);

        // Stats do Guardian (posicionados à direita dos slots)
        drawGuardianStats(guiGraphics, x + 152, guardianSectionY + 5);

        // Seção Player Inventory
        int playerSectionX = x + 7;
        int playerSectionY = y + 142;
        int playerSectionWidth = 206;
        int playerSectionHeight = 108;

        // Fundo da seção Player
        guiGraphics.fill(playerSectionX, playerSectionY,
                playerSectionX + playerSectionWidth,
                playerSectionY + playerSectionHeight,
                0xFFB0B0B0);
        drawThickBorder(guiGraphics, playerSectionX, playerSectionY,
                playerSectionWidth, playerSectionHeight,
                0xFF707070, 1);

        // Título Player
        String playerTitle = "Inventory";
        guiGraphics.drawString(this.font, playerTitle, playerSectionX + 3, playerSectionY - 10, 0x404040, false);

        // Slots
        drawSlotBackgrounds(guiGraphics);
    }

    private void drawGuardianStats(GuiGraphics guiGraphics, int x, int y) {
        // Dados sincronizados
        float currentHealth = this.menu.getGuardianHealth();
        float maxHealth = this.menu.getGuardianMaxHealth();
        double attackDamage = this.menu.getGuardianAttackDamage();
        double armorValue = this.menu.getGuardianArmor();
        double armorToughness = this.menu.getGuardianToughness();

        if (maxHealth <= 0) {
            guiGraphics.drawString(this.font, "No Data", x, y, 0x808080, false);
            return;
        }

        int lineHeight = 10;
        int currentY = y;

        // Título Stats
        guiGraphics.drawString(this.font, "Stats", x, currentY, 0x404040, false);
        currentY += lineHeight + 2;

        // Health com coração
        String healthText = String.format("♥ %.1f/%.1f", currentHealth, maxHealth);
        guiGraphics.drawString(this.font, healthText, x, currentY, 0xC03030, false);
        currentY += lineHeight;

        // Attack com espada
        String damageText = String.format("⚔ %.1f", attackDamage);
        guiGraphics.drawString(this.font, damageText, x, currentY, 0x404040, false);
        currentY += lineHeight;

        // Armor com escudo
        String armorText = String.format("◈ %.1f", armorValue);
        guiGraphics.drawString(this.font, armorText, x, currentY, 0x404040, false);
        currentY += lineHeight;

        // Toughness com diamante
        String toughnessText = String.format("◆ %.1f", armorToughness);
        guiGraphics.drawString(this.font, toughnessText, x, currentY, 0x404040, false);
        currentY += lineHeight + 3;

        // Gear breakdown
        ZombieGuardian guardian = this.menu.getGuardian();
        if (guardian != null) {
            guiGraphics.drawString(this.font, "Gear", x, currentY, 0x404040, false);
            currentY += lineHeight + 2;

            // Weapon damage
            ItemStack mainhand = guardian.getItemBySlot(EquipmentSlot.MAINHAND);
            double weaponDamage = getWeaponDamage(mainhand);

            if (weaponDamage > 0) {
                String weaponText = String.format("+%.1f Damage", weaponDamage);
                guiGraphics.drawString(this.font, weaponText, x + 2, currentY, 0x606060, false);
                currentY += lineHeight - 1;
            }

            // Armor from gear
            double totalArmorFromGear = getArmorValue(guardian.getItemBySlot(EquipmentSlot.HEAD)) +
                    getArmorValue(guardian.getItemBySlot(EquipmentSlot.CHEST)) +
                    getArmorValue(guardian.getItemBySlot(EquipmentSlot.LEGS)) +
                    getArmorValue(guardian.getItemBySlot(EquipmentSlot.FEET));

            if (totalArmorFromGear > 0) {
                String armorGearText = String.format("+%.1f Armor", totalArmorFromGear);
                guiGraphics.drawString(this.font, armorGearText, x + 2, currentY, 0x606060, false);
                currentY += lineHeight - 1;
            }

            // Toughness from gear
            double totalToughness = getToughnessValue(guardian.getItemBySlot(EquipmentSlot.HEAD)) +
                    getToughnessValue(guardian.getItemBySlot(EquipmentSlot.CHEST)) +
                    getToughnessValue(guardian.getItemBySlot(EquipmentSlot.LEGS)) +
                    getToughnessValue(guardian.getItemBySlot(EquipmentSlot.FEET));

            if (totalToughness > 0) {
                String toughGearText = String.format("+%.1f Tough", totalToughness);
                guiGraphics.drawString(this.font, toughGearText, x + 2, currentY, 0x606060, false);
            }
        }
    }

    private double getWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        double totalDamage = 0.0;
        var modifiers = stack.getAttributeModifiers();

        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                totalDamage += entry.modifier().amount();
            }
        }

        return totalDamage;
    }

    private double getArmorValue(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        double totalArmor = 0.0;
        var modifiers = stack.getAttributeModifiers();

        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ARMOR)) {
                totalArmor += entry.modifier().amount();
            }
        }

        return totalArmor;
    }

    private double getToughnessValue(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        double totalToughness = 0.0;
        var modifiers = stack.getAttributeModifiers();

        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ARMOR_TOUGHNESS)) {
                totalToughness += entry.modifier().amount();
            }
        }

        return totalToughness;
    }

    private void drawSlotBackgrounds(GuiGraphics guiGraphics) {
        for (int i = 0; i < this.menu.slots.size(); i++) {
            var slot = this.menu.slots.get(i);
            if (slot != null && slot.x != -1000) {
                int slotX = this.leftPos + slot.x;
                int slotY = this.topPos + slot.y;

                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
                drawSlotBorder(guiGraphics, slotX - 1, slotY - 1, 0xFF373737, 0xFFFFFFFF);
            }
        }
    }

    private void drawSlotBorder(GuiGraphics guiGraphics, int x, int y, int darkColor, int lightColor) {
        guiGraphics.fill(x, y, x + 18, y + 1, darkColor);
        guiGraphics.fill(x, y, x + 1, y + 18, darkColor);
        guiGraphics.fill(x + 1, y + 17, x + 18, y + 18, lightColor);
        guiGraphics.fill(x + 17, y + 1, x + 18, y + 17, lightColor);
    }

    private void drawThickBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color, int thickness) {
        for (int i = 0; i < thickness; i++) {
            guiGraphics.fill(x + i, y + i, x + width - i, y + i + 1, color);
            guiGraphics.fill(x + i, y + height - i - 1, x + width - i, y + height - i, color);
            guiGraphics.fill(x + i, y + i, x + i + 1, y + height - i, color);
            guiGraphics.fill(x + width - i - 1, y + i, x + width - i, y + height - i, color);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Não renderizar labels padrão
    }
}