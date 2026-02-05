package com.hellwaves.hellwavesmod.Items;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class Recaller extends Item {

    private static final int CHARGE_TICKS = 60; // 3 seconds

    public Recaller(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand); // bow-style animation
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW; // shows bow-like animation while using
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getItem() == null) return;
        if (event.getItem().getItem() instanceof Recaller) {
            event.setDuration(CHARGE_TICKS); // set required hold duration
        }
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getItem() == null) return;
        if (!(event.getItem().getItem() instanceof Recaller)) return;

        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide()) return;

        ServerLevel currentWorld = (ServerLevel) sp.level();
        BlockPos respawnPos = sp.getRespawnPosition();
        ServerLevel respawnWorld = null;

        // Only teleport to respawn if it's a valid bed
        if (respawnPos != null && sp.getRespawnDimension() != null) {
            respawnWorld = (ServerLevel) sp.level().getServer().getLevel(sp.getRespawnDimension());

            if (respawnWorld != null) {
                // Check if the block at respawnPos is a bed
                if (!(respawnWorld.getBlockState(respawnPos).getBlock() instanceof BedBlock)) {
                    respawnPos = null; // Bed destroyed, fallback to world spawn
                }
            }
        }

        boolean teleported = false;

        if (respawnPos != null && respawnWorld != null) {
            sp.teleportTo(respawnWorld,
                    respawnPos.getX() + 0.5,
                    respawnPos.getY(),
                    respawnPos.getZ() + 0.5,
                    sp.getYRot(),
                    sp.getXRot());
            teleported = true;
        } else {
            BlockPos spawn = currentWorld.getSharedSpawnPos();
            if (spawn != null) {
                sp.teleportTo(currentWorld,
                        spawn.getX() + 0.5,
                        spawn.getY(),
                        spawn.getZ() + 0.5,
                        sp.getYRot(),
                        sp.getXRot());
                teleported = true;
            }
        }

        // Send on-screen action-bar/status messages to the client
        if (teleported) {
            sp.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("§aTeleport successful!")));
        } else {
            sp.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("§cTeleport failed: no spawn set!")));
        }
    }

    @SubscribeEvent
    public static void onPlayerDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Cancel charge if using Recaller
        if (player.isUsingItem() && player.getUseItem() != null && player.getUseItem().getItem() instanceof Recaller) {
            player.stopUsingItem();
            if (player instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("§cTeleport cancelled!")));
            }
        }
    }
}
