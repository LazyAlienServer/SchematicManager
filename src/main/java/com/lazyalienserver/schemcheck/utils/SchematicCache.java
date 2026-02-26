package com.lazyalienserver.schemcheck.utils;

import net.minecraft.nbt.NbtCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchematicCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemCheck-Cache");

    private static final Map<String, NbtCompound> CACHE = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SchemCheck-CacheCleaner");
        thread.setDaemon(true);
        return thread;
    });

    static {
        // 参数说明：执行的具体任务, 首次延迟多久执行, 之后每隔多久执行一次, 时间单位
        // 这里设置为：启动 30 分钟后第一次执行，之后每隔 20 分钟执行一次
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                if (!CACHE.isEmpty()) {
                    int size = CACHE.size();
                    CACHE.clear();
                    LOGGER.info("【内存管家】已自动清理内存中驻留的 {} 个投影缓存文件，释放内存。", size);
                }
            } catch (Exception e) {
                LOGGER.error("定时清理缓存时发生异常", e);
            }
        }, 30, 20, TimeUnit.MINUTES);
    }

    /**
     * 获取投影的 NBT 数据。如果缓存中有，直接返回；如果没有，读取硬盘并存入缓存。
     */
    public static NbtCompound getOrLoad(File file, String hash) {
        if (file == null || !file.exists()) return null;

        return CACHE.computeIfAbsent(hash, k -> SchematicLoader.load(file));
    }

    /**
     * 提供给指令或其他地方的手动清理接口
     */
    public static void clearCache() {
        int size = CACHE.size();
        CACHE.clear();
        LOGGER.info("已手动强制清理了 {} 个投影缓存。", size);
    }
}