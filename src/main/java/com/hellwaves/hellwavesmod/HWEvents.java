package com.hellwaves.hellwavesmod;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class HWEvents {

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockHitResult hit = event.getHitVec();
        Player player = event.getEntity();

        Block block = level.getBlockState(hit.getBlockPos()).getBlock();

        if (block instanceof ActivatorBlock activatorBlock) {
            System.out.println("Right-click detected on ActivatorBlock!");

            if (!level.isClientSide()) {
                activatorBlock.activateWave(level, hit.getBlockPos(), player);
            }

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
