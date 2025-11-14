package com.hellwaves.hellwavesmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NewRegistryEvent;

import static com.hellwaves.hellwavesmod.HWRegistry.WAVE_REGISTRY;

public class Wrapper {

    public Wrapper(IEventBus modBus) {
        // Only register Wave registry here, blocks/items are already registered in ModDeferredRegister
        modBus.addListener(this::onRegister);
    }

    public void onRegister(NewRegistryEvent event) {
        event.register(WAVE_REGISTRY);
    }
}
