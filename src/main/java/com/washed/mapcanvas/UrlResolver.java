package com.washed.mapcanvas;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlResolver {

    public static String resolve(String url) throws Exception {
        url = url.trim();
        if (url.startsWith("<") && url.endsWith(">")) url = url.substring(1, url.length() - 1);
        url = normalizeDiscordAttachment(url);
        if (isDirectMedia(url)) return url;

        if (isDiscordPageUrl(url)) {
            throw new Exception(discordGifInstructions());
        }

        if (url.contains("klipy.com")) return resolveKlipy(url);
        if (url.contains("tenor.com")) return resolveTenor(url);
        if (url.contains("giphy.com")) return resolveGiphy(url);

        return url;
    }

    public static boolean isDiscordUrl(String url) {
        try {
            String cleaned = url.trim();
            if (cleaned.startsWith("<") && cleaned.endsWith(">")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            String host = URI.create(cleaned).getHost();
            if (host == null) return false;
            String lowerHost = host.toLowerCase();
            return lowerHost.equals("discord.com") || lowerHost.endsWith(".discord.com")
                    || lowerHost.equals("discordapp.com") || lowerHost.endsWith(".discordapp.com")
                    || lowerHost.equals("discordapp.net") || lowerHost.endsWith(".discordapp.net");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String discordGifInstructions() {
        return "Discord GIF tip: Open the GIF itself in your browser, right-click the image, and choose Copy image address. "
                + "Paste that direct image URL here; Discord message/share links are not image URLs.";
    }

    private static boolean isDirectMedia(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
            String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase();
            return hasImageExtension(path) || query.matches(".*(?:^|&)format=(?:gif|png|jpe?g|webp)(?:&.*|$).*");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String normalizeDiscordAttachment(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (host == null || path == null || !path.startsWith("/attachments/")) {
                return url;
            }

            String lowerHost = host.toLowerCase();
            boolean mediaProxy = lowerHost.equals("media.discordapp.net");
            boolean discordAttachmentHost = mediaProxy || lowerHost.equals("cdn.discordapp.com")
                    || lowerHost.equals("discord.com") || lowerHost.equals("discordapp.com");
            if (!discordAttachmentHost) return url;

            // Discord's media proxy can transcode an animated GIF to WebP.
            // The CDN attachment URL serves the original file format instead.
            String query = uri.getRawQuery();
            if (mediaProxy && query != null) {
                query = Arrays.stream(query.split("&"))
                        .filter(parameter -> {
                            String key = parameter.split("=", 2)[0].toLowerCase();
                            return !key.equals("format") && !key.equals("width")
                                    && !key.equals("height") && !key.equals("quality");
                        })
                        .collect(Collectors.joining("&"));
            }
            return uri.getScheme() + "://cdn.discordapp.com" + path
                    + (query == null || query.isEmpty() ? "" : "?" + query);
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static boolean isDiscordPageUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            String lowerHost = host.toLowerCase();
            boolean webHost = lowerHost.equals("discord.com") || lowerHost.equals("www.discord.com")
                    || lowerHost.equals("discordapp.com") || lowerHost.equals("www.discordapp.com");
            return webHost && (uri.getPath() == null || !uri.getPath().startsWith("/attachments/"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String resolveKlipy(String url) throws Exception {
        String html = fetchHtml(url);
        String gifUrl = findKlipyGifUrl(html);
        if (gifUrl != null) return gifUrl;

        String mediaUrl = findMediaUrl(html);
        if (mediaUrl != null && GifProcessor.isGifUrl(mediaUrl)) return mediaUrl;
        if (mediaUrl != null) {
            throw new Exception("Klipy returned a still-image preview instead of the GIF. Copy the GIF's direct media address.");
        }
        throw new Exception("Could not find a direct GIF URL on that Klipy page.");
    }

    private static String findKlipyGifUrl(String html) {
        // KLIPY pages can embed their original GIF in escaped JSON while the
        // Open Graph image points to a static thumbnail. Normalize JSON URL
        // escapes, then search specifically for GIF media before considering
        // generic page preview metadata.
        String normalized = html.replace("\\u002F", "/")
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
        Pattern[] patterns = {
                Pattern.compile("(?i)\\\"(?:gif|gif_url|original_gif|source_gif)\\\"\\s*:\\s*\\\"(https?://[^\\\"]+?\\.gif(?:\\?[^\\\"]*)?)\\\""),
                Pattern.compile("(?i)(https?://static[0-9]*\\.klipy\\.(?:com|co)/[^\\s\\\"'<>]+?\\.gif(?:\\?[^\\s\\\"'<>]*)?)"),
                Pattern.compile("(?i)(https?://[^\\s\\\"'<>]+?\\.gif(?:\\?[^\\s\\\"'<>]*)?)")
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.find()) return matcher.group(1).replace("&amp;", "&");
        }
        return null;
    }

    private static String resolveTenor(String url) throws Exception {
        String html = fetchHtml(url);
        Pattern[] patterns = {
                Pattern.compile("\"(https://media\\.tenor\\.com/[^\"]+\\.gif)\""),
                Pattern.compile("content=\"(https://[^\"]+\\.gif)\""),
                Pattern.compile("(https://media\\.tenor\\.com/[^\\s\"']+\\.gif)")
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(html);
            if (m.find()) return m.group(1);
        }
        throw new Exception("Could not extract GIF URL from Tenor page");
    }

    private static String resolveGiphy(String url) throws Exception {
        String html = fetchHtml(url);
        Pattern[] patterns = {
                Pattern.compile("\"(https://media[0-9]*\\.giphy\\.com/[^\"]+\\.gif)\""),
                Pattern.compile("content=\"(https://[^\"]+\\.gif)\""),
                Pattern.compile("(https://media[0-9]*\\.giphy\\.com/[^\\s\"']+\\.gif)")
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(html);
            if (m.find()) return m.group(1);
        }
        throw new Exception("Could not extract GIF URL from Giphy page");
    }

    private static String findMediaUrl(String html) {
        Pattern[] patterns = {
                Pattern.compile("(?i)<meta[^>]+(?:property|name)=\"(?:og:image|twitter:image)\"[^>]+content=\"([^\"]+)\""),
                Pattern.compile("(?i)<meta[^>]+content=\"([^\"]+)\"[^>]+(?:property|name)=\"(?:og:image|twitter:image)\""),
                Pattern.compile("(?i)(https?://[^\\s\"'<>]+\\.(?:gif|png|jpe?g|webp)(?:\\?[^\\s\"'<>]*)?)")
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) return matcher.group(1).replace("&amp;", "&");
        }
        return null;
    }

    private static boolean hasImageExtension(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".gif") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }

    private static String fetchHtml(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                if (sb.length() > 200000) break;
            }
        }
        return sb.toString();
    }
}
