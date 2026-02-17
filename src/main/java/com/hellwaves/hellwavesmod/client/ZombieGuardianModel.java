package com.hellwaves.hellwavesmod.client;

import com.hellwaves.hellwavesmod.HWMobs.ZombieGuardian;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class ZombieGuardianModel<T extends ZombieGuardian> extends ZombieModel<T> {

    public ZombieGuardianModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // Check if the entity has a target
        boolean hasTarget = entity.getTarget() != null;

        if (!hasTarget) {
            // No target - arms down like a skeleton (idle stance)
            // Reset aggressive pose and set arms to hang naturally at sides
            this.rightArm.xRot = -0.1F;  // Slight forward angle
            this.rightArm.yRot = 0.0F;
            this.rightArm.zRot = 0.1F;   // Slight outward angle

            this.leftArm.xRot = -0.1F;   // Slight forward angle
            this.leftArm.yRot = 0.0F;
            this.leftArm.zRot = -0.1F;   // Slight outward angle

            // Add subtle idle animation for natural look
            float idleSwing = Mth.cos(ageInTicks * 0.09F) * 0.05F;
            this.rightArm.xRot += idleSwing;
            this.leftArm.xRot -= idleSwing;
        } else {
            // Has target - use zombie aggressive pose (arms forward)
            // The super.setupAnim() already handles the zombie aggressive pose
            // but we'll make it more pronounced when targeting
            this.rightArm.xRot = -1.5F;  // Arms forward/up
            this.leftArm.xRot = -1.5F;   // Arms forward/up
        }
    }
}