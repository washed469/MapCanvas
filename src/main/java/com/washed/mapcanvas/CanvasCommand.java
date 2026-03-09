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
                .then(CommandManager.literal("convert")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(ctx -> convertImage(ctx))))
                .then(CommandManager.literal("help")
                        .executes(ctx -> showHelp(ctx)))
        );
    }

    private static int showHelp(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("--- MapCanvas Commands ---").styled(s -> s.withColor(net.minecraft.util.Formatting.GOLD).withBold(true)), false);
        source.sendFeedback(() -> Text.literal("/canvas add single <w> <h> <url>").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Gives individual maps for a WxH grid").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("/canvas add poster <w> <h> <url>").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Gives 1 poster map that auto-fills a WxH grid").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("/canvas name <n>").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Saves the last created image").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("/canvas list").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Lists your saved images").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("/canvas reload <n>").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Re-downloads the image data for a saved image").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("/canvas convert <n>").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Converts individual maps in inventory into 1 poster map").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("/canvas delete <n>").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Deletes a saved image").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("/canvas give <player> <n>").styled(s -> s.withColor(net.minecraft.util.Formatting.YELLOW))
                .append(Text.literal(" - Gives your saved image to another player").styled(s -> s.withColor(net.minecraft.util.Formatting.GRAY))), false);
        source.sendFeedback(() -> Text.literal("Cost: width x height empty maps per /canvas add").styled(s -> s.withColor(net.minecraft.util.Formatting.DARK_GRAY).withItalic(true)), false);
        return 1;
    }

    private static int countEmptyMaps(net.minecraft.entity.player.PlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            net.minecraft.item.ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(net.minecraft.item.Items.MAP)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeEmptyMaps(net.minecraft.entity.player.PlayerEntity player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            net.minecraft.item.ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(net.minecraft.item.Items.MAP)) {
                int take = Math.min(stack.getCount(), remaining);
                stack.decrement(take);
                remaining -= take;
            }
        }
    }

    private static int addImage(CommandContext<ServerCommandSource> ctx, boolean single) {
        String url = StringArgumentType.getString(ctx, "url");
        int width = IntegerArgumentType.getInteger(ctx, "width");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        ServerCommandSource source = ctx.getSource();

        net.minecraft.entity.player.PlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        int cost = width * height;
        boolean creative = player.isCreative();
        if (!creative) {
            int held = countEmptyMaps(player);
            if (held < cost) {
                source.sendFeedback(() -> Text.literal(
                        "You need " + cost + " empty map(s) to create a " + width + "x" + height + " canvas. You only have " + held + "."
                ), false);
                return 0;
            }
            consumeEmptyMaps(player, cost);
            source.sendFeedback(() -> Text.literal(cost + " empty map(s) consumed."), false);
        }

        new Thread(() -> {
            try {
                source.sendFeedback(() -> Text.literal("Downloading image..."), false);
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(url);

                java.awt.image.BufferedImage resized = ImageProcessor.resizeImage(image, width * 128, height * 128);

                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, width, height);

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.world.ServerWorld world = source.getWorld();

                        if (single) {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.item.ItemStack mapStack = MapCreator.createMapFromImage(sections[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.component.DataComponentTypes.MAP_ID).id();
                            }
                            net.minecraft.item.ItemStack posterMap = MapCreator.createPosterMap(mapIds, width, height, world);
                            player.getInventory().insertStack(posterMap);

                            Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
                            String playerKey = player.getUuidAsString();
                            data.computeIfAbsent(playerKey, k -> new HashMap<>())
                                    .put("__last__", new CanvasDataStore.CanvasEntry("__last__", url, true, width, height, mapIds));
                            CanvasDataStore.save(source.getServer(), data);

                            source.sendFeedback(() -> Text.literal("Poster ready! Right click any corner of a " + width + "x" + height + " item frame grid. Use /canvas name <n> to save it."), false);
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

                            Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
                            String playerKey = player.getUuidAsString();
                            data.computeIfAbsent(playerKey, k -> new HashMap<>())
                                    .put("__last__", new CanvasDataStore.CanvasEntry("__last__", url, false, width, height, mapIds));
                            CanvasDataStore.save(source.getServer(), data);

                            source.sendFeedback(() -> Text.literal("Got " + sections.length + " maps! Place them in a " + width + "x" + height + " grid. Use /canvas name <n> to save it."), false);
                        }
                    } catch (Exception e) {
                        source.sendFeedback(() -> Text.literal("Failed: " + e.getMessage()), false);
                    }
                });
            } catch (Exception e) {
                if (!creative) {
                    net.minecraft.item.ItemStack refund = new net.minecraft.item.ItemStack(net.minecraft.item.Items.MAP, cost);
                    source.getServer().execute(() -> {
                        if (!player.getInventory().insertStack(refund)) {
                            player.dropItem(refund, false);
                        }
                    });
                    source.sendFeedback(() -> Text.literal("Failed to download image: " + e.getMessage() + " Your maps have been refunded."), false);
                } else {
                    source.sendFeedback(() -> Text.literal("Failed to download image: " + e.getMessage()), false);
                }
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
        new Thread(() -> {
            try {
                source.sendFeedback(() -> Text.literal("Reloading \"" + name + "\"..."), false);
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(entry.url());
                java.awt.image.BufferedImage resized = ImageProcessor.resizeImage(image, entry.width() * 128, entry.height() * 128);
                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, entry.width(), entry.height());

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.world.ServerWorld world = source.getWorld();
                        int[] mapIds = new int[sections.length];
                        for (int i = 0; i < sections.length; i++) {
                            net.minecraft.item.ItemStack mapStack = MapCreator.createMapFromImage(sections[i], world);
                            mapIds[i] = mapStack.get(net.minecraft.component.DataComponentTypes.MAP_ID).id();
                        }
                        Map<String, Map<String, CanvasDataStore.CanvasEntry>> updatedData = CanvasDataStore.load(source.getServer());
                        updatedData.get(player.getUuidAsString()).put(name,
                                new CanvasDataStore.CanvasEntry(entry.name(), entry.url(), entry.single(), entry.width(), entry.height(), mapIds));
                        CanvasDataStore.save(source.getServer(), updatedData);
                        source.sendFeedback(() -> Text.literal("\"" + name + "\" reloaded successfully!"), false);
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

    private static int convertImage(CommandContext<ServerCommandSource> ctx) {
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
        int[] requiredIds = entry.mapIds();

        java.util.Map<Integer, Integer> idToSlot = new java.util.HashMap<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            net.minecraft.item.ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(net.minecraft.item.Items.FILLED_MAP)) {
                net.minecraft.component.type.MapIdComponent mapId = stack.get(net.minecraft.component.DataComponentTypes.MAP_ID);
                if (mapId != null) {
                    for (int requiredId : requiredIds) {
                        if (mapId.id() == requiredId && !idToSlot.containsKey(requiredId)) {
                            idToSlot.put(requiredId, i);
                            break;
                        }
                    }
                }
            }
        }

        int missing = 0;
        for (int id : requiredIds) {
            if (!idToSlot.containsKey(id)) missing++;
        }

        if (missing > 0) {
            int finalMissing = missing;
            source.sendFeedback(() -> Text.literal("Missing " + finalMissing + " map(s) from \"" + name + "\". Make sure all maps are in your inventory."), false);
            return 0;
        }

        for (int slot : idToSlot.values()) {
            player.getInventory().getStack(slot).decrement(1);
        }

        net.minecraft.item.ItemStack posterMap = MapCreator.createPosterMap(requiredIds, entry.width(), entry.height(), source.getWorld());
        if (!player.getInventory().insertStack(posterMap)) {
            player.dropItem(posterMap, false);
        }

        source.sendFeedback(() -> Text.literal("Converted \"" + name + "\" into a poster map!"), false);
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
                java.awt.image.BufferedImage resized = ImageProcessor.resizeImage(image, entry.width() * 128, entry.height() * 128);
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