package me.ryleu.cccredstonelink;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RedstoneLinkBridgeBlockEntity extends BlockEntity {

    private final Map<String, RedstoneLinkChannel> channels = new LinkedHashMap<>();

    public RedstoneLinkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_LINK_BRIDGE.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        Level currentLevel = this.level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            for (RedstoneLinkChannel channel : this.channels.values()) {
                channel.register(currentLevel);
                channel.update(currentLevel);
            }
        }
    }

    @Override
    public void setRemoved() {
        Level currentLevel = this.level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            for (RedstoneLinkChannel channel : this.channels.values()) {
                channel.unregister(currentLevel);
            }
        }
        super.setRemoved();
    }

    // -------------------------------------------------------------------------
    // Public API (called from the peripheral)
    // -------------------------------------------------------------------------

    /**
     * Query the current signal strength on a redstone link network.
     *
     * @param first ItemStack for the first frequency slot (may carry a dye color component)
     * @param last  ItemStack for the second frequency slot (may carry a dye color component)
     */
    public int getLinkSignal(ItemStack first, ItemStack last) {
        Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide) return 0;

        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(normalize(first)),
                RedstoneLinkNetworkHandler.Frequency.of(normalize(last))
        );
        return querySignal(currentLevel, key);
    }

    /**
     * Transmit a redstone signal on a link network channel.
     *
     * @param first    ItemStack for the first frequency slot
     * @param last     ItemStack for the second frequency slot
     * @param strength Signal strength 0–15 (clamped automatically)
     */
    public void sendLinkSignal(ItemStack first, ItemStack last, int strength) {
        ItemStack normalizedFirst = normalize(first);
        ItemStack normalizedLast = normalize(last);

        String key = channelKey(normalizedFirst, normalizedLast);
        RedstoneLinkChannel channel = this.channels.get(key);
        Level currentLevel = this.level;

        if (channel == null) {
            channel = new RedstoneLinkChannel(normalizedFirst, normalizedLast, clampStrength(strength));
            this.channels.put(key, channel);
            this.setChanged();
            if (currentLevel != null && !currentLevel.isClientSide) {
                channel.register(currentLevel);
                channel.update(currentLevel);
            }
        } else {
            channel.setTransmitStrength(strength);
        }
    }

    // -------------------------------------------------------------------------
    // ItemStack / frequency helpers
    // -------------------------------------------------------------------------

    /**
     * Build an ItemStack from an item registry ID string.
     * Returns {@link ItemStack#EMPTY} if the ID is blank or unknown.
     *
     * <p>This is the original helper kept for backward compatibility.
     */
    public static ItemStack fromFrequencyId(String value) {
        String trimmed = (value == null) ? "" : value.trim();
        if (trimmed.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /**
     * Build an ItemStack from an item registry ID, optionally tinted with a
     * 24-bit RGB color. The color is stored as the {@code DYED_COLOR} data
     * component — the exact same component used by dyed leather armour, and
     * the same one Create's {@code Frequency} reads to differentiate networks
     * that share an item type.
     *
     * <p>Any int outside 0x000000–0xFFFFFF is masked to 24 bits, so passing
     * {@code 0xFFFF3344} (with alpha) is equivalent to {@code 0xFF3344}.
     *
     * @param itemId Item registry ID (e.g. {@code "minecraft:leather_chestplate"})
     * @param rgb    24-bit RGB color, or {@code null} for no color
     * @return ItemStack ready to be passed to {@link #getLinkSignal} /
     *         {@link #sendLinkSignal}
     */
    public static ItemStack fromFrequencySpec(String itemId, @Nullable Integer rgb) {
        ItemStack stack = fromFrequencyId(itemId);
        if (stack.isEmpty() || rgb == null) return stack;
        ItemStack copy = stack.copy();
        // showInTooltip=false: Create only reads .rgb() for network keying,
        // so the tooltip flag has no gameplay impact.
        copy.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF, false));
        return copy;
    }

    /**
     * Returns the RGB value stored in the {@code DYED_COLOR} component, or
     * {@code -1} if the stack carries no dye color. Mirrors the logic inside
     * Create's {@code Frequency} constructor.
     */
    private static int getDyeColorRgb(ItemStack stack) {
        if (stack.has(DataComponents.DYED_COLOR))
            return Objects.requireNonNull(stack.get(DataComponents.DYED_COLOR)).rgb();
        return -1;
    }

    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static int clampStrength(int strength) {
        return Math.max(0, Math.min(15, strength));
    }

    private static String itemIdString(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * A stable string representation of one channel that includes both items
     * and both dye RGBs, so two channels that differ only in color get
     * distinct map keys.
     */
    private static String channelKey(ItemStack first, ItemStack last) {
        return itemIdString(first) + "#" + getDyeColorRgb(first)
             + "\0" + itemIdString(last) + "#" + getDyeColorRgb(last);
    }

    private static int querySignal(Level level,
            Couple<RedstoneLinkNetworkHandler.Frequency> frequency) {
        int power = 0;
        Set<?> network = Create.REDSTONE_LINK_NETWORK_HANDLER
                .networksIn(level)
                .get(frequency);
        if (network == null) return 0;
        for (Object obj : network) {
            IRedstoneLinkable linkable = (IRedstoneLinkable) obj;
            power = Math.max(power, linkable.getTransmittedStrength());
            if (power >= 15) return 15;
        }
        return power;
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NonNull CompoundTag tag, HolderLookup.@NonNull Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag channelList = new ListTag();
        for (RedstoneLinkChannel channel : this.channels.values()) {
            channelList.add(channel.save(provider));
        }
        tag.put("Channels", channelList);
    }

    @Override
    protected void loadAdditional(@NonNull CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.channels.clear();

        if (tag.contains("Channels", Tag.TAG_LIST)) {
            ListTag channelList = tag.getList("Channels", Tag.TAG_COMPOUND);
            for (Tag entry : channelList) {
                if (!(entry instanceof CompoundTag channelTag)) continue;
                RedstoneLinkChannel channel = loadChannel(channelTag, provider);
                if (channel != null) this.channels.put(channel.key(), channel);
            }
        } else {
            // Legacy single-channel format from the original mod (pre-1.0.4)
            ItemStack frequencyFirst = fromFrequencyId(tag.getString("FrequencyFirst"));
            ItemStack frequencyLast  = fromFrequencyId(tag.getString("FrequencyLast"));
            RedstoneLinkChannel channel = new RedstoneLinkChannel(
                    frequencyFirst, frequencyLast, tag.getInt("Transmit"));
            this.channels.put(channel.key(), channel);
        }
    }

    private RedstoneLinkChannel loadChannel(CompoundTag tag, HolderLookup.Provider provider) {
        ItemStack frequencyFirst;
        ItemStack frequencyLast;

        // Preferred (1.0.4+): FrequencyFirst/Last stored as full ItemStack NBT
        // produced by ItemStack.saveOptional — preserves every data component
        // (DYED_COLOR, custom_name, anything else), exactly like Create's own
        // LinkBehaviour does it.
        if (tag.contains("FrequencyFirst", Tag.TAG_COMPOUND)) {
            frequencyFirst = ItemStack.parseOptional(provider, tag.getCompound("FrequencyFirst"));
            frequencyLast  = ItemStack.parseOptional(provider, tag.getCompound("FrequencyLast"));
        } else {
            // Backward-compat: read the older string-id + separate-int format
            // (and the original mod's plain string-id format with no colors).
            frequencyFirst = fromFrequencyId(tag.getString("FrequencyFirst"));
            frequencyLast  = fromFrequencyId(tag.getString("FrequencyLast"));
            if (tag.contains("DyeColorFirst", Tag.TAG_INT) && !frequencyFirst.isEmpty()) {
                frequencyFirst.set(DataComponents.DYED_COLOR,
                        new DyedItemColor(tag.getInt("DyeColorFirst"), false));
            }
            if (tag.contains("DyeColorLast", Tag.TAG_INT) && !frequencyLast.isEmpty()) {
                frequencyLast.set(DataComponents.DYED_COLOR,
                        new DyedItemColor(tag.getInt("DyeColorLast"), false));
            }
        }

        return new RedstoneLinkChannel(frequencyFirst, frequencyLast, tag.getInt("Transmit"));
    }

    // -------------------------------------------------------------------------
    // Inner class – one transmitter per distinct (first, last) frequency pair
    // -------------------------------------------------------------------------

    private final class RedstoneLinkChannel implements IRedstoneLinkable {

        private final ItemStack frequencyFirst;
        private final ItemStack frequencyLast;
        private int transmittedStrength;
        private boolean registered;

        private RedstoneLinkChannel(ItemStack first, ItemStack last, int strength) {
            this.frequencyFirst     = normalize(first);
            this.frequencyLast      = normalize(last);
            this.transmittedStrength = clampStrength(strength);
        }

        /**
         * Serialise this channel to NBT. The frequencies are stored as
         * full {@link ItemStack} NBT via {@link ItemStack#saveOptional} so
         * every data component on the stack — including {@code DYED_COLOR}
         * — survives round-trip. This matches Create's own
         * {@code LinkBehaviour} on-disk format.
         */
        private CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.put("FrequencyFirst", frequencyFirst.saveOptional(provider));
            tag.put("FrequencyLast",  frequencyLast.saveOptional(provider));
            tag.putInt("Transmit", transmittedStrength);
            return tag;
        }

        private String key() {
            return channelKey(frequencyFirst, frequencyLast);
        }

        private void register(Level level) {
            if (registered) return;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
            registered = true;
        }

        private void unregister(Level level) {
            if (!registered) return;
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
            registered = false;
        }

        private void update(Level level) {
            if (level != null && !level.isClientSide)
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
        }

        private void setTransmitStrength(int strength) {
            this.transmittedStrength = clampStrength(strength);
            RedstoneLinkBridgeBlockEntity.this.setChanged();
            Level currentLevel = RedstoneLinkBridgeBlockEntity.this.level;
            if (currentLevel != null && !currentLevel.isClientSide) update(currentLevel);
        }

        // IRedstoneLinkable

        @Override
        public int getTransmittedStrength() { return transmittedStrength; }

        @Override
        public void setReceivedStrength(int power) { /* transmit-only channel */ }

        @Override
        public boolean isListening() { return false; }

        @Override
        public boolean isAlive() {
            return RedstoneLinkBridgeBlockEntity.this.level != null
                    && !RedstoneLinkBridgeBlockEntity.this.isRemoved();
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(frequencyFirst),
                    RedstoneLinkNetworkHandler.Frequency.of(frequencyLast)
            );
        }

        @Override
        public BlockPos getLocation() {
            return RedstoneLinkBridgeBlockEntity.this.worldPosition;
        }
    }
}
