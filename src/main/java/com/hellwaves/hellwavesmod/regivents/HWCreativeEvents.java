package com.hellwaves.hellwavesmod.regivents;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public class HWCreativeEvents {

    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(HWDeferredRegister.ZOMBIE_GUARDIAN_SPAWN_EGG.get());
        }
    }
}
