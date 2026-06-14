package de.programmierin.revivegraves.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies the eyeHeight field on the local player when they are a ghost chicken.
 * This is the single source of truth for eye height — both the Camera (for rendering)
 * and getEyePosition (for raycasting) read from this field.
 *
 * Targets Entity because eyeHeight is a private field on Entity.
 * Only applies to the local client player who is a ghost.
 */
@Mixin(Entity.class)
public abstract class GhostRaycastMixin {

    private static final float CHICKEN_EYE_HEIGHT = 0.595f;

    @Shadow
    private float eyeHeight;

    @Unique
    private boolean revivegraves$wasGhost = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void revivegraves$lowerGhostEyeHeight(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;
        if ((Object) this != client.player) return;

        boolean isGhost = client.gameMode.getPlayerMode() == GameType.ADVENTURE
                && client.player.hasEffect(MobEffects.INVISIBILITY);

        if (isGhost) {
            this.eyeHeight = CHICKEN_EYE_HEIGHT;
            this.revivegraves$wasGhost = true;
        } else if (this.revivegraves$wasGhost) {
            this.revivegraves$wasGhost = false;
            ((Entity) (Object) this).refreshDimensions();
        }
    }
}
