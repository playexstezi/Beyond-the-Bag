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
        CompoundTag playerData = player.getPersistentData().getCompound(TAG);
        if (!playerData.getBoolean("home_generated")) {
            makeHomeLanding(pocket, x, z, player.getUUID().getLeastSignificantBits());
            playerData.putBoolean("home_generated", true);
            player.getPersistentData().put(TAG, playerData);
        }
        player.teleportTo(pocket, x + 0.5, 102, z + 0.5, player.getYRot(), player.getXRot());
        CompoundTag homeData = player.getPersistentData().getCompound(TAG);
        homeData.putBoolean("inside", true);
        homeData.putBoolean("in_maze", false);
        player.getPersistentData().put(TAG, homeData);
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
        CompoundTag expedition = player.getPersistentData().getCompound(TAG);
        expedition.putBoolean("inside", true);
        expedition.putBoolean("in_maze", true);
        expedition.putInt("maze_ticks", 0);
        expedition.putInt("maze_event_cooldown", 200);
        expedition.putBoolean("maze_boss_spawned", false);
        player.getPersistentData().put(TAG, expedition);
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

    public static void leavePocket(ServerPlayer player) {
        if (player.level().dimension().equals(POCKET_KEY)) returnHome(player);
        else player.sendSystemMessage(Component.literal("Ты сейчас не находишься внутри мешка."));
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
        data.putBoolean("in_maze", false);
        player.getPersistentData().put(TAG, data);
        player.sendSystemMessage(Component.literal("Мешок схлопывается. Ты вернулся туда, откуда вошёл."));
    }

    private static ServerLevel getPocket(ServerPlayer player) { return player.getServer().getLevel(POCKET_KEY); }

    private static int region(ServerPlayer player) {
        int slot = Math.floorMod(player.getUUID().hashCode(), 10000);
        return (slot - 5000) * 512;
    }

    private static void makeHomeLanding(ServerLevel level, int x, int z, long seed) {
        Random random = new Random(seed);
        // Initial home terrain is generated once. Returning to the home never clears player builds.
        for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) {
            int height = 98 + (int)(Math.sin(dx * 0.18) * 2) + (int)(Math.cos(dz * 0.16) * 2);
            for (int y = 88; y <= height; y++) {
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                level.setBlock(pos, y == height ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        for (int i = 0; i < 16; i++) {
            int tx = x + random.nextInt(43) - 21, tz = z + random.nextInt(43) - 21;
            if (Math.abs(tx - x) < 5 && Math.abs(tz - z) < 5) continue;
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, tx, tz);
            for (int h = 0; h < 4 + random.nextInt(3); h++)
                level.setBlock(new BlockPos(tx, ground + h, tz), Blocks.OAK_LOG.defaultBlockState(), 3);
            for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++) for (int oy = 3; oy <= 5; oy++) {
                if (Math.abs(ox) + Math.abs(oz) + Math.abs(oy - 4) < 6)
                    level.setBlock(new BlockPos(tx + ox, ground + oy, tz + oz), Blocks.OAK_LEAVES.defaultBlockState(), 3);
            }
        }
        // A small pond, a lantern and a clear arrival pad.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz <= 10) {
                level.setBlock(new BlockPos(x + dx, 98, z + dz), Blocks.WATER.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x + dx, 97, z + dz), Blocks.CLAY.defaultBlockState(), 3);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            level.setBlock(new BlockPos(x + dx, 100, z + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, 101, z), Blocks.LANTERN.defaultBlockState(), 3);
    }

    private static void generateFreshMaze(ServerLevel level, int x, int z, long seed) {
        Random random = new Random(seed);
        // The expedition footprint is reset; personal-home coordinates are kept far away.
        for (int dx = -32; dx <= 32; dx++) for (int dz = -32; dz <= 32; dz++)
            for (int y = 68; y <= 91; y++) level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
        for (int dx = -28; dx <= 28; dx++) for (int dz = -28; dz <= 28; dz++) {
            level.setBlock(new BlockPos(x + dx, 78, z + dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + dx, 86, z + dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
        }
        // Grid rooms with randomized openings, dead ends and cross-links.
        for (int gx = -24; gx <= 24; gx += 12) for (int gz = -24; gz <= 24; gz += 12) {
            boolean rare = random.nextInt(12) == 0;
            for (int h = 79; h <= 85; h++) for (int t = -5; t <= 5; t++) {
                if (t != 0 || random.nextBoolean()) {
                    level.setBlock(new BlockPos(x + gx + t, h, z + gz - 6), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x + gx + t, h, z + gz + 6), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x + gx - 6, h, z + gz + t), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x + gx + 6, h, z + gz + t), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                }
            }
            // Doorways are deliberately randomized so every run has a different route.
            if (random.nextBoolean()) clearOpening(level, x + gx, z + gz - 6);
            if (random.nextBoolean()) clearOpening(level, x + gx + 6, z + gz);
            if (rare) {
                BlockPos spawner = new BlockPos(x + gx, 79, z + gz);
                level.setBlock(spawner, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
                level.setBlock(spawner.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
            }
            if (random.nextInt(3) == 0) placeLoot(level, new BlockPos(x + gx + 2, 79, z + gz + 2), random);
            if (random.nextInt(5) == 0) {
                BlockPos trap = new BlockPos(x + gx - 2, 79, z + gz - 2);
                level.setBlock(trap, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            }
        }
        // Entry chamber is always safe and recognizable.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            for (int y = 79; y <= 85; y++) level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, 79, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, 80, z), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        placeLoot(level, new BlockPos(x + 18, 79, z + 18), random);
    }

    private static void clearOpening(ServerLevel level, int x, int z) {
        for (int y = 80; y <= 83; y++) for (int offset = -1; offset <= 1; offset++) {
            level.setBlock(new BlockPos(x + offset, y, z), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, y, z + offset), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void placeLoot(ServerLevel level, BlockPos pos, Random random) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setItem(10, new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(7)));
            chest.setItem(13, new ItemStack(random.nextBoolean() ? Items.DIAMOND : Items.EMERALD, 1 + random.nextInt(3)));
            chest.setItem(16, new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2)));
            if (random.nextInt(9) == 0) chest.setItem(22, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            if (random.nextInt(6) == 0) chest.setItem(4, new ItemStack(Items.ENDER_PEARL, 2 + random.nextInt(4)));
            chest.setChanged();
        }
    }

}
