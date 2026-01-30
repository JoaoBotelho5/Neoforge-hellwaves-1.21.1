package com.hellwaves.hellwavesmod.inventory;

import com.hellwaves.hellwavesmod.HellwavesMod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.MENU, HellwavesMod.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<MenuType<?>, MenuType<GuardianInventoryMenu>> GUARDIAN_INVENTORY_MENU =
            MENUS.register("guardian_inventory",
                    () -> IMenuTypeExtension.create(GuardianInventoryMenu::new));
// Isso já está correto - o MenuType usará o construtor com FriendlyByteBuf


}