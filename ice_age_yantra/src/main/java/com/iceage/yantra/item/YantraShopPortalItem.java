package com.iceage.yantra.item;

import com.iceage.yantra.block.IcePortalBlock;
import com.iceage.yantra.registry.ModBlocks;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pre-given Shop Portal. Right-click anywhere: opens the 3D Yantra Progression Shop.
 * Sneak + right-click a block: conjures the physical Ice Portal to the Frozen Showroom.
 */
public class YantraShopPortalItem extends Item {
    public YantraShopPortalItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.iceage.yantra.client.ClientHooks::openShop);
        } else {
            level.playSound(null, player.blockPosition(), ModSounds.PORTAL_WHOOSH.get(), SoundSource.PLAYERS, 0.5f, 1.6f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        BlockPos base = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (ctx.getClickedFace() != Direction.UP) base = ctx.getClickedPos().above();
        if (!level.getBlockState(base).canBeReplaced() || !level.getBlockState(base.above()).canBeReplaced()) {
            if (!level.isClientSide) {
                
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            Direction facing = player.getDirection().getOpposite();
            BlockState lower = ModBlocks.ICE_PORTAL.get().defaultBlockState().setValue(IcePortalBlock.FACING, facing)
                    .setValue(IcePortalBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(base, lower, Block.UPDATE_ALL);
            level.setBlock(base.above(), lower.setValue(IcePortalBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            level.playSound(null, base, ModSounds.PORTAL_WHOOSH.get(), SoundSource.BLOCKS, 1.2f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.ice_age_yantra.portal.use").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.ice_age_yantra.portal.sneak").withStyle(ChatFormatting.GRAY));
    }
}
