package com.washed.mapcanvas;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.server.world.ServerWorld;

import java.awt.image.BufferedImage;

public class MapCreator {

    public static ItemStack createMapFromImage(BufferedImage image, ServerWorld world) {
        MapState mapState = MapState.of((byte) 0, true, world.getRegistryKey());
        MapIdComponent mapId = world.increaseAndGetMapId();
        world.putMapState(mapId, mapState);

        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                int rgb = image.getRGB(x, z);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                mapState.setColor(x, z, closestMapColor(r, g, b));
            }
        }


        ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
        mapStack.set(DataComponentTypes.MAP_ID, mapId);
        return mapStack;
    }

    public static net.minecraft.item.ItemStack createPosterMap(int[] mapIds, int width, int height, ServerWorld world) {
        net.minecraft.item.ItemStack posterStack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.FILLED_MAP);
        posterStack.set(net.minecraft.component.DataComponentTypes.MAP_ID, new net.minecraft.component.type.MapIdComponent(mapIds[0]));

        // Store poster data in NBT
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putBoolean("isPoster", true);
        nbt.putInt("posterWidth", width);
        nbt.putInt("posterHeight", height);
        net.minecraft.nbt.NbtIntArray mapIdsNbt = new net.minecraft.nbt.NbtIntArray(mapIds);
        nbt.put("mapIds", mapIdsNbt);
        posterStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt));

        posterStack.set(net.minecraft.component.DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                        net.minecraft.text.Text.literal("Size: " + width + "x" + height)
                                .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false)),
                        net.minecraft.text.Text.literal("Type: Poster")
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