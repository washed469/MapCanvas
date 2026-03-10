package com.washed.mapcanvas;

import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

public class AnimationManager {

    public static final int RANGE = 48;
    public static final int BASE_FPS = 10;

    private record AnimatedPoster(
            BlockPos topLeft,
            ServerWorld world,
            int cols,
            int rows,
            int[] mapIds,
            byte[][] frames,
            int[] delaysMs,
            boolean[][] dirtyTiles
    ) {}

    private static final Map<String, AnimatedPoster> activePosterMap = new HashMap<>();
    private static final Map<String, Integer> currentFrame = new HashMap<>();
    private static final Map<String, Long> nextFrameTime = new HashMap<>();

    public static void register(String key, BlockPos topLeft, ServerWorld world, int cols, int rows, int[] mapIds, byte[][] frames, int[] delaysMs) {
        boolean[][] dirtyTiles = precomputeDirtyTiles(frames, cols * rows);
        activePosterMap.put(key, new AnimatedPoster(topLeft, world, cols, rows, mapIds, frames, delaysMs, dirtyTiles));
        currentFrame.put(key, 0);
        nextFrameTime.put(key, System.currentTimeMillis());
    }

    private static boolean[][] precomputeDirtyTiles(byte[][] frames, int tileCount) {
        boolean[][] dirty = new boolean[frames.length][tileCount];
        for (int f = 0; f < frames.length; f++) {
            int prevFrame = (f == 0) ? frames.length - 1 : f - 1;
            for (int t = 0; t < tileCount; t++) {
                int offset = t * 128 * 128;
                boolean changed = false;
                for (int p = 0; p < 128 * 128; p++) {
                    if (frames[f][offset + p] != frames[prevFrame][offset + p]) {
                        changed = true;
                        break;
                    }
                }
                dirty[f][t] = changed;
            }
        }
        return dirty;
    }

    public static void unregister(String key) {
        activePosterMap.remove(key);
        currentFrame.remove(key);
        nextFrameTime.remove(key);
    }

    public static boolean isRegistered(String key) {
        return activePosterMap.containsKey(key);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, AnimatedPoster> entry : activePosterMap.entrySet()) {
            String key = entry.getKey();
            AnimatedPoster poster = entry.getValue();

            if (now < nextFrameTime.get(key)) continue;

            List<ServerPlayerEntity> nearbyPlayers = poster.world().getEntitiesByClass(
                    ServerPlayerEntity.class,
                    new Box(poster.topLeft()).expand(RANGE),
                    p -> true
            );

            if (nearbyPlayers.isEmpty()) continue;

            List<ServerPlayerEntity> closePlayers = poster.world().getEntitiesByClass(
                    ServerPlayerEntity.class,
                    new Box(poster.topLeft()).expand(32),
                    p -> true
            );

            int frame = currentFrame.get(key);
            byte[] frameBytes = poster.frames()[frame];
            boolean[] dirty = poster.dirtyTiles()[frame];

            for (int i = 0; i < poster.mapIds().length; i++) {
                if (!dirty[i]) continue;

                int mapId = poster.mapIds()[i];
                byte[] mapBytes = Arrays.copyOfRange(frameBytes, i * 128 * 128, (i + 1) * 128 * 128);

                MapUpdateS2CPacket packet = new MapUpdateS2CPacket(
                        new net.minecraft.component.type.MapIdComponent(mapId),
                        (byte) 0,
                        false,
                        null,
                        new MapState.UpdateData(0, 0, 128, 128, mapBytes)
                );

                for (ServerPlayerEntity player : closePlayers) {
                    player.networkHandler.sendPacket(packet);
                }
            }

            int nextFrame = (frame + 1) % poster.frames().length;
            currentFrame.put(key, nextFrame);
            nextFrameTime.put(key, now + poster.delaysMs()[frame]);
        }
    }

    public static String makeKey(BlockPos topLeft, ServerWorld world) {
        return world.getRegistryKey().getValue() + ":" + topLeft.getX() + "," + topLeft.getY() + "," + topLeft.getZ();
    }
}