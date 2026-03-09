package com.washed.mapcanvas;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class Mapcanvas implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CanvasCommand.register(dispatcher);
        });
    }
}