package me.ryleu.cccredstonelink.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sable-typed implementation, isolated from the rest of the mod so its types
 * are class-loaded lazily. Callers must go through {@link SableCompat} and
 * gate on {@link SableCompat#IS_LOADED} — calling any method here without
 * Sable installed will fail at link time.
 */
final class SableImpl {

    private SableImpl() {
    }

    /**
     * Looks up the sub-level (if any) containing the block at {@code pos} in
     * {@code level}, then transforms {@code pos} from plot-local space into
     * the contraption's current visual world position via its logical pose.
     * When the block isn't on any sub-level, returns {@code localPos} unchanged.
     */
    static Vec3 toWorldPos(Level level, BlockPos pos, Vec3 localPos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        if (sub == null) return localPos;
        return sub.logicalPose().transformPosition(localPos);
    }
}
