package de.programmierin.revivegraves.client;

import de.programmierin.revivegraves.entity.GravestoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class GravestoneBlockEntityRenderer
        implements BlockEntityRenderer<GravestoneBlockEntity, GravestoneBlockEntityRenderState> {

    private final SkullModelBase skullModel;
    private final PlayerSkinRenderCache skinCache;

    public GravestoneBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.skullModel = SkullBlockRenderer.createModel(
                ctx.entityModelSet(), SkullBlock.Types.PLAYER);
        this.skinCache = ctx.playerSkinRenderCache();
    }

    @Override
    public GravestoneBlockEntityRenderState createRenderState() {
        return new GravestoneBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(GravestoneBlockEntity entity,
                                   GravestoneBlockEntityRenderState state,
                                   float tickDelta,
                                   Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tickDelta, cameraPos, crumbling);

        UUID ownerUuid = entity.getOwner();

        state.facing = entity.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

        // Always render a skull when the gravestone block exists.
        // Use vanilla's cutout render layer (Steve default skin).
        // ownerUuid may be null if BlockEntity data hasn't synced to this client yet.
        state.skullRenderLayer = SkullBlockRenderer.getSkullRenderType(
                SkullBlock.Types.PLAYER, null);
    }

    @Override
    public void submit(GravestoneBlockEntityRenderState state,
                       PoseStack matrices,
                       SubmitNodeCollector renderQueue,
                       CameraRenderState camera) {
        if (state.skullRenderLayer == null) return;

        Direction facing = state.facing;

        matrices.pushPose();

        // In 26.1.2, submitSkull no longer applies the skull base transform — the vanilla
        // renderer does it via mulPose(transformation). We replicate it here to preserve the
        // original visual: translate(0.5, 0, 0.5) + rotate(yaw) + scale(-1,-1,1).
        // We pre-translate to compensate: tx = nicheX - 0.5*scale, tz = nicheZ - 0.5*scale

        float nicheDepth = 6.95f / 16f; // just in front of back wall to avoid Z-fighting
        float scale = 0.34f;
        float nicheY = 8.5f / 16f;

        // Niche center + skull yaw per facing (skull face points -Z at yaw=0)
        float nicheX, nicheZ, yRot;
        switch (facing) {
            case SOUTH -> { nicheX = 0.5f;            nicheZ = 1.0f - nicheDepth; yRot = 180f; }
            case WEST  -> { nicheX = nicheDepth;       nicheZ = 0.5f;             yRot = -90f; }
            case EAST  -> { nicheX = 1.0f - nicheDepth; nicheZ = 0.5f;            yRot = 90f;  }
            default    -> { nicheX = 0.5f;             nicheZ = nicheDepth;        yRot = 0f;   }
        }

        float tx = nicheX - 0.5f * scale;
        float ty = nicheY;
        float tz = nicheZ - 0.5f * scale;

        matrices.translate(tx, ty, tz);
        matrices.scale(scale, scale, scale);

        // Replicate the skull base transform that the vanilla SkullBlockRenderer applies
        // via the block-state transformation (previously done inside the old render() call).
        matrices.translate(0.5f, 0.0f, 0.5f);
        matrices.mulPose(Axis.YP.rotationDegrees(yRot));
        matrices.scale(-1.0f, -1.0f, 1.0f);

        SkullBlockRenderer.submitSkull(
                0f, matrices, renderQueue,
                state.lightCoords, skullModel,
                state.skullRenderLayer, 0, state.breakProgress
        );

        matrices.popPose();
    }
}
