package org.mod.schemcheck.logic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ProgressCalculator {
    public static Result calculate(World world, NbtCompound schematicNbt, BlockPos placementOrigin, BlockRotation rotation) {
        int total = 0;
        int correct = 0;

        NbtCompound regions = schematicNbt.getCompound("Regions");
        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName);

            // 获取该区域相对于投影原点的初始偏移量
            NbtCompound posNbt = region.getCompound("Position");
            BlockPos regionOffset = new BlockPos(posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z"));

            // 将旋转参数一起传进去
            var pairs = BlockDataSampler.sampleRegion(world, region, placementOrigin, regionOffset, rotation);

            for (var pair : pairs) {
                total++;
                if (Comparer.isMatch(pair.schemState(), pair.worldState())) {
                    correct++;
                }
            }
        }
        return new Result(total, correct);
    }

    public record Result(int total, int correct) {}
}