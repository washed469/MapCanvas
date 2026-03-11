package com.washed.mapcanvas;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.server.world.ServerWorld;

import java.awt.image.BufferedImage;

public class MapCreator {

    public static ItemStack createMapFromImage(BufferedImage image, ServerWorld world, boolean dither) {
        MapState mapState = MapState.of((byte) 0, true, world.getRegistryKey());
        MapIdComponent mapId = world.increaseAndGetMapId();
        world.putMapState(mapId, mapState);

        byte[] colors = dither ? Dithering.ditherToMapColors(image) : toMapColors(image);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                mapState.setColor(x, z, colors[z * 128 + x]);
            }
        }

        ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
        mapStack.set(DataComponentTypes.MAP_ID, mapId);
        return mapStack;
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

    public static ItemStack createPosterMap(int[] mapIds, int width, int height, ServerWorld world, boolean dither) {
        ItemStack posterStack = new ItemStack(Items.FILLED_MAP);
        posterStack.set(DataComponentTypes.MAP_ID, new MapIdComponent(mapIds[0]));

        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putBoolean("isPoster", true);
        nbt.putInt("posterWidth", width);
        nbt.putInt("posterHeight", height);
        nbt.put("mapIds", new net.minecraft.nbt.NbtIntArray(mapIds));
        posterStack.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));

        posterStack.set(DataComponentTypes.CUSTOM_NAME,
                net.minecraft.text.Text.literal("Poster Map")
                        .styled(s -> s.withColor(net.minecraft.util.Formatting.GOLD).withItalic(false).withBold(true)));
        posterStack.set(DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                        net.minecraft.text.Text.literal("Size: " + width + "x" + height)
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false)),
                        net.minecraft.text.Text.literal("Type: " + (dither ? "Poster HD" : "Poster"))
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false))
                )));

        return posterStack;
    }

    public static ItemStack createGifPosterMap(int[] mapIds, int width, int height, GifProcessor.GifData gifData, String gifKey) {
        ItemStack posterStack = new ItemStack(Items.FILLED_MAP);
        posterStack.set(DataComponentTypes.MAP_ID, new MapIdComponent(mapIds[0]));

        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putBoolean("isPoster", true);
        nbt.putBoolean("isGif", true);
        nbt.putString("gifKey", gifKey);
        nbt.putInt("posterWidth", width);
        nbt.putInt("posterHeight", height);
        nbt.put("mapIds", new net.minecraft.nbt.NbtIntArray(mapIds));
        nbt.putInt("gifFrameCount", gifData.frames().length);
        posterStack.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));

        posterStack.set(DataComponentTypes.CUSTOM_NAME,
                net.minecraft.text.Text.literal("Animated Poster Map")
                        .styled(s -> s.withColor(net.minecraft.util.Formatting.AQUA).withItalic(false).withBold(true)));
        posterStack.set(DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                        net.minecraft.text.Text.literal("Size: " + width + "x" + height)
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false)),
                        net.minecraft.text.Text.literal("Type: Animated Poster")
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false)),
                        net.minecraft.text.Text.literal("Frames: " + gifData.frames().length)
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false))
                )));

        return posterStack;
    }

    private static byte closestMapColor(int r, int g, int b) {
        byte closest = 4;
        double closestDist = Double.MAX_VALUE;

        for (int i = 4; i < 256; i++) {
            int base = i >> 2;
            int shade = i & 3;
            if (base >= MapColorPalette.COLORS.length) continue;
            int[] baseColor = MapColorPalette.COLORS[base];
            float multiplier = MapColorPalette.MULTIPLIERS[shade];
            int mr = (int)(baseColor[0] * multiplier);
            int mg = (int)(baseColor[1] * multiplier);
            int mb = (int)(baseColor[2] * multiplier);
            double dist = Math.pow(r - mr, 2) + Math.pow(g - mg, 2) + Math.pow(b - mb, 2);
            if (dist < closestDist) {
                closestDist = dist;
                closest = (byte) i;
            }
        }
        return closest;
    }
}