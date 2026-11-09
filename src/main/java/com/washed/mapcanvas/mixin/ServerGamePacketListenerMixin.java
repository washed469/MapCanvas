package com.washed.mapcanvas.mixin;

import com.washed.mapcanvas.MapCanvasDebug;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attributes every server-to-client map update for image maps to its caller. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void mapcanvas$traceOutgoingMapPackets(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundMapItemDataPacket mapPacket)) return;
        int mapId = mapPacket.mapId().id();
        if (!MapCanvasDebug.isTrackedMapId(mapId)) return;
        MapCanvasDebug.log("server-map-packet-out id=" + mapId + " caller=" + MapCanvasDebug.callerTrace());
    }
}
