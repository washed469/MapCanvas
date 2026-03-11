package com.washed.mapcanvas;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Path;

public class MapCanvasConfig {

    private static Path getConfigPath(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("mapcanvas_config.json");
    }

    public static boolean loadCostEnabled(MinecraftServer server) {
        Path path = getConfigPath(server);
        if (!path.toFile().exists()) return true;
        try (Reader reader = new FileReader(path.toFile())) {
            JsonObject obj = new Gson().fromJson(reader, JsonObject.class);
            if (obj.has("costEnabled")) return obj.get("costEnabled").getAsBoolean();
        } catch (Exception e) {
            System.out.println("MapCanvas: Failed to load config: " + e.getMessage());
        }
        return true;
    }

    public static void saveCostEnabled(MinecraftServer server, boolean costEnabled) {
        Path path = getConfigPath(server);
        try (Writer writer = new FileWriter(path.toFile())) {
            JsonObject obj = new JsonObject();
            obj.addProperty("costEnabled", costEnabled);
            new Gson().toJson(obj, writer);
        } catch (Exception e) {
            System.out.println("MapCanvas: Failed to save config: " + e.getMessage());
        }
    }
}