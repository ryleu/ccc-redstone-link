package me.ryleu.cccredstonelink;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.AbstractPocketUpgrade;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Pocket-computer upgrade exposing the redstone-link bridge peripheral.
 * The transmitter lives in Create's network handler at the holder entity's
 * position; we let Create's range checks read the holder's current position
 * lazily via the {@link RedstoneLinkChannelHost.LinkHost} adapter, and we only
 * re-register channels when the holder crosses a level boundary.
 */
public class RedstoneLinkPocketUpgrade extends AbstractPocketUpgrade {

    public RedstoneLinkPocketUpgrade(ItemStack stack) {
        super("upgrade.cccredstonelink.redstone_link_pocket.adjective", stack);
    }

    @Override
    public UpgradeType<? extends IPocketUpgrade> getType() {
        return CCRegistration.REDSTONE_LINK_POCKET.get();
    }

    @Nullable
    @Override
    public IPeripheral createPeripheral(@NonNull IPocketAccess access) {
        return new RedstoneLinkUpgradePeripheral(new PocketLinkHost(access, this));
    }

    @Override
    public void update(@NonNull IPocketAccess access, @Nullable IPeripheral peripheral) {
        if (peripheral instanceof RedstoneLinkUpgradePeripheral linkPeripheral) {
            linkPeripheral.channelHost().migrateIfNeeded();
        }
    }

    private static final class PocketLinkHost implements RedstoneLinkChannelHost.LinkHost {
        private final IPocketAccess access;
        private final RedstoneLinkPocketUpgrade upgrade;

        PocketLinkHost(IPocketAccess access, RedstoneLinkPocketUpgrade upgrade) {
            this.access = access;
            this.upgrade = upgrade;
        }

        @Override
        public @Nullable Level level() {
            return access.getLevel();
        }

        @Override
        public BlockPos pos() {
            Vec3 p = access.getPosition();
            return BlockPos.containing(p);
        }

        @Override
        public boolean isAlive() {
            Level lv = access.getLevel();
            if (lv == null || lv.isClientSide) return false;
            var equipped = access.getUpgrade();
            return equipped != null && equipped.upgrade() == upgrade;
        }
    }
}
