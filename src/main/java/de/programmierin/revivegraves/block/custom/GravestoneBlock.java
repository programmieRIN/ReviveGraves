package de.programmierin.revivegraves.block.custom;

import com.mojang.serialization.MapCodec;
import de.programmierin.revivegraves.entity.GravestoneBlockEntity;
import de.programmierin.revivegraves.item.ModItems;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class GravestoneBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<GravestoneBlock> CODEC = createCodec(GravestoneBlock::new);
    private static final VoxelShape SHAPE = Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    public GravestoneBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(FACING, this.getDefaultState().get(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GravestoneBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public ActionResult onUse(BlockState state,
                              World world,
                              BlockPos pos,
                              PlayerEntity clicker,
                              BlockHitResult hit) {
        if (world.isClient) return ActionResult.PASS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof GravestoneBlockEntity gbe)) {
            return super.onUse(state, world, pos, clicker, hit);
        }

        // Finding 2: Null check for owner UUID
        UUID ownerUuid = gbe.getOwner();
        if (ownerUuid == null) return ActionResult.PASS;

        ServerWorld serverWorld = (ServerWorld) world;
        ServerPlayerEntity dead = serverWorld
                .getServer()
                .getPlayerManager()
                .getPlayer(ownerUuid);

        if (dead == null || dead.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            return super.onUse(state, world, pos, clicker, hit);
        }

        // Finding 7: Check token BEFORE consuming — consume only after successful revive
        ItemStack main = clicker.getMainHandStack();
        ItemStack off  = clicker.getOffHandStack();
        boolean mainHasToken = main.getItem() == ModItems.REVIVE_TOKEN;
        boolean offHasToken  = off.getItem() == ModItems.REVIVE_TOKEN;
        if (!mainHasToken && !offHasToken) {
            clicker.sendMessage(Text.translatable("message.revivegraves.need_token"), true);
            return ActionResult.PASS;
        }

        // Finding 5: Restore original game mode stored in block entity
        GameMode originalMode = gbe.getOriginalGameMode();
        if (originalMode == null) originalMode = GameMode.SURVIVAL;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        dead.teleport(
                serverWorld,
                x, y, z,
                EnumSet.noneOf(PositionFlag.class),
                dead.getYaw(), dead.getPitch(),
                false
        );
        dead.changeGameMode(originalMode);

        // Finding 7: Consume token AFTER successful teleport + gamemode change
        if (mainHasToken) {
            main.decrement(1);
        } else {
            off.decrement(1);
        }

        serverWorld.spawnParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                x, y, z,
                30,
                0.3, 0.5, 0.3,
                0.0
        );

        serverWorld.playSound(
                null,
                pos,
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.BLOCKS,
                1f,
                1f
        );

        // Finding 9: Clean up hologram
        UUID holoId = gbe.getHologram();
        if (holoId != null) {
            Entity holo = serverWorld.getEntity(holoId);
            if (holo != null) {
                holo.discard();
            }
        }

        world.removeBlock(pos, false);
        return ActionResult.SUCCESS;
    }

    // Finding 9: Clean up hologram when block is removed by any means
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof GravestoneBlockEntity gbe) {
            UUID holoId = gbe.getHologram();
            if (holoId != null) {
                Entity holo = world.getEntity(holoId);
                if (holo != null) {
                    holo.discard();
                }
            }
        }
        super.onStateReplaced(state, world, pos, moved);
    }
}
