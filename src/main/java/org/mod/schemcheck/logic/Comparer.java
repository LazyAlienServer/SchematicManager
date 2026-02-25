package org.mod.schemcheck.logic;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

public class Comparer {
    public static boolean isMatch(BlockState schem, BlockState world) {
        if (schem.getBlock() != world.getBlock()) return false;

        for (Property<?> prop : schem.getProperties()) {
            String name = prop.getName();
            // 跳过不影响结构的动态属性：红石强度、音符盒音调
            if (name.equals("power") || name.equals("note")) continue;

            if (!schem.get(prop).equals(world.get(prop))) {
                return false;
            }
        }
        return true;
    }
}