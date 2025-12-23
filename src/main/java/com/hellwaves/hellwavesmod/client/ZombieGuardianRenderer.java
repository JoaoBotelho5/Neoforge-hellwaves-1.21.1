package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ZombieGuardianRenderer extends HumanoidMobRenderer<ZombieGuardian, ZombieModel<ZombieGuardian>> {
    private static final ResourceLocation ZOMBIE_GUARDIAN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hellwavesmod", "textures/entity/zombie_guardian.png");

    public ZombieGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieGuardian entity) {
        return ZOMBIE_GUARDIAN_TEXTURE;
    }
}