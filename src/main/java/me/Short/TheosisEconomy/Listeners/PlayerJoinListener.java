package me.Short.TheosisEconomy.Listeners;

import me.Short.TheosisEconomy.PlayerAccount;
import me.Short.TheosisEconomy.TheosisEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerJoinListener implements Listener
{

    // Instance of "TheosisEconomy"
    private TheosisEconomy instance;

    // Constructor
    public PlayerJoinListener(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Add player to 'playerCache' if they have not joined before
        if (!player.hasPlayedBefore())
        {
            instance.getPlayerCache().put(player.getName(), uuid);
        }

        // Update the player's last known username in their account, or create an account for the player if they don't already have one
        Economy economy = instance.getEconomy();
        Map<UUID, PlayerAccount> playerAccounts = TheosisEconomy.getPlayerAccounts();
        if (economy.hasAccount(player)) // If the player already has an account...
        {
            if (!playerAccounts.get(uuid).getLastKnownUsername().equals(name))
            {
                PlayerAccount account = TheosisEconomy.getPlayerAccounts().get(uuid);

                // Update the player's last known username
                account.setLastKnownUsername(name);

                // Mark for saving
                instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());
            }
        }
        else // If they player does NOT have an account...
        {
            FileConfiguration config = instance.getConfig();

            // Try to create an account for the player
            if (economy.createPlayerAccount(player)) // This returns true if the account creation was successful, and false if not
            {
                // Log the successful account creation to console if config.yml says to do so
                if (config.getBoolean("settings.logging.account-creation-success.log"))
                {
                    instance.getLogger().log(Level.INFO, config.getString("settings.logging.account-creation-success.message")
                            .replace("<player>", name)
                            .replace("<uuid>", uuid.toString())
                            .replace("<default_balance>", new BigDecimal(config.getString("settings.currency.default-balance")).stripTrailingZeros().toPlainString()));
                }
            }
            else
            {
                // Log the failed account creation to console if config.yml says to do so
                if (config.getBoolean("settings.logging.account-creation-fail.log"))
                {
                    instance.getLogger().log(Level.WARNING, config.getString("settings.logging.account-creation-fail.message")
                            .replace("<player>", name)
                            .replace("<uuid>", uuid.toString()));
                }
            }
        }
    }

}