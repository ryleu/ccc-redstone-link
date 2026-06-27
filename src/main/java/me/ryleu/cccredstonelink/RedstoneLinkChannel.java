package me.ryleu.cccredstonelink;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

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
    private boolean listening;
    private boolean transmitting;

    @Nullable
    private Level registeredLevel;

    RedstoneLinkChannel(RedstoneLinkChannelHost host, ItemStack first, ItemStack last, int strength, boolean listening, boolean transmitting) {
        this.host = host;
        this.frequencyFirst = RedstoneLinkChannelHost.normalize(first);
        this.frequencyLast = RedstoneLinkChannelHost.normalize(last);
        this.transmittedStrength = RedstoneLinkChannelHost.clampStrength(strength);
        this.listening = listening;
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

    boolean isListeningChannel() { return listening; }

    boolean shouldSave() { return transmitting; }

    boolean shouldRemoveWhenNotListening() { return !transmitting; }

    void setListening(boolean listening) {
        this.listening = listening;
    }

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
        host.queueRedstoneLinkChange(frequencyFirst, frequencyLast, oldStrength, newStrength);
    }

    @Override public boolean isListening() { return listening; }

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
