package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class ZombieGuardianRenderer extends HumanoidMobRenderer<ZombieGuardian, ZombieGuardianModel<ZombieGuardian>> {
    private static final ResourceLocation ZOMBIE_GUARDIAN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");

    public ZombieGuardianRenderer(EntityRendererProvider.Context context) {
        // Use our custom ZombieGuardianModel instead of the vanilla ZombieModel
        super(context, new ZombieGuardianModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);

        // Add layer to show items in hand (including bow)
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new ZombieGuardianModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieGuardianModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieGuardian entity) {
        return ZOMBIE_GUARDIAN_TEXTURE;
    }
}