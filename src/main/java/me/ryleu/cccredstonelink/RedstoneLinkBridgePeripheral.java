package me.ryleu.cccredstonelink;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * CC:Tweaked peripheral for the Redstone Link Bridge block.
 *
 * <h2>Lua API</h2>
 *
 * <h3>getLinkSignal(freq1, freq2 [, color1 [, color2]])</h3>
 * <p>Returns the current signal strength (0–15) on the Create redstone-link
 * network identified by the two frequency items and their optional dye colors.
 *
 * <h3>sendLinkSignal(freq1, freq2, strength [, color1 [, color2]])</h3>
 * <p>Transmits a signal on the specified network. {@code strength} is clamped
 * to the range 0–15.
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li><b>freq1 / freq2</b> – Item registry IDs, e.g.
 *       {@code "minecraft:leather_chestplate"}. These match the items you
 *       would physically place in the two frequency slots of a Create
 *       Redstone Link.</li>
 *   <li><b>color1 / color2</b> – Optional 24-bit RGB integers (0x000000–0xFFFFFF).
 *       This is the same value stored in the {@code DYED_COLOR} component of a
 *       dyed leather-armor piece, so any color the cauldron-dyeing system
 *       can produce is valid here. Pass {@code nil} or omit to leave that slot
 *       uncolored. A colored frequency only connects to another link whose
 *       <em>same</em> slot carries the <em>same</em> RGB value, matching
 *       Create's own {@code (item, color)} network-key logic.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>
 * local bridge = peripheral.find("redstone_link_bridge")
 *
 * -- Plain item frequencies (backward-compatible)
 * local s = bridge.getLinkSignal("minecraft:diamond", "minecraft:emerald")
 *
 * -- Dyed leather chestplate as a frequency, hex-literal RGB
 * bridge.sendLinkSignal(
 *     "minecraft:leather_chestplate",
 *     "minecraft:leather_helmet",
 *     15,
 *     0xFF3344,
 *     0x33AAFF)
 *
 * -- Only the first slot colored; second slot uncolored
 * local s2 = bridge.getLinkSignal(
 *     "minecraft:leather_chestplate",
 *     "minecraft:leather_helmet",
 *     0xFF3344)
 * </pre>
 */
public class RedstoneLinkBridgePeripheral implements IPeripheral {

    private final RedstoneLinkBridgeBlockEntity blockEntity;

    public RedstoneLinkBridgePeripheral(RedstoneLinkBridgeBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public @NonNull String getType() {
        return "redstone_link_bridge";
    }

    // -------------------------------------------------------------------------
    // Lua functions
    // -------------------------------------------------------------------------

    /**
     * Returns the current signal strength on the given network.
     *
     * @param frequency1 First frequency slot item ID
     * @param frequency2 Second frequency slot item ID
     * @param color1     (optional) 24-bit RGB for the first slot
     * @param color2     (optional) 24-bit RGB for the second slot
     * @return Signal strength 0–15
     */
    @LuaFunction(mainThread = true)
    public final int getLinkSignal(
            String frequency1,
            String frequency2,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency2, color2.orElse(null));

        return blockEntity.getLinkSignal(first, last);
    }

    /**
     * Transmits a signal on the given network.
     *
     * @param frequency1 First frequency slot item ID
     * @param frequency2 Second frequency slot item ID
     * @param strength   Signal strength 0–15 (clamped automatically)
     * @param color1     (optional) 24-bit RGB for the first slot
     * @param color2     (optional) 24-bit RGB for the second slot
     */
    @LuaFunction(mainThread = true)
    public final void sendLinkSignal(
            String frequency1,
            String frequency2,
            int strength,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency2, color2.orElse(null));

        blockEntity.sendLinkSignal(first, last, strength);
    }


    /**
     * Starts listening for Create Redstone Link signal changes on a frequency.
     *
     * <p>When the signal changes, the subscribing computer (and only that
     * computer) receives:
     * <pre>
     * redstone_link_change, freq1, freq2, newStrength, oldStrength, color1, color2
     * </pre>
     * Uncolored slots pass {@code nil} for their color. Each computer is tracked
     * independently, so one computer calling {@code unwatchLinkSignal} won't
     * cancel another computer's subscription to the same pair.
     *
     * @return Current signal strength when the watch is registered.
     */
    @LuaFunction(mainThread = true)
    public final int watchLinkSignal(
            IComputerAccess computer,
            String frequency1,
            String frequency2,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency2, color2.orElse(null));

        return blockEntity.watchLinkSignal(computer, first, last);
    }

    /** Stops listening for Create Redstone Link signal changes on a frequency. */
    @LuaFunction(mainThread = true)
    public final void unwatchLinkSignal(
            IComputerAccess computer,
            String frequency1,
            String frequency2,
            Optional<Integer> color1,
            Optional<Integer> color2) {

        ItemStack first = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency1, color1.orElse(null));
        ItemStack last  = RedstoneLinkBridgeBlockEntity.fromFrequencySpec(
                frequency2, color2.orElse(null));

        blockEntity.unwatchLinkSignal(computer, first, last);
    }

    // -------------------------------------------------------------------------
    // IPeripheral
    // -------------------------------------------------------------------------

    @Override
    public void detach(IComputerAccess computer) {
        blockEntity.detachComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (this == other) return true;
        if (!(other instanceof RedstoneLinkBridgePeripheral that)) return false;
        return this.blockEntity == that.blockEntity;
    }

    @Override
    public Object getTarget() {
        return blockEntity;
    }
}
