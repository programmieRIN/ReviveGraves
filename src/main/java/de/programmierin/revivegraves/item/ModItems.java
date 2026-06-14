package de.programmierin.revivegraves.item;

import de.programmierin.revivegraves.ReviveGraves;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final Item REVIVE_TOKEN = registerItem("revive_token",
            new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "revive_token")))));

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, name), item);
    }

    public static void registerModItems() {
        ReviveGraves.LOGGER.info("Registering mod items for {}", ReviveGraves.MOD_ID);
    }
}
