package com.iceage.yantra.block;

import com.iceage.yantra.ShopPortals;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** The Exit Portal inside the Ice Shop: step into it to return to the village. */
public class ExitPortalBlock extends PortalBlock {
    public ExitPortalBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void onPlayerEntered(ServerPlayerEntity player, BlockPos base, Direction facing) {
        ShopPortals.exitShop(player);
    }
}
