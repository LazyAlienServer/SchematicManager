package com.lazyalienserver.schemcheck.client;

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
import com.lazyalienserver.schemcheck.data.DataMsg;
import com.lazyalienserver.schemcheck.data.ResultMsg;
import com.lazyalienserver.schemcheck.data.WsProtocol;
import com.lazyalienserver.schemcheck.logic.MaterialChecker;
import com.lazyalienserver.schemcheck.logic.ProgressCalculator;
import com.lazyalienserver.schemcheck.utils.SchematicCache;
import com.lazyalienserver.schemcheck.utils.SyncmaticaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public class WsClient extends WebSocketClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemCheck-WsClient");

    private final ObjectMapper mapper = new ObjectMapper();

    public WsClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("WebSocket 已成功连接到服务器: {}", this.getURI());
    }

    private void sendError(String id, String action, String errorMsg) {
        try {
            Map<String, String> errMap = new HashMap<>();
            errMap.put("error", errorMsg);

            String responseAction = action;
            if ("GET_PROGRESS_TASK".equals(action)) responseAction = "RESULT";
            if ("GET_MATERIAL_TASK".equals(action)) responseAction = "MATERIAL_RESULT";

            this.send(mapper.writeValueAsString(new WsProtocol(id, responseAction, mapper.writeValueAsString(errMap))));
            LOGGER.warn("已向后端拦截并报告错误: {}", errorMsg);
        } catch (JsonProcessingException e) {
            LOGGER.error("发送错误信息时 JSON 序列化失败", e);
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            WsProtocol req = mapper.readValue(message, WsProtocol.class);

            // ================= 1. 获取投影文件列表 (修复 Jackson 序列化原生类报错) =================
            if ("GET_SCHEM_FILES".equals(req.action)) {
                LOGGER.info("执行 GET_SCHEM_FILES 请求");

                List<Map<String, Object>> result = new ArrayList<>();

                for (String name : SyncmaticaUtil.getAllPlacementNames()){
                    SyncmaticaUtil.PlacementInfo info = SyncmaticaUtil.getPlacement(name);
                    if (info != null) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("fileName", info.fileName);
                        map.put("hash", info.hash);
                        map.put("dimension", info.dimension);

                        // 将 BlockPos 拆解为普通的 int 数组
                        if (info.origin != null) {
                            map.put("origin", new int[]{info.origin.getX(), info.origin.getY(), info.origin.getZ()});
                        }

                        // 将枚举类型转换为普通字符串
                        if (info.rotation != null) {
                            map.put("rotation", info.rotation.name());
                        }
                        if (info.mirror != null) {
                            map.put("mirror", info.mirror.name());
                        }

                        result.add(map);
                    }
                }

                String resultJson = mapper.writeValueAsString(result);
                this.send(mapper.writeValueAsString(new WsProtocol(req.id, "SCHEMAITC_FILES_INFO", resultJson)));
                return;
            }

            // ================= 2. 解析公共参数与加载缓存 =================
            DataMsg data = mapper.readValue(req.data, DataMsg.class);

            SyncmaticaUtil.PlacementInfo tempInfo = SyncmaticaUtil.getPlacementByUUID(data.filename);
            if (tempInfo == null) tempInfo = SyncmaticaUtil.getPlacement(data.filename);

            if (tempInfo == null) {
                sendError(req.id, req.action, "未找到投影记录 (UUID或文件名不存在)");
                return;
            }

            NbtCompound schematicNbt = loadSchematicByHash(tempInfo.hash);
            if (schematicNbt == null) {
                sendError(req.id, req.action, "服务端 syncmatics 文件夹缺失原文件: " + tempInfo.hash + ".litematic");
                return;
            }

            MinecraftServer server = (MinecraftServer) FabricLoader.getInstance().getGameInstance();
            final SyncmaticaUtil.PlacementInfo finalInfo = tempInfo;

            // ================= 3. 进度查询 =================
            if ("GET_PROGRESS_TASK".equals(req.action)) {
                server.execute(() -> {
                    try {
                        RegistryKey<net.minecraft.world.World> dimKey = RegistryKey.of(Registry.WORLD_KEY, new Identifier(finalInfo.dimension));
                        ServerWorld targetWorld = server.getWorld(dimKey);
                        if (targetWorld == null) {
                            sendError(req.id, req.action, "服务端未加载投影所在的维度: " + finalInfo.dimension);
                            return;
                        }

                        ProgressCalculator.Result result = ProgressCalculator.calculate(
                                targetWorld, schematicNbt, finalInfo.origin, finalInfo.rotation, finalInfo.mirror
                        );

                        ResultMsg res = new ResultMsg();
                        res.correct = result.correct();
                        res.total = result.total();

                        this.send(mapper.writeValueAsString(new WsProtocol(req.id, "RESULT", mapper.writeValueAsString(res))));
                    } catch (Exception e) {
                        LOGGER.error("Mod主线程处理进度查询异常", e);
                        sendError(req.id, req.action, "Mod主线程处理进度异常: " + e.getMessage());
                    }
                });
            }
            // ================= 4. 材料查询 =================
            else if ("GET_MATERIAL_TASK".equals(req.action)) {
                server.execute(() -> {
                    try {
                        RegistryKey<net.minecraft.world.World> dimKey = RegistryKey.of(Registry.WORLD_KEY, new Identifier(finalInfo.dimension));
                        ServerWorld targetWorld = server.getWorld(dimKey);
                        if (targetWorld == null) {
                            sendError(req.id, req.action, "服务端未加载投影所在的维度: " + finalInfo.dimension);
                            return;
                        }

                        BlockPos p1 = new BlockPos(data.mx1, data.my1, data.mz1);
                        BlockPos p2 = new BlockPos(data.mx2, data.my2, data.mz2);

                        Map<Item, Integer> missing = MaterialChecker.calculateMissingMaterials(
                                targetWorld, schematicNbt, finalInfo.origin, finalInfo.rotation, finalInfo.mirror, p1, p2, data.includeBuilt
                        );

                        Map<String, Integer> missingStrMap = new HashMap<>();
                        for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
                            missingStrMap.put(Registry.ITEM.getId(entry.getKey()).toString(), entry.getValue());
                        }

                        this.send(mapper.writeValueAsString(new WsProtocol(req.id, "MATERIAL_RESULT", mapper.writeValueAsString(missingStrMap))));
                    } catch (Exception e) {
                        LOGGER.error("Mod主线程处理材料查询异常", e);
                        sendError(req.id, req.action, "Mod主线程处理材料异常: " + e.getMessage());
                    }
                });
            }

        } catch (JsonProcessingException e) {
            LOGGER.error("处理 WebSocket 消息时 JSON 语法错误", e);
        } catch (Exception e) {
            LOGGER.error("处理 WebSocket 消息时发生严重异常", e);
        }
    }

    private NbtCompound loadSchematicByHash(String hash) {
        Path syncmaticsDir = FabricLoader.getInstance().getGameDir().resolve("syncmatics");
        File file = syncmaticsDir.resolve(hash + ".litematic").toFile();
        return SchematicCache.getOrLoad(file, hash);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // 交由 WsManager 定时重连，这里只打印日志
        LOGGER.warn("WebSocket 连接已关闭 (Code: {}, Reason: {})。等待管理器自动重连...", code, reason);
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.error("WebSocket 发生底层错误", ex);
    }
}