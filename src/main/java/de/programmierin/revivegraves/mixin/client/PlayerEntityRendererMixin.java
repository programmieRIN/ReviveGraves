package de.programmierin.revivegraves.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects when the local player is a ghost chicken and sets the flag on the render state.
 * Ghost state = Invisibility effect + Adventure mode (set by server on death).
 */
@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void revivegraves$detectGhostChicken(Avatar entity, AvatarRenderState state, float tickProgress, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        // Only apply to the local player
        if (client.player == null || entity != client.player) {
            return;
        }

        boolean isGhost = client.gameMode != null
                && client.gameMode.getPlayerMode() == GameType.ADVENTURE
                && client.player.hasEffect(MobEffects.INVISIBILITY);

        ((de.programmierin.revivegraves.ghost.GhostChickenRenderState) state).revivegraves$setGhostChicken(isGhost);
    }
}
