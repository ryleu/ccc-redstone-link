package me.ryleu.cccredstonelink;

import dan200.computercraft.api.client.turtle.RegisterTurtleModellersEvent;
import dan200.computercraft.api.client.turtle.TurtleUpgradeModeller;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.world.item.CreativeModeTabs;
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
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.REDSTONE_LINK_BRIDGE.get());
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
            // Placeholder: use the bridge item flat on the turtle's side until a proper
            // 3D upgrade model lands.
            event.register(CCRegistration.REDSTONE_LINK_TURTLE.get(), TurtleUpgradeModeller.flatItem());
        }
    }
}
