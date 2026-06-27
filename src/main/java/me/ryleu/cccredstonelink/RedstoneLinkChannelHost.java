package me.ryleu.cccredstonelink;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dan200.computercraft.api.peripheral.IComputerAccess;
import me.ryleu.cccredstonelink.compat.SableCompat;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns a collection of {@link RedstoneLinkChannel}s and brokers
 * register / unregister / migrate with Create's redstone-link network handler.
 * <p>
 * Every flavour of bridge (placed block, pocket-upgrade, turtle-upgrade) wraps
 * one of these and supplies a {@link LinkHost} to answer "where am I?" and
 * "am I still alive?". The host abstraction is what lets the same channel
 * plumbing live on a moving player or turtle.
 */
public final class RedstoneLinkChannelHost {

    /**
     * Adapter implemented by whatever owns a {@link RedstoneLinkChannelHost}.
     * The channel asks the host for its current position, level, and liveness
     * on demand so Create's per-tick range checks always see the current state
     * without us having to push updates per-tick.
     */
    public interface LinkHost {
        /** Current level. May be {@code null} if the host is not loaded server-side. */
        @Nullable Level level();

        /** Current block position. */
        BlockPos pos();

        /**
         * Whether this host should keep its channels live. When this returns
         * {@code false}, Create's network handler evicts the channels on its
         * next sweep — no explicit teardown required.
         */
        boolean isAlive();

        /** Notify the host that channel state changed (block entity uses this to call setChanged). */
        default void markDirty() { }
    }

    private final LinkHost owner;
    private final Map<String, RedstoneLinkChannel> channels = new LinkedHashMap<>();

    @Nullable
    private Level registeredLevel;

    /**
     * Last effective (Sable-aware) world position seen by {@link #refreshListenersIfMoved()}.
     * Used to skip work when the owner hasn't moved.
     */
    @Nullable
    private BlockPos lastListenerPos;

    public RedstoneLinkChannelHost(LinkHost owner) {
        this.owner = owner;
    }

    // ----- Lua-facing operations -----

    public int getLinkSignal(ItemStack first, ItemStack last) {
        Level level = owner.level();
        if (level == null || level.isClientSide) return 0;

        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(normalize(first)),
                RedstoneLinkNetworkHandler.Frequency.of(normalize(last))
        );

        Set<?> network = Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level).get(key);
        if (network == null) return 0;

        // Match Create's own range-gating: only transmitters within linkRange of
        // this bridge's position contribute. Both endpoints are routed through
        // SableCompat.toWorldPos so blocks on a Sable sub-level are compared at
        // their *visual* world position via the sub-level's logicalPose — same
        // trick Sable's own mixin on RedstoneLinkNetworkHandler.updateNetworkOf
        // does, but for the read path.
        BlockPos here = SableCompat.toWorldPos(level, owner.pos());
        int range = AllConfigs.server().logistics.linkRange.get();

        int power = 0;
        for (Object obj : network) {
            IRedstoneLinkable linkable = (IRedstoneLinkable) obj;
            BlockPos there = SableCompat.toWorldPos(level, linkable.getLocation());
            if (!here.closerThan(there, range)) continue;
            power = Math.max(power, linkable.getTransmittedStrength());
            if (power >= 15) return 15;
        }
        return power;
    }

    public void sendLinkSignal(ItemStack first, ItemStack last, int strength) {
        ItemStack normalizedFirst = normalize(first);
        ItemStack normalizedLast = normalize(last);

        String key = channelKey(normalizedFirst, normalizedLast);
        RedstoneLinkChannel channel = channels.get(key);
        Level level = owner.level();

        if (channel == null) {
            channel = new RedstoneLinkChannel(this, normalizedFirst, normalizedLast, clampStrength(strength), true);
            channels.put(key, channel);
            owner.markDirty();
            if (level != null && !level.isClientSide) {
                channel.register(level);
                channel.update();
                registeredLevel = level;
            }
        } else {
            channel.setTransmitStrength(strength);
            refreshReceivedStrength(channel, normalizedFirst, normalizedLast);
        }
    }

    /**
     * Subscribe {@code computer} to {@code redstone_link_change} events for this
     * channel. Each computer is tracked independently, so an unwatch by one only
     * affects events for that computer.
     *
     * @return the current signal on the channel — handy for initialising state
     *         in Lua without a follow-up {@code getLinkSignal} call.
     */
    public int watchLinkSignal(IComputerAccess computer, ItemStack first, ItemStack last) {
        ItemStack normalizedFirst = normalize(first);
        ItemStack normalizedLast = normalize(last);

        String key = channelKey(normalizedFirst, normalizedLast);
        RedstoneLinkChannel channel = channels.get(key);
        Level level = owner.level();

        boolean justBecameListening;
        if (channel == null) {
            channel = new RedstoneLinkChannel(this, normalizedFirst, normalizedLast, 0, false);
            channels.put(key, channel);
            channel.addSubscriber(computer);
            owner.markDirty();
            if (level != null && !level.isClientSide) {
                channel.register(level);
                channel.update();
                registeredLevel = level;
            }
            justBecameListening = true;
        } else {
            justBecameListening = channel.addSubscriber(computer);
            if (justBecameListening) {
                owner.markDirty();
                channel.update();
            }
        }

        int current = getLinkSignal(normalizedFirst, normalizedLast);
        channel.setReceivedStrengthSilently(current);
        return current;
    }

    public void unwatchLinkSignal(IComputerAccess computer, ItemStack first, ItemStack last) {
        RedstoneLinkChannel channel = channels.get(channelKey(normalize(first), normalize(last)));
        if (channel == null) return;
        if (!channel.removeSubscriber(computer)) return; // wasn't subscribed, or others still are
        finishStoppedListening(channel);
    }

    /**
     * Called by the peripheral when a computer detaches (logout, chunk unload,
     * etc.). Drops the computer from every channel's subscriber set.
     */
    public void onComputerDetached(IComputerAccess computer) {
        Iterator<Map.Entry<String, RedstoneLinkChannel>> it = channels.entrySet().iterator();
        while (it.hasNext()) {
            RedstoneLinkChannel channel = it.next().getValue();
            if (!channel.removeSubscriber(computer)) continue;
            // last subscriber gone — handle teardown inline so we can use the iterator
            if (!channel.isTransmitting()) {
                channel.unregister();
                it.remove();
            } else {
                channel.update();
            }
            owner.markDirty();
        }
    }

    private void finishStoppedListening(RedstoneLinkChannel channel) {
        owner.markDirty();
        if (!channel.isTransmitting()) {
            channel.unregister();
            channels.remove(channel.key());
        } else {
            channel.update();
        }
    }

    private void refreshReceivedStrength(RedstoneLinkChannel channel, ItemStack first, ItemStack last) {
        if (!channel.hasSubscribers()) return;
        channel.setReceivedStrength(getLinkSignal(first, last));
    }

    // ----- Lifecycle hooks for the owner -----

    public void onLoad() {
        Level level = owner.level();
        if (level == null || level.isClientSide) return;
        for (RedstoneLinkChannel channel : channels.values()) {
            channel.register(level);
            channel.update();
        }
        registeredLevel = level;
    }

    public void onUnload() {
        for (RedstoneLinkChannel channel : channels.values()) {
            channel.unregister();
        }
        registeredLevel = null;
    }

    /**
     * Re-register every channel with Create's handler in whatever level the
     * owner is now in. Cheap no-op if {@code owner.level()} hasn't actually
     * changed since the last call. Called by mobile owners (pocket, turtle)
     * when the holder crosses a chunk or dimension boundary.
     */
    public void migrateIfNeeded() {
        Level level = owner.level();
        if (level == registeredLevel) return;
        for (RedstoneLinkChannel channel : channels.values()) {
            channel.unregister();
            if (level != null && !level.isClientSide) {
                channel.register(level);
                channel.update();
            }
        }
        registeredLevel = level;
        lastListenerPos = null; // force a listener refresh on the next tick after migration
    }

    /**
     * If the owner has moved since this method was last called, re-poll every
     * listening channel's signal so subscribers receive events when the owner
     * crosses into / out of a transmitter's range.
     *
     * <p>Create's network handler only fires {@link IRedstoneLinkable#setReceivedStrength}
     * in response to <em>transmitter</em> changes — a moving listener never
     * triggers that path on its own. Mobile peripherals (pocket / turtle) call
     * this every tick from their {@code update} hook to close that gap.
     */
    public void refreshListenersIfMoved() {
        Level level = owner.level();
        if (level == null || level.isClientSide) return;
        BlockPos cur = SableCompat.toWorldPos(level, owner.pos());
        if (cur.equals(lastListenerPos)) return;
        lastListenerPos = cur;
        for (RedstoneLinkChannel channel : channels.values()) {
            if (!channel.hasSubscribers()) continue;
            channel.setReceivedStrength(getLinkSignal(channel.frequencyFirst(), channel.frequencyLast()));
        }
    }

    public boolean isEmpty() {
        return channels.isEmpty();
    }

    // ----- Forwarders for RedstoneLinkChannel -----

    /** Raw plot-local position — the convention every {@code IRedstoneLinkable} returns. */
    BlockPos pos() { return owner.pos(); }

    boolean isAlive() { return owner.isAlive(); }

    void markDirty() { owner.markDirty(); }

    void queueRedstoneLinkChange(RedstoneLinkChannel channel, int oldStrength, int newStrength) {
        ItemStack first = channel.frequencyFirst();
        ItemStack last = channel.frequencyLast();
        String f1 = itemIdString(first);
        String f2 = itemIdString(last);
        Integer c1 = dyeRgbOrNull(first);
        Integer c2 = dyeRgbOrNull(last);
        for (IComputerAccess computer : channel.subscribers()) {
            computer.queueEvent("redstone_link_change", f1, f2, newStrength, oldStrength, c1, c2);
        }
    }

    // ----- NBT (block entity uses these; mobile upgrades don't persist) -----

    public ListTag saveChannels(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (RedstoneLinkChannel channel : channels.values()) {
            if (channel.shouldSave()) list.add(channel.save(provider));
        }
        return list;
    }

    public void loadChannels(ListTag list, HolderLookup.Provider provider) {
        channels.clear();
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag tag)) continue;
            RedstoneLinkChannel channel = loadChannel(tag, provider);
            if (channel != null) channels.put(channel.key(), channel);
        }
    }

    /** Load a single channel — split out so the legacy single-channel format can reuse it. */
    public void addLegacyChannel(ItemStack first, ItemStack last, int strength) {
        RedstoneLinkChannel channel = new RedstoneLinkChannel(this, first, last, strength, true);
        channels.put(channel.key(), channel);
    }

    @Nullable
    private RedstoneLinkChannel loadChannel(CompoundTag tag, HolderLookup.Provider provider) {
        ItemStack first;
        ItemStack last;

        // Preferred (1.0.4+): full ItemStack NBT.
        if (tag.contains("FrequencyFirst", Tag.TAG_COMPOUND)) {
            first = ItemStack.parseOptional(provider, tag.getCompound("FrequencyFirst"));
            last  = ItemStack.parseOptional(provider, tag.getCompound("FrequencyLast"));
        } else {
            // Backward-compat string-id + separate-int format, and the original mod's plain
            // string-id format with no colors.
            first = fromFrequencyId(tag.getString("FrequencyFirst"));
            last  = fromFrequencyId(tag.getString("FrequencyLast"));
            if (tag.contains("DyeColorFirst", Tag.TAG_INT) && !first.isEmpty()) {
                first.set(DataComponents.DYED_COLOR, new DyedItemColor(tag.getInt("DyeColorFirst"), false));
            }
            if (tag.contains("DyeColorLast", Tag.TAG_INT) && !last.isEmpty()) {
                last.set(DataComponents.DYED_COLOR, new DyedItemColor(tag.getInt("DyeColorLast"), false));
            }
        }

        return new RedstoneLinkChannel(this, first, last, tag.getInt("Transmit"), true);
    }

    // ----- Shared item / frequency helpers -----

    public static ItemStack fromFrequencyId(String value) {
        String trimmed = (value == null) ? "" : value.trim();
        if (trimmed.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /**
     * Build an ItemStack from an item registry id, optionally tinted with a 24-bit
     * RGB color stored as the {@code DYED_COLOR} component. Mirrors the logic
     * Create's {@code Frequency} uses to key colored networks.
     *
     * <p>Any int outside 0x000000–0xFFFFFF is masked to 24 bits, so passing
     * {@code 0xFFFF3344} (with alpha) is equivalent to {@code 0xFF3344}.
     */
    public static ItemStack fromFrequencySpec(String itemId, @Nullable Integer rgb) {
        ItemStack stack = fromFrequencyId(itemId);
        if (stack.isEmpty() || rgb == null) return stack;
        ItemStack copy = stack.copy();
        copy.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF, false));
        return copy;
    }

    static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    static int clampStrength(int strength) {
        return Math.max(0, Math.min(15, strength));
    }

    private static int dyeRgb(ItemStack stack) {
        Integer rgb = dyeRgbOrNull(stack);
        return rgb == null ? -1 : rgb;
    }

    private static Integer dyeRgbOrNull(ItemStack stack) {
        if (stack.has(DataComponents.DYED_COLOR))
            return Objects.requireNonNull(stack.get(DataComponents.DYED_COLOR)).rgb();
        return null;
    }

    private static String itemIdString(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Stable per-channel string key including dye colors so colored variants don't collide. */
    static String channelKey(ItemStack first, ItemStack last) {
        return itemIdString(first) + "#" + dyeRgb(first)
             + "\0" + itemIdString(last) + "#" + dyeRgb(last);
    }
}
