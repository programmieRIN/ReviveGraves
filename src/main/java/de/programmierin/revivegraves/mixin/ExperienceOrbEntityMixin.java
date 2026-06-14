package de.programmierin.revivegraves.mixin;

import de.programmierin.revivegraves.ghost.GhostChickenState;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents ghost chicken players from picking up or attracting XP orbs.
 * Uses isInvisible() as a client-compatible check since ghost players are always invisible.
 */
@Mixin(ExperienceOrb.class)
public class ExperienceOrbEntityMixin {

    @Shadow
    @Nullable
    private Player followingPlayer;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void revivegraves$preventGhostXpPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.level() instanceof ServerLevel serverWorld) {
            GhostChickenState state = GhostChickenState.get(serverWorld.getServer());
            if (state.isGhost(serverPlayer.getUUID())) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "followNearbyPlayer", at = @At("HEAD"), cancellable = true)
    private void revivegraves$preventGhostAttraction(CallbackInfo ci) {
        // Clear existing target if it's a ghost (invisible) player
        if (this.followingPlayer != null && this.followingPlayer.isInvisible()) {
            this.followingPlayer = null;
        }

        // If no target, check if closest player is invisible (ghost) — skip attraction entirely
        if (this.followingPlayer == null) {
            ExperienceOrb self = (ExperienceOrb) (Object) this;
            Player closest = self.level().getNearestPlayer(self, 8.0);
            if (closest != null && closest.isInvisible()) {
                ci.cancel();
            }
        }
    }
}
