package me.ryleu.cccredstonelink;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

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

    public RedstoneLinkUpgradePeripheral(RedstoneLinkChannelHost.LinkHost linkHost) {
        this.channelHost = new RedstoneLinkChannelHost(linkHost);
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
    public boolean equals(IPeripheral other) {
        return this == other;
    }
}
