package com.hellwaves.hellwavesmod.regivents;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

public class ZombieGuardianEvents {

    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        LivingEntity target = event.getNewAboutToBeSetTarget();

        // A: Impedir ataque PROATIVO destes mobs ao ZombieGuardian
        if (target instanceof ZombieGuardian) {
            boolean isFriendlyMob = attacker instanceof IronGolem ||
                    attacker instanceof SnowGolem ||
                    attacker instanceof Wolf;

            if (isFriendlyMob) {
                // Verificar se é em DEFESA (foi atacado primeiro)
                boolean isDefending = attacker.getLastHurtByMob() == target;

                if (!isDefending) {
                    // Não é defesa - cancelar ataque proativo
                    event.setCanceled(true);
                }
                // Se IS defesa, permite (não cancela)
            }
        }

        // B: Impedir ataque PROATIVO do ZombieGuardian a estes mobs
        if (attacker instanceof ZombieGuardian) {
            boolean isFriendlyTarget = target instanceof IronGolem ||
                    target instanceof SnowGolem ||
                    target instanceof Wolf;

            if (isFriendlyTarget) {
                // Verificar se é em DEFESA (foi atacado primeiro)
                boolean isDefending = attacker.getLastHurtByMob() == target;

                if (!isDefending) {
                    // Não é defesa - cancelar ataque proativo
                    event.setCanceled(true);
                }
                // Se IS defesa, permite (não cancela)
            }
        }
    }
}