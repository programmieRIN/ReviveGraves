package de.programmierin.revivegraves.item;

import de.programmierin.revivegraves.ReviveGraves;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {

    public static final ItemGroup REVIVEGRAVES_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(ReviveGraves.MOD_ID, "revivegraves"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModItems.REVIVE_TOKEN))
                    .displayName(Text.translatable("itemGroup.revivegraves"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.REVIVE_TOKEN);
                    }).build());

    public static void registerItemGroups() {
        ReviveGraves.LOGGER.info("Registering Item Groups for {}", ReviveGraves.MOD_ID);
    }
}
