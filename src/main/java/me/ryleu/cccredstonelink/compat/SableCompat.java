package me.ryleu.cccredstonelink.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Sable (physics engine) integration entry point.
 *
 * <p>Sable's contraptions ("sub-levels") keep their blocks in the parent
 * {@link Level} but render them at a transformed visual position derived from
 * the sub-level's {@code logicalPose}. A bridge on a contraption therefore has
 * a stable {@code worldPosition} in plot-local space while visually flying
 * around — so Create's redstone-link range checks compare against the wrong
 * coordinates unless we translate first.
 *
 * <p>This class is the safe-to-load entry point: it has no Sable imports of
 * its own and short-circuits with the input when Sable isn't installed. The
 * Sable-typed work lives in {@link SableImpl}, which is class-loaded lazily —
 * the JVM only resolves its references the first time one of its methods is
 * actually invoked, and the guards here ensure that never happens unless
 * Sable is on the classpath.
 */
public final class SableCompat {

    public static final boolean IS_LOADED = ModList.get().isLoaded("sable");

    private SableCompat() {
    }

    /**
     * Translates a block's plot-local position to its current visual world
     * position. Pass-through if Sable isn't installed or the block isn't on a
     * sub-level.
     */
    public static BlockPos toWorldPos(Level level, BlockPos pos) {
        if (!IS_LOADED) return pos;
        Vec3 world = SableImpl.toWorldPos(level, pos, Vec3.atCenterOf(pos));
        return BlockPos.containing(world);
    }
}
