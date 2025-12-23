package com.hellwaves.hellwavesmod;

import com.hellwaves.hellwavesmod.client.WarpedMinerRenderer;
import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = HellwavesMod.MOD_ID, value = Dist.CLIENT)
public class HWClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Use o renderizador padrão de Zombie - funciona perfeitamente!
        event.registerEntityRenderer(HWDeferredRegister.UNDEAD_LORD.get(),
                context -> new ZombieRenderer(context)
        );

        // Use o renderizador padrão de Piglin para o Warped Miner
        event.registerEntityRenderer(HWDeferredRegister.WARPED_MINER.get(),
                context -> new WarpedMinerRenderer(context)
        );

        // ADICIONE ESTA LINHA PARA O ZOMBIE GUARDIAN
        event.registerEntityRenderer(HWDeferredRegister.ZOMBIE_GUARDIAN.get(),
                context -> new ZombieRenderer(context) // Usa o mesmo renderer de zombie
        );
    }
}