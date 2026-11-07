package com.iceage.yantra.block;

import com.iceage.yantra.IcePoints;
import com.iceage.yantra.ModItems;
import com.iceage.yantra.Tier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import com.iceage.yantra.ModBlocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A display piece in the Ice Shop. Right-click to take the item once you froze enough blocks. */
public class ShowcaseBlock extends BlockWithEntity {
    public static final IntProperty TIER = IntProperty.of("tier", 1, 5);

    public ShowcaseBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(TIER, 1));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TIER);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClient || type != ModBlocks.SHOWCASE_BE) return null;
        return (w, p, s, be) -> ShowcaseBlockEntity.clientTick(w, p, s, (ShowcaseBlockEntity) be);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return VoxelShapes.fullCube();
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ShowcaseBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        Tier tier = Tier.byNumber(state.get(TIER));
        long points = IcePoints.get(world.getServer(), player.getUuid());
        if (points < tier.price) {
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 0.8f, 1.2f);
            return ActionResult.CONSUME;
        }
        ItemStack stack = new ItemStack(ModItems.forTier(tier));
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        world.playSound(null, pos, com.iceage.yantra.ModSounds.PURCHASE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        return ActionResult.CONSUME;
    }
}
