package com.washed.mapcanvas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.HashMap;
import java.util.Map;

public class CanvasCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("canvas")
                .then(Commands.literal("add")
                        .then(Commands.literal("single")
                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 10))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 10))
                                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> addImage(ctx, false, false))))))
                        .then(Commands.literal("single-hd")
                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 10))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 10))
                                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> addImage(ctx, false, true))))))
                        .then(Commands.literal("poster")
                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 10))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 10))
                                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> addImage(ctx, true, false))))))
                        .then(Commands.literal("poster-hd")
                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 10))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 10))
                                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> addImage(ctx, true, true)))))))
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> nameImage(ctx))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> deleteImage(ctx))))
                .then(Commands.literal("list")
                        .executes(ctx -> listImages(ctx)))
                .then(Commands.literal("reload")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> reloadImage(ctx))))
                .then(Commands.literal("convert")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> convertImage(ctx))))
                .then(Commands.literal("help")
                        .executes(ctx -> showHelp(ctx)))
                .then(Commands.literal("cost")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("enable")
                                .executes(ctx -> setCost(ctx, true)))
                        .then(Commands.literal("disable")
                                .executes(ctx -> setCost(ctx, false))))
        );
    }

    private static boolean costEnabled = true;

    public static void loadConfig(MinecraftServer server) {
        costEnabled = MapCanvasConfig.loadCostEnabled(server);
    }

    private static int setCost(CommandContext<CommandSourceStack> ctx, boolean enable) {
        costEnabled = enable;
        MapCanvasConfig.saveCostEnabled(ctx.getSource().getServer(), enable);
        ctx.getSource().sendSuccess(() -> Component.literal("Map cost " + (enable ? "enabled" : "disabled") + ".")
                .withStyle(s -> s.withColor(enable ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED)), true);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("--- MapCanvas Commands ---").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD).withBold(true)), false);
        source.sendSuccess(() -> Component.literal("/canvas add single <w> <h> <url>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Gives individual maps for a WxH grid").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas add single-hd <w> <h> <url>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Same as single with dithering for smoother colors").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas add poster <w> <h> <url>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Gives 1 poster map that auto-fills a WxH grid").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas add poster-hd <w> <h> <url>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Same as poster with dithering for smoother colors").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas name <n>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Saves the last created image").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas list").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Lists your saved images").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas reload <n>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Re-downloads the image data for a saved image").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas convert <n>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Converts individual maps in inventory into 1 poster map").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas delete <n>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Deletes a saved image").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas give <player> <n>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Gives your saved image to another player").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("/canvas cost <enable|disable>").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" - Enable or disable the map cost (ops only)").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> Component.literal("Cost: width x height empty maps per /canvas add").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.DARK_GRAY).withItalic(true)), false);
        return 1;
    }

    private static int countEmptyMaps(net.minecraft.world.entity.player.Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if ((stack.getItem() == net.minecraft.world.item.Items.MAP)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeEmptyMaps(net.minecraft.world.entity.player.Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if ((stack.getItem() == net.minecraft.world.item.Items.MAP)) {
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static int addImage(CommandContext<CommandSourceStack> ctx, boolean single, boolean dither) {
        String url = StringArgumentType.getString(ctx, "url");
        int width = IntegerArgumentType.getInteger(ctx, "width");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        CommandSourceStack source = ctx.getSource();

        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null) return 0;

        int cost = width * height;
        boolean creative = player.isCreative();
        if (costEnabled && !creative) {
            int held = countEmptyMaps(player);
            if (held < cost) {
                source.sendSuccess(() -> Component.literal(
                        "You need " + cost + " empty map(s) to create a " + width + "x" + height + " canvas. You only have " + held + "."
                ), false);
                return 0;
            }
            consumeEmptyMaps(player, cost);
            source.sendSuccess(() -> Component.literal(cost + " empty map(s) consumed."), false);
        }

        new Thread(() -> {
            try {
                String resolvedUrl;
                try {
                    resolvedUrl = UrlResolver.resolve(url);
                } catch (Exception e) {
                    source.sendSuccess(() -> Component.literal("Could not load URL.")
                            .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED)), false);
                    String message = UrlResolver.isDiscordUrl(url)
                            ? UrlResolver.discordGifInstructions() : e.getMessage();
                    source.sendSuccess(() -> Component.literal(message)
                            .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)), false);
                    return;
                }
                if (!resolvedUrl.equals(url)) {
                    source.sendSuccess(() -> Component.literal("Resolved URL from page..."), false);
                }

                if (GifProcessor.isGifUrl(resolvedUrl)) {
                    if (!single) source.sendSuccess(() -> Component.literal("GIF detected — using poster mode automatically."), false);
                    source.sendSuccess(() -> Component.literal("Processing GIF..."), false);
                    GifProcessor.GifData gifData = GifProcessor.processGif(resolvedUrl, width, height);
                    String gifKey = GifDataStore.save(source.getServer(), gifData);

                    source.getServer().execute(() -> {
                        try {
                            net.minecraft.server.level.ServerLevel world = source.getLevel();
                            int[] mapIds = new int[width * height];
                            byte[] firstFrame = new byte[128 * 128];
                            for (int i = 0; i < mapIds.length; i++) {
                                System.arraycopy(gifData.frames()[0], i * 128 * 128, firstFrame, 0, 128 * 128);
                                net.minecraft.world.level.saveddata.maps.MapItemSavedData mapState = net.minecraft.world.level.saveddata.maps.MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, world.dimension()).locked();
                                net.minecraft.world.level.saveddata.maps.MapId mapId = world.getFreeMapId();
                                world.setMapData(mapId, mapState);
                                MapCanvasDebug.trackMapId(mapId.id());
                                System.arraycopy(firstFrame, 0, mapState.colors, 0, firstFrame.length);
                                mapState.setDirty();
                                mapIds[i] = mapId.id();
                            }

                            net.minecraft.world.item.ItemStack posterMap = MapCreator.createGifPosterMap(mapIds, width, height, gifData, gifKey);
                            player.getInventory().add(posterMap);
                            for (int i = 0; i < mapIds.length; i++) {
                                byte[] tileColors = java.util.Arrays.copyOfRange(gifData.frames()[0], i * 128 * 128, (i + 1) * 128 * 128);
                                MapCreator.syncMapToPlayer((net.minecraft.server.level.ServerPlayer) player,
                                        new net.minecraft.world.level.saveddata.maps.MapId(mapIds[i]), tileColors);
                            }
                            MapCanvasDebug.log("gif-poster-created size=" + width + "x" + height + " frames="
                                    + gifData.frames().length + " mapIds=" + java.util.Arrays.toString(mapIds));

                            Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
                            String playerKey = player.getStringUUID();
                            data.computeIfAbsent(playerKey, k -> new HashMap<>())
                                    .put("__last__", new CanvasDataStore.CanvasEntry("__last__", url, true, width, height, mapIds));
                            CanvasDataStore.save(source.getServer(), data);

                            source.sendSuccess(() -> Component.literal("Animated poster ready! Right click any corner of a " + width + "x" + height + " item frame grid."), false);
                        } catch (Exception e) {
                            source.sendSuccess(() -> Component.literal("Failed: " + e.getMessage()), false);
                        }
                    });
                    return;
                }

                source.sendSuccess(() -> Component.literal("Downloading image..."), false);
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(resolvedUrl);

                java.awt.image.BufferedImage resized = ImageProcessor.resizeImage(image, width * 128, height * 128);

                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, width, height);
                byte[][] mapColors = new byte[sections.length][];
                for (int i = 0; i < sections.length; i++) {
                    mapColors[i] = MapCreator.imageToMapColors(sections[i], dither);
                }

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.level.ServerLevel world = source.getLevel();

                        if (single) {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.world.item.ItemStack mapStack = MapCreator.createMapFromColors(mapColors[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID).id();
                            }
                            net.minecraft.world.item.ItemStack posterMap = MapCreator.createPosterMap(mapIds, width, height, world, dither);
                            player.getInventory().add(posterMap);
                            for (int i = 0; i < mapIds.length; i++) {
                                MapCreator.syncMapToPlayer((net.minecraft.server.level.ServerPlayer) player,
                                        new net.minecraft.world.level.saveddata.maps.MapId(mapIds[i]), mapColors[i]);
                            }
                            MapCanvasDebug.log("poster-created size=" + width + "x" + height + " mapIds="
                                    + java.util.Arrays.toString(mapIds));

                            Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
                            String playerKey = player.getStringUUID();
                            data.computeIfAbsent(playerKey, k -> new HashMap<>())
                                    .put("__last__", new CanvasDataStore.CanvasEntry("__last__", url, true, width, height, mapIds));
                            CanvasDataStore.save(source.getServer(), data);

                            source.sendSuccess(() -> Component.literal("Poster ready! Right click any corner of a " + width + "x" + height + " item frame grid. Use /canvas name <n> to save it."), false);
                        } else {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.world.item.ItemStack mapStack = MapCreator.createMapFromColors(mapColors[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID).id();
                                mapStack.set(net.minecraft.core.component.DataComponents.LORE,
                                        new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                                                net.minecraft.network.chat.Component.literal("Size: " + width + "x" + height)
                                                        .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)),
                                                net.minecraft.network.chat.Component.literal("Type: " + (dither ? "Single HD" : "Single"))
                                                        .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)),
                                                net.minecraft.network.chat.Component.literal("Map: " + (i + 1) + "/" + sections.length)
                                                        .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false))
                                        )));
                                if (!player.getInventory().add(mapStack)) {
                                    player.drop(mapStack, false, null);
                                }
                                MapCreator.syncMapToPlayer((net.minecraft.server.level.ServerPlayer) player,
                                        new net.minecraft.world.level.saveddata.maps.MapId(mapIds[i]), mapColors[i]);
                            }
                            MapCanvasDebug.log("single-maps-created size=" + width + "x" + height + " mapIds="
                                    + java.util.Arrays.toString(mapIds));

                            Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
                            String playerKey = player.getStringUUID();
                            data.computeIfAbsent(playerKey, k -> new HashMap<>())
                                    .put("__last__", new CanvasDataStore.CanvasEntry("__last__", url, false, width, height, mapIds));
                            CanvasDataStore.save(source.getServer(), data);

                            source.sendSuccess(() -> Component.literal("Got " + sections.length + " maps! Place them in a " + width + "x" + height + " grid. Use /canvas name <n> to save it."), false);
                        }
                    } catch (Exception e) {
                        source.sendSuccess(() -> Component.literal("Failed: " + e.getMessage()), false);
                    }
                });
            } catch (Exception e) {
                if (UrlResolver.isDiscordUrl(url)) {
                    source.sendSuccess(() -> Component.literal(UrlResolver.discordGifInstructions())
                            .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)), false);
                }
                if (costEnabled && !creative) {
                    net.minecraft.world.item.ItemStack refund = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAP, cost);
                    source.getServer().execute(() -> {
                        if (!player.getInventory().add(refund)) {
                            player.drop(refund, false, null);
                        }
                    });
                    source.sendSuccess(() -> Component.literal("Failed to download image: " + e.getMessage() + " Your maps have been refunded."), false);
                } else {
                    source.sendSuccess(() -> Component.literal("Failed to download image: " + e.getMessage()), false);
                }
            }
        }).start();

        return 1;
    }

    private static int nameImage(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack source = ctx.getSource();
        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getStringUUID();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey("__last__")) {
            source.sendSuccess(() -> Component.literal("No recent image to name! Use /canvas add first."), false);
            return 0;
        }

        CanvasDataStore.CanvasEntry last = images.get("__last__");
        CanvasDataStore.CanvasEntry named = new CanvasDataStore.CanvasEntry(name, last.url(), last.single(), last.width(), last.height(), last.mapIds());
        images.put(name, named);
        CanvasDataStore.save(source.getServer(), data);
        source.sendSuccess(() -> Component.literal("Image saved as \"" + name + "\"!"), false);
        return 1;
    }

    private static int deleteImage(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack source = ctx.getSource();
        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getStringUUID();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey(name)) {
            source.sendSuccess(() -> Component.literal("No image found with name \"" + name + "\"."), false);
            return 0;
        }

        images.remove(name);
        CanvasDataStore.save(source.getServer(), data);
        source.sendSuccess(() -> Component.literal("Deleted image \"" + name + "\"."), false);
        return 1;
    }

    private static int listImages(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getStringUUID();
        Map<String, CanvasDataStore.CanvasEntry> images = data.getOrDefault(playerKey, new HashMap<>());

        if (images.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You have no saved images."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("--- Your Images ---"), false);
        int[] index = {1};
        images.forEach((name, entry) -> {
            if (name.equals("__last__")) return;
            net.minecraft.network.chat.MutableComponent text = Component.literal(index[0]++ + ". \"" + entry.name() + "\" - " + (entry.single() ? "poster" : "single") + " " + entry.width() + "x" + entry.height() + " ");
            net.minecraft.network.chat.MutableComponent link = Component.literal(entry.url())
                    .withStyle(style -> style
                            .withColor(net.minecraft.ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(entry.url())))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("Click to open image URL"))));
            source.sendSuccess(() -> text.append(link), false);
        });
        return 1;
    }

    private static int reloadImage(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack source = ctx.getSource();
        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getStringUUID();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey(name)) {
            source.sendSuccess(() -> Component.literal("No image found with name \"" + name + "\"."), false);
            return 0;
        }

        CanvasDataStore.CanvasEntry entry = images.get(name);
        new Thread(() -> {
            try {
                source.sendSuccess(() -> Component.literal("Reloading \"" + name + "\"..."), false);
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(entry.url());
                java.awt.image.BufferedImage resized = ImageProcessor.resizeImage(image, entry.width() * 128, entry.height() * 128);
                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, entry.width(), entry.height());
                byte[][] mapColors = new byte[sections.length][];
                for (int i = 0; i < sections.length; i++) {
                    mapColors[i] = MapCreator.imageToMapColors(sections[i], false);
                }

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.level.ServerLevel world = source.getLevel();
                        int[] mapIds = new int[sections.length];
                        for (int i = 0; i < sections.length; i++) {
                            net.minecraft.world.item.ItemStack mapStack = MapCreator.createMapFromColors(mapColors[i], world);
                            mapIds[i] = mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID).id();
                        }
                        Map<String, Map<String, CanvasDataStore.CanvasEntry>> updatedData = CanvasDataStore.load(source.getServer());
                        updatedData.get(player.getStringUUID()).put(name,
                                new CanvasDataStore.CanvasEntry(entry.name(), entry.url(), entry.single(), entry.width(), entry.height(), mapIds));
                        CanvasDataStore.save(source.getServer(), updatedData);
                        source.sendSuccess(() -> Component.literal("\"" + name + "\" reloaded successfully!"), false);
                    } catch (Exception e) {
                        source.sendSuccess(() -> Component.literal("Failed: " + e.getMessage()), false);
                    }
                });
            } catch (Exception e) {
                source.sendSuccess(() -> Component.literal("Failed to reload: " + e.getMessage()), false);
            }
        }).start();
        return 1;
    }

    private static int convertImage(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack source = ctx.getSource();
        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null) return 0;

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getStringUUID();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey(name)) {
            source.sendSuccess(() -> Component.literal("No image found with name \"" + name + "\"."), false);
            return 0;
        }

        CanvasDataStore.CanvasEntry entry = images.get(name);
        int[] requiredIds = entry.mapIds();

        java.util.Map<Integer, Integer> idToSlot = new java.util.HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if ((stack.getItem() == net.minecraft.world.item.Items.FILLED_MAP)) {
                net.minecraft.world.level.saveddata.maps.MapId mapId = stack.get(net.minecraft.core.component.DataComponents.MAP_ID);
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
            source.sendSuccess(() -> Component.literal("Missing " + finalMissing + " map(s) from \"" + name + "\". Make sure all maps are in your inventory."), false);
            return 0;
        }

        for (int slot : idToSlot.values()) {
            player.getInventory().getItem(slot).shrink(1);
        }

        net.minecraft.world.item.ItemStack posterMap = MapCreator.createPosterMap(requiredIds, entry.width(), entry.height(), source.getLevel(), false);
        if (!player.getInventory().add(posterMap)) {
            player.drop(posterMap, false, null);
        }

        source.sendSuccess(() -> Component.literal("Converted \"" + name + "\" into a poster map!"), false);
        return 1;
    }

    private static int giveImage(CommandContext<CommandSourceStack> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack source = ctx.getSource();
        net.minecraft.world.entity.player.Player player = source.getPlayer();
        if (player == null) return 0;

        net.minecraft.server.players.PlayerList pm = source.getServer().getPlayerList();
        net.minecraft.server.level.ServerPlayer target = pm.getPlayerByName(targetName);
        if (target == null) {
            source.sendSuccess(() -> Component.literal("Player \"" + targetName + "\" not found."), false);
            return 0;
        }

        Map<String, Map<String, CanvasDataStore.CanvasEntry>> data = CanvasDataStore.load(source.getServer());
        String playerKey = player.getStringUUID();
        Map<String, CanvasDataStore.CanvasEntry> images = data.get(playerKey);

        if (images == null || !images.containsKey(name)) {
            source.sendSuccess(() -> Component.literal("No image found with name \"" + name + "\"."), false);
            return 0;
        }

        CanvasDataStore.CanvasEntry entry = images.get(name);
        new Thread(() -> {
            try {
                java.awt.image.BufferedImage image = ImageProcessor.downloadImage(entry.url());
                java.awt.image.BufferedImage resized = ImageProcessor.resizeImage(image, entry.width() * 128, entry.height() * 128);
                java.awt.image.BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, entry.width(), entry.height());
                byte[][] mapColors = new byte[sections.length][];
                for (int i = 0; i < sections.length; i++) {
                    mapColors[i] = MapCreator.imageToMapColors(sections[i], false);
                }

                source.getServer().execute(() -> {
                    try {
                        net.minecraft.server.level.ServerLevel world = source.getLevel();
                        if (entry.single()) {
                            int[] mapIds = new int[sections.length];
                            for (int i = 0; i < sections.length; i++) {
                                net.minecraft.world.item.ItemStack mapStack = MapCreator.createMapFromColors(mapColors[i], world);
                                mapIds[i] = mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID).id();
                            }
                            net.minecraft.world.item.ItemStack posterMap = MapCreator.createPosterMap(mapIds, entry.width(), entry.height(), world, false);
                            target.getInventory().add(posterMap);
                            for (int i = 0; i < mapIds.length; i++) {
                                MapCreator.syncMapToPlayer((net.minecraft.server.level.ServerPlayer) target,
                                        new net.minecraft.world.level.saveddata.maps.MapId(mapIds[i]), mapColors[i]);
                            }
                        } else {
                            for (int i = 0; i < mapColors.length; i++) {
                                net.minecraft.world.item.ItemStack mapStack = MapCreator.createMapFromColors(mapColors[i], world);
                                net.minecraft.world.level.saveddata.maps.MapId mapId = mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID);
                                target.getInventory().add(mapStack);
                                MapCreator.syncMapToPlayer((net.minecraft.server.level.ServerPlayer) target,
                                        mapId, mapColors[i]);
                            }
                        }
                        source.sendSuccess(() -> Component.literal("Gave \"" + name + "\" to " + targetName + "!"), false);
                    } catch (Exception e) {
                        source.sendSuccess(() -> Component.literal("Failed: " + e.getMessage()), false);
                    }
                });
            } catch (Exception e) {
                source.sendSuccess(() -> Component.literal("Failed to give image: " + e.getMessage()), false);
            }
        }).start();
        return 1;
    }
}
