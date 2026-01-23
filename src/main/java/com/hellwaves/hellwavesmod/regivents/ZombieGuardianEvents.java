package com.hellwaves.hellwavesmod.regivents;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

public class ZombieGuardianEvents {

    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        LivingEntity target = event.getNewAboutToBeSetTarget();

        // 1. Iron Golem tentando atacar ZombieGuardian
        if (attacker instanceof IronGolem && target instanceof ZombieGuardian) {
            event.setCanceled(true);
            return;
        }

        // 2. ZombieGuardian tentando atacar Iron Golem
        if (attacker instanceof ZombieGuardian && target instanceof IronGolem) {
            event.setCanceled(true);
            return;
        }
    }
}