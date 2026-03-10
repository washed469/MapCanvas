package com.washed.mapcanvas;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

public class PosterHandler {

    private static final java.util.Set<java.util.UUID> recentMessages = new java.util.HashSet<>();

    public static final java.util.Set<Integer> collectingFrames = new java.util.HashSet<>();

    public static boolean tryPlacePoster(PlayerEntity player, ItemFrameEntity frame, ItemStack stack) {
        NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent == null) return false;

        NbtCompound nbt = nbtComponent.copyNbt();
        if (!nbt.contains("isPoster")) return false;

        int width = nbt.getInt("posterWidth").orElse(0);
        int height = nbt.getInt("posterHeight").orElse(0);
        int[] mapIds = nbt.getIntArray("mapIds").orElse(new int[0]);

        if (width == 0 || height == 0 || mapIds.length == 0) return false;

        World world = frame.getEntityWorld();
        BlockPos framePos = frame.getBlockPos();
        Direction facing = frame.getHorizontalFacing();

        BlockPos topLeft = findTopLeft(world, framePos, facing, width, height);
        if (topLeft == null) {
            if (!recentMessages.contains(player.getUuid())) {
                recentMessages.add(player.getUuid());
                player.sendMessage(net.minecraft.text.Text.literal("Could not find a " + width + "x" + height + " grid of item frames here!"), false);
                new Thread(() -> {
                    try { Thread.sleep(100); } catch (Exception ignored) {}
                    recentMessages.remove(player.getUuid());
                }).start();
            }
            return false;
        }

        placeAllMaps(world, topLeft, facing, width, height, mapIds, nbt);
        stack.decrement(1);

        if (nbt.contains("isGif") && world instanceof net.minecraft.server.world.ServerWorld sw) {
            String gifKey = nbt.getString("gifKey").orElse("");
            if (!gifKey.isEmpty()) {
                GifProcessor.GifData gifData = GifDataStore.load(sw.getServer(), gifKey);
                if (gifData != null) {
                    String key = AnimationManager.makeKey(topLeft, sw);
                    AnimationManager.register(key, topLeft, sw, width, height, mapIds, gifData.frames(), gifData.delaysMs());
                }
            }
        }

        return true;
    }

    public static boolean tryCollectPoster(PlayerEntity player, ItemFrameEntity clickedFrame) {
        World world = clickedFrame.getEntityWorld();
        BlockPos framePos = clickedFrame.getBlockPos();
        Direction facing = clickedFrame.getHorizontalFacing();

        ItemStack heldItem = clickedFrame.getHeldItemStack();
        if (!heldItem.isOf(Items.FILLED_MAP)) return false;

        NbtComponent nbtComponent = heldItem.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent == null) return false;
        NbtCompound sectionNbt = nbtComponent.copyNbt();
        if (!sectionNbt.contains("isPosterSection")) return false;

        int bestWidth = sectionNbt.getInt("posterWidth").orElse(0);
        int bestHeight = sectionNbt.getInt("posterHeight").orElse(0);
        int gridX = sectionNbt.getInt("gridX").orElse(0);
        int gridY = sectionNbt.getInt("gridY").orElse(0);
        if (bestWidth == 0 || bestHeight == 0) return false;

        Direction left = facing.rotateYClockwise();
        BlockPos bestTopLeft = framePos.offset(left, gridX).offset(Direction.UP, gridY);

        int[] mapIds = collectGridMapIds(world, bestTopLeft, facing, bestWidth, bestHeight);
        if (mapIds == null) return false;

        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            String key = AnimationManager.makeKey(bestTopLeft, sw);
            AnimationManager.unregister(key);
        }

        clearGrid(world, bestTopLeft, facing, bestWidth, bestHeight);

        ItemStack posterMap = new ItemStack(Items.FILLED_MAP);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("isPoster", true);
        nbt.putInt("posterWidth", bestWidth);
        nbt.putInt("posterHeight", bestHeight);
        nbt.putIntArray("mapIds", mapIds);

        if (sectionNbt.contains("isGif")) {
            nbt.putBoolean("isGif", true);
            nbt.putString("gifKey", sectionNbt.getString("gifKey").orElse(""));
            nbt.putInt("gifFrameCount", sectionNbt.getInt("gifFrameCount").orElse(0));
        }

        posterMap.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        posterMap.set(DataComponentTypes.CUSTOM_NAME,
                net.minecraft.text.Text.literal(sectionNbt.contains("isGif") ? "Animated Poster Map" : "Poster Map")
                        .styled(s -> s.withColor(sectionNbt.contains("isGif") ? net.minecraft.util.Formatting.AQUA : net.minecraft.util.Formatting.GOLD)
                                .withItalic(false)
                                .withBold(true)));
        posterMap.set(DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                        net.minecraft.text.Text.literal("Size: " + bestWidth + "x" + bestHeight)
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false)),
                        net.minecraft.text.Text.literal("Type: " + (sectionNbt.contains("isGif") ? "Animated Poster" : "Poster"))
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false))
                )));

        world.spawnEntity(new ItemEntity(world,
                framePos.getX() + 0.5,
                framePos.getY() + 0.5,
                framePos.getZ() + 0.5,
                posterMap));
        return true;
    }

    private static int[] collectGridMapIds(World world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.rotateYCounterclockwise();
        int[] mapIds = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.offset(right, x).offset(Direction.DOWN, y);
                List<ItemFrameEntity> frames = world.getEntitiesByClass(ItemFrameEntity.class, new Box(pos), e -> true);
                if (frames.isEmpty()) return null;
                ItemStack stack = frames.get(0).getHeldItemStack();
                if (!stack.isOf(Items.FILLED_MAP)) return null;
                MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
                if (mapId == null) return null;
                mapIds[y * width + x] = mapId.id();
            }
        }
        return mapIds;
    }

    private static void clearGrid(World world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.rotateYCounterclockwise();
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.offset(right, x).offset(Direction.DOWN, y);
                List<ItemFrameEntity> frames = world.getEntitiesByClass(ItemFrameEntity.class, new Box(pos), e -> true);
                if (!frames.isEmpty()) {
                    int id = frames.get(0).getId();
                    collectingFrames.add(id);
                    ids.add(id);
                    frames.get(0).setHeldItemStack(ItemStack.EMPTY);
                }
            }
        }
        new Thread(() -> {
            try { Thread.sleep(100); } catch (Exception ignored) {}
            collectingFrames.removeAll(ids);
        }).start();
    }

    private static BlockPos findTopLeft(World world, BlockPos clickedPos, Direction facing, int width, int height) {
        Direction left = facing.rotateYClockwise();
        for (int offX = 0; offX < width; offX++) {
            for (int offY = 0; offY < height; offY++) {
                BlockPos candidate = clickedPos.offset(left, offX).offset(Direction.UP, offY);
                if (isValidGrid(world, candidate, facing, width, height)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isValidGrid(World world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.rotateYCounterclockwise();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.offset(right, x).offset(Direction.DOWN, y);
                if (!hasItemFrame(world, pos)) return false;
            }
        }
        return true;
    }

    private static boolean hasItemFrame(World world, BlockPos pos) {
        List<ItemFrameEntity> frames = world.getEntitiesByClass(ItemFrameEntity.class, new Box(pos), e -> true);
        return !frames.isEmpty();
    }

    private static void placeAllMaps(World world, BlockPos topLeft, Direction facing, int width, int height, int[] mapIds, NbtCompound posterNbt) {
        Direction right = facing.rotateYCounterclockwise();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.offset(right, x).offset(Direction.DOWN, y);
                List<ItemFrameEntity> frames = world.getEntitiesByClass(ItemFrameEntity.class, new Box(pos), e -> true);
                if (!frames.isEmpty()) {
                    ItemFrameEntity frameEntity = frames.get(0);
                    ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
                    NbtCompound nbt = new NbtCompound();
                    nbt.putBoolean("isPosterSection", true);
                    nbt.putInt("posterWidth", width);
                    nbt.putInt("posterHeight", height);
                    nbt.putInt("gridX", x);
                    nbt.putInt("gridY", y);
                    if (posterNbt.contains("isGif")) {
                        nbt.putBoolean("isGif", true);
                        nbt.putString("gifKey", posterNbt.getString("gifKey").orElse(""));
                        nbt.putInt("gifFrameCount", posterNbt.getInt("gifFrameCount").orElse(0));
                    }
                    mapStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                    mapStack.set(DataComponentTypes.MAP_ID, new MapIdComponent(mapIds[y * width + x]));
                    frameEntity.setHeldItemStack(mapStack);
                }
            }
        }
    }
}