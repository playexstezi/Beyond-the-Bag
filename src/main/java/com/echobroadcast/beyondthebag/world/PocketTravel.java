package com.echobroadcast.beyondthebag.world;

import com.echobroadcast.beyondthebag.BeyondTheBag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.Random;

public final class PocketTravel {
    private static final ResourceKey<Level> POCKET_KEY = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(BeyondTheBag.MODID, "pocket"));
    private static final String TAG = "BeyondTheBag";

    private PocketTravel() {}

    public static void enterHome(ServerPlayer player) {
        if (isInside(player)) { returnHome(player); return; }
        saveReturnPoint(player);
        ServerLevel pocket = getPocket(player);
        if (pocket == null) { player.sendSystemMessage(Component.literal("[Beyond the Bag] Карманное измерение не загрузилось. Проверь установку мода.")); return; }
        int base = region(player);
        int x = base;
        int z = base;
        makeHomeLanding(pocket, x, z);
        player.teleportTo(pocket, x + 0.5, 102, z + 0.5, player.getYRot(), player.getXRot());
        player.getPersistentData().getCompound(TAG).putBoolean("inside", true);
        player.sendSystemMessage(Component.literal("Мешок раскрывается. Это твоё личное пространство. Постройки сохраняются."));
    }

    public static void enterMaze(ServerPlayer player) {
        if (isInside(player)) { returnHome(player); return; }
        saveReturnPoint(player);
        ServerLevel pocket = getPocket(player);
        if (pocket == null) { player.sendSystemMessage(Component.literal("[Beyond the Bag] Карманное измерение не загрузилось.")); return; }
        int base = region(player);
        int x = base + 180;
        int z = base + 180;
        generateFreshMaze(pocket, x, z, player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits() ^ player.level().getGameTime());
        player.teleportTo(pocket, x + 0.5, 82, z + 0.5, player.getYRot(), player.getXRot());
        player.getPersistentData().getCompound(TAG).putBoolean("inside", true);
        player.sendSystemMessage(Component.literal("Нестабильное измерение сформировалось заново. Не задерживайся."));
    }

    private static void saveReturnPoint(ServerPlayer player) {
        CompoundTag data = player.getPersistentData().getCompound(TAG);
        data.putString("return_dimension", player.level().dimension().location().toString());
        data.putDouble("return_x", player.getX()); data.putDouble("return_y", player.getY()); data.putDouble("return_z", player.getZ());
        data.putFloat("return_yaw", player.getYRot()); data.putFloat("return_pitch", player.getXRot());
        player.getPersistentData().put(TAG, data);
    }

    private static boolean isInside(ServerPlayer player) {
        return player.level().dimension().equals(POCKET_KEY) && player.getPersistentData().getCompound(TAG).getBoolean("inside");
    }

    private static void returnHome(ServerPlayer player) {
        CompoundTag data = player.getPersistentData().getCompound(TAG);
        MinecraftServer server = player.getServer();
        ResourceLocation id = ResourceLocation.tryParse(data.getString("return_dimension"));
        ServerLevel destination = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        double x = data.getDouble("return_x");
        double y = data.getDouble("return_y");
        double z = data.getDouble("return_z");
        float yaw = data.getFloat("return_yaw");
        float pitch = data.getFloat("return_pitch");

        // Missing or unavailable return data must never send the player to an unsafe origin.
        if (destination == null || !data.contains("return_x") || !data.contains("return_y") || !data.contains("return_z")) {
            destination = server.getLevel(Level.OVERWORLD);
            if (destination == null) {
                player.sendSystemMessage(Component.literal("Не удалось найти безопасную точку возвращения."));
                return;
            }
            BlockPos spawn = destination.getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            y = spawn.getY() + 1.0;
            z = spawn.getZ() + 0.5;
            yaw = 0.0F;
            pitch = 0.0F;
        }
        player.teleportTo(destination, x, y, z, yaw, pitch);
        data.putBoolean("inside", false);
        player.getPersistentData().put(TAG, data);
        player.sendSystemMessage(Component.literal("Мешок схлопывается. Ты вернулся туда, откуда вошёл."));
    }

    private static ServerLevel getPocket(ServerPlayer player) { return player.getServer().getLevel(POCKET_KEY); }

    private static int region(ServerPlayer player) {
        int slot = Math.floorMod(player.getUUID().hashCode(), 10000);
        return (slot - 5000) * 512;
    }

    private static void makeHomeLanding(ServerLevel level, int x, int z) {
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
            level.setBlock(new BlockPos(x + dx, 100, z + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, 101, z), Blocks.LANTERN.defaultBlockState(), 3);
    }

    private static void generateFreshMaze(ServerLevel level, int x, int z, long seed) {
        Random random = new Random(seed);
        // Remove only the maze footprint; the home area is hundreds of blocks away and untouched.
        for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++)
            for (int y = 70; y <= 88; y++) level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++)
            level.setBlock(new BlockPos(x + dx, 79, z + dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
        // A shifting grid of corridors and dead ends.
        for (int gx = -18; gx <= 18; gx += 6) for (int gz = -18; gz <= 18; gz += 6) {
            if (random.nextFloat() < 0.62f) {
                for (int h = 1; h <= 4; h++) {
                    for (int t = -2; t <= 2; t++) {
                        if (random.nextBoolean()) level.setBlock(new BlockPos(x + gx + t, 79 + h, z + gz - 2), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                        if (random.nextBoolean()) level.setBlock(new BlockPos(x + gx - 2, 79 + h, z + gz + t), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Entrance chamber and a loot chest. The chest is newly populated on every visit.
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            for (int y = 80; y <= 83; y++) level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
        BlockPos chestPos = new BlockPos(x + 15, 80, z + 15);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setItem(10, new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(7)));
            chest.setItem(13, new ItemStack(random.nextBoolean() ? Items.DIAMOND : Items.EMERALD, 1 + random.nextInt(3)));
            chest.setItem(16, new ItemStack(Items.GOLDEN_APPLE, random.nextInt(2) + 1));
            if (random.nextInt(5) == 0) chest.setItem(22, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            chest.setChanged();
        }
    }
}
