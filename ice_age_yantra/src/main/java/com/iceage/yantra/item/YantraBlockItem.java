package com.iceage.yantra.item;

import com.iceage.yantra.progress.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Yantra block items render their full 3D rig in hand / inventory (custom item renderer). */
public class YantraBlockItem extends BlockItem {
    public final Tier tier;

    public YantraBlockItem(Block block, Tier tier, Properties props) {
        super(block, props);
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        addTierTooltip(tier, tip);
    }

    public static void addTierTooltip(Tier tier, List<Component> tip) {
        tip.add(Component.translatable("tooltip.ice_age_yantra.tier", tier.number).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        tip.add(Component.translatable("tooltip.ice_age_yantra.target", String.format("%,d", tier.target)).withStyle(ChatFormatting.GRAY));
        tip.add(tier.gag().copy().withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.iceage.yantra.client.render.YantraItemRenderer.get();
            }
        });
    }

    /** Two-block-tall variant (the fridge). */
    public static class Tall extends DoubleHighBlockItem {
        public final Tier tier;

        public Tall(Block block, Tier tier, Properties props) {
            super(block, props);
            this.tier = tier;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            addTierTooltip(tier, tip);
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return com.iceage.yantra.client.render.YantraItemRenderer.get();
                }
            });
        }
    }
}
