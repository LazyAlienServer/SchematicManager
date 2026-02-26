package com.lazyalienserver.schemcheck.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemCheck-WsManager");

    // 配置文件路径：config/SchmWSServer
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("SchmWSServer");

    private static WsClient currentClient;
    private static String currentUrl = "";

    // 创建一个后台定时任务线程池
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SchemCheck-WsMonitor");
        thread.setDaemon(true); // 设为守护线程，随游戏关闭而关闭
        return thread;
    });

    public static void init() {
        // 1. 如果配置文件不存在，自动生成一个默认的
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Files.writeString(CONFIG_PATH, "ws://127.0.0.1:8080/ws");
                LOGGER.info("已生成默认的 WS 配置文件: {}", CONFIG_PATH);
            }
        } catch (Exception e) {
            LOGGER.error("创建 WS 配置文件失败", e);
        }

        // 2. 启动定时任务，0秒后开始执行，之后每隔 10 秒执行一次
        SCHEDULER.scheduleAtFixedRate(WsManager::checkAndConnect, 0, 10, TimeUnit.SECONDS);
    }

    private static void checkAndConnect() {
        try {
            if (!Files.exists(CONFIG_PATH)) return;

            // 读取文件内容，并去掉可能存在的回车换行符或空格
            String fileUrl = Files.readString(CONFIG_PATH).trim();

            if (fileUrl.isEmpty()) return;

            // 情况 A：发现 URL 发生了改变
            if (!fileUrl.equals(currentUrl)) {
                LOGGER.info("检测到 WS 服务器地址变更: [{}] -> [{}]", currentUrl, fileUrl);
                currentUrl = fileUrl;
                reconnect();
            }
            // 情况 B：URL 没变，但连接意外断开了 (自动重连)
            else {
                if (currentClient == null || currentClient.isClosed()) {
                    LOGGER.info("检测到 WS 连接未连接或已断开，正在尝试连接到: {}", currentUrl);
                    reconnect();
                }
            }
        } catch (Exception e) {
            LOGGER.error("读取 WS 配置或检查连接时发生异常", e);
        }
    }

    private static void reconnect() {
        try {
            // 如果旧客户端还在运行，先把它安全关闭
            if (currentClient != null && !currentClient.isClosed()) {
                currentClient.closeBlocking();
            }

            // 使用新地址创建全新的客户端并连接
            URI uri = new URI(currentUrl);
            currentClient = new WsClient(uri);
            currentClient.connect();

        } catch (Exception e) {
            LOGGER.error("连接 WS 服务器失败 (请检查 URL 格式是否正确): {}", currentUrl, e);
        }
    }
}