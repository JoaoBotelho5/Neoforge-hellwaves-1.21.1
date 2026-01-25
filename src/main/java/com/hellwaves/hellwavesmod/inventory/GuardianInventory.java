package com.hellwaves.hellwavesmod.inventory;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class GuardianInventory implements Container {
    public final ZombieGuardian guardian;
    private final NonNullList<ItemStack> items;
    private static final int SIZE = 38; // 0-37

    // Layout:
    // 0-8: Grid 3x3 (storage)
    // 9-31: Extra storage (23 slots)
    // 32-35: Armor (HEAD, CHEST, LEGS, FEET)
    // 36-37: Hands (MAINHAND, OFFHAND)

    public GuardianInventory(ZombieGuardian guardian) {
        this.guardian = guardian;
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        loadFromEntity();
    }

    private void loadFromEntity() {
        // Load equipment from entity
        items.set(32, guardian.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        items.set(33, guardian.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        items.set(34, guardian.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
        items.set(35, guardian.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
        items.set(36, guardian.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        items.set(37, guardian.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND));

        // Load storage from NBT
        loadStorageFromNBT();
    }

    public void saveToEntity() {
        // Save equipment to entity
        guardian.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, items.get(32));
        guardian.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, items.get(33));
        guardian.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, items.get(34));
        guardian.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, items.get(35));
        guardian.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, items.get(36));
        guardian.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, items.get(37));

        // Save storage to NBT
        saveStorageToNBT();
    }

    private void loadStorageFromNBT() {
        CompoundTag persistentData = guardian.getPersistentData();
        if (persistentData.contains("GuardianStorage", 9)) { // 9 = LIST type
            ListTag storageList = persistentData.getList("GuardianStorage", 10); // 10 = COMPOUND type

            for (int i = 0; i < storageList.size() && i < 32; i++) { // 0-31 = storage slots
                CompoundTag itemTag = storageList.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;

                if (slot >= 0 && slot < 32) {
                    items.set(slot, ItemStack.parseOptional(guardian.level().registryAccess(), itemTag));
                }
            }
        }
    }

    private void saveStorageToNBT() {
        ListTag storageList = new ListTag();

        for (int i = 0; i < 32; i++) { // 0-31 = storage slots
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                storageList.add(stack.save(guardian.level().registryAccess(), itemTag));
            }
        }

        guardian.getPersistentData().put("GuardianStorage", storageList);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= SIZE) return ItemStack.EMPTY;
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (index < 0 || index >= SIZE) return ItemStack.EMPTY;

        ItemStack stack = items.get(index);
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack result;
        if (stack.getCount() <= count) {
            result = stack.copy();
            items.set(index, ItemStack.EMPTY);
        } else {
            result = stack.split(count);
        }

        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index < 0 || index >= SIZE) return ItemStack.EMPTY;

        ItemStack stack = items.get(index);
        if (!stack.isEmpty()) {
            items.set(index, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= SIZE) return;

        items.set(index, stack);

        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }

        setChanged();
    }

    @Override
    public void setChanged() {
        saveToEntity();
    }

    @Override
    public boolean stillValid(Player player) {
        return guardian.isAlive() && player.distanceToSqr(guardian) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}