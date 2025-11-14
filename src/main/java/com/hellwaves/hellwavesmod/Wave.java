package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;

public class Wave {
    private final List<EntityType<? extends Mob>> enemies;

    public Wave(List<EntityType<? extends Mob>> enemies) {
        this.enemies = enemies;
    }

    public void spawn(Level world, BlockPos pos) {
        int i=0;
        for (EntityType<? extends Mob> type : enemies) {
            Mob mob = type.create(world);
            if (mob != null) {
                mob.moveTo(pos.getX() + i, pos.getY(), pos.getZ() + i, 0, 0);
                world.addFreshEntity(mob);
                i++;
            }
        }
    }
}
