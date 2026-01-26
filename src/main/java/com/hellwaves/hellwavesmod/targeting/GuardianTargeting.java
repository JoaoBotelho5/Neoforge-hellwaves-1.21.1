package com.hellwaves.hellwavesmod.targeting;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber
public class GuardianTargeting {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof Mob mob && !(mob instanceof ZombieGuardian)) {
            // Excluir Creepers do comportamento de ataque
            if (mob instanceof Creeper) {
                return; // Creepers não atacam guardians
            }

            // QUALQUER mob que não seja ZombieGuardian e não seja Creeper recebe o goal
            mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                    mob,
                    ZombieGuardian.class,
                    10, true, false, null
            ));
        }
    }
}