package com.lazyalienserver.schemcheck.logic;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class MaterialChecker {

    public static Map<Item, Integer> calculateMissingMaterials(
            World world, NbtCompound schematicNbt, BlockPos placementOrigin, BlockRotation rotation, BlockMirror mirror,
            BlockPos matPos1, BlockPos matPos2, boolean includeBuilt) {

        Map<Item, Integer> required = new HashMap<>();
        Map<Item, Integer> available = new HashMap<>();

        NbtCompound regions = schematicNbt.getCompound("Regions");
        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName);
            NbtCompound posNbt = region.getCompound("Position");
            BlockPos regionOffset = new BlockPos(posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z"));

            var pairs = BlockDataSampler.sampleRegion(world, region, placementOrigin, regionOffset, rotation, mirror);

            for (var pair : pairs) {
                Block requiredBlock = pair.schemState().getBlock();
                Item requiredItem = requiredBlock.asItem();
                if (requiredBlock == Blocks.WATER) {
                    requiredItem = Items.WATER_BUCKET;
                } else if (requiredBlock == Blocks.LAVA) {
                    requiredItem = Items.LAVA_BUCKET;
                }

                if (requiredItem == Items.AIR) continue;

                // 粗略防止双层方块（如门、床、高花）被重复计算两次需求
                String stateStr = pair.schemState().toString();
                if (stateStr.contains("half=upper") || stateStr.contains("part=head")) {
                    continue;
                }

                // 记录所需
                required.put(requiredItem, required.getOrDefault(requiredItem, 0) + 1);

                // 如果选项开启，且该方块已经正确放置在世界中，则计入“已有材料”
                if (includeBuilt && Comparer.isMatch(pair.schemState(), pair.worldState())) {
                    available.put(requiredItem, available.getOrDefault(requiredItem, 0) + 1);
                }
            }
        }

        // 2. 扫描材料区内的所有容器 (箱子、漏斗、潜影盒等)
        int minX = Math.min(matPos1.getX(), matPos2.getX());
        int minY = Math.min(matPos1.getY(), matPos2.getY());
        int minZ = Math.min(matPos1.getZ(), matPos2.getZ());
        int maxX = Math.max(matPos1.getX(), matPos2.getX());
        int maxY = Math.max(matPos1.getY(), matPos2.getY());
        int maxZ = Math.max(matPos1.getZ(), matPos2.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos currentPos = new BlockPos(x, y, z);
                    BlockEntity be = world.getBlockEntity(currentPos);

                    // 如果这个方块实体是容器
                    if (be instanceof Inventory inv) {
                        for (int i = 0; i < inv.size(); i++) {
                            var stack = inv.getStack(i);
                            if (!stack.isEmpty()) {
                                Item item = stack.getItem();
                                available.put(item, available.getOrDefault(item, 0) + stack.getCount());
                            }
                        }
                    }
                }
            }
        }

        // 3. 对比计算缺少的材料
        Map<Item, Integer> missing = new HashMap<>();
        for (Map.Entry<Item, Integer> req : required.entrySet()) {
            Item item = req.getKey();
            int reqCount = req.getValue();
            int availCount = available.getOrDefault(item, 0);

            if (availCount < reqCount) {
                missing.put(item, reqCount - availCount);
            }
        }

        return missing;
    }
}