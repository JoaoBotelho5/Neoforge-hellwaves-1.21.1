package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class WaveManager {

    public static final Wave FIRST_WAVE;

    static {
        List<EntityType<? extends net.minecraft.world.entity.Mob>> enemies = new ArrayList<>();
        for (int i = 0; i < 5; i++) enemies.add(EntityType.ZOMBIE);
        for (int i = 0; i < 5; i++) enemies.add(EntityType.SKELETON);
        FIRST_WAVE = new Wave(enemies);
    }

    public static void activateWave(Level world, BlockPos pos, Player player) {
        FIRST_WAVE.spawn(world, pos);
    }
}
