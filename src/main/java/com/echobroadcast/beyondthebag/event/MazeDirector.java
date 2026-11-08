package com.echobroadcast.beyondthebag.event;

import com.echobroadcast.beyondthebag.BeyondTheBag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Warden;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/** Server-authoritative director for escalating maze expeditions. */
@Mod.EventBusSubscriber(modid = BeyondTheBag.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MazeDirector {
    private static final String ROOT = "BeyondTheBag";
    private static final String MAZE_TICKS = "maze_ticks";
    private static final String EVENT_COOLDOWN = "maze_event_cooldown";
    private static final String BOSS_SPAWNED = "maze_boss_spawned";
    private static final Random RANDOM = new Random();

    private MazeDirector() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!player.level().dimension().location().getNamespace().equals(BeyondTheBag.MODID)
                || !player.level().dimension().location().getPath().equals("pocket")) return;

        var data = player.getPersistentData().getCompound(ROOT);
        if (!data.getBoolean("inside") || !data.getBoolean("in_maze")) return;

        int ticks = data.getInt(MAZE_TICKS) + 1;
        int cooldown = Math.max(0, data.getInt(EVENT_COOLDOWN) - 1);
        data.putInt(MAZE_TICKS, ticks);
        data.putInt(EVENT_COOLDOWN, cooldown);
        player.getPersistentData().put(ROOT, data);

        if (ticks >= 6000 && !data.getBoolean(BOSS_SPAWNED)) {
            spawnBoss(player);
            data.putBoolean(BOSS_SPAWNED, true);
            player.getPersistentData().put(ROOT, data);
            return;
        }
        if (cooldown == 0 && ticks > 100 && RANDOM.nextInt(500) == 0) {
            triggerAnomaly(player, Math.min(4, ticks / 1800));
            data.putInt(EVENT_COOLDOWN, 900);
            player.getPersistentData().put(ROOT, data);
        }
        if (ticks % 900 == 0) {
            player.sendSystemMessage(Component.literal("§5Нестабильность: " + instabilityLevel(ticks) + "/4"));
        }
    }

    private static int instabilityLevel(int ticks) {
        return Math.min(4, Math.max(0, ticks / 1800));
    }

    private static void triggerAnomaly(ServerPlayer player, int level) {
        Level world = player.level();
        switch (RANDOM.nextInt(level >= 3 ? 4 : 3)) {
            case 0 -> {
                world.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.4F, 0.55F);
                player.sendSystemMessage(Component.literal("§8За стеной кто-то повторяет твои шаги..."));
            }
            case 1 -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80 + level * 30, 0, false, false));
                world.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.8F, 0.8F);
                player.sendSystemMessage(Component.literal("§5Пространство вокруг тебя мерцает."));
            }
            case 2 -> {
                spawnStalker(player, level);
                player.sendSystemMessage(Component.literal("§cТы чувствуешь, что за тобой наблюдают."));
            }
            default -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, false, false));
                world.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.7F);
                player.sendSystemMessage(Component.literal("§4Коридор изменился, пока ты не смотрел."));
            }
        }
    }

    private static void spawnStalker(ServerPlayer player, int level) {
        EntityType<? extends Mob> type = level >= 3 && RANDOM.nextBoolean() ? EntityType.ENDERMAN : EntityType.CAVE_SPIDER;
        Mob mob = type.create(player.level());
        if (mob == null) return;
        BlockPos pos = player.blockPosition().offset(RANDOM.nextInt(13) - 6, 0, RANDOM.nextInt(13) - 6);
        mob.moveTo(pos, RANDOM.nextFloat() * 360.0F, 0.0F);
        mob.setCustomName(Component.literal(type == EntityType.ENDERMAN ? "Странник" : "Искажённый преследователь"));
        mob.setCustomNameVisible(false);
        mob.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        player.serverLevel().addFreshEntity(mob);
    }

    private static void spawnBoss(ServerPlayer player) {
        Warden boss = EntityType.WARDEN.create(player.level());
        if (boss == null) return;
        BlockPos pos = player.blockPosition().offset(5, 0, 5);
        boss.moveTo(pos, 0.0F, 0.0F);
        boss.setCustomName(Component.literal("§5Хранитель разлома"));
        boss.setCustomNameVisible(true);
        boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        boss.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        player.serverLevel().addFreshEntity(boss);
        player.sendSystemMessage(Component.literal("§4ЛАБИРИНТ ПРОСНУЛСЯ. ХРАНИТЕЛЬ ИДЁТ ЗА ТОБОЙ."));
        player.serverLevel().playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0F, 0.8F);
    }
}
