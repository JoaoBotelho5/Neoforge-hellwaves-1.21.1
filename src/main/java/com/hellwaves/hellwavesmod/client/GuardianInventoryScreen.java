package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.IGuardian;
import com.hellwaves.hellwavesmod.inventory.GuardianInventoryMenu;
import com.hellwaves.hellwavesmod.packets.Modpackets;
import com.hellwaves.hellwavesmod.packets.upgradeguardianpacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class GuardianInventoryScreen extends AbstractContainerScreen<GuardianInventoryMenu> {

    private Button upgradeButton;

    public GuardianInventoryScreen(GuardianInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;  // Increased from 220
        this.imageHeight = 280; // Increased from 256
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        // Add upgrade button - moved down below cost display
        int buttonX = this.leftPos + 170;  // Centered under stats
        int buttonY = this.topPos + 142;   // Moved down from 120

        // FIXED: Use synced level from menu
        int currentLevel = this.menu.getGuardianLevel();
        boolean canUpgrade = currentLevel < 5;

        upgradeButton = Button.builder(
                        Component.literal(canUpgrade ? "Upgrade" : "MAX"),
                        button -> onUpgradeClicked()
                )
                .bounds(buttonX, buttonY, 70, 20)  // Slightly wider
                .build();

        upgradeButton.active = canUpgrade;

        this.addRenderableWidget(upgradeButton);
    }

    private void onUpgradeClicked() {
        LivingEntity guardianEntity = this.menu.getGuardianEntity();
        if (guardianEntity instanceof IGuardian guardian) {
            // Check if can upgrade based on current level
            int currentLevel = guardian.getGuardianLevel();
            if (currentLevel < 5) {
                // Send packet to server
                Modpackets.sendToServer(new upgradeguardianpacket(guardianEntity.getId()));
                // Menu will auto-sync the new level through container data
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Fundo principal
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        drawThickBorder(guiGraphics, x, y, imageWidth, imageHeight, 0xFF8B7355, 2);

        // Seção Guardian Equipment - recentered
        int guardianSectionX = x + 20;   // Adjusted
        int guardianSectionY = y + 25;   // Adjusted
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
        drawGuardianStats(guiGraphics, x + 170, guardianSectionY + 5);  // Adjusted

        // Draw gear bonus below guardian equipment section
        drawGearBonus(guiGraphics, guardianSectionX + 5, guardianSectionY + guardianSectionHeight + 5);

        // Seção Player Inventory - recentered
        int playerSectionX = x + 25;    // Adjusted
        int playerSectionY = y + 165;   // Adjusted
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
        LivingEntity guardian = this.menu.getGuardianEntity();
        // Dados sincronizados
        float currentHealth = this.menu.getGuardianHealth();
        float maxHealth = this.menu.getGuardianMaxHealth();
        double baseDamage = guardian != null
                ? guardian.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
                : 0.0;

        double weaponBonus = guardian != null
                ? getWeaponDamage(guardian.getItemBySlot(EquipmentSlot.MAINHAND))
                : 0.0;
        double attackDamage = baseDamage + weaponBonus;        double armorValue = this.menu.getGuardianArmor();
        double armorToughness = this.menu.getGuardianToughness();
        int currentLevel = this.menu.getGuardianLevel();  // FIXED: Use synced level

        if (maxHealth <= 0) {
            guiGraphics.drawString(this.font, "No Data", x, y, 0x808080, false);
            return;
        }

        int lineHeight = 10;
        int currentY = y;

        // Título Stats
        guiGraphics.drawString(this.font, "Stats", x, currentY, 0x404040, false);
        currentY += lineHeight + 2;

        // Level do Guardian - FIXED: Use synced level
        String levelText = String.format("§6Level %d", currentLevel);
        guiGraphics.drawString(this.font, levelText, x, currentY, 0xFFAA00, false);
        currentY += lineHeight;

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

        // Upgrade cost - FIXED: Calculate from synced level
        if (currentLevel < 5) {  // Can upgrade
            int nextLevel = currentLevel + 1;
            ItemStack upgradeCost = getUpgradeCostForLevel(currentLevel);  // FIXED: Calculate locally

            guiGraphics.drawString(this.font, "§7Next Upgrade:", x, currentY, 0x808080, false);
            currentY += lineHeight;

            // Draw upgrade level info - FIXED
            String levelInfo = String.format("§eLevel %d → %d", currentLevel, nextLevel);
            guiGraphics.drawString(this.font, levelInfo, x, currentY, 0xFFAA00, false);
            currentY += lineHeight;

            // Draw cost - FIXED to show actual cost
            if (!upgradeCost.isEmpty()) {
                String costText = String.format("§7Cost: %dx", upgradeCost.getCount());
                guiGraphics.drawString(this.font, costText, x, currentY, 0x606060, false);
                currentY += lineHeight;

                // Draw item icon
                guiGraphics.renderItem(upgradeCost, x, currentY);

                // Draw item name next to icon
                String itemName = upgradeCost.getHoverName().getString();
                if (itemName.length() > 12) {
                    itemName = itemName.substring(0, 12) + "...";
                }
                guiGraphics.drawString(this.font, itemName, x + 18, currentY + 4, 0x606060, false);
            }
        } else {  // Max level
            guiGraphics.drawString(this.font, "§6MAX LEVEL", x, currentY, 0xFFAA00, false);
        }
    }

    // NEW: Separate method for gear bonus display below guardian equipment
    private void drawGearBonus(GuiGraphics guiGraphics, int x, int y) {
        LivingEntity guardianEntity = this.menu.getGuardianEntity();
        if (guardianEntity == null) {
            return;
        }

        // Title
        guiGraphics.drawString(this.font, "Gear Bonus:", x, y, 0x404040, false);
        y += 12;

        // Calculate all bonuses
        ItemStack mainhand = guardianEntity.getItemBySlot(EquipmentSlot.MAINHAND);
        double weaponDamage = getWeaponDamage(mainhand);

        double totalArmorFromGear = getArmorValue(guardianEntity.getItemBySlot(EquipmentSlot.HEAD)) +
                getArmorValue(guardianEntity.getItemBySlot(EquipmentSlot.CHEST)) +
                getArmorValue(guardianEntity.getItemBySlot(EquipmentSlot.LEGS)) +
                getArmorValue(guardianEntity.getItemBySlot(EquipmentSlot.FEET));

        double totalToughness = getToughnessValue(guardianEntity.getItemBySlot(EquipmentSlot.HEAD)) +
                getToughnessValue(guardianEntity.getItemBySlot(EquipmentSlot.CHEST)) +
                getToughnessValue(guardianEntity.getItemBySlot(EquipmentSlot.LEGS)) +
                getToughnessValue(guardianEntity.getItemBySlot(EquipmentSlot.FEET));

        // Horizontal layout: DMG and ARM on top row
        int spacing = 50;

        // DMG (left)
        if (weaponDamage > 0) {
            String dmgText = String.format("DMG: +%.1f", weaponDamage);
            guiGraphics.drawString(this.font, dmgText, x, y, 0x606060, false);
        } else {
            guiGraphics.drawString(this.font, "DMG: +0.0", x, y, 0x808080, false);
        }

        // ARM (right)
        if (totalArmorFromGear > 0) {
            String armText = String.format("ARM: +%.1f", totalArmorFromGear);
            guiGraphics.drawString(this.font, armText, x + spacing, y, 0x606060, false);
        } else {
            guiGraphics.drawString(this.font, "ARM: +0.0", x + spacing, y, 0x808080, false);
        }

        y += 10;

        // TOUGH centered on bottom row - FIXED calculation
        String toughText = totalToughness > 0 ?
                String.format("TOUGH: +%.1f", totalToughness) :
                "TOUGH: +0.0";

        // ARM starts at x + spacing
        // To center TOUGH, we need the midpoint between:
        // - DMG starting position (x)
        // - ARM ending position (x + spacing + armTextWidth)
        String armDisplayText = totalArmorFromGear > 0 ?
                String.format("ARM: +%.1f", totalArmorFromGear) :
                "ARM: +0.0";
        int armTextWidth = this.font.width(armDisplayText);

        // The total visual span goes from x to (x + spacing + armTextWidth)
        int totalSpan = spacing + armTextWidth;

        // Center TOUGH within this span
        int toughTextWidth = this.font.width(toughText);
        int toughX = x + (totalSpan - toughTextWidth) / 2;

        guiGraphics.drawString(this.font, toughText, toughX, y,
                totalToughness > 0 ? 0x606060 : 0x808080, false);
    }

    // FIXED: Add method to calculate upgrade cost locally from level
    // This now detects guardian type and returns appropriate costs
    private ItemStack getUpgradeCostForLevel(int currentLevel) {
        LivingEntity guardianEntity = this.menu.getGuardianEntity();

        // Check if it's a Skeleton Guardian
        if (guardianEntity instanceof com.hellwaves.hellwavesmod.HWMobs.SkeletonGuardian) {
            return switch (currentLevel) {
                case 1 -> new ItemStack(net.minecraft.world.item.Items.GOLD_BLOCK, 1);
                case 2 -> new ItemStack(net.minecraft.world.item.Items.EMERALD_BLOCK, 1);
                case 3 -> new ItemStack(net.minecraft.world.item.Items.ENDER_EYE, 1);
                case 4 -> new ItemStack(net.minecraft.world.item.Items.NAUTILUS_SHELL, 1);
                default -> ItemStack.EMPTY;
            };
        }

        // Default to Zombie Guardian costs
        return switch (currentLevel) {
            case 1 -> new ItemStack(net.minecraft.world.item.Items.BOOK, 10);
            case 2 -> new ItemStack(net.minecraft.world.item.Items.DIAMOND_BLOCK, 1);
            case 3 -> new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP, 1);
            case 4 -> new ItemStack(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL, 1);
            default -> ItemStack.EMPTY;
        };
    }

    private double getWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        double totalDamage = 1.0;
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