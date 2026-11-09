package com.washed.mapcanvas;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

public class Mapcanvas implements ModInitializer {

    private static boolean restored = false;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CanvasCommand.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MapCreator.flushMapSyncs();
            if (!restored) {
                restored = true;
                CanvasCommand.loadConfig(server);
                restoreAnimatedPosters(server);
            }
            AnimationManager.tick();
        });
    }

    private static void restoreAnimatedPosters(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : world.getAllEntities()) {
                if (!(entity instanceof ItemFrame frame)) continue;
                if (!(frame.getItem().getItem() == Items.FILLED_MAP)) continue;

                CustomData nbtComponent = frame.getItem().get(DataComponents.CUSTOM_DATA);
                if (nbtComponent == null) continue;
                CompoundTag nbt = nbtComponent.copyTag();
                if (!nbt.contains("isPosterSection") || !nbt.contains("isGif")) continue;

                int gridX = nbt.getInt("gridX").orElse(0);
                int gridY = nbt.getInt("gridY").orElse(0);
                if (gridX != 0 || gridY != 0) continue;

                int width = nbt.getInt("posterWidth").orElse(0);
                int height = nbt.getInt("posterHeight").orElse(0);
                String gifKey = nbt.getString("gifKey").orElse("");
                if (width == 0 || height == 0 || gifKey.isEmpty()) continue;

                Direction facing = frame.getDirection();
                BlockPos topLeft = frame.blockPosition();

                int[] mapIds = collectMapIds(world, topLeft, facing, width, height);
                if (mapIds == null) continue;

                GifProcessor.GifData gifData = GifDataStore.load(server, gifKey);
                if (gifData == null) continue;

                String key = AnimationManager.makeKey(topLeft, world);
                if (!AnimationManager.isRegistered(key)) {
                    AnimationManager.register(key, topLeft, world, width, height, mapIds, gifData.frames(), gifData.delaysMs());
                    System.out.println("MapCanvas: Restored animated poster at " + topLeft);
                }
            }
        }
    }

    private static int[] collectMapIds(ServerLevel world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.getCounterClockWise();
        int[] mapIds = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.relative(right, x).relative(Direction.DOWN, y);
                java.util.List<ItemFrame> frames = world.getEntities(EntityTypeTest.forClass(ItemFrame.class),
                        new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), e -> true);
                if (frames.isEmpty()) return null;
                net.minecraft.world.level.saveddata.maps.MapId mapId = frames.get(0).getItem()
                        .get(DataComponents.MAP_ID);
                if (mapId == null) return null;
                mapIds[y * width + x] = mapId.id();
            }
        }
        return mapIds;
    }
}
