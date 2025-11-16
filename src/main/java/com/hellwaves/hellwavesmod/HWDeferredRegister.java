package com.hellwaves.hellwavesmod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HWDeferredRegister {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            BuiltInRegistries.BLOCK,
            HellwavesMod.MOD_ID
    );

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            BuiltInRegistries.ENTITY_TYPE, HellwavesMod.MOD_ID
    );

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, HellwavesMod.MOD_ID);



    public static final DeferredHolder<Block, ActivatorBlock> ACTIVATOR_BLOCK = BLOCKS.register(
            // block name in code
            "activator_block",
            // STATS of the custom block 1
            () -> new ActivatorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredHolder<Item, BlockItem> ACTIVATOR_BLOCK_ITEM = ITEMS.register(
           "activator_block",
            () -> new BlockItem(ACTIVATOR_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredHolder<Block, EliteActivatorBlock> ELITE_ACTIVATOR_BLOCK = BLOCKS.register(
            "elite_activator_block",
            () -> new EliteActivatorBlock(BlockBehaviour.Properties.of()
                    .strength(5.0f)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final DeferredHolder<Item, BlockItem> ELITE_ACTIVATOR_BLOCK_ITEM = ITEMS.register(
            "elite_activator_block",
            () -> new BlockItem(ELITE_ACTIVATOR_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredHolder<EntityType<?>, EntityType<UndeadLord>> UNDEAD_LORD = ENTITIES.register(
            "piglin_lord",
            () -> EntityType.Builder.of(UndeadLord::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F) // Tamanho normal de zombie
                    .clientTrackingRange(8)
                    .build("piglin_lord")
    );

    public static void registerAttributes(IEventBus modEventBus) {
        modEventBus.addListener(HWDeferredRegister::onEntityAttributeCreation);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        System.out.println("=== Registering Piglin Lord Attributes ===");
        event.put(UNDEAD_LORD.get(), UndeadLord.createAttributes().build());
        System.out.println("=== Piglin Lord Attributes Registered ===");
    }

}
