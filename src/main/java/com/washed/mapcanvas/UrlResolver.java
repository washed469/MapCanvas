package com.washed.mapcanvas;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlResolver {

    public static String resolve(String url) throws Exception {
        if (isDirectMedia(url)) return url;

        if (url.contains("klipy.com")) return resolveKlipy(url);
        if (url.contains("tenor.com")) return resolveTenor(url);
        if (url.contains("giphy.com")) return resolveGiphy(url);

        return url;
    }

    private static boolean isDirectMedia(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".gif") || lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp");
    }

    private static String resolveKlipy(String url) throws Exception {
        throw new Exception("Klipy/Discord GIFs are not directly supported. Open the GIF in your browser, right click it and copy the image address/.gif URL instead.");
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