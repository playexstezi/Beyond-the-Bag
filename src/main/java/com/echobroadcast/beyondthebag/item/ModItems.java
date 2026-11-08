package com.echobroadcast.beyondthebag.item;

import com.echobroadcast.beyondthebag.BeyondTheBag;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BeyondTheBag.MODID);
    public static final RegistryObject<Item> DIMENSION_BAG = ITEMS.register("dimension_bag",
            () -> new DimensionBagItem(new Item.Properties().stacksTo(1)));
    private ModItems() {}
}
