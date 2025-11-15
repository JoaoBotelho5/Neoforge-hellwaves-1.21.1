package com.hellwaves.hellwavesmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    // NeoForge 1.21.1 requires the wildcard here.
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HellwavesMod.MOD_ID);

    // IMPORTANT:
    // The *left* side must be <BlockEntityType<?>, BlockEntityType<ActivatorBlockEntity>>
    // because the register holds wildcard types.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ActivatorBlockEntity>>
            ACTIVATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "activator_block_entity",
                    () -> BlockEntityType.Builder.of(
                            ActivatorBlockEntity::new,
                            HWDeferredRegister.ACTIVATOR_BLOCK.get()
                    ).build(null)
            );
}
