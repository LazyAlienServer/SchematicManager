package org.mod.schemcheck.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SyncmaticaUtil {
    private SyncmaticaUtil() {
        /* This utility class should not be instantiated */
    }

    // 指向 config/syncmatica/placements.json
    private static final Path PLACEMENTS_FILE = FabricLoader.getInstance().getConfigDir().resolve("syncmatica/placements.json");
    private static final Gson GSON = new Gson();

    // 数据包装类
    public static class PlacementInfo {
        public String fileName;
        public String hash; // 对应的 UUID 文件名
        public String dimension; // 维度 (如 minecraft:the_nether)
        public BlockPos origin; // 起始坐标
        public BlockRotation rotation; // 旋转角度

        public PlacementInfo(){}
        public PlacementInfo(BlockPos origin, String fileName, String hash, String dimension, BlockRotation rotation) {
            this.origin = origin;
            this.fileName = fileName;
            this.hash = hash;
            this.dimension = dimension;
            this.rotation = rotation;
        }
    }

    // 获取所有已放置的投影名字 (用于指令补全)
    public static List<String> getAllPlacementNames() {
        List<String> names = new ArrayList<>();
        if (!Files.exists(PLACEMENTS_FILE)) return names;

        try (FileReader reader = new FileReader(PLACEMENTS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonArray placements = root.getAsJsonArray("placements");
            for (JsonElement elem : placements) {
                names.add(elem.getAsJsonObject().get("file_name").getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return names;
    }

    // 根据名字获取详细放置信息
    public static PlacementInfo getPlacement(String targetFileName) {
        if (!Files.exists(PLACEMENTS_FILE)) return null;

        try (FileReader reader = new FileReader(PLACEMENTS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonArray placements = root.getAsJsonArray("placements");

            for (JsonElement elem : placements) {
                JsonObject placement = elem.getAsJsonObject();
                String fileName = placement.get("file_name").getAsString();

                // 忽略大小写匹配文件名
                if (fileName.equalsIgnoreCase(targetFileName)) {
                    PlacementInfo info = new PlacementInfo();
                    info.fileName = fileName;
                    info.hash = placement.get("hash").getAsString();

                    // 解析原点
                    JsonObject originObj = placement.getAsJsonObject("origin");
                    info.dimension = originObj.get("dimension").getAsString();
                    JsonArray posArray = originObj.getAsJsonArray("position");
                    info.origin = new BlockPos(posArray.get(0).getAsInt(), posArray.get(1).getAsInt(), posArray.get(2).getAsInt());

                    // 解析旋转
                    String rot = placement.has("rotation") ? placement.get("rotation").getAsString() : "NONE";
                    info.rotation = switch (rot) {
                        case "CLOCKWISE_90" -> BlockRotation.CLOCKWISE_90;
                        case "CLOCKWISE_180" -> BlockRotation.CLOCKWISE_180;
                        case "COUNTERCLOCKWISE_90" -> BlockRotation.COUNTERCLOCKWISE_90;
                        default -> BlockRotation.NONE;
                    };
                    return info;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}