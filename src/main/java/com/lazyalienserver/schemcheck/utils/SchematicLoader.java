package com.lazyalienserver.schemcheck.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.FileInputStream;

public class SchematicLoader {
    public static NbtCompound load(File file) {
        if (file == null || !file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            return NbtIo.readCompressed(fis);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}