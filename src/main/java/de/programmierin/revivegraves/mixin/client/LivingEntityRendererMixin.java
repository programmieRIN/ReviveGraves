package de.programmierin.revivegraves.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts LivingEntityRenderer.submit to replace ghost chicken players
 * with an actual chicken model when viewed in F5 mode.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void revivegraves$renderGhostChicken(
            LivingEntityRenderState state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!(state instanceof AvatarRenderState playerState)) {
            return;
        }

        if (!((de.programmierin.revivegraves.ghost.GhostChickenRenderState) playerState).revivegraves$isGhostChicken()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        // Get the chicken renderer from the entity render dispatcher
        EntityRenderDispatcher renderManager = client.getEntityRenderDispatcher();
        EntityRenderer<?, ?> renderer = ((EntityRenderManagerAccessor) renderManager).revivegraves$getRenderers().get(EntityType.CHICKEN);

        if (!(renderer instanceof ChickenRenderer chickenRenderer)) {
            return;
        }

        // Build a ChickenRenderState with position/rotation from the player
        ChickenRenderState chickenState = new ChickenRenderState();
        // Base EntityRenderState fields
        chickenState.x = state.x;
        chickenState.y = state.y;
        chickenState.z = state.z;
        chickenState.ageInTicks = state.ageInTicks;
        chickenState.lightCoords = state.lightCoords;
        chickenState.isInvisible = false;
        chickenState.boundingBoxWidth = 0.4f;
        chickenState.boundingBoxHeight = 0.7f;
        chickenState.distanceToCameraSq = state.distanceToCameraSq;
        chickenState.shadowRadius = 0.3f;

        // LivingEntityRenderState fields — animation
        chickenState.bodyRot = state.bodyRot;
        chickenState.yRot = state.yRot;
        chickenState.xRot = state.xRot;
        chickenState.walkAnimationPos = state.walkAnimationPos;
        chickenState.walkAnimationSpeed = state.walkAnimationSpeed;
        // In inventory screens, use the player's actual scale (0.389) so the
        // inventory's entity-fitting logic produces a correctly sized chicken.
        // In-world (F5), use 1.0 for a normal-sized chicken model.
        boolean isInventoryRender = client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
        chickenState.scale = isInventoryRender ? state.scale : 1.0f;
        chickenState.ageScale = 1.0f;
        chickenState.isBaby = false;

        // ChickenRenderState fields — wing flap animation
        // Simulate gentle wing flap based on age (time)
        float flapSpeed = 0.6f;
        float flapAmount = 0.3f;
        if (state.walkAnimationSpeed > 0.01f) {
            // Walking — flap more
            flapSpeed = 1.0f;
            flapAmount = 0.5f;
        }
        chickenState.flap = state.ageInTicks * flapSpeed;
        chickenState.flapSpeed = flapAmount;

        // Set the chicken variant - required for rendering (ChickenRenderer returns early if null)
        client.level.registryAccess()
                .lookupOrThrow(Registries.CHICKEN_VARIANT)
                .getOptional(ChickenVariants.TEMPERATE)
                .ifPresent(variant -> chickenState.variant = variant);

        // Render the chicken instead of the player
        chickenRenderer.submit(chickenState, matrices, queue, cameraState);
        ci.cancel();
    }
}
