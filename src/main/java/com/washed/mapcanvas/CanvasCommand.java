package com.washed.mapcanvas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class CanvasCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("canvas")
                .then(CommandManager.literal("add")
                        .then(CommandManager.literal("single")
                                .then(CommandManager.argument("width", IntegerArgumentType.integer(1, 10))
                                        .then(CommandManager.argument("height", IntegerArgumentType.integer(1, 10))
                                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> addImage(ctx, false))))))
                        .then(CommandManager.literal("poster")
                                .then(CommandManager.argument("width", IntegerArgumentType.integer(1, 10))
                                        .then(CommandManager.argument("height", IntegerArgumentType.integer(1, 10))
                                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> addImage(ctx, true)))))))
                .then(CommandManager.literal("name")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(ctx -> nameImage(ctx))))
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(ctx -> deleteImage(ctx))))
                .then(CommandManager.literal("list")
                        .executes(ctx -> listImages(ctx)))
                .then(CommandManager.literal("reload")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(ctx -> reloadImage(ctx))))
                .then(CommandManager.literal("give")
                        .then(CommandManager.argument("player", StringArgumentType.string())
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .executes(ctx -> giveImage(ctx)))))
        );
    }

    private static int addImage(CommandContext<ServerCommandSource> ctx, boolean single) {
        String url = StringArgumentType.getString(ctx, "url");
        int width = IntegerArgumentType.getInteger(ctx, "width");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        ServerCommandSource source = ctx.getSource();

        new Thread(() -> {
            try {
                source.sendFeedback(() -> Text.literal("Downloading image..."), false);
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(url);

                java.awt.image.BufferedImage resized;
                if (single) {
                    resized = ImageProcessor.resizeImage(image, 128, 128);
                    resized = ImageProcessor.resizeImage(resized, width * 128, height * 128);
                } else {
                    resized = ImageProcessor.resizeImage(image, width * 128, height * 128);
                }

                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, width, height);

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.world.ServerWorld world = source.getWorld();
                        net.minecraft.entity.player.PlayerEntity player = source.getPlayer();

                        if (single) {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.item.ItemStack mapStack = MapCreator.createMapFromImage(sections[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.component.DataComponentTypes.MAP_ID).id();
                            }
                            net.minecraft.item.ItemStack posterMap = MapCreator.createPosterMap(mapIds, width, height, world);
                            player.getInventory().insertStack(posterMap);

                            // Save to data store
                            Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
                            String playerKey = player.getUuidAsString();
                            data.computeIfAbsent(playerKey, k -> new HashMap<>())
                                    .put("__last__", new CanvasDataStore.CanvasEntry("__last__", url, true, width, height, mapIds));
                            CanvasDataStore.save(source.getServer(), data);

                            source.sendFeedback(() -> Text.literal("Poster ready! Right click any corner of a " + width + "x" + height + " item frame grid. Use /canvas name <name> to save it."), false);
                        } else {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.item.ItemStack mapStack = MapCreator.createMapFromImage(sections[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.component.DataComponentTypes.MAP_ID).id();
                                mapStack.set(net.minecraft.component.DataComponentTypes.LORE,
                                        new net.minecraft.component.type.LoreComponent(java.util.List.of(
                                                net.minecraft.text.Text.literal("Size: " + width + "x" + height)
                                                        .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false)),
                                                net.minecraft.text.Text.literal("Type: Single")
                                                        .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false)),
                                                net.minecraft.text.Text.literal("Map: " + (i + 1) + "/" + sections.length)
                                                        .styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY).withItalic(false))
                                        )));
                                if (!player.getInventory().insertStack(mapStack)) {
                                    player.dropItem(mapStack, false);
                                }
                            }

                            // Save to data store
                            Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
                            String playerKey = player.getUuidAsString();
                            data.computeIfAbsent(playerKey, k -> new HashMap<>())
                                    .put("__last__", new CanvasDataStore.CanvasEntry("__last__", url, false, width, height, mapIds));
                            CanvasDataStore.save(source.getServer(), data);

                            source.sendFeedback(() -> Text.literal("Got " + sections.length + " maps! Place them in a " + width + "x" + height + " grid. Use /canvas name <name> to save it."), false);
                        }
                    } catch (Exception e) {
                        source.sendFeedback(() -> Text.literal("Failed: " + e.getMessage()), false);
                    }
                });
            } catch (Exception e) {
                source.sendFeedback(() -> Text.literal("Failed to download image: " + e.getMessage()), false);
            }
        }).start();

        return 1;
    }

    private static int nameImage(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerCommandSource source = ctx.getSource();
        net.minecraft.entity.player.PlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getUuidAsString();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey("__last__")) {
            source.sendFeedback(() -> Text.literal("No recent image to name! Use /canvas add first."), false);
            return 0;
        }

        CanvasDataStore.CanvasEntry last = images.get("__last__");
        CanvasDataStore.CanvasEntry named = new CanvasDataStore.CanvasEntry(name, last.url(), last.single(), last.width(), last.height(), last.mapIds());
        images.put(name, named);
        CanvasDataStore.save(source.getServer(), data);
        source.sendFeedback(() -> Text.literal("Image saved as \"" + name + "\"!"), false);
        return 1;
    }

    private static int deleteImage(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerCommandSource source = ctx.getSource();
        net.minecraft.entity.player.PlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getUuidAsString();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey(name)) {
            source.sendFeedback(() -> Text.literal("No image found with name \"" + name + "\"."), false);
            return 0;
        }

        images.remove(name);
        CanvasDataStore.save(source.getServer(), data);
        source.sendFeedback(() -> Text.literal("Deleted image \"" + name + "\"."), false);
        return 1;
    }

    private static int listImages(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        net.minecraft.entity.player.PlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getUuidAsString();
        Map<String, CanvasDataStore.CanvasEntry> images = data.getOrDefault(playerKey, new HashMap<>());

        if (images.isEmpty()) {
            source.sendFeedback(() -> Text.literal("You have no saved images."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("--- Your Images ---"), false);
        int[] index = {1};
        images.forEach((name, entry) -> {
            if (name.equals("__last__")) return;
            net.minecraft.text.MutableText text = Text.literal(index[0]++ + ". \"" + entry.name() + "\" - " + (entry.single() ? "poster" : "single") + " " + entry.width() + "x" + entry.height() + " ");
            net.minecraft.text.MutableText link = Text.literal(entry.url())
                    .styled(style -> style
                            .withColor(net.minecraft.util.Formatting.AQUA)
                            .withUnderline(true)
                            .withClickEvent(new net.minecraft.text.ClickEvent.OpenUrl(java.net.URI.create(entry.url())))
                            .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(Text.literal("Click to open image URL"))));
            source.sendFeedback(() -> text.append(link), false);
        });
        return 1;
    }

    private static int reloadImage(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerCommandSource source = ctx.getSource();
        net.minecraft.entity.player.PlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getUuidAsString();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey(name)) {
            source.sendFeedback(() -> Text.literal("No image found with name \"" + name + "\"."), false);
            return 0;
        }

        CanvasDataStore.CanvasEntry entry = images.get(name);
        // Re-run addImage logic with saved parameters
        new Thread(() -> {
            try {
                source.sendFeedback(() -> Text.literal("Reloading \"" + name + "\"..."), false);
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(entry.url());
                java.awt.image.BufferedImage resized;
                if (entry.single()) {
                    resized = ImageProcessor.resizeImage(image, 128, 128);
                    resized = ImageProcessor.resizeImage(resized, entry.width() * 128, entry.height() * 128);
                } else {
                    resized = ImageProcessor.resizeImage(image, entry.width() * 128, entry.height() * 128);
                }
                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, entry.width(), entry.height());

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.world.ServerWorld world = source.getWorld();
                        if (entry.single()) {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.item.ItemStack mapStack = MapCreator.createMapFromImage(sections[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.component.DataComponentTypes.MAP_ID).id();
                            }
                            net.minecraft.item.ItemStack posterMap = MapCreator.createPosterMap(mapIds, entry.width(), entry.height(), world);
                            player.getInventory().insertStack(posterMap);
                        } else {
                            for (java.awt.image.BufferedImage section : sections) {
                                player.getInventory().insertStack(MapCreator.createMapFromImage(section, world));
                            }
                        }
                        source.sendFeedback(() -> Text.literal("Reloaded \"" + name + "\" successfully!"), false);
                    } catch (Exception e) {
                        source.sendFeedback(() -> Text.literal("Failed: " + e.getMessage()), false);
                    }
                });
            } catch (Exception e) {
                source.sendFeedback(() -> Text.literal("Failed to reload: " + e.getMessage()), false);
            }
        }).start();
        return 1;
    }

    private static int giveImage(CommandContext<ServerCommandSource> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");
        String name = StringArgumentType.getString(ctx, "name");
        ServerCommandSource source = ctx.getSource();
        net.minecraft.entity.player.PlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        net.minecraft.server.PlayerManager pm = source.getServer().getPlayerManager();
        net.minecraft.server.network.ServerPlayerEntity target = pm.getPlayer(targetName);
        if (target == null) {
            source.sendFeedback(() -> Text.literal("Player \"" + targetName + "\" not found."), false);
            return 0;
        }

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getUuidAsString();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey(name)) {
            source.sendFeedback(() -> Text.literal("No image found with name \"" + name + "\"."), false);
            return 0;
        }

        CanvasDataStore.CanvasEntry entry = images.get(name);
        new Thread(() -> {
            try {
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(entry.url());
                java.awt.image.BufferedImage resized;
                if (entry.single()) {
                    resized = ImageProcessor.resizeImage(image, 128, 128);
                    resized = ImageProcessor.resizeImage(resized, entry.width() * 128, entry.height() * 128);
                } else {
                    resized = ImageProcessor.resizeImage(image, entry.width() * 128, entry.height() * 128);
                }
                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, entry.width(), entry.height());

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.world.ServerWorld world = source.getWorld();
                        if (entry.single()) {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.item.ItemStack mapStack = MapCreator.createMapFromImage(sections[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.component.DataComponentTypes.MAP_ID).id();
                            }
                            net.minecraft.item.ItemStack posterMap = MapCreator.createPosterMap(mapIds, entry.width(), entry.height(), world);
                            target.getInventory().insertStack(posterMap);
                        } else {
                            for (java.awt.image.BufferedImage section : sections) {
                                target.getInventory().insertStack(MapCreator.createMapFromImage(section, world));
                            }
                        }
                        source.sendFeedback(() -> Text.literal("Gave \"" + name + "\" to " + targetName + "!"), false);
                    } catch (Exception e) {
                        source.sendFeedback(() -> Text.literal("Failed: " + e.getMessage()), false);
                    }
                });
            } catch (Exception e) {
                source.sendFeedback(() -> Text.literal("Failed to give image: " + e.getMessage()), false);
            }
        }).start();
        return 1;
    }
}