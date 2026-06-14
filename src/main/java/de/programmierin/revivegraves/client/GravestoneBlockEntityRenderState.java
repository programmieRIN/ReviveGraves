package de.programmierin.revivegraves.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;

public class GravestoneBlockEntityRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.NORTH;
    public RenderType skullRenderLayer;
}
