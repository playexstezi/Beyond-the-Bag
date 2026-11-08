package com.echobroadcast.beyondthebag.item;

import com.echobroadcast.beyondthebag.client.BagScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DimensionBagItem extends Item {
    public DimensionBagItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.minecraft.client.Minecraft.getInstance().setScreen(new BagScreen()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
