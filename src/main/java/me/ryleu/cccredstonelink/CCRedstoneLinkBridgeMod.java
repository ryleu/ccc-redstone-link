package me.ryleu.cccredstonelink;

import dan200.computercraft.api.client.turtle.RegisterTurtleModellersEvent;
import dan200.computercraft.api.client.turtle.TurtleUpgradeModeller;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.upgrades.UpgradeData;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(value="cccredstonelink")
public class CCRedstoneLinkBridgeMod {
    public static final String MOD_ID = "cccredstonelink";

    public CCRedstoneLinkBridgeMod(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModItems.register(modBus);
        CCRegistration.register(modBus);
        modBus.addListener(this::addCreativeTabs);
        modBus.addListener(this::registerCapabilities);
    }

    private void addCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.REDSTONE_BLOCKS) return;

        event.accept(ModItems.REDSTONE_LINK_BRIDGE.get());

        // Pre-equipped pocket and turtle variants. CC:T's own creative tab filters
        // to its own namespace, so without this our upgrade items only show up after
        // crafting. We look up CC:T's data-component types and items by their
        // public registry ids so we don't depend on CC:T's internal ModRegistry.
        HolderLookup.Provider holders = event.getParameters().holders();
        addEquippedPockets(event, holders);
        addEquippedTurtles(event, holders);
    }

    @SuppressWarnings("unchecked")
    private static void addEquippedPockets(BuildCreativeModeTabContentsEvent event, HolderLookup.Provider holders) {
        DataComponentType<UpgradeData<IPocketUpgrade>> upgradeComponent =
                (DataComponentType<UpgradeData<IPocketUpgrade>>) BuiltInRegistries.DATA_COMPONENT_TYPE
                        .get(ResourceLocation.fromNamespaceAndPath("computercraft", "pocket_upgrade"));
        if (upgradeComponent == null) return;

        Holder.Reference<IPocketUpgrade> upgrade = holders
                .lookupOrThrow(IPocketUpgrade.REGISTRY)
                .get(ResourceKey.create(IPocketUpgrade.REGISTRY,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "redstone_link_pocket")))
                .orElse(null);
        if (upgrade == null) return;

        for (String tier : new String[]{"pocket_computer_normal", "pocket_computer_advanced"}) {
            Item base = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("computercraft", tier));
            if (base == null) continue;
            ItemStack stack = new ItemStack(base);
            stack.set(upgradeComponent, UpgradeData.ofDefault(upgrade));
            event.accept(stack);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addEquippedTurtles(BuildCreativeModeTabContentsEvent event, HolderLookup.Provider holders) {
        DataComponentType<UpgradeData<ITurtleUpgrade>> rightSlot =
                (DataComponentType<UpgradeData<ITurtleUpgrade>>) BuiltInRegistries.DATA_COMPONENT_TYPE
                        .get(ResourceLocation.fromNamespaceAndPath("computercraft", "right_turtle_upgrade"));
        if (rightSlot == null) return;

        Holder.Reference<ITurtleUpgrade> upgrade = holders
                .lookupOrThrow(ITurtleUpgrade.REGISTRY)
                .get(ResourceKey.create(ITurtleUpgrade.REGISTRY,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "redstone_link_turtle")))
                .orElse(null);
        if (upgrade == null) return;

        for (String tier : new String[]{"turtle_normal", "turtle_advanced"}) {
            Item base = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("computercraft", tier));
            if (base == null) continue;
            ItemStack stack = new ItemStack(base);
            stack.set(rightSlot, UpgradeData.ofDefault(upgrade));
            event.accept(stack);
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModBlockEntities.REDSTONE_LINK_BRIDGE.get(),
                (blockEntity, side) -> new RedstoneLinkBridgePeripheral(blockEntity)
        );
    }

    @Mod(value = MOD_ID, dist = Dist.CLIENT)
    public static final class Client {
        public Client(IEventBus modBus) {
            modBus.addListener(Client::registerTurtleModellers);
        }

        private static void registerTurtleModellers(RegisterTurtleModellersEvent event) {
            event.register(CCRegistration.REDSTONE_LINK_TURTLE.get(),
                    TurtleUpgradeModeller.sided(
                            ResourceLocation.fromNamespaceAndPath(MOD_ID, "block/turtle_link_left"),
                            ResourceLocation.fromNamespaceAndPath(MOD_ID, "block/turtle_link_right")));
        }
    }
}
