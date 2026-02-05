package com.hellwaves.hellwavesmod.Blocks;

import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import com.hellwaves.hellwavesmod.HellwavesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {


    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HellwavesMod.MOD_ID);


    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ActivatorBlockEntity>>
            ACTIVATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "activator_block_entity",
                    () -> BlockEntityType.Builder.of(
                            ActivatorBlockEntity::new,
                            HWDeferredRegister.ACTIVATOR_BLOCK.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EliteActivatorBlockEntity>>
            ELITE_ACTIVATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "elite_activator_block_entity",
                    () -> BlockEntityType.Builder.of(
                            EliteActivatorBlockEntity::new,
                            HWDeferredRegister.ELITE_ACTIVATOR_BLOCK.get()
                    ).build(null)
            );
}

