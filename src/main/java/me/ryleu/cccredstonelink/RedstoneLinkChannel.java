package me.ryleu.cccredstonelink;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single (frequency1, frequency2, strength) channel that registers itself with
 * Create's redstone-link network. Decoupled from any specific owner — its
 * {@code getLocation()} / {@code isAlive()} are answered by the
 * {@link RedstoneLinkChannelHost.LinkHost} that owns it.
 */
final class RedstoneLinkChannel implements IRedstoneLinkable {

    private final RedstoneLinkChannelHost host;
    private final ItemStack frequencyFirst;
    private final ItemStack frequencyLast;
    private int transmittedStrength;
    private int receivedStrength;
    private boolean transmitting;

    /**
     * Computers subscribed to {@code redstone_link_change} events on this channel.
     * Listening (in Create's sense) is derived from "has subscribers" — so multiple
     * computers can independently watch and unwatch the same frequency pair, and
     * Create's network handler only stops calling {@link #setReceivedStrength}
     * when every subscriber is gone.
     */
    private final Set<IComputerAccess> subscribers = ConcurrentHashMap.newKeySet();

    @Nullable
    private Level registeredLevel;

    RedstoneLinkChannel(RedstoneLinkChannelHost host, ItemStack first, ItemStack last, int strength, boolean transmitting) {
        this.host = host;
        this.frequencyFirst = RedstoneLinkChannelHost.normalize(first);
        this.frequencyLast = RedstoneLinkChannelHost.normalize(last);
        this.transmittedStrength = RedstoneLinkChannelHost.clampStrength(strength);
        this.transmitting = transmitting;
    }

    ItemStack frequencyFirst() { return frequencyFirst; }
    ItemStack frequencyLast()  { return frequencyLast;  }

    String key() {
        return RedstoneLinkChannelHost.channelKey(frequencyFirst, frequencyLast);
    }

    /**
     * Serialise this channel as a {@link CompoundTag}. Frequency slots are written
     * as full {@link ItemStack} NBT via {@link ItemStack#saveOptional} so dye-color
     * and any other components survive round-trip, matching Create's own
     * {@code LinkBehaviour} on-disk format.
     */
    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put("FrequencyFirst", frequencyFirst.saveOptional(provider));
        tag.put("FrequencyLast",  frequencyLast.saveOptional(provider));
        tag.putInt("Transmit", transmittedStrength);
        return tag;
    }

    void register(Level level) {
        if (level == null || level.isClientSide) return;
        if (registeredLevel == level) return;
        if (registeredLevel != null) unregister();
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
        registeredLevel = level;
    }

    void unregister() {
        if (registeredLevel == null) return;
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(registeredLevel, this);
        registeredLevel = null;
    }

    void update() {
        if (registeredLevel == null || registeredLevel.isClientSide) return;
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(registeredLevel, this);
    }

    void setTransmitStrength(int strength) {
        this.transmittedStrength = RedstoneLinkChannelHost.clampStrength(strength);
        this.transmitting = true;
        host.markDirty();
        update();
    }

    boolean shouldSave() { return transmitting; }

    boolean isTransmitting() { return transmitting; }

    /**
     * Adds {@code computer} to this channel's subscribers. Returns {@code true}
     * iff this was the first subscriber (i.e. the channel just transitioned to
     * listening from Create's perspective — caller should run {@link #update()}).
     */
    boolean addSubscriber(IComputerAccess computer) {
        boolean wasEmpty = subscribers.isEmpty();
        subscribers.add(computer);
        return wasEmpty;
    }

    /**
     * Removes {@code computer}. Returns {@code true} iff it was actually a
     * subscriber and the set is now empty (i.e. the channel just transitioned
     * out of listening — caller should decide whether to remove or update it).
     */
    boolean removeSubscriber(IComputerAccess computer) {
        if (!subscribers.remove(computer)) return false;
        return subscribers.isEmpty();
    }

    boolean hasSubscribers() { return !subscribers.isEmpty(); }

    Set<IComputerAccess> subscribers() { return subscribers; }

    void setReceivedStrengthSilently(int power) {
        this.receivedStrength = RedstoneLinkChannelHost.clampStrength(power);
    }

    @Override public int getTransmittedStrength() { return transmittedStrength; }

    @Override
    public void setReceivedStrength(int power) {
        int newStrength = RedstoneLinkChannelHost.clampStrength(power);
        if (newStrength == receivedStrength) return;
        int oldStrength = receivedStrength;
        receivedStrength = newStrength;
        host.queueRedstoneLinkChange(this, oldStrength, newStrength);
    }

    @Override public boolean isListening() { return hasSubscribers(); }

    @Override public boolean isAlive() { return host.isAlive(); }

    @Override
    public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
        return Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(frequencyFirst),
                RedstoneLinkNetworkHandler.Frequency.of(frequencyLast)
        );
    }

    @Override
    public BlockPos getLocation() {
        // Convention: return the raw plot-local position. Sable's mixin on
        // RedstoneLinkNetworkHandler.updateNetworkOf transforms this through
        // the containing sub-level's pose itself, so applying the transform
        // here would double-count (or skip the transform when we *are* on a
        // contraption, depending on direction). Our own getLinkSignal does
        // the equivalent transform on both ends explicitly.
        return host.pos();
    }
}
