package com.washed.mapcanvas;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class AnimationManager {

    public static final int RANGE = 48;
    public static final int BASE_FPS = 10;

    private record AnimatedPoster(
            BlockPos topLeft,
            ServerLevel world,
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

    public static void register(String key, BlockPos topLeft, ServerLevel world, int cols, int rows, int[] mapIds, byte[][] frames, int[] delaysMs) {
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

            List<ServerPlayer> nearbyPlayers = poster.world().getEntities(
                    EntityTypeTest.forClass(ServerPlayer.class),
                    new AABB(poster.topLeft().getX(), poster.topLeft().getY(), poster.topLeft().getZ(), poster.topLeft().getX() + 1, poster.topLeft().getY() + 1, poster.topLeft().getZ() + 1).inflate(RANGE),
                    p -> true
            );

            if (nearbyPlayers.isEmpty()) continue;

            AABB closeRange = new AABB(
                    poster.topLeft().getX(), poster.topLeft().getY(), poster.topLeft().getZ(),
                    poster.topLeft().getX() + 1, poster.topLeft().getY() + 1, poster.topLeft().getZ() + 1
            ).inflate(32);
            List<ServerPlayer> closePlayers = nearbyPlayers.stream()
                    .filter(player -> player.getBoundingBox().intersects(closeRange))
                    .toList();

            int frame = currentFrame.get(key);
            byte[] frameBytes = poster.frames()[frame];
            boolean[] dirty = poster.dirtyTiles()[frame];

            for (int i = 0; i < poster.mapIds().length; i++) {
                if (!dirty[i]) continue;

                int mapId = poster.mapIds()[i];
                byte[] mapBytes = Arrays.copyOfRange(frameBytes, i * 128 * 128, (i + 1) * 128 * 128);

                ClientboundMapItemDataPacket packet = new ClientboundMapItemDataPacket(
                        new net.minecraft.world.level.saveddata.maps.MapId(mapId),
                        (byte) 0,
                        false,
                        null,
                        new MapItemSavedData.MapPatch(0, 0, 128, 128, mapBytes)
                );

                for (ServerPlayer player : closePlayers) {
                    player.connection.send(packet);
                }
            }

            int nextFrame = (frame + 1) % poster.frames().length;
            currentFrame.put(key, nextFrame);
            nextFrameTime.put(key, now + poster.delaysMs()[frame]);
        }
    }

    public static String makeKey(BlockPos topLeft, ServerLevel world) {
        return world.dimension().identifier() + ":" + topLeft.getX() + "," + topLeft.getY() + "," + topLeft.getZ();
    }
}
