package com.hellwaves.hellwavesmod;


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

        // new Wrapper(modBus);

    }


}
