package com.iceage.yantra.block;

import com.iceage.yantra.Tier;
import com.iceage.yantra.YantraActions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.property.DirectionProperty;
import com.iceage.yantra.ModBlocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A Yantra: right-click to fire 20-30 flying ice blocks and instantly freeze the ground around it. Reusable forever. */
public class YantraBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public final Tier tier;
    private final VoxelShape shape;

    public YantraBlock(Settings settings, Tier tier, VoxelShape shape) {
        super(settings);
        this.tier = tier;
        this.shape = shape;
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new YantraBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlocks.YANTRA_BE) return null;
        if (world.isClient) return (w, p, s, be) -> YantraBlockEntity.clientTick(w, p, s, (YantraBlockEntity) be);
        return (w, p, s, be) -> YantraBlockEntity.serverTick(w, p, s, (YantraBlockEntity) be);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** A machine that has started its cycle is used up: breaking it drops nothing. */
    @Override
    public java.util.List<net.minecraft.item.ItemStack> getDroppedStacks(BlockState state, net.minecraft.loot.context.LootContextParameterSet.Builder builder) {
        BlockEntity be = builder.getOptional(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY);
        if (be instanceof YantraBlockEntity y && y.phase != YantraBlockEntity.IDLE) return java.util.List.of();
        return super.getDroppedStacks(state, builder);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return shape;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof YantraBlockEntity be) {
            be.tryStart(sp);   // one single right-click runs the whole cycle; used-up machines ignore clicks
        }
        return ActionResult.CONSUME;
    }
}
