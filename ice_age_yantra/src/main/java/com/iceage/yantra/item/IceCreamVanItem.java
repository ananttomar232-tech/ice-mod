package com.iceage.yantra.item;

import com.iceage.yantra.entity.IceCreamTruckEntity;
import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Places the driveable Musical Ice Cream Van (also used for the testing spawn egg). */
public class IceCreamVanItem extends Item {
    private final boolean egg;

    public IceCreamVanItem(Properties props, boolean egg) {
        super(props);
        this.egg = egg;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        Vec3 at = Vec3.atBottomCenterOf(pos);
        IceCreamTruckEntity van = ModEntities.ICE_CREAM_TRUCK.get().create(server);
        if (van == null) return InteractionResult.FAIL;
        float yaw = player != null ? player.getYRot() : 0f;
        van.moveTo(at.x, at.y, at.z, yaw, 0f);
        if (!level.noCollision(van, van.getBoundingBox().deflate(0.05))) {
            van.moveTo(at.x, at.y + 1, at.z, yaw, 0f);
            if (!level.noCollision(van, van.getBoundingBox().deflate(0.05))) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("msg.ice_age_yantra.van.no_space").withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
        }
        if (player != null) van.setOwner(player.getUUID());
        server.addFreshEntity(van);
        server.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        if (egg) {
            tip.add(Component.translatable("tooltip.ice_age_yantra.van_egg").withStyle(ChatFormatting.GRAY));
        } else {
            YantraBlockItem.addTierTooltip(Tier.VAN, tip);
        }
        tip.add(Component.translatable("tooltip.ice_age_yantra.van.drive").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        if (egg) return;
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.iceage.yantra.client.render.YantraItemRenderer.get();
            }
        });
    }
}
