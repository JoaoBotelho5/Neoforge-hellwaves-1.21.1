package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.WarpedMiner;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class WarpedMinerRenderer extends MobRenderer<WarpedMiner, ZombieModel<WarpedMiner>> {
    // Use a textura do Zombified Piglin
    private static final ResourceLocation ZOMBIFIED_PIGLIN_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png");

    public WarpedMinerRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);

        // Layer para a picareta na mão
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));

        // Layer para a armadura
        this.addLayer(new HumanoidArmorLayer<>(this,
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(WarpedMiner entity) {
        return ZOMBIFIED_PIGLIN_TEXTURE;
    }
}