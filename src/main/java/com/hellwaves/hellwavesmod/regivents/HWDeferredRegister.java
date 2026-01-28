package com.hellwaves.hellwavesmod.regivents;

import com.hellwaves.hellwavesmod.Blocks.ActivatorBlock;
import com.hellwaves.hellwavesmod.Blocks.EliteActivatorBlock;
import com.hellwaves.hellwavesmod.HWMobs.UndeadLord;
import com.hellwaves.hellwavesmod.HWMobs.WarpedMiner;
import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import com.hellwaves.hellwavesmod.HellwavesMod;
import com.hellwaves.hellwavesmod.Items.EmptySoulCageItem;
import com.hellwaves.hellwavesmod.Items.SoulCageItem;
import com.hellwaves.hellwavesmod.Items.ZombieGuardianSummonerItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
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
                    .strength(-1.0f)
                    .requiresCorrectToolForDrops()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .forceSolidOn() // Force the block to be considered solid
            )
    );

    public static final DeferredHolder<Item, BlockItem> ACTIVATOR_BLOCK_ITEM = ITEMS.register(
            "activator_block",
            () -> new BlockItem(ACTIVATOR_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredHolder<Block, EliteActivatorBlock> ELITE_ACTIVATOR_BLOCK = BLOCKS.register(
            "elite_activator_block",
            () -> new EliteActivatorBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f)
                    .requiresCorrectToolForDrops()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .forceSolidOn() // Force the block to be considered solid
            )
    );

    public static final DeferredHolder<Item, BlockItem> ELITE_ACTIVATOR_BLOCK_ITEM = ITEMS.register(
            "elite_activator_block",
            () -> new BlockItem(ELITE_ACTIVATOR_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredHolder<EntityType<?>, EntityType<UndeadLord>> UNDEAD_LORD = ENTITIES.register(
            "undead_lord",
            () -> EntityType.Builder.of(UndeadLord::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F) // Tamanho normal de zombie
                    .clientTrackingRange(8)
                    .build("undead_lord")
    );

    // ADICIONE ESTE REGISTRO PARA O WARPED MINER
    public static final DeferredHolder<EntityType<?>, EntityType<WarpedMiner>> WARPED_MINER = ENTITIES.register(
            "warped_miner",
            () -> EntityType.Builder.of(WarpedMiner::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F) // Tamanho do Zombified Piglin
                    .clientTrackingRange(8)
                    .build("warped_miner")
    );

    // ADICIONE ESTE REGISTRO PARA O ZOMBIE GUARDIAN
    public static final DeferredHolder<EntityType<?>, EntityType<ZombieGuardian>> ZOMBIE_GUARDIAN = ENTITIES.register(
            "zombie_guardian",
            () -> EntityType.Builder.of(ZombieGuardian::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F) // Tamanho normal de zombie
                    .clientTrackingRange(8)
                    .build("zombie_guardian")
    );

    // Spawn egg removed - use Zombie Guardian Soul item instead

    public static final DeferredHolder<Item, Item> ZOMBIE_GUARDIAN_SUMMONER =
            ITEMS.register("zombie_guardian_summoner",
                    () -> new ZombieGuardianSummonerItem(
                            new Item.Properties().stacksTo(16)
                    )
            );

    // Soul Cage Items
    public static final DeferredHolder<Item, Item> EMPTY_SOUL_CAGE = ITEMS.register(
            "empty_soul_cage",
            () -> new EmptySoulCageItem(
                    new Item.Properties()
                            .stacksTo(16)
            )
    );

    public static final DeferredHolder<Item, Item> SOUL_CAGE = ITEMS.register(
            "soul_cage",
            () -> new SoulCageItem(
                    new Item.Properties()
                            .stacksTo(1) // Can only stack 1 since each contains unique guardian data
            )
    );



    public static void registerAttributes(IEventBus modEventBus) {
        modEventBus.addListener(HWDeferredRegister::onEntityAttributeCreation);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {

        event.put(UNDEAD_LORD.get(), UndeadLord.createAttributes().build());

        event.put(WARPED_MINER.get(), WarpedMiner.createAttributes().build());

        event.put(ZOMBIE_GUARDIAN.get(), ZombieGuardian.createAttributes().build());
    }

}