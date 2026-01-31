package com.hellwaves.hellwavesmod.regivents;

import com.hellwaves.hellwavesmod.HWMobs.SkeletonGuardian;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

public class SkeletonGuardianEvents {

    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        LivingEntity target = event.getNewAboutToBeSetTarget();

        /* =========================================================
           A) Impedir ataques PROATIVOS ao SkeletonGuardian
           ========================================================= */
        if (target instanceof SkeletonGuardian) {
            boolean isFriendlyMob =
                    attacker instanceof IronGolem ||
                            attacker instanceof SnowGolem ||
                            attacker instanceof Wolf;

            if (isFriendlyMob) {
                // Só permitir se for DEFESA (foi atacado primeiro)
                boolean isDefending = attacker.getLastHurtByMob() == target;

                if (!isDefending) {
                    event.setCanceled(true);
                }
            }
        }

        /* =========================================================
           B) Impedir ataques PROATIVOS do SkeletonGuardian
           ========================================================= */
        if (attacker instanceof SkeletonGuardian) {
            boolean isFriendlyTarget =
                    target instanceof IronGolem ||
                            target instanceof SnowGolem ||
                            target instanceof Wolf;

            if (isFriendlyTarget) {
                boolean isDefending = attacker.getLastHurtByMob() == target;

                if (!isDefending) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
