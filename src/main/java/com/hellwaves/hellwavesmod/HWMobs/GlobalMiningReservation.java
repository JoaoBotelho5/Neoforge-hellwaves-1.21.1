package com.hellwaves.hellwavesmod.HWMobs;

import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalMiningReservation {

    private static final Set<BlockPos> RESERVED = ConcurrentHashMap.newKeySet();

    private GlobalMiningReservation() {}

    public static boolean reserve(BlockPos pos) {
        return RESERVED.add(pos);
    }

    public static void release(BlockPos pos) {
        RESERVED.remove(pos);
    }

    public static boolean isReserved(BlockPos pos) {
        return RESERVED.contains(pos);
    }
}
