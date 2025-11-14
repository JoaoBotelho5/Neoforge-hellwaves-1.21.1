package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

public class Wave {
    private final List<EntityType<? extends Mob>> enemies;

    public Wave(List<EntityType<? extends Mob>> enemies) {
        this.enemies = enemies;
    }

    public void spawn(Level world, BlockPos pos) {
        int numberofMobs = enemies.size();
        double radius = 20.0;

        for (int i = 0; i < numberofMobs; i++) {
            EntityType<? extends Mob> type = enemies.get(i);
            Mob mob = type.create(world);
            if (mob != null) {
                double angle = 2 * Math.PI * i / numberofMobs;
                double x = pos.getX() + radius * Math.cos(angle);
                double z = pos.getZ() + radius * Math.sin(angle);

                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int)x, (int)z);

                mob.moveTo(x, y, z, 0, 0);
                world.addFreshEntity(mob);
            }
        }
    }
}
