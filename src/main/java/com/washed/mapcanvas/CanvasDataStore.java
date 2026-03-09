package com.washed.mapcanvas;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class CanvasDataStore {

    public record CanvasEntry(String name, String url, boolean single, int width, int height, int[] mapIds) {}

    private static Path getSavePath(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("mapcanvas_data.json");
    }

    public static Map<String, Map<String, CanvasEntry>> load(MinecraftServer server) {
        Map<String, Map<String, CanvasEntry>> data = new HashMap<>();
        Path path = getSavePath(server);
        if (!path.toFile().exists()) return data;
        try (Reader reader = new FileReader(path.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> playerEntry : root.entrySet()) {
                Map<String, CanvasEntry> images = new HashMap<>();
                for (Map.Entry<String, JsonElement> imageEntry : playerEntry.getValue().getAsJsonObject().entrySet()) {
                    JsonObject e = imageEntry.getValue().getAsJsonObject();
                    JsonArray idsArray = e.get("mapIds").getAsJsonArray();
                    int[] mapIds = new int[idsArray.size()];
                    for (int i = 0; i < idsArray.size(); i++) mapIds[i] = idsArray.get(i).getAsInt();
                    images.put(imageEntry.getKey(), new CanvasEntry(
                            e.get("name").getAsString(),
                            e.get("url").getAsString(),
                            e.get("single").getAsBoolean(),
                            e.get("width").getAsInt(),
                            e.get("height").getAsInt(),
                            mapIds
                    ));
                }
                data.put(playerEntry.getKey(), images);
            }
        } catch (Exception e) {
            System.out.println("MapCanvas: Failed to load data: " + e.getMessage());
        }
        return data;
    }

    public static void save(MinecraftServer server, Map<String, Map<String, CanvasEntry>> data) {
        Path path = getSavePath(server);
        try (Writer writer = new FileWriter(path.toFile())) {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Map<String, CanvasEntry>> playerEntry : data.entrySet()) {
                JsonObject playerObj = new JsonObject();
                for (Map.Entry<String, CanvasEntry> imageEntry : playerEntry.getValue().entrySet()) {
                    CanvasEntry entry = imageEntry.getValue();
                    JsonObject entryObj = new JsonObject();
                    entryObj.addProperty("name", entry.name());
                    entryObj.addProperty("url", entry.url());
                    entryObj.addProperty("single", entry.single());
                    entryObj.addProperty("width", entry.width());
                    entryObj.addProperty("height", entry.height());
                    JsonArray idsArray = new JsonArray();
                    for (int id : entry.mapIds()) idsArray.add(id);
                    entryObj.add("mapIds", idsArray);
                    playerObj.add(imageEntry.getKey(), entryObj);
                }
                root.add(playerEntry.getKey(), playerObj);
            }
            new Gson().toJson(root, writer);
        } catch (Exception e) {
            System.out.println("MapCanvas: Failed to save data: " + e.getMessage());
        }
    }
}