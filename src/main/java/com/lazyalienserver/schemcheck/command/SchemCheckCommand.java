package com.lazyalienserver.schemcheck.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import org.jetbrains.annotations.UnknownNullability;
import com.lazyalienserver.schemcheck.logic.MaterialChecker;
import com.lazyalienserver.schemcheck.logic.ProgressCalculator;
import com.lazyalienserver.schemcheck.utils.SchematicLoader;
import com.lazyalienserver.schemcheck.utils.SyncmaticaUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

public class SchemCheckCommand {
    private SchemCheckCommand(){
        //INOP
    }
    public static void register(@UnknownNullability CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("schemcheck")
                .then(CommandManager.argument("file", StringArgumentType.string())
                        // 从 placements.json 自动补全文件名
                        .suggests((context, builder) -> {
                            // 1. 获取玩家当前正在输入的文本
                            String input = builder.getRemaining();

                            // 2. 如果玩家已经输入了前置双引号，我们去掉它再做匹配，否则匹配不到
                            String matchInput = input.startsWith("\"") ? input.substring(1).toLowerCase() : input.toLowerCase();

                            SyncmaticaUtil.getAllPlacementNames().stream()
                                    .filter(name -> name.toLowerCase().contains(matchInput))
                                    .forEach(name -> {
                                        // 3. 如果真实文件名中包含空格，补全时必须用双引号包起来
                                        if (name.contains(" ") || name.contains("　") || !name.matches("^[a-zA-Z]*$")) {
                                            builder.suggest("\"" + name + "\"");
                                        } else {
                                            builder.suggest(name);
                                        }
                                    });
                            return builder.buildFuture();
                        })

                        // 1. 自动查询进度分支：/schemcheck [文件名]
                        .executes(SchemCheckCommand::executeAutoProgress)

                        // 2. 自动查询材料分支：/schemcheck [文件名] [点1] [点2]
                        .then(CommandManager.argument("mat_pos1", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("mat_pos2", BlockPosArgumentType.blockPos())
                                        .executes(context -> executeAutoMaterial(context, true))

                                        // 可选：是否计入已搭建的材料
                                        .then(CommandManager.argument("include_built", BoolArgumentType.bool())
                                                .executes(context -> executeAutoMaterial(context, BoolArgumentType.getBool(context, "include_built")))
                                        )
                                )
                        )
                )
        );
    }

    // ========= 自动进度校验 =========
    private static int executeAutoProgress(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String fileName = StringArgumentType.getString(context, "file");

        SyncmaticaUtil.PlacementInfo info = SyncmaticaUtil.getPlacement(fileName);
        if (info == null) {
            source.sendError(new LiteralText("未在 Syncmatica 中找到该投影的放置信息: " + fileName));
            return 0;
        }

        try {
            // 解析投影所在的正确维度 (例如从主世界查下界的投影)
            ServerWorld targetWorld = getWorldFromDimension(source, info.dimension);
            if (targetWorld == null) {
                source.sendError(new LiteralText("无法解析目标维度: " + info.dimension));
                return 0;
            }

            NbtCompound schematicNbt = loadSchematicByHash(source, info.hash);
            if (schematicNbt == null) return 0;

            ProgressCalculator.Result result = ProgressCalculator.calculate(targetWorld, schematicNbt, info.origin, info.rotation, info.mirror);

            LiteralText fileNameText = new LiteralText("文件: " + info.fileName);
            LiteralText progressText = new LiteralText("\n进度: ");
            double percent = (result.total() == 0) ? 0.0 : (result.correct() * 100.0 / result.total());
            progressText.append(String.format("%.2f%%", percent));

            source.sendFeedback(fileNameText, false);
            source.sendFeedback(progressText, false);
        } catch (Exception e) {
            source.sendError(new LiteralText("Error: " + e.getMessage()));
        }
        return 1;
    }

    // ========= 自动材料统计 =========
    private static int executeAutoMaterial(CommandContext<ServerCommandSource> context, boolean includeBuilt) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        String fileName = StringArgumentType.getString(context, "file");
        BlockPos matPos1 = BlockPosArgumentType.getBlockPos(context, "mat_pos1");
        BlockPos matPos2 = BlockPosArgumentType.getBlockPos(context, "mat_pos2");

        SyncmaticaUtil.PlacementInfo info = SyncmaticaUtil.getPlacement(fileName);
        if (info == null) {
            source.sendError(new LiteralText("未在 Syncmatica 中找到该投影的放置信息: " + fileName));
            return 0;
        }

        try {
            ServerWorld targetWorld = getWorldFromDimension(source, info.dimension);
            if (targetWorld == null) return 0;

            NbtCompound schematicNbt = loadSchematicByHash(source, info.hash);
            if (schematicNbt == null) return 0;

            // 调用材料检查逻辑 (使用当前的 source.getWorld() 作为箱子读取的维度，投影坐标则使用 targetWorld)
            // 假设材料箱子在玩家当前维度，投影也在玩家当前维度，这里传入 source.getWorld()
            Map<Item, Integer> missingMaterials = MaterialChecker.calculateMissingMaterials(
                    source.getWorld(), schematicNbt, info.origin, info.rotation, info.mirror, matPos1, matPos2, includeBuilt
            );

            source.sendFeedback(new LiteralText("\n§e--- " + info.fileName + " 材料缺口 ---"), false);
            if (missingMaterials.isEmpty()) {
                source.sendFeedback(new LiteralText("§a材料已全部备齐！(或已搭建完毕)"), false);
            } else {
                for (Map.Entry<Item, Integer> entry : missingMaterials.entrySet()) {
                    MutableText text = new LiteralText("§c" + entry.getValue() + "x ")
                            .append(new TranslatableText(entry.getKey().getTranslationKey()));
                    source.sendFeedback(text, false);
                }
            }
        } catch (Exception e) {
            source.sendError(new LiteralText("Error: " + e.getMessage()));
        }
        return 1;
    }

    // ========= 辅助方法 =========

    // 从 UUID hash 读取物理文件
    private static NbtCompound loadSchematicByHash(ServerCommandSource source, String hash) {
        Path syncmaticsDir = FabricLoader.getInstance().getGameDir().resolve("syncmatics");
        File file = syncmaticsDir.resolve(hash + ".litematic").toFile();
        if (!file.exists()) {
            source.sendError(new LiteralText("找不到文件: " + hash + ".litematic"));
            return null;
        }
        NbtCompound schematicNbt = SchematicLoader.load(file);
        if (schematicNbt == null) {
            source.sendError(new LiteralText("无法加载投影数据"));
        }
        return schematicNbt;
    }

    // 将 dimension 字符串转为对应的 ServerWorld (跨维度查询)
    private static ServerWorld getWorldFromDimension(ServerCommandSource source, String dimensionStr) {
        RegistryKey<net.minecraft.world.World> dimensionKey = RegistryKey.of(Registry.WORLD_KEY, new Identifier(dimensionStr));
        return source.getServer().getWorld(dimensionKey);
    }
}