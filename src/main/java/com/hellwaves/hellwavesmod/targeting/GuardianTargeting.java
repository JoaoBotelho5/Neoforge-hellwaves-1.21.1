package com.hellwaves.hellwavesmod.targeting;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.world.entity.LivingEntity;
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

            // FIXED: Use a custom goal that continues searching for guardians
            mob.targetSelector.addGoal(3, new PersistentGuardianTargetGoal(mob));
        }
    }

    // Custom goal that automatically retargets guardians when current target dies
    private static class PersistentGuardianTargetGoal extends NearestAttackableTargetGoal<ZombieGuardian> {
        private static final int RETARGET_INTERVAL = 20; // Check every second
        private int retargetTimer = 0;

        public PersistentGuardianTargetGoal(Mob mob) {
            super(mob, ZombieGuardian.class, 10, true, false, null);
        }

        @Override
        public boolean canUse() {
            // Always try to find a guardian target if we don't have one
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity currentTarget = this.mob.getTarget();

            // If current target is dead or not a guardian, find a new one
            if (currentTarget == null || !currentTarget.isAlive() || !(currentTarget instanceof ZombieGuardian)) {
                return false; // This will trigger canUse() again to find new target
            }

            return super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();

            retargetTimer++;

            // Periodically check if current target is still valid
            if (retargetTimer >= RETARGET_INTERVAL) {
                retargetTimer = 0;

                LivingEntity currentTarget = this.mob.getTarget();

                // If target is dead or invalid, clear it so we can find a new one
                if (currentTarget != null && (!currentTarget.isAlive() || !(currentTarget instanceof ZombieGuardian))) {
                    this.mob.setTarget(null);
                }
            }
        }

        @Override
        public void start() {
            super.start();
            retargetTimer = 0;
        }

        @Override
        public void stop() {
            super.stop();
            // Don't clear the target here - let the goal system handle retargeting
            retargetTimer = 0;
        }
    }
}