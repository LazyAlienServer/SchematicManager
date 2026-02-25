package org.mod.schemcheck.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.mod.schemcheck.data.DataMsg;
import org.mod.schemcheck.data.ResultMsg;
import org.mod.schemcheck.data.WsProtocol;
import org.mod.schemcheck.logic.MaterialChecker;
import org.mod.schemcheck.logic.ProgressCalculator;
import org.mod.schemcheck.utils.SchematicLoader;
import org.mod.schemcheck.utils.SyncmaticaUtil;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public class WsClient extends WebSocketClient {
    private final ObjectMapper mapper = new ObjectMapper();

    public WsClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("WebSocket 已连接");
    }

    @Override
    public void onMessage(String message) {
        try {
            WsProtocol req = mapper.readValue(message, WsProtocol.class);

            MinecraftServer server = (MinecraftServer) FabricLoader.getInstance().getGameInstance();

            WsProtocol finalReq = req;
            server.execute(() -> {
                try {


                    if ("GET_PROGRESS_TASK".equals(finalReq.action)) {
                        //-----------------------
                        DataMsg data = mapper.readValue(req.data, DataMsg.class);
                        SyncmaticaUtil.PlacementInfo info = SyncmaticaUtil.getPlacement(data.filename);
                        if (info == null) return;

                        RegistryKey<net.minecraft.world.World> dimKey = RegistryKey.of(Registry.WORLD_KEY, new Identifier(info.dimension));
                        ServerWorld targetWorld = server.getWorld(dimKey);
                        if (targetWorld == null) return;

                        NbtCompound schematicNbt = loadSchematicByHash(info.hash);
                        if (schematicNbt == null) return;
                        //-----------------------

                        ProgressCalculator.Result result = ProgressCalculator.calculate(
                                targetWorld, schematicNbt, info.origin, info.rotation
                        );

                        ResultMsg res = new ResultMsg();
                        res.correct = result.correct();
                        res.total = result.total();

                        String resultJson = mapper.writeValueAsString(res);
                        this.send(mapper.writeValueAsString(new WsProtocol(finalReq.id, "RESULT", resultJson)));
                    }
                    else if ("GET_MATERIAL_TASK".equals(finalReq.action)) {
                        //-----------------------
                        DataMsg data = mapper.readValue(req.data, DataMsg.class);
                        SyncmaticaUtil.PlacementInfo info = SyncmaticaUtil.getPlacement(data.filename);
                        if (info == null) return;

                        RegistryKey<net.minecraft.world.World> dimKey = RegistryKey.of(Registry.WORLD_KEY, new Identifier(info.dimension));
                        ServerWorld targetWorld = server.getWorld(dimKey);
                        if (targetWorld == null) return;

                        NbtCompound schematicNbt = loadSchematicByHash(info.hash);
                        if (schematicNbt == null) return;
                        //-----------------------
                        BlockPos p1 = new BlockPos(data.mx1, data.my1, data.mz1);
                        BlockPos p2 = new BlockPos(data.mx2, data.my2, data.mz2);

                        Map<Item, Integer> missing = MaterialChecker.calculateMissingMaterials(
                                targetWorld, schematicNbt, info.origin, info.rotation, p1, p2, data.includeBuilt
                        );

                        Map<String, Integer> missingStrMap = new HashMap<>();
                        for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
                            missingStrMap.put(Registry.ITEM.getId(entry.getKey()).toString(), entry.getValue());
                        }

                        String resultJson = mapper.writeValueAsString(missingStrMap);
                        this.send(mapper.writeValueAsString(new WsProtocol(finalReq.id, "MATERIAL_RESULT", resultJson)));
                    }else if ("GET_SCHEM_FILES".equals(finalReq.action)) {
                        System.out.println("Running");
                        List<SyncmaticaUtil.PlacementInfo> result = new ArrayList<>();
                        for (String name : SyncmaticaUtil.getAllPlacementNames()){
                            result.add(SyncmaticaUtil.getPlacement(name));
                        }

                        String resultJson = mapper.writeValueAsString(result);
                        this.send(mapper.writeValueAsString(new WsProtocol(finalReq.id, "SCHEMAITC_FILES_INFO", resultJson)));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private NbtCompound loadSchematicByHash(String hash) {
        Path syncmaticsDir = FabricLoader.getInstance().getGameDir().resolve("syncmatics");
        File file = syncmaticsDir.resolve(hash + ".litematic").toFile();
        return file.exists() ? SchematicLoader.load(file) : null;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("3秒后尝试重连");
        Thread reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(3000);
                this.reconnect();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        });
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    @Override
    public void onError(Exception ex) {}
}