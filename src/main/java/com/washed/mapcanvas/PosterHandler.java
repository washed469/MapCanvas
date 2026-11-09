package com.washed.mapcanvas;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;

public class PosterHandler {

    private static final java.util.Set<java.util.UUID> recentMessages = new java.util.HashSet<>();

    public static final java.util.Set<Integer> collectingFrames = new java.util.HashSet<>();

    public static boolean tryPlacePoster(Player player, ItemFrame frame, ItemStack stack) {
        CustomData nbtComponent = stack.get(DataComponents.CUSTOM_DATA);
        if (nbtComponent == null) return false;

        CompoundTag nbt = nbtComponent.copyTag();
        if (!nbt.contains("isPoster")) return false;

        int width = nbt.getInt("posterWidth").orElse(0);
        int height = nbt.getInt("posterHeight").orElse(0);
        int[] mapIds = nbt.getIntArray("mapIds").orElse(new int[0]);

        if (width == 0 || height == 0 || mapIds.length == 0) return false;

        Level world = frame.level();
        BlockPos framePos = frame.blockPosition();
        Direction facing = frame.getDirection();

        BlockPos topLeft = findTopLeft(world, framePos, facing, width, height);
        if (topLeft == null) {
            MapCanvasDebug.log("poster-placement-blocked size=" + width + "x" + height + " reason=no-empty-frame-grid");
            if (!recentMessages.contains(player.getUUID())) {
                recentMessages.add(player.getUUID());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Could not find a " + width + "x" + height + " grid of item frames here!"));
                new Thread(() -> {
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                    recentMessages.remove(player.getUUID());
                }).start();
            }
            return false;
        }

        MapCanvasDebug.log("poster-placement-found size=" + width + "x" + height + " topLeft=" + topLeft
                + " mapIds=" + java.util.Arrays.toString(mapIds));
        placeAllMaps(world, topLeft, facing, width, height, mapIds, nbt);
        stack.shrink(1);

        if (nbt.contains("isGif") && world instanceof net.minecraft.server.level.ServerLevel sw) {
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

    public static boolean tryCollectPoster(Player player, ItemFrame clickedFrame) {
        Level world = clickedFrame.level();
        BlockPos framePos = clickedFrame.blockPosition();
        Direction facing = clickedFrame.getDirection();

        ItemStack heldItem = clickedFrame.getItem();
        if (!(heldItem.getItem() == Items.FILLED_MAP)) return false;

        CustomData nbtComponent = heldItem.get(DataComponents.CUSTOM_DATA);
        if (nbtComponent == null) return false;
        CompoundTag sectionNbt = nbtComponent.copyTag();
        if (!sectionNbt.contains("isPosterSection")) return false;

        int bestWidth = sectionNbt.getInt("posterWidth").orElse(0);
        int bestHeight = sectionNbt.getInt("posterHeight").orElse(0);
        int gridX = sectionNbt.getInt("gridX").orElse(0);
        int gridY = sectionNbt.getInt("gridY").orElse(0);
        if (bestWidth == 0 || bestHeight == 0) return false;

        Direction left = facing.getClockWise();
        BlockPos bestTopLeft = framePos.relative(left, gridX).relative(Direction.UP, gridY);

        int[] mapIds = collectGridMapIds(world, bestTopLeft, facing, bestWidth, bestHeight);
        if (mapIds == null) return false;

        if (world instanceof net.minecraft.server.level.ServerLevel sw) {
            String key = AnimationManager.makeKey(bestTopLeft, sw);
            AnimationManager.unregister(key);
        }

        clearGrid(world, bestTopLeft, facing, bestWidth, bestHeight);

        ItemStack posterMap = new ItemStack(Items.FILLED_MAP);
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("isPoster", true);
        nbt.putInt("posterWidth", bestWidth);
        nbt.putInt("posterHeight", bestHeight);
        nbt.putIntArray("mapIds", mapIds);

        if (sectionNbt.contains("isGif")) {
            nbt.putBoolean("isGif", true);
            nbt.putString("gifKey", sectionNbt.getString("gifKey").orElse(""));
            nbt.putInt("gifFrameCount", sectionNbt.getInt("gifFrameCount").orElse(0));
        }

        posterMap.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        posterMap.set(DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal(sectionNbt.contains("isGif") ? "Animated Poster Map" : "Poster Map")
                        .withStyle(s -> s.withColor(sectionNbt.contains("isGif") ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GOLD)
                                .withItalic(false)
                                .withBold(true)));
        posterMap.set(DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal("Size: " + bestWidth + "x" + bestHeight)
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)),
                        net.minecraft.network.chat.Component.literal("Type: " + (sectionNbt.contains("isGif") ? "Animated Poster" : "Poster"))
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false))
                )));

        world.addFreshEntity(new ItemEntity(world,
                framePos.getX() + 0.5,
                framePos.getY() + 0.5,
                framePos.getZ() + 0.5,
                posterMap));
        return true;
    }

    private static int[] collectGridMapIds(Level world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.getCounterClockWise();
        int[] mapIds = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.relative(right, x).relative(Direction.DOWN, y);
                List<ItemFrame> frames = framesAt(world, pos);
                if (frames.isEmpty()) return null;
                ItemStack stack = frames.get(0).getItem();
                if (!(stack.getItem() == Items.FILLED_MAP)) return null;
                MapId mapId = stack.get(DataComponents.MAP_ID);
                if (mapId == null) return null;
                mapIds[y * width + x] = mapId.id();
            }
        }
        return mapIds;
    }

    private static void clearGrid(Level world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.getCounterClockWise();
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.relative(right, x).relative(Direction.DOWN, y);
                List<ItemFrame> frames = framesAt(world, pos);
                if (!frames.isEmpty()) {
                    int id = frames.get(0).getId();
                    collectingFrames.add(id);
                    ids.add(id);
                    frames.get(0).setItem(ItemStack.EMPTY);
                }
            }
        }
        new Thread(() -> {
            try { Thread.sleep(100); } catch (Exception ignored) {}
            collectingFrames.removeAll(ids);
        }).start();
    }

    private static BlockPos findTopLeft(Level world, BlockPos clickedPos, Direction facing, int width, int height) {
        Direction left = facing.getClockWise();
        for (int offX = 0; offX < width; offX++) {
            for (int offY = 0; offY < height; offY++) {
                BlockPos candidate = clickedPos.relative(left, offX).relative(Direction.UP, offY);
                if (isValidGrid(world, candidate, facing, width, height)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isValidGrid(Level world, BlockPos topLeft, Direction facing, int width, int height) {
        Direction right = facing.getCounterClockWise();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.relative(right, x).relative(Direction.DOWN, y);
                List<ItemFrame> frames = framesAt(world, pos);
                if (frames.isEmpty()) return false;
                if (!frames.get(0).getItem().isEmpty()) return false;
            }
        }
        return true;
    }

    private static boolean hasItemFrame(Level world, BlockPos pos) {
        List<ItemFrame> frames = framesAt(world, pos);
        return !frames.isEmpty();
    }

    private static void placeAllMaps(Level world, BlockPos topLeft, Direction facing, int width, int height, int[] mapIds, CompoundTag posterNbt) {
        Direction right = facing.getCounterClockWise();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = topLeft.relative(right, x).relative(Direction.DOWN, y);
                List<ItemFrame> frames = framesAt(world, pos);
                if (!frames.isEmpty()) {
                    ItemFrame frameEntity = frames.get(0);
                    ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
                    CompoundTag nbt = new CompoundTag();
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
                    mapStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                    mapStack.set(DataComponents.MAP_ID, new MapId(mapIds[y * width + x]));
                    frameEntity.setItem(mapStack);
                    MapCanvasDebug.log("poster-frame-set x=" + x + " y=" + y + " pos=" + pos
                            + " mapId=" + mapIds[y * width + x]);
                }
            }
        }
    }
    private static List<ItemFrame> framesAt(Level world, BlockPos pos) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        return world.getEntities(EntityTypeTest.forClass(ItemFrame.class), box, e -> true);
    }
}
