package me.ryleu.cccredstonelink;

import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;

public class RedstoneLinkBridgeBlockEntity extends BlockEntity {

    private final RedstoneLinkChannelHost channelHost;

    public RedstoneLinkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_LINK_BRIDGE.get(), pos, state);
        this.channelHost = new RedstoneLinkChannelHost(new BlockEntityLinkHost());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        channelHost.onLoad();
    }

    @Override
    public void setRemoved() {
        channelHost.onUnload();
        super.setRemoved();
    }

    // -------------------------------------------------------------------------
    // Public API (called from the peripheral)
    // -------------------------------------------------------------------------

    public int getLinkSignal(ItemStack first, ItemStack last) {
        return channelHost.getLinkSignal(first, last);
    }

    public void sendLinkSignal(ItemStack first, ItemStack last, int strength) {
        channelHost.sendLinkSignal(first, last, strength);
    }

    public int watchLinkSignal(IComputerAccess computer, ItemStack first, ItemStack last) {
        return channelHost.watchLinkSignal(computer, first, last);
    }

    public void unwatchLinkSignal(IComputerAccess computer, ItemStack first, ItemStack last) {
        channelHost.unwatchLinkSignal(computer, first, last);
    }

    void detachComputer(IComputerAccess computer) {
        channelHost.onComputerDetached(computer);
    }

    // -------------------------------------------------------------------------
    // Backward-compat static helpers — the peripheral still calls these
    // -------------------------------------------------------------------------

    public static ItemStack fromFrequencyId(String value) {
        return RedstoneLinkChannelHost.fromFrequencyId(value);
    }

    public static ItemStack fromFrequencySpec(String itemId, @Nullable Integer rgb) {
        return RedstoneLinkChannelHost.fromFrequencySpec(itemId, rgb);
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NonNull CompoundTag tag, HolderLookup.@NonNull Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("Channels", channelHost.saveChannels(provider));
    }

    @Override
    protected void loadAdditional(@NonNull CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        if (tag.contains("Channels", Tag.TAG_LIST)) {
            channelHost.loadChannels(tag.getList("Channels", Tag.TAG_COMPOUND), provider);
        } else if (tag.contains("FrequencyFirst") || tag.contains("FrequencyLast") || tag.contains("Transmit")) {
            // Legacy single-channel format from the original mod (pre-1.0.4)
            ItemStack first = fromFrequencyId(tag.getString("FrequencyFirst"));
            ItemStack last  = fromFrequencyId(tag.getString("FrequencyLast"));
            channelHost.addLegacyChannel(first, last, tag.getInt("Transmit"));
        }
    }

    // -------------------------------------------------------------------------
    // LinkHost adapter
    // -------------------------------------------------------------------------

    private final class BlockEntityLinkHost implements RedstoneLinkChannelHost.LinkHost {
        @Override
        public @Nullable Level level() {
            return RedstoneLinkBridgeBlockEntity.this.level;
        }

        @Override
        public BlockPos pos() {
            return RedstoneLinkBridgeBlockEntity.this.worldPosition;
        }

        @Override
        public boolean isAlive() {
            Level lv = RedstoneLinkBridgeBlockEntity.this.level;
            return lv != null && !RedstoneLinkBridgeBlockEntity.this.isRemoved();
        }

        @Override
        public void markDirty() {
            RedstoneLinkBridgeBlockEntity.this.setChanged();
        }
    }
}
