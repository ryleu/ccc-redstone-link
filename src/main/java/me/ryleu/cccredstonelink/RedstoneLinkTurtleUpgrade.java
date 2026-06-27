package me.ryleu.cccredstonelink;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.AbstractTurtleUpgrade;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Turtle peripheral upgrade exposing the redstone-link bridge peripheral.
 * Mirrors {@link RedstoneLinkPocketUpgrade}; the turtle moves discretely
 * (a block at a time) so the chunk/level migration logic in
 * {@link RedstoneLinkChannelHost#migrateIfNeeded()} is just as relevant here.
 */
public class RedstoneLinkTurtleUpgrade extends AbstractTurtleUpgrade {

    public RedstoneLinkTurtleUpgrade(ItemStack stack) {
        super(TurtleUpgradeType.PERIPHERAL,
                "upgrade.cccredstonelink.redstone_link_turtle.adjective",
                stack);
    }

    @Override
    public UpgradeType<? extends ITurtleUpgrade> getType() {
        return CCRegistration.REDSTONE_LINK_TURTLE.get();
    }

    @Nullable
    @Override
    public IPeripheral createPeripheral(@NonNull ITurtleAccess turtle, @NonNull TurtleSide side) {
        return new RedstoneLinkUpgradePeripheral(new TurtleLinkHost(turtle, side, this));
    }

    @Override
    public void update(@NonNull ITurtleAccess turtle, @NonNull TurtleSide side) {
        IPeripheral peripheral = turtle.getPeripheral(side);
        if (peripheral instanceof RedstoneLinkUpgradePeripheral linkPeripheral) {
            RedstoneLinkChannelHost host = linkPeripheral.channelHost();
            host.migrateIfNeeded();
            // Turtles tick this too; same reasoning as the pocket upgrade.
            host.refreshListenersIfMoved();
        }
    }

    private static final class TurtleLinkHost implements RedstoneLinkChannelHost.LinkHost {
        private final ITurtleAccess turtle;
        private final TurtleSide side;
        private final RedstoneLinkTurtleUpgrade upgrade;

        TurtleLinkHost(ITurtleAccess turtle, TurtleSide side, RedstoneLinkTurtleUpgrade upgrade) {
            this.turtle = turtle;
            this.side = side;
            this.upgrade = upgrade;
        }

        @Override
        public @Nullable Level level() {
            return turtle.getLevel();
        }

        @Override
        public BlockPos pos() {
            return turtle.getPosition();
        }

        @Override
        public boolean isAlive() {
            if (turtle.isRemoved()) return false;
            Level lv = turtle.getLevel();
            if (lv == null || lv.isClientSide) return false;
            return turtle.getUpgrade(side) == upgrade;
        }
    }
}
