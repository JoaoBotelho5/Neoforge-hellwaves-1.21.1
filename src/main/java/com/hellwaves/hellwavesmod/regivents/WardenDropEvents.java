package com.hellwaves.hellwavesmod.regivents;

import com.hellwaves.hellwavesmod.regivents.HWDeferredRegister;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Random;

public class WardenDropEvents {

    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onWardenDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Warden warden)) return;
        if (warden.level().isClientSide()) return;

        // 20% drop chance
        if (RANDOM.nextFloat() <= 0.20f) {
            ItemStack drop = new ItemStack(HWDeferredRegister.ECHO_RESONATOR.get());
            warden.spawnAtLocation(drop);
        }
    }
}
