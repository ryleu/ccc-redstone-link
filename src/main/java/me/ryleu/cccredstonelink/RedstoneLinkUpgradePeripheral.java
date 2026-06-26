package me.ryleu.cccredstonelink;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The peripheral exposed by the pocket and turtle upgrades.
 * <p>
 * Returns the same {@code "redstone_link_bridge"} peripheral type as the block
 * peripheral so Lua programs that call {@code peripheral.find("redstone_link_bridge")}
 * keep working unchanged whether the bridge is a placed block, a pocket upgrade,
 * or a turtle upgrade.
 */
public class RedstoneLinkUpgradePeripheral implements IPeripheral {

    private final RedstoneLinkChannelHost channelHost;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public RedstoneLinkUpgradePeripheral(RedstoneLinkChannelHost.LinkHost linkHost) {
        this.channelHost = new RedstoneLinkChannelHost(new EventForwardingLinkHost(linkHost));
    }

    public RedstoneLinkChannelHost channelHost() {
        return channelHost;
    }

    @Override
    public @NonNull String getType() {
        return "redstone_link_bridge";
    }

    @LuaFunction(mainThread = true)
    public final int getLinkSignal(
            String frequency1,
            String frequency2,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkChannelHost.fromFrequencySpec(frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkChannelHost.fromFrequencySpec(frequency2, color2.orElse(null));
        return channelHost.getLinkSignal(first, last);
    }

    @LuaFunction(mainThread = true)
    public final int watchLinkSignal(
            String frequency1,
            String frequency2,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkChannelHost.fromFrequencySpec(frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkChannelHost.fromFrequencySpec(frequency2, color2.orElse(null));
        return channelHost.watchLinkSignal(first, last);
    }

    @LuaFunction(mainThread = true)
    public final void unwatchLinkSignal(
            String frequency1,
            String frequency2,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkChannelHost.fromFrequencySpec(frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkChannelHost.fromFrequencySpec(frequency2, color2.orElse(null));
        channelHost.unwatchLinkSignal(first, last);
    }

    @LuaFunction(mainThread = true)
    public final void sendLinkSignal(
            String frequency1,
            String frequency2,
            int strength,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkChannelHost.fromFrequencySpec(frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkChannelHost.fromFrequencySpec(frequency2, color2.orElse(null));
        channelHost.sendLinkSignal(first, last, strength);
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    private void queueEvent(String event, Object... arguments) {
        for (IComputerAccess computer : computers) {
            computer.queueEvent(event, arguments);
        }
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other;
    }

    private final class EventForwardingLinkHost implements RedstoneLinkChannelHost.LinkHost {
        private final RedstoneLinkChannelHost.LinkHost delegate;

        private EventForwardingLinkHost(RedstoneLinkChannelHost.LinkHost delegate) {
            this.delegate = delegate;
        }

        @Override public net.minecraft.world.level.Level level() { return delegate.level(); }

        @Override public net.minecraft.core.BlockPos pos() { return delegate.pos(); }

        @Override public boolean isAlive() { return delegate.isAlive(); }

        @Override public void markDirty() { delegate.markDirty(); }

        @Override public void queueEvent(String event, Object... arguments) {
            RedstoneLinkUpgradePeripheral.this.queueEvent(event, arguments);
        }
    }
}
