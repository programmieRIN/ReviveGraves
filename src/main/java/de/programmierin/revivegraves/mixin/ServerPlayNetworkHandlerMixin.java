package de.programmierin.revivegraves.mixin;

import de.programmierin.revivegraves.ghost.GhostChickenState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Intercepts outgoing ClientboundPlayerInfoUpdatePacket to hide ghost players from the tab list
 * while preserving their GameProfile data (needed for gravestone skull skin resolution).
 * Ghost entries are kept but modified to listed=false instead of being removed.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Inject(method = "send", at = @At("HEAD"))
    private void revivegraves$unlistGhostFromTabList(Packet<?> packet, CallbackInfo ci) {
        if (!(((Object) this) instanceof ServerGamePacketListenerImpl handler)) return;

        ServerPlayer receiver = handler.getPlayer();
        if (receiver == null) return;

        MinecraftServer server = receiver.level().getServer();
        if (server == null) return;

        if (!(packet instanceof ClientboundPlayerInfoUpdatePacket listPacket)) return;

        GhostChickenState state = GhostChickenState.get(server);
        Set<UUID> ghostUuids = state.getGhostPlayerUuids();
        if (ghostUuids.isEmpty()) return;

        List<ClientboundPlayerInfoUpdatePacket.Entry> originalEntries =
                ((PlayerListS2CPacketAccessor) listPacket).revivegraves$getEntries();

        boolean modified = false;
        List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries = new ArrayList<>(originalEntries.size());
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : originalEntries) {
            if (ghostUuids.contains(entry.profileId()) && entry.listed()) {
                // Keep the entry but set listed=false so the GameProfile is preserved
                // on the client (needed for gravestone skull skin) without showing in tab
                newEntries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId(), entry.profile(), false, entry.latency(),
                        entry.gameMode(), entry.displayName(), entry.showHat(),
                        entry.listOrder(), entry.chatSession()));
                modified = true;
            } else {
                newEntries.add(entry);
            }
        }

        if (modified) {
            ((PlayerListS2CPacketAccessor) listPacket).revivegraves$setEntries(newEntries);
        }
    }
}
