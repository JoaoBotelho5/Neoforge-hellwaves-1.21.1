package com.hellwaves.hellwavesmod.regivents;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public class HWCreativeEvents {

    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(HWDeferredRegister.ZOMBIE_GUARDIAN_SUMMONER.get());
        }

        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(HWDeferredRegister.SKELETON_GUARDIAN_SUMMONER.get());
        }

        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(HWDeferredRegister.ACTIVATOR_BLOCK_ITEM.get());
        }

        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(HWDeferredRegister.ELITE_ACTIVATOR_BLOCK_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HWDeferredRegister.EMPTY_SOUL_CAGE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(HWDeferredRegister.EMPTY_SOUL_CAGE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HWDeferredRegister.RECALLER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HWDeferredRegister.SOULTETHER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HWDeferredRegister.GREAT_SWORD.get());
        }
    }
}
