package me.ryleu.cccredstonelink;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

public class RedstoneLinkBridgeBlock
extends BaseEntityBlock
implements IWrenchable {
    public static final MapCodec<RedstoneLinkBridgeBlock> CODEC = RedstoneLinkBridgeBlock.simpleCodec(RedstoneLinkBridgeBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // Selection / collision shape per facing — rotations of the original
    // 12×6×12 controller box so the bridge sits flush against whatever surface
    // it was clicked onto.
    private static final Map<Direction, VoxelShape> SHAPES;
    static {
        Map<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
        m.put(Direction.UP,    Block.box(2,  0,  2, 14,  6, 14));
        m.put(Direction.DOWN,  Block.box(2, 10,  2, 14, 16, 14));
        m.put(Direction.NORTH, Block.box(2,  2, 10, 14, 14, 16));
        m.put(Direction.SOUTH, Block.box(2,  2,  0, 14, 14,  6));
        m.put(Direction.EAST,  Block.box(0,  2,  2,  6, 14, 14));
        m.put(Direction.WEST,  Block.box(10, 2,  2, 16, 14, 14));
        SHAPES = m;
    }

    public RedstoneLinkBridgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // Default FACING = UP. This is the backward-compatibility path: bridges
        // placed before this property existed were saved with no FACING in their
        // BlockState NBT. Vanilla's BlockState codec fills any property missing
        // from saved data with the block's default value, so existing bridges
        // load as FACING=UP and keep their original floor-sitting orientation.
        // No separate DataFixer registration is needed.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public @NonNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NonNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new RedstoneLinkBridgeBlockEntity(pos, state);
    }

    @Override
    public @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter worldIn, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        return null;
    }

    @Override
    public boolean isSignalSource(@NonNull BlockState state) {
        return false;
    }

    @Override
    public int getSignal(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull Direction side) {
        return 0;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!level.isClientSide) {
            ItemStack stack = new ItemStack(this);
            level.destroyBlock(pos, false, player);
            if (player == null || !player.getInventory().add(stack)) {
                Block.popResource(level, pos, stack);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
