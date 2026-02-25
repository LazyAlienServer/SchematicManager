package org.mod.schemcheck;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import org.mod.schemcheck.client.WsClient;
import org.mod.schemcheck.command.SchemCheckCommand;

import java.net.URI;
import java.net.URISyntaxException;

public class Schemcheck implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            SchemCheckCommand.register(dispatcher);
        });
        try {
            WsClient client = new WsClient(new URI("ws://localhost:8082/ws"));
                client.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }


    }
}
