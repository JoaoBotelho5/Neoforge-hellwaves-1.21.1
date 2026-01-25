package com.hellwaves.hellwavesmod.inventory;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import javax.annotation.Nullable;

public class GuardianInventoryMenu extends AbstractContainerMenu {

    @Nullable
    private final ZombieGuardian guardian;
    private final GuardianInventory guardianInventory;
    private final Player player;
    private final boolean isClientSide;
    private final ContainerData data;

    // POSIÇÕES DOS SLOTS - CENTRALIZADAS
    private static final int GUARDIAN_BASE_X = 30;
    private static final int GUARDIAN_BASE_Y = 30;

    // Armadura (coluna vertical à esquerda)
    private static final int ARMOR_X = GUARDIAN_BASE_X;
    private static final int ARMOR_Y = GUARDIAN_BASE_Y;

    // Mãos (horizontalmente ao lado das botas)
    private static final int HANDS_X = GUARDIAN_BASE_X + 18;
    private static final int HANDS_Y = GUARDIAN_BASE_Y + 54;

    // Grid 3x3 (mais à direita, centralizado)
    private static final int GRID_X = GUARDIAN_BASE_X + 54;
    private static final int GRID_Y = GUARDIAN_BASE_Y;

    // Player inventory (centralizado horizontalmente)
    private static final int PLAYER_BASE_X = 30;
    private static final int PLAYER_BASE_Y = 153;
    private static final int PLAYER_INV_X = PLAYER_BASE_X;
    private static final int PLAYER_INV_Y = PLAYER_BASE_Y;
    private static final int HOTBAR_X = PLAYER_BASE_X;
    private static final int HOTBAR_Y = PLAYER_BASE_Y + 58;

    // Contadores
    private static final int PLAYER_SLOT_COUNT = 36;
    private static final int GUARDIAN_SLOT_COUNT = 38;
    private static final int TOTAL_SLOT_COUNT = PLAYER_SLOT_COUNT + GUARDIAN_SLOT_COUNT;

    // Data slots para sincronizar stats
    private static final int DATA_HEALTH = 0;
    private static final int DATA_MAX_HEALTH = 1;
    private static final int DATA_ATTACK_DAMAGE = 2;
    private static final int DATA_ARMOR = 3;
    private static final int DATA_TOUGHNESS = 4;
    private static final int DATA_COUNT = 5;

    // Construtor para MenuType (CLIENTE SEM DADOS)
    public GuardianInventoryMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, true);
    }

    // Construtor com FriendlyByteBuf (CLIENTE COM DADOS DO SERVIDOR)
    public GuardianInventoryMenu(int containerId, Inventory playerInventory, net.minecraft.network.FriendlyByteBuf extraData) {
        super(ModMenuTypes.GUARDIAN_INVENTORY_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.isClientSide = true;

        // No client, try to get guardian from world
        int guardianId = extraData.readInt();
        this.guardian = (ZombieGuardian) playerInventory.player.level().getEntity(guardianId);
        this.guardianInventory = guardian != null ? guardian.getGuardianInventory() : null;

        this.data = new SimpleContainerData(DATA_COUNT);

        addPlayerSlots(playerInventory);
        addGuardianSlots();
        addDataSlots(this.data);
    }

    // Construtor com Guardian (SERVER)
    public GuardianInventoryMenu(int containerId, Inventory playerInventory, @Nullable ZombieGuardian guardian) {
        this(containerId, playerInventory, guardian, false);
    }

    // Construtor privado principal
    private GuardianInventoryMenu(int containerId, Inventory playerInventory, @Nullable ZombieGuardian guardian, boolean clientConstructor) {
        super(ModMenuTypes.GUARDIAN_INVENTORY_MENU.get(), containerId);
        this.guardian = guardian;
        this.guardianInventory = guardian != null ? guardian.getGuardianInventory() : null;
        this.player = playerInventory.player;
        this.isClientSide = clientConstructor || player.level().isClientSide();

        // Container data para sincronizar stats
        if (this.isClientSide) {
            this.data = new SimpleContainerData(DATA_COUNT);
        } else {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    if (guardian == null) return 0;
                    return switch (index) {
                        case DATA_HEALTH -> (int) (guardian.getHealth() * 10);
                        case DATA_MAX_HEALTH -> (int) (guardian.getMaxHealth() * 10);
                        case DATA_ATTACK_DAMAGE -> (int) (guardian.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 10);
                        case DATA_ARMOR -> (int) (guardian.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) * 10);
                        case DATA_TOUGHNESS -> (int) (guardian.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) * 10);
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        }

        addPlayerSlots(playerInventory);
        addGuardianSlots();
        addDataSlots(this.data);
    }

    // Getters para stats
    public float getGuardianHealth() {
        return this.data.get(DATA_HEALTH) / 10.0f;
    }

    public float getGuardianMaxHealth() {
        return this.data.get(DATA_MAX_HEALTH) / 10.0f;
    }

    public double getGuardianAttackDamage() {
        return this.data.get(DATA_ATTACK_DAMAGE) / 10.0;
    }

    public double getGuardianArmor() {
        return this.data.get(DATA_ARMOR) / 10.0;
    }

    public double getGuardianToughness() {
        return this.data.get(DATA_TOUGHNESS) / 10.0;
    }

    private void addPlayerSlots(Inventory playerInventory) {
        // Inventário 3x9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Hotbar 1x9
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, HOTBAR_X + col * 18, HOTBAR_Y));
        }
    }

    private void addGuardianSlots() {
        if (guardianInventory == null) {
            // Add fake slots for client before entity is synced
            addClientGuardianSlots();
            return;
        }

        // Armadura (4 slots verticais) - índices 32-35
        addSlot(new GuardianArmorSlot(guardianInventory, EquipmentSlot.HEAD, 32, ARMOR_X, ARMOR_Y));
        addSlot(new GuardianArmorSlot(guardianInventory, EquipmentSlot.CHEST, 33, ARMOR_X, ARMOR_Y + 18));
        addSlot(new GuardianArmorSlot(guardianInventory, EquipmentSlot.LEGS, 34, ARMOR_X, ARMOR_Y + 36));
        addSlot(new GuardianArmorSlot(guardianInventory, EquipmentSlot.FEET, 35, ARMOR_X, ARMOR_Y + 54));

        // Mãos (2 slots horizontais) - índices 36-37
        addSlot(new GuardianSlot(guardianInventory, 36, HANDS_X, HANDS_Y));         // MAINHAND
        addSlot(new GuardianSlot(guardianInventory, 37, HANDS_X + 18, HANDS_Y));    // OFFHAND

        // Grid 3x3 (9 slots) - índices 0-8
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new GuardianSlot(guardianInventory, row * 3 + col, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // Slots extras invisíveis (23 slots) - índices 9-31
        for (int i = 0; i < 23; i++) {
            addSlot(new GuardianSlot(guardianInventory, 9 + i, -1000, -1000));
        }
    }

    private void addClientGuardianSlots() {
        // Armadura (4 slots verticais)
        addSlot(new FakeSlot(ARMOR_X, ARMOR_Y));
        addSlot(new FakeSlot(ARMOR_X, ARMOR_Y + 18));
        addSlot(new FakeSlot(ARMOR_X, ARMOR_Y + 36));
        addSlot(new FakeSlot(ARMOR_X, ARMOR_Y + 54));

        // Mãos (2 slots horizontais)
        addSlot(new FakeSlot(HANDS_X, HANDS_Y));
        addSlot(new FakeSlot(HANDS_X + 18, HANDS_Y));

        // Grid 3x3 (9 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new FakeSlot(GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // Slots extras invisíveis (23 slots)
        for (int i = 0; i < 23; i++) {
            addSlot(new FakeSlot(-1000, -1000));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // From guardian to player
            if (index >= PLAYER_SLOT_COUNT && index < TOTAL_SLOT_COUNT) {
                if (!this.moveItemStackTo(slotStack, 0, PLAYER_SLOT_COUNT, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // From player to guardian
            else if (index < PLAYER_SLOT_COUNT) {
                if (!this.moveItemStackTo(slotStack, PLAYER_SLOT_COUNT, TOTAL_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, slotStack);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return guardian != null && guardian.isAlive() && player.distanceToSqr(guardian) <= 64.0D;
    }

    @Nullable
    public ZombieGuardian getGuardian() {
        return guardian;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (guardianInventory != null) {
            guardianInventory.setChanged();
        }
    }

    // Slot FAKE
    private static class FakeSlot extends Slot {
        public FakeSlot(int x, int y) {
            super(new net.minecraft.world.SimpleContainer(1), 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public ItemStack getItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public int getMaxStackSize() {
            return 0;
        }
    }

    // Slot REAL
    private static class GuardianSlot extends Slot {
        public GuardianSlot(GuardianInventory container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    // Slot de armadura
    private static class GuardianArmorSlot extends Slot {
        private final EquipmentSlot slotType;

        public GuardianArmorSlot(GuardianInventory container, EquipmentSlot slotType, int index, int x, int y) {
            super(container, index, x, y);
            this.slotType = slotType;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return slotType != null && stack.canEquip(slotType, ((GuardianInventory) this.container).guardian);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}