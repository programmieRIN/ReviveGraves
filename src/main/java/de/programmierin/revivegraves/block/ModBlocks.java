package de.programmierin.revivegraves.block;

import de.programmierin.revivegraves.ReviveGraves;
import de.programmierin.revivegraves.block.custom.GravestoneBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

    public static final Block GRAVESTONE = registerBlockWithoutBlockItem("gravestone",
            new GravestoneBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "gravestone")))
                            .strength(-1.0f, 3600000.0f)
                            .noLootTable()
                            .noOcclusion()
                            .lightLevel(state -> 6)
            )
    );

    private static Block registerBlockWithoutBlockItem(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK,
                Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, name), block);
    }

    // Hidden BlockItem for advancement icon only — not in any creative tab, not craftable
    public static final Item GRAVESTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
            Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "gravestone"),
            new BlockItem(GRAVESTONE, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "gravestone")))));

    public static void registerModBlocks() {
        ReviveGraves.LOGGER.info("Registering Mod Blocks for {}", ReviveGraves.MOD_ID);
    }
}
