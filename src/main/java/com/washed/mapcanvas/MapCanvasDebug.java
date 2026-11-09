package com.washed.mapcanvas;

import java.util.zip.CRC32;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Small, URL-free diagnostics for tracing image map data from server to client. */
public final class MapCanvasDebug {
    private MapCanvasDebug() {}

    private static final Set<Integer> TRACKED_MAP_IDS = ConcurrentHashMap.newKeySet();

    public static void trackMapId(int mapId) {
        TRACKED_MAP_IDS.add(mapId);
    }

    public static boolean isTrackedMapId(int mapId) {
        return TRACKED_MAP_IDS.contains(mapId);
    }

    /** Returns short caller names so outgoing map updates can be attributed without logging URLs or player data. */
    public static String callerTrace() {
        return StackWalker.getInstance().walk(frames -> frames
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                .filter(name -> !name.startsWith("java.lang.Thread#")
                        && !name.contains("ServerGamePacketListenerMixin")
                        && !name.startsWith("com.washed.mapcanvas.MapCanvasDebug#"))
                .limit(5)
                .collect(java.util.stream.Collectors.joining(" <- ")));
    }

    public static void log(String message) {
        System.out.println("[MapCanvas DEBUG] " + message);
    }

    public static String pixels(byte[] colors) {
        int nonZero = 0;
        CRC32 crc = new CRC32();
        crc.update(colors);
        for (byte color : colors) if (color != 0) nonZero++;
        return "pixels=" + nonZero + "/" + colors.length + ",crc=" + Long.toHexString(crc.getValue());
    }
}
