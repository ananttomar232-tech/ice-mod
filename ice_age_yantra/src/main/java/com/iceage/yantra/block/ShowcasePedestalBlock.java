package com.iceage.yantra.block;

import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.shop.ShopLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Ice-carved showcase pedestal. Displays one Yantra floating above it; right-click to buy it. */
public class ShowcasePedestalBlock extends BaseEntityBlock {
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 5);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0.5, 0, 0.5, 15.5, 3, 15.5), Block.box(3.5, 3, 3.5, 12.5, 13, 12.5),
            Block.box(1, 13, 1, 15, 15.2, 15));

    public ShowcasePedestalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(TIER);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide) {
            Tier t = Tier.byNumber(state.getValue(TIER));
            if (t != null) ShopLogic.purchase((ServerPlayer) player, t);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShowcasePedestalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return (lvl, p, st, be) -> {
            if (be instanceof ShowcasePedestalBlockEntity ped) ped.clientTick();
        };
    }
}
