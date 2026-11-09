package com.washed.mapcanvas.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    private static final Set<Integer> MAPCANVAS_TRACKED_MAP_IDS = ConcurrentHashMap.newKeySet();

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void mapcanvas$refreshInventoryMapSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        refreshInventoryMaps();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void mapcanvas$refreshInventoryMapContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        refreshInventoryMaps();
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void mapcanvas$refreshItemFrameMap(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        Entity entity = level.getEntity(packet.id());
        if (entity instanceof ItemFrame frame) refreshMap(frame.getItem(), "item-frame");
    }

    @Inject(method = "handleMapItemData", at = @At("TAIL"))
    private void mapcanvas$debugAndRefreshMapData(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        int rawId = packet.mapId().id();
        if (!MAPCANVAS_TRACKED_MAP_IDS.contains(rawId)) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        MapItemSavedData data = level == null ? null : level.getMapData(packet.mapId());
        if (data == null) {
            com.washed.mapcanvas.MapCanvasDebug.log("client-map-packet id=" + rawId + " result=no-client-map-data");
            return;
        }
        minecraft.getMapTextureManager().update(packet.mapId(), data);
        com.washed.mapcanvas.MapCanvasDebug.log("client-map-packet id=" + rawId + " refreshed=true "
                + com.washed.mapcanvas.MapCanvasDebug.pixels(data.colors));
    }

    private static void refreshInventoryMaps() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            refreshMap(minecraft.player.getInventory().getItem(slot), "inventory");
        }
    }

    private static void refreshMap(ItemStack stack, String source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || stack.isEmpty()) return;
        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null) return;
        boolean marked = false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            marked = tag.getBoolean("mapcanvasImage").orElse(false)
                    || tag.getBoolean("isPosterSection").orElse(false)
                    || tag.getBoolean("isPoster").orElse(false);
            if (tag.contains("isPoster")) {
                for (int id : tag.getIntArray("mapIds").orElse(new int[0])) track(id, source);
            }
        }
        marked |= MAPCANVAS_TRACKED_MAP_IDS.contains(mapId.id());
        if (!marked) return;
        track(mapId.id(), source);
        MapItemSavedData mapData = minecraft.level.getMapData(mapId);
        if (mapData != null) {
            minecraft.getMapTextureManager().update(mapId, mapData);
            com.washed.mapcanvas.MapCanvasDebug.log("client-map-item source=" + source + " id=" + mapId.id()
                    + " refreshed=true " + com.washed.mapcanvas.MapCanvasDebug.pixels(mapData.colors));
        } else {
            com.washed.mapcanvas.MapCanvasDebug.log("client-map-item source=" + source + " id=" + mapId.id()
                    + " refreshed=false reason=no-client-map-data");
        }
    }

    private static void track(int mapId, String source) {
        if (MAPCANVAS_TRACKED_MAP_IDS.add(mapId)) {
            com.washed.mapcanvas.MapCanvasDebug.log("client-map-tracked source=" + source + " id=" + mapId);
        }
    }
}
