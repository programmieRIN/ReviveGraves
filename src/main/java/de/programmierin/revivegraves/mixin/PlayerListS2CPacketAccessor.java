package de.programmierin.revivegraves.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin for ClientboundPlayerInfoUpdatePacket to allow modifying the entries list.
 * Used to filter ghost players from tab list packets.
 */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerListS2CPacketAccessor {

    @Accessor("entries")
    List<ClientboundPlayerInfoUpdatePacket.Entry> revivegraves$getEntries();

    @Mutable
    @Accessor("entries")
    void revivegraves$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
