package com.hellwaves.hellwavesmod;

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
        event.registerEntityRenderer(HWDeferredRegister.PIGLIN_LORD.get(),
                context -> new ZombieRenderer(context)
        );
    }
}