package de.programmierin.revivegraves.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import de.programmierin.revivegraves.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {

    public static final BlockEntityType<GravestoneBlockEntity> GRAVESTONE =
            FabricBlockEntityTypeBuilder
                    .create(GravestoneBlockEntity::new, ModBlocks.GRAVESTONE)
                    .build();
}