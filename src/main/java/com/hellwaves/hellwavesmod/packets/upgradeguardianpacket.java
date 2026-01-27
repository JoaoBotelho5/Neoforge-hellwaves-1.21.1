package com.hellwaves.hellwavesmod.packets;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record upgradeguardianpacket(int guardianId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<upgradeguardianpacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("hellwavesmod", "upgrade_guardian"));

    public static final StreamCodec<FriendlyByteBuf, upgradeguardianpacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    upgradeguardianpacket::guardianId,
                    upgradeguardianpacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(upgradeguardianpacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Entity entity = serverPlayer.level().getEntity(packet.guardianId());

                if (entity instanceof ZombieGuardian guardian) {
                    // Check distance
                    if (serverPlayer.distanceToSqr(guardian) <= 64.0D) {
                        guardian.tryUpgrade(serverPlayer);
                    } else {
                        serverPlayer.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§cGuardian is too far away!"),
                                true
                        );
                    }
                }
            }
        });
    }
}