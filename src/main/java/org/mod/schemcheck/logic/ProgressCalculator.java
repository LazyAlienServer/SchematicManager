package org.mod.schemcheck.logic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ProgressCalculator {
    public static Result calculate(World world, NbtCompound schematicNbt, BlockPos placementOrigin, BlockRotation rotation, BlockMirror mirror) {
        int total = 0;
        int correct = 0;

        NbtCompound regions = schematicNbt.getCompound("Regions");
        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName);
            NbtCompound posNbt = region.getCompound("Position");
            BlockPos regionOffset = new BlockPos(posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z"));

            var pairs = BlockDataSampler.sampleRegion(world, region, placementOrigin, regionOffset, rotation, mirror);

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