package de.programmierin.revivegraves.item;

import de.programmierin.revivegraves.ReviveGraves;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModItemGroups {

    public static final CreativeModeTab REVIVEGRAVES_GROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(ReviveGraves.MOD_ID, "revivegraves"),
            FabricCreativeModeTab.builder().icon(() -> new ItemStack(ModItems.REVIVE_TOKEN))
                    .title(Component.translatable("itemGroup.revivegraves"))
                    .displayItems((displayContext, entries) -> {
                        entries.accept(ModItems.REVIVE_TOKEN);
                    }).build());

    public static void registerItemGroups() {
        ReviveGraves.LOGGER.info("Registering Item Groups for {}", ReviveGraves.MOD_ID);
    }
}
