package com.hellwaves.hellwavesmod;

import com.hellwaves.hellwavesmod.HWMobs.SkeletonGuardian;
import com.hellwaves.hellwavesmod.client.GuardianInventoryScreen;
import com.hellwaves.hellwavesmod.client.SkeletonGuardianRenderer;
import com.hellwaves.hellwavesmod.client.WarpedMinerRenderer;
import com.hellwaves.hellwavesmod.inventory.ModMenuTypes;
import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.world.entity.monster.Skeleton;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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
        // ADICIONE ESTA LINHA PARA O ZOMBIE GUARDIAN
        event.registerEntityRenderer(HWDeferredRegister.SKELETON_GUARDIAN.get(),
                context -> new SkeletonGuardianRenderer(context)
        );
    }
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        System.out.println("=== [HELLWAVES DEBUG] RegisterMenuScreensEvent START ===");
        System.out.println("[HELLWAVES] MenuType ID: " + ModMenuTypes.GUARDIAN_INVENTORY_MENU.getId());
        System.out.println("[HELLWAVES] MenuType exists: " + (ModMenuTypes.GUARDIAN_INVENTORY_MENU.get() != null));
        System.out.println("[HELLWAVES] Screen class: " + GuardianInventoryScreen.class.getName());

        try {
            event.register(ModMenuTypes.GUARDIAN_INVENTORY_MENU.get(), GuardianInventoryScreen::new);
            System.out.println("=== [HELLWAVES DEBUG] Screen registered SUCCESS ===");
        } catch (Exception e) {
            System.out.println("=== [HELLWAVES DEBUG] ERROR registering screen: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("=== [HELLWAVES DEBUG] RegisterMenuScreensEvent END ===");
    }
}