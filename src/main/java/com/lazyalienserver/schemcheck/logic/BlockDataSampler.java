package com.lazyalienserver.schemcheck.logic;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class BlockDataSampler {
    public record BlockPair(BlockState schemState, BlockState worldState) {}

    // ⭐ 在参数列表中加入 BlockMirror mirror
    public static List<BlockPair> sampleRegion(World world, NbtCompound regionNbt, BlockPos placementOrigin, BlockPos regionOffset, BlockRotation rotation, BlockMirror mirror) {
        List<BlockPair> pairs = new ArrayList<>();

        NbtCompound size = regionNbt.getCompound("Size");
        int sx = size.getInt("x");
        int sy = size.getInt("y");
        int sz = size.getInt("z");
        int dx = Math.abs(sx);
        int dy = Math.abs(sy);
        int dz = Math.abs(sz);
        int offsetX = sx < 0 ? sx + 1 : 0;
        int offsetY = sy < 0 ? sy + 1 : 0;
        int offsetZ = sz < 0 ? sz + 1 : 0;

        NbtList paletteNbt = regionNbt.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteNbt.size(); i++) {
            palette.add(NbtHelper.toBlockState(paletteNbt.getCompound(i)));
        }

        int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] blockStates = regionNbt.getLongArray("BlockStates");
        long maxEntryValue = (1L << bits) - 1L;

        for (int y = 0; y < dy; y++) {
            for (int z = 0; z < dz; z++) {
                for (int x = 0; x < dx; x++) {
                    int index = (y * dz + z) * dx + x;
                    long val = getVal(index, bits, blockStates);
                    int paletteIndex = (int) (val & maxEntryValue);
                    if (paletteIndex >= palette.size()) paletteIndex = 0;

                    BlockState schemState = palette.get(paletteIndex);
                    if (schemState.isAir()) continue;

                    int unrotatedX = regionOffset.getX() + offsetX + x;
                    int unrotatedY = regionOffset.getY() + offsetY + y;
                    int unrotatedZ = regionOffset.getZ() + offsetZ + z;

                    int mirroredX = unrotatedX;
                    int mirroredZ = unrotatedZ;
                    if (mirror == BlockMirror.LEFT_RIGHT) {
                        mirroredX = -unrotatedX;
                    } else if (mirror == BlockMirror.FRONT_BACK) {
                        mirroredZ = -unrotatedZ;
                    }

                    int rotX = mirroredX;
                    int rotZ = mirroredZ;
                    switch (rotation) {
                        case CLOCKWISE_90 -> { rotX = -mirroredZ; rotZ = mirroredX; }
                        case CLOCKWISE_180 -> { rotX = -mirroredX; rotZ = -mirroredZ; }
                        case COUNTERCLOCKWISE_90 -> { rotX = mirroredZ; rotZ = -mirroredX; }
                        default -> {}
                    }

                    BlockPos worldPos = placementOrigin.add(rotX, unrotatedY, rotZ);

                    BlockState transformedSchemState = schemState.mirror(mirror).rotate(rotation);

                    pairs.add(new BlockPair(transformedSchemState, world.getBlockState(worldPos)));
                }
            }
        }
        return pairs;
    }

    private static long getVal(int index, int bits, long[] blockStates) {
        int startBit = index * bits;
        int startLong = startBit / 64;
        int bitOffset = startBit % 64;

        long val = 0;
        if (startLong < blockStates.length) {
            val = blockStates[startLong] >>> bitOffset;
            if (bitOffset + bits > 64 && startLong + 1 < blockStates.length) {
                val |= blockStates[startLong + 1] << (64 - bitOffset);
            }
        }
        return val;
    }
}