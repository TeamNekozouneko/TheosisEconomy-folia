package me.Short.TheosisEconomy.Tasks;

import litebans.api.Database;
import me.Short.TheosisEconomy.Events.PreBaltopSortEvent;
import me.Short.TheosisEconomy.Managers.BaltopManager;
import me.Short.TheosisEconomy.PlayerAccount;
import me.Short.TheosisEconomy.TheosisEconomy;
import net.milkbowl.vault.permission.Permission;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class UpdateBaltopTask extends TaskManager{

    private BaltopManager baltopManager = TheosisEconomy.getBaltopManager();

    @Override
    public void run(){
        // Create PreBaltopSortEvent instance with an initial empty HashSet of excluded players' UUIDs
        PreBaltopSortEvent preBaltopSortEvent = new PreBaltopSortEvent(new HashSet<>());

        // Call the event
        Bukkit.getServer().getPluginManager().callEvent(preBaltopSortEvent);

        // Call "updateBaltop", passing in the HashSet of excluded players' UUIDs, if the event was NOT cancelled
        if (!preBaltopSortEvent.isCancelled())
        {
            updateBaltop(preBaltopSortEvent.getExcludedPlayers()).thenAccept(pair ->
            {
                baltopManager.setBaltop(pair.getLeft());
                baltopManager.setCombinedTotalBalance(pair.getRight());
            });
        }
    }
    // Method to update baltop async
    public CompletableFuture<MutablePair<Map<String, BigDecimal>, BigDecimal>> updateBaltop(Set<UUID> excludedPlayers)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            Map<UUID, PlayerAccount> playerAccounts = TheosisEconomy.getPlayerAccounts();
            Boolean isBaltopConsiderExcludePermission = baltopManager.isBaltopConsiderExcludePermission();
            Permission permission = TheosisEconomy.getPermission();
            Boolean isBaltopExcludeBannedPlayers = baltopManager.isBaltopExcludeBannedPlayers();
            Boolean isLiteBansInstalled = TheosisEconomy.isLiteBansInstalled();
            BigDecimal baltopMinBalance = baltopManager.getBaltopMinBalance();

            Map<String, BigDecimal> unsortedBaltop = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;

            // Get player names and their balances in no particular order, excluding banned players if config.yml says to not include them - the "Bukkit.getOfflinePlayer(uuid).isBanned()" is the only thing here that might not be safe to run async, but no issues so far in testing
            for (UUID uuid : playerAccounts.keySet())
            {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

                if (!excludedPlayers.contains(uuid) && !(isBaltopConsiderExcludePermission && permission.playerHas(null, player, "theosiseconomy.balancetop.exclude")) && (!isBaltopExcludeBannedPlayers || !((isLiteBansInstalled && Database.get().isPlayerBanned(uuid, null)) || player.isBanned())))
                {
                    PlayerAccount account = playerAccounts.get(uuid);
                    BigDecimal balance = account.getBalance();

                    total = total.add(balance);

                    if (balance.compareTo(baltopMinBalance) >= 0)
                    {
                        unsortedBaltop.put(account.getLastKnownUsername(), balance);
                    }
                }
            }

            // Create and return sorted version of "unsortedBaltop"
            LinkedHashMap<String, BigDecimal> sortedBaltop = new LinkedHashMap<>();
            unsortedBaltop.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(entry -> sortedBaltop.put(entry.getKey(), entry.getValue()));

            return new MutablePair<>(sortedBaltop, total);
        });
    }
}
