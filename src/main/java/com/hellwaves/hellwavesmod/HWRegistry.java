package com.hellwaves.hellwavesmod;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;

public class HWRegistry {

    public static final ResourceKey<Registry<Wave>> WAVE_REGISTRY_KEY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(HellwavesMod.MOD_ID, "waves")
    );

    public static final Registry<Wave> WAVE_REGISTRY = new RegistryBuilder<>(WAVE_REGISTRY_KEY)
            .sync(true)
            .defaultKey(ResourceLocation.fromNamespaceAndPath(HellwavesMod.MOD_ID, "empty"))
            .maxId(256)
            .create();
}
