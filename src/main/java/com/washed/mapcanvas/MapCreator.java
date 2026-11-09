package com.washed.mapcanvas;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MapCreator {

    private record PendingMapSync(ServerPlayer viewer, MapId mapId, byte[] colors, long sendAfterTick) {}
    private static final ConcurrentLinkedQueue<PendingMapSync> PENDING_MAP_SYNCS = new ConcurrentLinkedQueue<>();
    private static long serverTick;

    private static final int[] MAP_RED = new int[256];
    private static final int[] MAP_GREEN = new int[256];
    private static final int[] MAP_BLUE = new int[256];
    private static final boolean[] VALID_MAP_COLOR = new boolean[256];
    private record PaletteNode(int index, int axis, PaletteNode left, PaletteNode right) {}
    private static final PaletteNode COLOR_TREE;

    static {
        for (int i = 4; i < 256; i++) {
            int base = i >> 2;
            if (base >= MapColorPalette.COLORS.length) continue;
            int[] rgb = MapColorPalette.COLORS[base];
            float multiplier = MapColorPalette.MULTIPLIERS[i & 3];
            MAP_RED[i] = (int) (rgb[0] * multiplier);
            MAP_GREEN[i] = (int) (rgb[1] * multiplier);
            MAP_BLUE[i] = (int) (rgb[2] * multiplier);
            VALID_MAP_COLOR[i] = true;
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 4; i < VALID_MAP_COLOR.length; i++) {
            if (VALID_MAP_COLOR[i]) indices.add(i);
        }
        COLOR_TREE = buildColorTree(indices, 0);
    }

    public static ItemStack createMapFromImage(BufferedImage image, ServerLevel world, boolean dither) {
        return createMapFromColors(imageToMapColors(image, dither), world);
    }

    public static byte[] imageToMapColors(BufferedImage image, boolean dither) {
        return dither ? Dithering.ditherToMapColors(image) : toMapColors(image);
    }

    public static ItemStack createMapFromColors(byte[] colors, ServerLevel world) {
        // These are fixed image maps, not explored-world maps. Tracking player
        // positions adds the moving green marker over the canvas artwork.
        // Lock image maps so vanilla map tracking cannot send world-rendered
        // color updates over the image after the initial full-map packet.
        MapItemSavedData mapState = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, world.dimension()).locked();
        MapId mapId = world.getFreeMapId();
        world.setMapData(mapId, mapState);
        MapCanvasDebug.trackMapId(mapId.id());
        if (colors.length != mapState.colors.length) {
            throw new IllegalArgumentException("A map requires exactly 16384 color values");
        }
        // setColor marks the map dirty for every pixel. Bulk-copying keeps the
        // same row-major map data while avoiding 16,384 dirty operations per
        // map on the integrated server thread.
        System.arraycopy(colors, 0, mapState.colors, 0, colors.length);
        mapState.setDirty();

        ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
        mapStack.set(DataComponents.MAP_ID, mapId);
        net.minecraft.nbt.CompoundTag marker = new net.minecraft.nbt.CompoundTag();
        marker.putBoolean("mapcanvasImage", true);
        mapStack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(marker));
        MapCanvasDebug.log("server-map-created id=" + mapId.id() + " " + MapCanvasDebug.pixels(colors));
        return mapStack;
    }

    public static void syncMapToPlayer(ServerPlayer viewer, MapId mapId, byte[] colors) {
        // Wait until a later tick so the inventory slot update has reached the
        // client before the corresponding map texture update arrives.
        long sendAfterTick = serverTick + 2L;
        PENDING_MAP_SYNCS.add(new PendingMapSync(viewer, mapId, colors.clone(), sendAfterTick));
        MapCanvasDebug.log("map-sync-queued id=" + mapId.id() + " sendAfterTick=" + sendAfterTick
                + " " + MapCanvasDebug.pixels(colors));
    }

    public static void flushMapSyncs() {
        serverTick++;
        int queuedAtStart = PENDING_MAP_SYNCS.size();
        for (int i = 0; i < queuedAtStart; i++) {
            PendingMapSync pending = PENDING_MAP_SYNCS.poll();
            if (pending == null) break;
            ServerPlayer viewer = pending.viewer();
            if (serverTick < pending.sendAfterTick()) {
                PENDING_MAP_SYNCS.add(pending);
                continue;
            }
            if (viewer.isRemoved() || viewer.connection == null) continue;
            ClientboundMapItemDataPacket packet = new ClientboundMapItemDataPacket(
                    pending.mapId(),
                    (byte) 0,
                    false,
                    null,
                    new MapItemSavedData.MapPatch(0, 0, 128, 128, pending.colors())
            );
            viewer.connection.send(packet);
            MapCanvasDebug.log("map-sync-sent id=" + pending.mapId().id() + " tick=" + serverTick
                    + " " + MapCanvasDebug.pixels(pending.colors()));
        }
    }

    private static byte[] toMapColors(BufferedImage image) {
        byte[] colors = new byte[128 * 128];
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                int rgb = image.getRGB(x, z);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                colors[z * 128 + x] = closestMapColor(r, g, b);
            }
        }
        return colors;
    }

    public static ItemStack createPosterMap(int[] mapIds, int width, int height, ServerLevel world, boolean dither) {
        ItemStack posterStack = new ItemStack(Items.FILLED_MAP);
        posterStack.set(DataComponents.MAP_ID, new MapId(mapIds[0]));

        net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
        nbt.putBoolean("isPoster", true);
        nbt.putInt("posterWidth", width);
        nbt.putInt("posterHeight", height);
        nbt.putIntArray("mapIds", mapIds);
        posterStack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(nbt));

        posterStack.set(DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Poster Map")
                        .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD).withItalic(false).withBold(true)));
        posterStack.set(DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal("Size: " + width + "x" + height)
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)),
                        net.minecraft.network.chat.Component.literal("Type: " + (dither ? "Poster HD" : "Poster"))
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false))
                )));

        return posterStack;
    }

    public static ItemStack createGifPosterMap(int[] mapIds, int width, int height, GifProcessor.GifData gifData, String gifKey) {
        ItemStack posterStack = new ItemStack(Items.FILLED_MAP);
        posterStack.set(DataComponents.MAP_ID, new MapId(mapIds[0]));

        net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
        nbt.putBoolean("isPoster", true);
        nbt.putBoolean("isGif", true);
        nbt.putString("gifKey", gifKey);
        nbt.putInt("posterWidth", width);
        nbt.putInt("posterHeight", height);
        nbt.putIntArray("mapIds", mapIds);
        nbt.putInt("gifFrameCount", gifData.frames().length);
        posterStack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(nbt));

        posterStack.set(DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Animated Poster Map")
                        .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA).withItalic(false).withBold(true)));
        posterStack.set(DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal("Size: " + width + "x" + height)
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)),
                        net.minecraft.network.chat.Component.literal("Type: Animated Poster")
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)),
                        net.minecraft.network.chat.Component.literal("Frames: " + gifData.frames().length)
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false))
                )));

        return posterStack;
    }

    private static byte closestMapColor(int r, int g, int b) {
        long best = findNearestColor(COLOR_TREE, r, g, b, Long.MAX_VALUE);
        return (byte) (best & 0xFF);
    }

    private static PaletteNode buildColorTree(List<Integer> indices, int depth) {
        if (indices.isEmpty()) return null;
        int axis = depth % 3;
        indices.sort(Comparator.comparingInt((Integer i) -> colorComponent(i, axis)).thenComparingInt(i -> i));
        int middle = indices.size() / 2;
        int index = indices.get(middle);
        return new PaletteNode(index, axis,
                buildColorTree(new ArrayList<>(indices.subList(0, middle)), depth + 1),
                buildColorTree(new ArrayList<>(indices.subList(middle + 1, indices.size())), depth + 1));
    }

    private static int colorComponent(int index, int axis) {
        return switch (axis) {
            case 0 -> MAP_RED[index];
            case 1 -> MAP_GREEN[index];
            default -> MAP_BLUE[index];
        };
    }

    private static long findNearestColor(PaletteNode node, int r, int g, int b, long best) {
        if (node == null) return best;

        int index = node.index();
        int dr = r - MAP_RED[index];
        int dg = g - MAP_GREEN[index];
        int db = b - MAP_BLUE[index];
        long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
        long bestDistance = best >>> 8;
        int bestIndex = (int) (best & 0xFF);
        if (distance < bestDistance || (distance == bestDistance && index < bestIndex)) {
            best = (distance << 8) | index;
        }

        int delta = switch (node.axis()) {
            case 0 -> r - MAP_RED[index];
            case 1 -> g - MAP_GREEN[index];
            default -> b - MAP_BLUE[index];
        };
        PaletteNode near = delta <= 0 ? node.left() : node.right();
        PaletteNode far = delta <= 0 ? node.right() : node.left();
        best = findNearestColor(near, r, g, b, best);
        if ((long) delta * delta <= (best >>> 8)) {
            best = findNearestColor(far, r, g, b, best);
        }
        return best;
    }
}
