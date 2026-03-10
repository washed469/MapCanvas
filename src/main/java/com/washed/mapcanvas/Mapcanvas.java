package com.washed.mapcanvas;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;

public class Mapcanvas implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CanvasCommand.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AnimationManager.tick();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (Object entity : world.iterateEntities()) {
                    if (!(entity instanceof ItemFrameEntity frame)) continue;
                    if (!frame.getHeldItemStack().isOf(Items.FILLED_MAP)) continue;

                    NbtComponent nbtComponent = frame.getHeldItemStack().get(DataComponentTypes.CUSTOM_DATA);
                    if (nbtComponent == null) continue;
                    NbtCompound nbt = nbtComponent.copyNbt();
                    if (!nbt.contains("isPosterSection") || !nbt.contains("isGif")) continue;

                    int gridX = nbt.getInt("gridX").orElse(0);
                    int gridY = nbt.getInt("gridY").orElse(0);
                    if (gridX != 0 || gridY != 0) continue;

                    int width = nbt.getInt("posterWidth").orElse(0);
                    int height = nbt.getInt("posterHeight").orElse(0);
                    String gifKey = nbt.getString("gifKey").orElse("");
                    if (width == 0 || height == 0 || gifKey.isEmpty()) continue;

                    Direction facing = frame.getHorizontalFacing();
                    Direction left = facing.rotateYClockwise();
                    BlockPos topLeft = frame.getBlockPos();

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
        });
    }

    private static int[] collectMapIds(ServerWorld world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.rotateYCounterclockwise();
        int[] mapIds = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.offset(right, x).offset(Direction.DOWN, y);
                java.util.List<ItemFrameEntity> frames = world.getEntitiesByClass(ItemFrameEntity.class,
                        new net.minecraft.util.math.Box(pos), e -> true);
                if (frames.isEmpty()) return null;
                net.minecraft.component.type.MapIdComponent mapId = frames.get(0).getHeldItemStack()
                        .get(DataComponentTypes.MAP_ID);
                if (mapId == null) return null;
                mapIds[y * width + x] = mapId.id();
            }
        }
        return mapIds;
    }
}