package com.lazyalienserver.schemcheck;

import com.lazyalienserver.schemcheck.client.WsManager;
import com.lazyalienserver.schemcheck.command.SchemCheckCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

public class Schemcheck implements ModInitializer {

    @Override
    public void onInitialize() {
        WsManager.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> SchemCheckCommand.register(dispatcher));
    }
}
