package me.Short.TheosisEconomy.Commands;

import me.Short.TheosisEconomy.PlayerAccount;
import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PaytoggleCommand implements CommandExecutor
{

    // Instance of "TheosisEconomy"
    private TheosisEconomy instance;

    // Constructor
    public PaytoggleCommand(TheosisEconomy mainInstance)
    {
        this.instance = mainInstance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,	String label, String[] args)
    {
        if (sender instanceof Player)
        {
            Player player = (Player) sender;

            if (instance.getEconomy().hasAccount((player)))
            {
                UUID uuid = player.getUniqueId();
                PlayerAccount account = TheosisEconomy.getPlayerAccounts().get(uuid);

                if (account.getAcceptingPayments())
                {
                    // Toggle the setting in the player's account
                    account.setAcceptingPayments(false);

                    // Mark for saving
                    instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

                    // Send message
                    instance.sendMiniMessage(player, instance.getConfig().getString("messages.paytoggle.disabled"));
                }
                else
                {
                    // Toggle the setting in the player's account
                    account.setAcceptingPayments(true);

                    // Mark for saving
                    instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

                    // Send message
                    instance.sendMiniMessage(player, instance.getConfig().getString("messages.paytoggle.enabled"));
                }
            }
            else
            {
                instance.sendMiniMessage(player, instance.getConfig().getString("messages.error.no-account"));
            }
        }
        else
        {
            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.console-cannot-use"));
        }

        return true;
    }

}