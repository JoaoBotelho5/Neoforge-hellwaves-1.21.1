package com.hellwaves.hellwavesmod;


import com.hellwaves.hellwavesmod.Blocks.ModBlockEntities;
import com.hellwaves.hellwavesmod.Items.Recaller;
import com.hellwaves.hellwavesmod.equipment.EquipmentConfig;
import com.hellwaves.hellwavesmod.inventory.ModMenuTypes;
import com.hellwaves.hellwavesmod.packets.Modpackets;
import com.hellwaves.hellwavesmod.regivents.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(HellwavesMod.MOD_ID)
public class HellwavesMod {

    public static final String MOD_ID = "hellwavesmod";

    public HellwavesMod(IEventBus modBus) {
        // IEventBus modBus = NeoForge.EVENT_BUS.getModBus(MOD_ID);
        EquipmentConfig.load();
        
        HWDeferredRegister.BLOCKS.register(modBus);

        HWDeferredRegister.ITEMS.register(modBus);

        HWDeferredRegister.ENTITIES.register(modBus);

        ModBlockEntities.BLOCK_ENTITIES.register(modBus);

        HWDeferredRegister.registerAttributes(modBus);

        NeoForge.EVENT_BUS.register(new HWEvents());

        NeoForge.EVENT_BUS.register(new ZombieGuardianEvents());

        NeoForge.EVENT_BUS.register(new SkeletonGuardianEvents());

        NeoForge.EVENT_BUS.register(WardenDropEvents.class);

        NeoForge.EVENT_BUS.register(Recaller.class);

        modBus.addListener(HWCreativeEvents::onBuildCreativeTab);

        ModMenuTypes.MENUS.register(modBus);
        System.out.println("[HELLWAVES] ModMenuTypes registered");

        modBus.addListener(Modpackets::register);

        // new Wrapper(modBus);

    }


}
