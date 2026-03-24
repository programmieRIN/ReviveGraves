package de.programmierin.revivegraves.entity;

import de.programmierin.revivegraves.ReviveGraves;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.UUID;

public class GravestoneBlockEntity extends BlockEntity {
    private UUID owner;
    private UUID hologram;
    private GameMode originalGameMode;

    public GravestoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVESTONE, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public UUID getHologram() {
        return hologram;
    }

    public void setOriginalGameMode(GameMode mode) {
        this.originalGameMode = mode;
        markDirty();
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public void spawnHologram(ServerWorld world) {
        if (owner == null || hologram != null) return;

        ServerPlayerEntity player = world.getServer()
                .getPlayerManager()
                .getPlayer(owner);
        if (player == null) return;
        String name = player.getGameProfile().getName();

        NbtCompound tag = new NbtCompound();
        tag.putString("CustomName",        "{\"text\":\"" + name + "\"}");
        tag.putBoolean("CustomNameVisible", true);
        tag.putBoolean("Invisible",         true);
        tag.putBoolean("NoGravity",         true);
        tag.putBoolean("Invulnerable",      true);
        tag.putBoolean("Small",             true);
        tag.putBoolean("NoBasePlate",       true);
        tag.putBoolean("Marker",            true);

        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
        if (stand == null) return;
        stand.readNbt(tag);
        stand.setInvisible(true);

        BlockState blockState = world.getBlockState(getPos());
        Direction facing = blockState.get(HorizontalFacingBlock.FACING);
        double offset = 0.25;
        double dx = -facing.getOffsetX() * offset;
        double dz = -facing.getOffsetZ() * offset;

        double x = getPos().getX() + 0.5 + dx;
        double y = getPos().getY() + 1.1;
        double z = getPos().getZ() + 0.5 + dz;
        stand.refreshPositionAndAngles(x, y, z, 0f, 0f);

        world.spawnEntity(stand);

        this.hologram = stand.getUuid();
        markDirty();
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (owner != null) nbt.putUuid("Owner", owner);
        if (hologram != null) nbt.putUuid("Hologram", hologram);
        if (originalGameMode != null) nbt.putString("OriginalGameMode", originalGameMode.name());
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.containsUuid("Owner")) {
            try {
                owner = nbt.getUuid("Owner");
            } catch (Exception e) {
                ReviveGraves.LOGGER.warn("Failed to parse Owner UUID from gravestone NBT", e);
                owner = null;
            }
        }
        if (nbt.containsUuid("Hologram")) {
            try {
                hologram = nbt.getUuid("Hologram");
            } catch (Exception e) {
                ReviveGraves.LOGGER.warn("Failed to parse Hologram UUID from gravestone NBT", e);
                hologram = null;
            }
        }
        if (nbt.contains("OriginalGameMode")) {
            try {
                originalGameMode = GameMode.valueOf(nbt.getString("OriginalGameMode"));
            } catch (Exception e) {
                ReviveGraves.LOGGER.warn("Failed to parse OriginalGameMode from gravestone NBT", e);
                originalGameMode = null;
            }
        }
    }
}
