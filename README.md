# MapCanvas

A Fabric mod for Minecraft 1.21.1 that lets players display images and animated GIFs on maps in item frames, directly from a URL.

> ⚠️ Required on both client and server.

---

## Features

- Display any image from a URL on a grid of item frames
- Animated GIF support with per-frame timing and optimized packet sending
- Poster map system — place and collect entire grids with a single item
- Named image library per player
- Economy system — costs empty maps to create canvases, free in creative mode
- Convert individual maps into a single poster map item
- Auto URL resolution for Tenor and Giphy page links

---

## Commands

| Command | Description |
|---|---|
| `/canvas add single <w> <h> <url>` | Gives individual maps for a WxH grid |
| `/canvas add poster <w> <h> <url>` | Gives 1 poster map that auto-fills a WxH grid |
| `/canvas name <n>` | Saves the last created image |
| `/canvas list` | Lists your saved images with clickable URLs |
| `/canvas reload <n>` | Re-downloads the image data for a saved image |
| `/canvas convert <n>` | Converts individual maps in inventory into 1 poster map |
| `/canvas delete <n>` | Deletes a saved image |
| `/canvas give <player> <n>` | Gives your saved image to another player |
| `/canvas help` | Shows all commands in-game |

---

## How It Works

### Single Mode
Resizes the image to fill the full WxH grid at full resolution. Each map tile gets a unique portion of the image. Gives individual map items — one per grid cell. Use `/canvas convert` to combine them into a poster map later.

### Poster Mode
Same full resolution as single mode but gives **1 poster map item** that auto-places all maps on the grid when right-clicked on any frame in the grid. Collect the whole grid back into 1 item by punching any map in the grid. Frames are not broken when collecting.

### GIF Support
Paste any direct `.gif` URL and the mod automatically processes it as an animated poster regardless of which subcommand you use. Animations play at up to 10fps and only send packets to players within 32 blocks. The animation pauses entirely if no players are within 48 blocks. All frames are pre-baked to Minecraft map colors at load time for maximum performance.

> ⚠️ Running many animated GIFs simultaneously on a server can cause lag, especially with large grids. Keep the number of active animated posters reasonable.

---

## Economy

Creating a canvas costs **empty maps** equal to the grid size (W × H):

- 1x1 = 1 map
- 2x2 = 4 maps
- 3x3 = 9 maps

Players in creative mode are not charged. Maps are refunded automatically if the download fails.

---

## Poster Map Usage

1. Run `/canvas add poster 3 3 <url>`
2. Build a 3x3 grid of item frames on a wall
3. Right-click any frame in the grid with the poster map — it auto-fills all frames
4. To collect, punch any map in the grid — all maps are removed and returned as 1 poster map item
5. Item frames are not broken when collecting

---

## GIF Tips

- Use direct `.gif` URLs from Giphy or Tenor
- For Tenor/Giphy page links, the mod will attempt to resolve the direct URL automatically
- Discord/Klipy GIF links are not supported — open the GIF in your browser, right click and copy the direct image address instead
- Smaller grids (1x1 to 3x3) perform much better than large ones (7x7+)

---

## Data Storage

- Named images saved to `<world>/mapcanvas_data.json`
- GIF frame data saved to `<world>/mapcanvas_gifs/`

---

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.18.4+
- Fabric API 0.141.3+1.21.1

---

## Building

```bash
./gradlew build
```

Output jar will be in `build/libs/`. Use the one without `-sources` in the name.

---

## Note

This project was made with the help of AI. I couldn't find any Fabric mod with these features, so I decided to build one myself.

---

## License

LGPL-3.0-only — see [LICENSE](LICENSE) for details.

---

## Links

- [Modrinth](https://modrinth.com/mod/mapcanvas)
- [GitHub](https://github.com/washed469/MapCanvas)
