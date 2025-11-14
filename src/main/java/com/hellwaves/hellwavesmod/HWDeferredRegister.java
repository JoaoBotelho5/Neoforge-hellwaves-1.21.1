package com.hellwaves.hellwavesmod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HWDeferredRegister {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            BuiltInRegistries.BLOCK,
            HellwavesMod.MOD_ID
    );

    public static final DeferredHolder<Block, ActivatorBlock> ACTIVATOR_BLOCK = BLOCKS.register(
            // block name in code
            "activator_block",
            // STATS of the custom block 1
            () -> new ActivatorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, HellwavesMod.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> ACTIVATOR_BLOCK_ITEM = ITEMS.register(
           "activator_block",
            () -> new BlockItem(ACTIVATOR_BLOCK.get(), new Item.Properties())
    );
}
