package me.Short.TheosisEconomy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import litebans.api.Database;
import me.Short.TheosisEconomy.Commands.BalanceCommand;
import me.Short.TheosisEconomy.Commands.BalancetopCommand;
import me.Short.TheosisEconomy.Commands.EconomyCommand;
import me.Short.TheosisEconomy.Commands.PayCommand;
import me.Short.TheosisEconomy.Commands.PaytoggleCommand;
import me.Short.TheosisEconomy.Events.PreBaltopSortEvent;
import me.Short.TheosisEconomy.Listeners.PlayerJoinListener;
import me.Short.TheosisEconomy.Listeners.ServerLoadListener;
import me.Short.TheosisEconomy.Managers.BaltopManager;
import me.Short.TheosisEconomy.Tasks.UpdateBaltopTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.permission.Permission;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.map.MinecraftFont;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TheosisEconomy extends JavaPlugin
{
    // Static Instance
    private static JavaPlugin instance;

    // Map containing snapshots of player accounts to be saved to respective JSON files on a schedule
    private Map<UUID, PlayerAccountSnapshot> dirtyPlayerAccountSnapshots;

    // Scheduler for handling the repeated saving of dirty player accounts
    private ScheduledExecutorService saveDirtyPlayerAccountsScheduler;

    // Instance of the current save task scheduled to run in the future
    private ScheduledFuture<?> scheduledSaveTask;

    // File handler for the plugin's logger
    private FileHandler logFileHandler;

    // Instance of the Gson library
    private Gson gson;

    // Instance of the Vault Economy API
    private net.milkbowl.vault.economy.Economy economy;

    // Instance of the Vault Permissions API
    private static Permission permissions;

    // Cache of all player UUIDs (who have joined before) - this is so that offline players can easily be retrieved from their usernames
    private Map<String, UUID> playerCache;

    // Cache of all player accounts
    private static Map<UUID, PlayerAccount> playerAccounts;

    // Baltop Managers
    private static BaltopManager baltopManager;

    // ID for the baltop update task, so it can be cancelled in the event of a reload
    private UpdateBaltopTask updateBaltopTask;

    // Instance of the MiniMessage API
    private MiniMessage miniMessage;

    // Instance of the BungeeComponentSerializer API - this is to convert Adventure Components to Bungee/legacy Components (BaseComponents), since we want Spigot compatibility
    private BungeeComponentSerializer bungeeComponentSerializer;

    // Instance of the LegacyComponentSerializer API - this is to convert Adventure Components to legacy text, for sending to non-Player CommandSenders (since in the Spigot API prior to 1.12, there is no "CommandSender.spigot()" method
    private LegacyComponentSerializer legacyComponentSerializer;

    // Whether LiteBans is installed - for checking in the "updateBaltop" method
    private static boolean liteBansInstalled;

    // Is running on Folia?
    private static boolean isFolia = isClassExists("io.papermc.paper.threadedregions.RegionizedServer") || isClassExists("io.papermc.paper.threadedregions.RegionizedServerInitEvent");

    private static boolean isClassExists(String clazz){
        try{
            Class.forName(clazz);
            return true;
        }catch(ClassNotFoundException e){
            return false;
        }
    }

    @Override
    public void onEnable()
    {
        instance = this;

        // Set up config.yml
        saveDefaultConfig();

        // Set "dirtyPlayerAccountSnapshots"
        dirtyPlayerAccountSnapshots = new ConcurrentHashMap<>();

        // Set "saveDirtyPlayerAccountsScheduler"
        saveDirtyPlayerAccountsScheduler = Executors.newSingleThreadScheduledExecutor();

        // Disable console logging if config.yml says to do so
        if (!getConfig().getBoolean("settings.logging.log-console"))
        {
            getLogger().setUseParentHandlers(false);
        }

        // Enable file logging to "logs.log" if config.yml says to do so
        if (getConfig().getBoolean("settings.logging.log-file"))
        {
            setupLogFileHandler();
        }

        // Create instance of Gson
        gson = new GsonBuilder().setPrettyPrinting().create();

        // Register TheosisEconomy as a Vault Economy provider
        economy = new Economy(this);
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, economy, this, ServicePriority.Highest);

        // Hook into Vault Permissions
        permissions = getServer().getServicesManager().getRegistration(Permission.class).getProvider();

        // Set initial "playerCache" value to an empty case-insensitive map
        playerCache = new CaseInsensitiveMap<>();

        baltopManager = new BaltopManager();

        // Get instance of the MiniMessage API
        miniMessage = MiniMessage.miniMessage();

        // Get instance of the LegacyComponentSerializer API
        legacyComponentSerializer = LegacyComponentSerializer.legacySection();

        // Get instance of the BungeeComponentSerializer API - the additional options make it so that hover events work on Minecraft versions prior to 1.16
        bungeeComponentSerializer = BungeeComponentSerializer.of(GsonComponentSerializer.builder().editOptions(options -> options.value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.BOTH)).build(), legacyComponentSerializer);

        // Register events
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ServerLoadListener(this), this);
        pluginManager.registerEvents(new PlayerJoinListener(this), this);

        // Register commands
        getCommand("economy").setExecutor(new EconomyCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("paytoggle").setExecutor(new PaytoggleCommand(this));
        getCommand("balancetop").setExecutor(new BalancetopCommand(this));

        // Register PlaceholderAPI placeholders, if PlaceholderAPI is installed
        if (pluginManager.getPlugin("PlaceholderAPI") != null)
        {
            new PlaceholderAPI(this).register();
        }

        // Get whether LiteBans is installed
        liteBansInstalled = pluginManager.getPlugin("LiteBans") != null;

        // bStats
        Metrics metrics = new Metrics(this, 13836);

        // Cache all players' UUIDs and their account data
        playerAccounts = cachePlayerAccounts();

        // Schedule repeating baltop update task
        scheduleBaltopUpdateTask();

        // Schedule task to periodically save any dirty player accounts to JSON files
        scheduledSaveTask = saveDirtyPlayerAccountsScheduler.schedule(this::runSaveDirtyPlayerAccountsLoop, getConfig().getLong("settings.player-accounts-save-frequency"), TimeUnit.SECONDS);
    }

    @Override
    public void onDisable()
    {
        // Cancel async dirty player account saving task, since we're going to save here manually
        scheduledSaveTask.cancel(false);

        // Save dirty player accounts to JSON files
        saveDirtyPlayerAccounts();
    }

    // Method to set up "logFileHandler" and set it to this plugin's logger
    public void setupLogFileHandler()
    {
        try
        {
            logFileHandler = new FileHandler(getDataFolder().getAbsolutePath() + "/logs.log", true);

            logFileHandler.setFormatter(new LogFormatter());

            getLogger().addHandler(logFileHandler);

        }
        catch (IOException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    // Method to cache all player accounts from their respective JSON files in the "player-accounts" folder - also migrates any files in the old "data.yml" file if it exists
    private Map<UUID, PlayerAccount> cachePlayerAccounts()
    {
        Map<UUID, PlayerAccount> playerAccounts = new HashMap<>();

        File playerAccountsFolder = new File(getDataFolder(), "player-accounts");

        // If a "player-accounts" folder doesn't exist, create one
        if (!playerAccountsFolder.exists())
        {
            playerAccountsFolder.mkdirs();
        }

        // If there is an old "data.yml" file, migrate all player accounts to separate JSON files
        File dataFile = new File(getDataFolder(), "data.yml");
        if (dataFile.exists())
        {
            getLogger().log(Level.INFO, "Old \"data.yml\" file found. Migrating player data to JSON files...");

            ConfigurationSection data = YamlConfiguration.loadConfiguration(dataFile).getConfigurationSection("players");

            // If there is data, create a `PlayerAccountSnapshot` based on it, add it to `dirtyPlayerAccountSnapshots`, then save the dirty player accounts to respective JSON files
            if (data != null)
            {
                // Add snapshots to `dirtyPlayerAccountSnapshots`
                for (String uuid : data.getKeys(false))
                {
                    dirtyPlayerAccountSnapshots.put(UUID.fromString(uuid), new PlayerAccountSnapshot(new BigDecimal(data.getString(uuid + ".balance")), data.getBoolean(uuid + ".acceptingPayments"), data.contains(uuid + ".lastKnownUsername") ? data.getString(uuid + ".lastKnownUsername") : null));
                }

                // Save
                saveDirtyPlayerAccounts();
            }

            // Delete the "data.yml" file
            if (dataFile.delete())
            {
                getLogger().log(Level.INFO, "The old \"data.yml\" file has been deleted.");
            }
            else
            {
                getLogger().log(Level.INFO, "Failed to delete the old \"data.yml\" file.");
            }

            getLogger().log(Level.INFO, "Migration complete.");
        }

        // Get a list of player account files
        File[] playerAccountFiles = playerAccountsFolder.listFiles();

        // If there are no files, return the unpopulated `playerAccounts` map
        if (playerAccountFiles == null)
        {
            return playerAccounts;
        }

        // Go through each file, read a `PlayerAccount` object from it, and put it in `PlayerAccounts`
        for (File file : playerAccountFiles)
        {
            try
            {
                UUID uuid = UUID.fromString(file.getName().replace(".json", ""));

                try (FileReader reader = new FileReader(file))
                {
                    playerAccounts.put(uuid, gson.fromJson(reader, PlayerAccount.class));
                }
            }
            catch (IllegalArgumentException exception)
            {
                getLogger().log(Level.WARNING, "Failed to load player account - invalid UUID in file name: " + file.getName());
                exception.printStackTrace();
            }
            catch (IOException | JsonIOException exception)
            {
                getLogger().log(Level.WARNING, "Failed to load player account - failed to read file: " + file.getName());
                exception.printStackTrace();
            }
        }

        return playerAccounts;
    }

    // Method to schedule the repeating baltop update task
    public void scheduleBaltopUpdateTask()
    {
        // Call "updateBaltop" on a schedule (frequency defined in config.yml) and set "baltop" and "combinedTotalBalance" to its results
        updateBaltopTask = new UpdateBaltopTask();
        updateBaltopTask.runTaskTimer(this, 0L, getConfig().getLong("settings.baltop.update-task-frequency"));
    }

    // Method to repeatedly call `saveDirtyPlayerAccounts()` async
    private void runSaveDirtyPlayerAccountsLoop()
    {
        CompletableFuture.runAsync(this::saveDirtyPlayerAccounts)
                .thenRunAsync(() -> scheduledSaveTask = saveDirtyPlayerAccountsScheduler.schedule(this::runSaveDirtyPlayerAccountsLoop, getConfig().getLong("settings.misc.data-file-save-frequency"), TimeUnit.SECONDS), saveDirtyPlayerAccountsScheduler);
    }

    // Method to save all player accounts in `dirtyPlayerAccountSnapshots` to respective JSON files, and remove the saved accounts from `dirtyPlayerAccountSnapshots` after
    private void saveDirtyPlayerAccounts()
    {
        if (!dirtyPlayerAccountSnapshots.isEmpty())
        {
            // Snapshot of `dirtyPlayerAccountSnapshots`
            Map<UUID, PlayerAccountSnapshot> dirtyPlayerAccountSnapshotsSnapshot = new HashMap<>(dirtyPlayerAccountSnapshots);

            // Save each dirty player account to a JSON file inside the "player-accounts" folder
            for (Map.Entry<UUID, PlayerAccountSnapshot> entry : dirtyPlayerAccountSnapshotsSnapshot.entrySet())
            {
                String fileName = getDataFolder().getAbsolutePath() + File.separator + "player-accounts" + File.separator + entry.getKey() + ".json";

                try (Writer writer = new FileWriter(fileName))
                {
                    // Write the `PlayerAccountSnapshot` to a JSON file with the name `fileName`
                    gson.toJson(entry.getValue(), writer);
                }
                catch (IOException exception)
                {
                    getLogger().log(Level.WARNING, "Failed to save player account to file: " + fileName);
                    exception.printStackTrace();
                    continue;
                }

                // Remove the entry from `dirtyPlayerAccountSnapshots`, since the player's account has now been saved
                dirtyPlayerAccountSnapshots.remove(entry.getKey());
            }
        }
    }

    // Method to send a MiniMessage-formatted string as BaseComponents to a CommandSender, with no tag resolvers
    public void sendMiniMessage(CommandSender sender, String message)
    {
        if (!message.isEmpty())
        {
            Component messageComponent = miniMessage.deserialize(message);

            if (sender instanceof Player)
            {
                ((Player) sender).spigot().sendMessage(bungeeComponentSerializer.serialize(messageComponent));
            }
            else
            {
                sender.sendMessage(legacyComponentSerializer.serialize(messageComponent));
            }
        }
    }

    // Method to send a MiniMessage-formatted string as BaseComponents to a CommandSender, WITH tag resolvers
    public void sendMiniMessage(CommandSender sender, String message, TagResolver... tagResolvers)
    {
        if (!message.isEmpty())
        {
            Component messageComponent = miniMessage.deserialize(message, tagResolvers);

            if (sender instanceof Player)
            {
                ((Player) sender).spigot().sendMessage(bungeeComponentSerializer.serialize(messageComponent));
            }
            else
            {
                sender.sendMessage(legacyComponentSerializer.serialize(messageComponent));
            }
        }
    }

    // Method to round a value to the number of decimal places that the currency is configured to use
    public BigDecimal round(BigDecimal value)
    {
        FileConfiguration config = getConfig();

        RoundingMode mode = RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"));

        if (mode == RoundingMode.ROUND_NEAREST)
        {
            return value.setScale(economy.fractionalDigits(), java.math.RoundingMode.HALF_UP);
        }

        if (mode == RoundingMode.ROUND_UP)
        {
            return value.setScale(economy.fractionalDigits(), java.math.RoundingMode.UP);
        }

        if (mode == RoundingMode.ROUND_DOWN)
        {
            return value.setScale(economy.fractionalDigits(), java.math.RoundingMode.DOWN);
        }

        return value;
    }

    // Method to reload the config and data files
    public void reload()
    {
        BukkitScheduler scheduler = Bukkit.getScheduler();

        // Reload config.yml from disk
        reloadConfig();

        // Re-get values from config that were gotten in "onEnable"
        baltopManager.reload();

        // Set whether to send logs to console
        Logger logger = getLogger();
        if (getConfig().getBoolean("settings.logging.log-console"))
        {
            if (!logger.getUseParentHandlers())
            {
                logger.setUseParentHandlers(true);
            }
        }
        else
        {
            if (logger.getUseParentHandlers())
            {
                logger.setUseParentHandlers(false);
            }
        }

        // Set whether to send logs to the "logs.log" file
        if (getConfig().getBoolean("settings.logging.log-file"))
        {
            if (logFileHandler != null)
            {
                if (!Arrays.asList(logger.getHandlers()).contains(logFileHandler))
                {
                    logger.addHandler(logFileHandler);
                }
            }
            else
            {
                setupLogFileHandler();
            }
        }
        else
        {
            // Remove the FileHandler from the logger, if it exists
            if (logFileHandler != null)
            {
                for (Handler handler : logger.getHandlers())
                {
                    if (handler.equals(logFileHandler))
                    {
                        logger.removeHandler(logFileHandler);
                        break;
                    }
                }
            }
        }

        // Cancel async dirty player account saving task
        scheduledSaveTask.cancel(false);

        // Save dirty player accounts to JSON files
        saveDirtyPlayerAccounts();

        // Re-schedule repeating data save task
        scheduledSaveTask = saveDirtyPlayerAccountsScheduler.schedule(this::runSaveDirtyPlayerAccountsLoop, getConfig().getLong("settings.player-accounts-save-frequency"), TimeUnit.SECONDS);

        // Re-cache player accounts
        playerAccounts = cachePlayerAccounts();

        // Cancel the current repeating baltop update task
        updateBaltopTask.safetyTaskCancel();

        // Re-schedule repeating baltop update task
        scheduleBaltopUpdateTask();
    }

    // Method to return the number of dots needed to align the end of a string after "<dots>"
    public int getNumberOfDotsToAlign(String message, boolean isForPlayer)
    {
        if (isForPlayer)
        {
            return Math.round((130F - MinecraftFont.Font.getWidth(message.substring(0, message.indexOf("<dots>") - 1))) / 2);
        }

        return Math.round((130F - MinecraftFont.Font.getWidth(message.substring(0, message.indexOf("<dots>") - 1))) / 6) + 7;
    }

    // ----- Getters -----

    public static JavaPlugin getInstance() { return instance; }

    // Getter for "dirtyPlayerAccountSnapshots"
    public Map<UUID, PlayerAccountSnapshot> getDirtyPlayerAccountSnapshots()
    {
        return dirtyPlayerAccountSnapshots;
    }

    // Getter for "economy"
    public net.milkbowl.vault.economy.Economy getEconomy()
    {
        return economy;
    }

    // Getter for "playerCache"
    public Map<String, UUID> getPlayerCache()
    {
        return playerCache;
    }

    // Getter for "playerAccounts"
    public static Map<UUID, PlayerAccount> getPlayerAccounts()
    {
        return playerAccounts;
    }

    // Getter for "miniMessage"
    public MiniMessage getMiniMessage()
    {
        return miniMessage;
    }

    // Getter for "bungeeComponentSerializer"
    public BungeeComponentSerializer getBungeeComponentSerializer()
    {
        return bungeeComponentSerializer;
    }

    // Getter for "legacyComponentSerializer"
    public LegacyComponentSerializer getLegacyComponentSerializer()
    {
        return legacyComponentSerializer;
    }

    // Getter for "isFolia"
    public static Boolean isFolia() {
        return isFolia;
    }

    // Getter for "baltopManager"
    public static BaltopManager getBaltopManager() {
        return baltopManager;
    }

    // Getter for "permissions"
    public static Permission getPermission() {
        return permissions;
    }

    // Getter for "liteBansInstalled"
    public static Boolean isLiteBansInstalled(){
        return liteBansInstalled;
    }

}