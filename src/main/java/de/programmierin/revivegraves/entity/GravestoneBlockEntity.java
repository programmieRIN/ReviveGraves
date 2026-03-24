package de.programmierin.revivegraves.entity;

import de.programmierin.revivegraves.ReviveGraves;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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
        tag.putString("CustomName", Text.literal(name).toString());
        tag.putBoolean("CustomNameVisible", true);
        tag.putBoolean("Invisible", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Small", true);
        tag.putBoolean("NoBasePlate", true);
        tag.putBoolean("Marker", true);

        BlockPos blockPos = getPos();
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(
                world,
                entity -> entity.readNbt(tag),
                blockPos,
                SpawnReason.TRIGGERED,
                true,
                false
        );
        if (stand == null) return;

        stand.setCustomName(Text.literal(name));
        stand.setCustomNameVisible(true);

        BlockState bs = world.getBlockState(blockPos);
        Direction facing = bs.get(HorizontalFacingBlock.FACING);
        double offset = 0.25;
        double dx = -facing.getOffsetX() * offset;
        double dz = -facing.getOffsetZ() * offset;

        double x = blockPos.getX() + 0.5 + dx;
        double y = blockPos.getY() + 1.1;
        double z = blockPos.getZ() + 0.5 + dz;
        stand.refreshPositionAndAngles(x, y, z, 0f, 0f);

        world.spawnEntity(stand);

        this.hologram = stand.getUuid();
        markDirty();
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (owner != null) {
            nbt.putString("Owner", owner.toString());
        }
        if (hologram != null) {
            nbt.putString("Hologram", hologram.toString());
        }
        if (originalGameMode != null) {
            nbt.putString("OriginalGameMode", originalGameMode.name());
        }
    }

    // Finding 6: Crash-safe NBT parsing
    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Owner")) {
            try {
                owner = UUID.fromString(
                        nbt.getString("Owner").orElse("")
                );
            } catch (IllegalArgumentException e) {
                ReviveGraves.LOGGER.warn("Invalid Owner UUID in gravestone NBT, ignoring");
                owner = null;
            }
        }
        if (nbt.contains("Hologram")) {
            try {
                hologram = UUID.fromString(
                        nbt.getString("Hologram").orElse("")
                );
            } catch (IllegalArgumentException e) {
                ReviveGraves.LOGGER.warn("Invalid Hologram UUID in gravestone NBT, ignoring");
                hologram = null;
            }
        }
        if (nbt.contains("OriginalGameMode")) {
            try {
                originalGameMode = GameMode.valueOf(
                        nbt.getString("OriginalGameMode").orElse("")
                );
            } catch (IllegalArgumentException e) {
                ReviveGraves.LOGGER.warn("Invalid OriginalGameMode in gravestone NBT, defaulting to SURVIVAL");
                originalGameMode = GameMode.SURVIVAL;
            }
        }
    }
}
