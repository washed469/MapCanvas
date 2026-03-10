package com.washed.mapcanvas;

import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Path;
import java.util.UUID;

public class GifDataStore {

    private static Path getDir(MinecraftServer server) {
        Path dir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("mapcanvas_gifs");
        dir.toFile().mkdirs();
        return dir;
    }

    public static String save(MinecraftServer server, GifProcessor.GifData gifData) {
        String key = UUID.randomUUID().toString();
        Path file = getDir(server).resolve(key + ".dat");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            out.writeInt(gifData.frames().length);
            out.writeInt(gifData.width());
            out.writeInt(gifData.height());
            for (int delay : gifData.delaysMs()) out.writeInt(delay);
            for (byte[] frame : gifData.frames()) {
                out.writeInt(frame.length);
                out.write(frame);
            }
        } catch (Exception e) {
            System.out.println("MapCanvas: Failed to save GIF data: " + e.getMessage());
        }
        return key;
    }

    public static GifProcessor.GifData load(MinecraftServer server, String key) {
        Path file = getDir(server).resolve(key + ".dat");
        if (!file.toFile().exists()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file.toFile())))) {
            int frameCount = in.readInt();
            int width = in.readInt();
            int height = in.readInt();
            int[] delays = new int[frameCount];
            for (int i = 0; i < frameCount; i++) delays[i] = in.readInt();
            byte[][] frames = new byte[frameCount][];
            for (int i = 0; i < frameCount; i++) {
                int len = in.readInt();
                frames[i] = new byte[len];
                in.readFully(frames[i]);
            }
            return new GifProcessor.GifData(frames, delays, width, height);
        } catch (Exception e) {
            System.out.println("MapCanvas: Failed to load GIF data: " + e.getMessage());
            return null;
        }
    }

    public static void delete(MinecraftServer server, String key) {
        Path file = getDir(server).resolve(key + ".dat");
        file.toFile().delete();
    }
}