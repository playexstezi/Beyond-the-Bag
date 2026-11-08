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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Server-authoritative travel and generation for the two pocket-space regions. */
public final class PocketTravel {
    private static final ResourceKey<Level> POCKET_KEY = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(BeyondTheBag.MODID, "pocket"));
    private static final String TAG = "BeyondTheBag";
    private static final int HOME_Y = 100;
    private static final int MAZE_FLOOR_Y = 79;
    private static final int CELL_SIZE = 6;
    private static final int GRID = 7;

    private PocketTravel() {}

    public static void enterHome(ServerPlayer player) {
        if (isInside(player)) { returnHome(player); return; }
        saveReturnPoint(player);
        ServerLevel pocket = getPocket(player);
        if (pocket == null) { player.sendSystemMessage(Component.literal("[Beyond the Bag] Карманное измерение не загрузилось.")); return; }
        int[] region = region(player);
        int x = region[0], z = region[1];
        makeHomeLanding(pocket, x, z);
        player.teleportTo(pocket, x + 0.5, HOME_Y + 1.0, z + 0.5, player.getYRot(), player.getXRot());
        markInside(player, "home");
        player.sendSystemMessage(Component.literal("Личное пространство открыто. Постройки сохраняются между посещениями."));
    }

    public static void enterMaze(ServerPlayer player) {
        if (isInside(player)) { returnHome(player); return; }
        saveReturnPoint(player);
        ServerLevel pocket = getPocket(player);
        if (pocket == null) { player.sendSystemMessage(Component.literal("[Beyond the Bag] Карманное измерение не загрузилось.")); return; }
        int[] region = region(player);
        int x = region[0] + 256, z = region[1] + 256;
        long seed = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits()
                ^ player.level().getGameTime() ^ player.getRandom().nextLong();
        generateFreshMaze(pocket, x, z, seed);
        player.teleportTo(pocket, x + 0.5, MAZE_FLOOR_Y + 1.0, z + 0.5, player.getYRot(), player.getXRot());
        markInside(player, "maze");
        player.sendSystemMessage(Component.literal("Нестабильное пространство перестроилось. Найди добычу и возвращайся мешком."));
    }

    private static void markInside(ServerPlayer player, String space) {
        CompoundTag data = player.getPersistentData().getCompound(TAG);
        data.putBoolean("inside", true);
        data.putString("space", space);
        player.getPersistentData().put(TAG, data);
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
        double x = data.getDouble("return_x"), y = data.getDouble("return_y"), z = data.getDouble("return_z");
        float yaw = data.getFloat("return_yaw"), pitch = data.getFloat("return_pitch");
        if (destination == null || !data.contains("return_x") || !data.contains("return_y") || !data.contains("return_z")) {
            destination = server.getLevel(Level.OVERWORLD);
            if (destination == null) { player.sendSystemMessage(Component.literal("Не удалось найти безопасную точку возвращения.")); return; }
            BlockPos spawn = destination.getSharedSpawnPos();
            x = spawn.getX() + 0.5; y = spawn.getY() + 1.0; z = spawn.getZ() + 0.5; yaw = 0; pitch = 0;
        }
        player.teleportTo(destination, x, y, z, yaw, pitch);
        data.putBoolean("inside", false);
        data.remove("space");
        player.getPersistentData().put(TAG, data);
        player.sendSystemMessage(Component.literal("Ты вернулся в точку входа."));
    }

    private static ServerLevel getPocket(ServerPlayer player) { return player.getServer().getLevel(POCKET_KEY); }

    /* A deterministic, widely spaced region per UUID. Home and maze are separate footprints. */
    private static int[] region(ServerPlayer player) {
        long a = player.getUUID().getMostSignificantBits();
        long b = player.getUUID().getLeastSignificantBits();
        int x = (int) Math.floorMod(a ^ (b >>> 17), 40_000L) * 512 - 10_240_000;
        int z = (int) Math.floorMod(b ^ (a >>> 19), 40_000L) * 512 - 10_240_000;
        return new int[]{x, z};
    }

    private static void makeHomeLanding(ServerLevel level, int x, int z) {
        // Clear only the personal landing volume; never touch the maze footprint.
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            for (int y = HOME_Y + 1; y <= HOME_Y + 12; y++) level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + dx, HOME_Y, z + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(x, HOME_Y + 1, z), Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x + 2, HOME_Y, z), Blocks.CHEST.defaultBlockState(), 3);
    }

    /** Creates a bounded, freshly randomized maze. The home region is never included in this footprint. */
    private static void generateFreshMaze(ServerLevel level, int x, int z, long seed) {
        Random random = new Random(seed);
        int half = (GRID * CELL_SIZE) / 2 + 2;
        // Clear the complete maze volume, including the previous run's chests and decorations.
        for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++)
            for (int y = MAZE_FLOOR_Y; y <= MAZE_FLOOR_Y + 8; y++)
                level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 3);

        // Floor, ceiling, and perimeter make this a real enclosed structure rather than an open platform.
        for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
            level.setBlock(new BlockPos(x + dx, MAZE_FLOOR_Y, z + dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + dx, MAZE_FLOOR_Y + 8, z + dz), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
            if (Math.abs(dx) == half || Math.abs(dz) == half)
                for (int y = 1; y <= 7; y++) level.setBlock(new BlockPos(x + dx, MAZE_FLOOR_Y + y, z + dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
        }

        // Randomized cell maze built with depth-first traversal.
        boolean[][] visited = new boolean[GRID][GRID];
        boolean[][] eastWall = new boolean[GRID][GRID];
        boolean[][] southWall = new boolean[GRID][GRID];
        for (int gx = 0; gx < GRID; gx++) for (int gz = 0; gz < GRID; gz++) { eastWall[gx][gz] = true; southWall[gx][gz] = true; }
        carve(0, 0, visited, eastWall, southWall, random);

        for (int gx = 0; gx < GRID; gx++) for (int gz = 0; gz < GRID; gz++) {
            int cx = x - ((GRID - 1) * CELL_SIZE) / 2 + gx * CELL_SIZE;
            int cz = z - ((GRID - 1) * CELL_SIZE) / 2 + gz * CELL_SIZE;
            // Cell dividers; openings are determined by the randomized traversal.
            if (eastWall[gx][gz]) for (int t = -2; t <= 2; t++) for (int h = 1; h <= 5; h++)
                level.setBlock(new BlockPos(cx + 3, MAZE_FLOOR_Y + h, cz + t), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
            if (southWall[gx][gz]) for (int t = -2; t <= 2; t++) for (int h = 1; h <= 5; h++)
                level.setBlock(new BlockPos(cx + t, MAZE_FLOOR_Y + h, cz + 3), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
            if (random.nextInt(9) == 0) placeLootChest(level, new BlockPos(cx, MAZE_FLOOR_Y + 1, cz), random);
            if (random.nextInt(7) == 0) level.setBlock(new BlockPos(cx + 1, MAZE_FLOOR_Y + 1, cz + 1), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }

        // Clear a safe entrance and mark the exit with a distinct light source.
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            for (int y = 1; y <= 6; y++) level.setBlock(new BlockPos(x + dx, MAZE_FLOOR_Y + y, z + dz), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, MAZE_FLOOR_Y + 1, z), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        placeLootChest(level, new BlockPos(x + half - 4, MAZE_FLOOR_Y + 1, z + half - 4), random);
    }

    private static void carve(int gx, int gz, boolean[][] visited, boolean[][] eastWall, boolean[][] southWall, Random random) {
        visited[gx][gz] = true;
        List<int[]> directions = new ArrayList<>();
        directions.add(new int[]{1, 0}); directions.add(new int[]{-1, 0});
        directions.add(new int[]{0, 1}); directions.add(new int[]{0, -1});
        Collections.shuffle(directions, random);
        for (int[] d : directions) {
            int nx = gx + d[0], nz = gz + d[1];
            if (nx < 0 || nz < 0 || nx >= GRID || nz >= GRID || visited[nx][nz]) continue;
            if (d[0] == 1) eastWall[gx][gz] = false;
            else if (d[0] == -1) eastWall[nx][nz] = false;
            else if (d[1] == 1) southWall[gx][gz] = false;
            else southWall[nx][nz] = false;
            carve(nx, nz, visited, eastWall, southWall, random);
        }
    }

    private static void placeLootChest(ServerLevel level, BlockPos pos, Random random) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setItem(10, new ItemStack(random.nextBoolean() ? Items.GOLD_INGOT : Items.IRON_INGOT, 2 + random.nextInt(6)));
            chest.setItem(13, new ItemStack(random.nextBoolean() ? Items.EMERALD : Items.DIAMOND, 1 + random.nextInt(2)));
            chest.setItem(16, new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2)));
            if (random.nextInt(12) == 0) chest.setItem(22, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            chest.setChanged();
        }
    }
}
