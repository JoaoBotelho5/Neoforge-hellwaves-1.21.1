package com.hellwaves.hellwavesmod.targeting;

import com.hellwaves.hellwavesmod.HWMobs.SkeletonGuardian;
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

        if (event.getEntity() instanceof Mob mob && !(mob instanceof ZombieGuardian) && !(mob instanceof SkeletonGuardian)) {
            // Exclude Creepers
            if (mob instanceof Creeper) return;

            // Add goal to attack guardians
            mob.targetSelector.addGoal(2, new PersistentGuardianTargetGoal(mob));
        }
    }

    private static class PersistentGuardianTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private static final int RETARGET_INTERVAL = 20;
        private int retargetTimer = 0;

        public PersistentGuardianTargetGoal(Mob mob) {
            super(
                    mob,
                    LivingEntity.class, // <-- Generic type must be LivingEntity to include all guardians
                    10,
                    true,
                    false,
                    target -> target instanceof ZombieGuardian || target instanceof SkeletonGuardian
            );
        }

        @Override
        public boolean canUse() {
            LivingEntity currentTarget = this.mob.getTarget();
            return (currentTarget == null || !currentTarget.isAlive()) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity currentTarget = this.mob.getTarget();
            if (currentTarget == null || !currentTarget.isAlive() ||
                    !(currentTarget instanceof ZombieGuardian || currentTarget instanceof SkeletonGuardian)) {
                return false;
            }
            return super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            retargetTimer++;
            if (retargetTimer >= RETARGET_INTERVAL) {
                retargetTimer = 0;
                LivingEntity currentTarget = this.mob.getTarget();
                if (currentTarget != null && (!currentTarget.isAlive() ||
                        !(currentTarget instanceof ZombieGuardian || currentTarget instanceof SkeletonGuardian))) {
                    this.mob.setTarget(null);
                }
            }
        }
    }
}
