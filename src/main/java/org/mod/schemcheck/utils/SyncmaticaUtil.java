package org.mod.schemcheck.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SyncmaticaUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemCheck-SyncmaticaUtil");

    private SyncmaticaUtil() { }

    private static final Path PLACEMENTS_FILE = FabricLoader.getInstance().getConfigDir().resolve("syncmatica/placements.json");
    private static final Gson GSON = new Gson();

    public static class PlacementInfo {
        public String fileName;
        public String hash;
        public String dimension;
        public BlockPos origin;
        public BlockRotation rotation;
        public BlockMirror mirror;

        public PlacementInfo(){}
    }

    private static PlacementInfo parsePlacementFromJson(JsonObject placement) {
        PlacementInfo info = new PlacementInfo();
        info.fileName = placement.has("file_name") ? placement.get("file_name").getAsString() : "unknown";
        info.hash = placement.has("hash") ? placement.get("hash").getAsString() : "";
        info.dimension = placement.getAsJsonObject("origin").get("dimension").getAsString();

        JsonArray posArray = placement.getAsJsonObject("origin").getAsJsonArray("position");
        info.origin = new BlockPos(posArray.get(0).getAsInt(), posArray.get(1).getAsInt(), posArray.get(2).getAsInt());

        String rot = placement.has("rotation") ? placement.get("rotation").getAsString() : "NONE";
        info.rotation = switch (rot) {
            case "CLOCKWISE_90" -> BlockRotation.CLOCKWISE_90;
            case "CLOCKWISE_180" -> BlockRotation.CLOCKWISE_180;
            case "COUNTERCLOCKWISE_90" -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };

        String mir = placement.has("mirror") ? placement.get("mirror").getAsString() : "NONE";
        info.mirror = switch (mir) {
            case "LEFT_RIGHT" -> BlockMirror.LEFT_RIGHT;
            case "FRONT_BACK" -> BlockMirror.FRONT_BACK;
            default -> BlockMirror.NONE;
        };
        return info;
    }

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
            LOGGER.error("获取所有投影名称失败", e);
        }
        return names;
    }

    public static PlacementInfo getPlacement(String targetFileName) {
        if (!Files.exists(PLACEMENTS_FILE)) return null;
        try (FileReader reader = new FileReader(PLACEMENTS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (JsonElement elem : root.getAsJsonArray("placements")) {
                JsonObject placement = elem.getAsJsonObject();
                if (placement.get("file_name").getAsString().equalsIgnoreCase(targetFileName)) {
                    return parsePlacementFromJson(placement);
                }
            }
        } catch (Exception e) {
            LOGGER.error("按文件名获取投影失败: {}", targetFileName, e);
        }
        return null;
    }

    public static PlacementInfo getPlacementByUUID(String targetUuid) {
        if (!Files.exists(PLACEMENTS_FILE)) return null;
        try (FileReader reader = new FileReader(PLACEMENTS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (JsonElement elem : root.getAsJsonArray("placements")) {
                JsonObject placement = elem.getAsJsonObject();
                String hash = placement.has("hash") ? placement.get("hash").getAsString() : "";
                String id = placement.has("id") ? placement.get("id").getAsString() : "";
                if (targetUuid.equalsIgnoreCase(hash) || targetUuid.equalsIgnoreCase(id)) {
                    return parsePlacementFromJson(placement);
                }
            }
        } catch (Exception e) {
            LOGGER.error("按 UUID 获取投影失败: {}", targetUuid, e);
        }
        return null;
    }
}