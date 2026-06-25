package me.ryleu.cccredstonelink;

import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static me.ryleu.cccredstonelink.CCRedstoneLinkBridgeMod.MOD_ID;

/**
 * Holds the CC:Tweaked upgrade-type registrations — pocket and turtle.
 * <p>
 * One JSON datapack entry per upgrade in {@code data/cccredstonelink/computercraft/{pocket,turtle}_upgrade/}
 * binds an upgrade instance to a type registered here. A single JSON entry works
 * for both normal and advanced pocket computers / turtles because CC:T's built-in
 * {@code pocket_computer_upgrade} / {@code turtle_upgrade} recipe handlers accept
 * either tier.
 */
public final class CCRegistration {

    static final DeferredRegister<UpgradeType<? extends IPocketUpgrade>> POCKET_SERIALIZER =
            DeferredRegister.create(IPocketUpgrade.typeRegistry(), MOD_ID);

    static final DeferredRegister<UpgradeType<? extends ITurtleUpgrade>> TURTLE_SERIALIZER =
            DeferredRegister.create(ITurtleUpgrade.typeRegistry(), MOD_ID);

    public static final DeferredHolder<UpgradeType<? extends IPocketUpgrade>, UpgradeType<RedstoneLinkPocketUpgrade>> REDSTONE_LINK_POCKET =
            POCKET_SERIALIZER.register("redstone_link_pocket",
                    () -> UpgradeType.simpleWithCustomItem(RedstoneLinkPocketUpgrade::new));

    public static final DeferredHolder<UpgradeType<? extends ITurtleUpgrade>, UpgradeType<RedstoneLinkTurtleUpgrade>> REDSTONE_LINK_TURTLE =
            TURTLE_SERIALIZER.register("redstone_link_turtle",
                    () -> UpgradeType.simpleWithCustomItem(RedstoneLinkTurtleUpgrade::new));

    private CCRegistration() {
    }

    public static void register(IEventBus bus) {
        POCKET_SERIALIZER.register(bus);
        TURTLE_SERIALIZER.register(bus);
    }
}
