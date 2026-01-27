package com.hellwaves.hellwavesmod.packets;

import com.hellwaves.hellwavesmod.HellwavesMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class Modpackets {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                upgradeguardianpacket.TYPE,
                upgradeguardianpacket.STREAM_CODEC,
                upgradeguardianpacket::handle
        );
    }

    public static void sendToServer(upgradeguardianpacket packet) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
    }
}