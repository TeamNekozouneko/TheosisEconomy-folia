package me.Short.TheosisEconomy.Commands;

import me.Short.TheosisEconomy.Economy;
import me.Short.TheosisEconomy.PlayerAccount;
import me.Short.TheosisEconomy.TheosisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class EconomyCommand implements TabExecutor
{

    // Instance of "TheosisEconomy"
    private TheosisEconomy instance;

    // Constructor
    public EconomyCommand(TheosisEconomy mainInstance)
    {
        this.instance = mainInstance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,	String label, String[] args)
    {
        if (args.length > 0)
        {
            if (args[0].equalsIgnoreCase("set"))
            {
                if (sender.hasPermission("theosiseconomy.command.economy.set"))
                {
                    if (args.length > 1)
                    {
                        // Try to get target player
                        OfflinePlayer target = Bukkit.getPlayer(args[1]);
                        if (target == null) // Target is not online, so now we check the cache to see if they have joined before...
                        {
                            UUID uuid = instance.getPlayerCache().get(args[1]);
                            if (uuid != null)
                            {
                                target = Bukkit.getOfflinePlayer(uuid);
                            }
                        }

                        if (target != null)
                        {
                            net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

                            if (economy.hasAccount(target))
                            {
                                if (args.length > 2)
                                {
                                    // Try to parse BigDecimal amount from the command argument
                                    BigDecimal amount;
                                    try
                                    {
                                        amount = instance.round(new BigDecimal(args[2])).stripTrailingZeros();
                                    }
                                    catch (NumberFormatException exception)
                                    {
                                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.invalid-amount"),
                                                Placeholder.component("amount", Component.text(args[2])));

                                        return true;
                                    }

                                    if (amount.compareTo(BigDecimal.ZERO) >= 0)
                                    {
                                        int decimalPlaces = economy.fractionalDigits();

                                        if (amount.scale() <= decimalPlaces)
                                        {
                                            FileConfiguration config = instance.getConfig();

                                            if (amount.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) <= 0)
                                            {
                                                UUID uuid = target.getUniqueId();
                                                PlayerAccount account = TheosisEconomy.getPlayerAccounts().get(uuid);

                                                // Set the player's balancee
                                                account.setBalance(amount);

                                                // Mark for saving
                                                instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

                                                String amountFormatted = economy.format(amount.doubleValue());

                                                // Log the change to the console if config.yml says to do so
                                                if (config.getBoolean("settings.logging.balance-set.log"))
                                                {
                                                    instance.getLogger().log(Level.INFO, config.getString("settings.logging.balance-set.message")
                                                            .replace("<player>", target.getName())
                                                            .replace("<uuid>", uuid.toString())
                                                            .replace("<amount>", amount.toPlainString()));
                                                }

                                                // Send message to the target player, if online
                                                if (target.isOnline())
                                                {
                                                    instance.sendMiniMessage(target.getPlayer(), config.getString("messages.economy.set.balance-set-target"),
                                                            Placeholder.component("amount", Component.text(amountFormatted)));
                                                }

                                                // Send message to the command sender
                                                instance.sendMiniMessage(sender, config.getString("messages.economy.set.balance-set-sender"),
                                                        Placeholder.component("target", Component.text(target.getName())),
                                                        Placeholder.component("amount", Component.text(amountFormatted)));
                                            }
                                            else
                                            {
                                                instance.sendMiniMessage(sender, config.getString("messages.error.would-exceed-max-balance"),
                                                        Placeholder.component("target", Component.text(target.getName())));
                                            }
                                        }
                                        else
                                        {
                                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                                                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                                                    Placeholder.component("decimal_places", Component.text(decimalPlaces)));
                                        }
                                    }
                                    else
                                    {
                                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.negative-amount"));
                                    }
                                }
                                else
                                {
                                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("usage", Component.text("/economy set <player> <amount>")));
                                }
                            }
                            else
                            {
                                instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-account-other"),
                                        Placeholder.component("target", Component.text(target.getName())));
                            }
                        }
                        else
                        {
                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.not-joined-before"),
                                    Placeholder.component("username", Component.text(args[1])));
                        }
                    }
                    else
                    {
                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("usage", Component.text("/economy set <player> <amount>")));
                    }
                }
                else
                {
                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-permission"));
                }
            }
            else if (args[0].equalsIgnoreCase("give"))
            {
                if (sender.hasPermission("theosiseconomy.command.economy.give"))
                {
                    if (args.length > 1)
                    {
                        // Try to get target player
                        OfflinePlayer target = Bukkit.getPlayer(args[1]);
                        if (target == null) // Target is not online, so now we check the cache to see if they have joined before...
                        {
                            UUID uuid = instance.getPlayerCache().get(args[1]);
                            if (uuid != null)
                            {
                                target = Bukkit.getOfflinePlayer(uuid);
                            }
                        }

                        if (target != null)
                        {
                            net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

                            if (economy.hasAccount(target))
                            {
                                if (args.length > 2)
                                {
                                    // Try to parse BigDecimal amount from the command argument
                                    BigDecimal amount;
                                    try
                                    {
                                        amount = new BigDecimal(args[2]);
                                    }
                                    catch (NumberFormatException exception)
                                    {
                                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.invalid-amount"),
                                                Placeholder.component("amount", Component.text(args[2])));

                                        return true;
                                    }

                                    // Try to deposit the amount to the target
                                    EconomyResponse depositPlayerResponse = economy.depositPlayer(target, amount.doubleValue());

                                    if (depositPlayerResponse.type == ResponseType.SUCCESS) // If the deposit was successful...
                                    {
                                        FileConfiguration config = instance.getConfig();

                                        String amountFormatted = economy.format(depositPlayerResponse.amount);

                                        // Send message to the target player, if online
                                        if (target.isOnline())
                                        {
                                            instance.sendMiniMessage(target.getPlayer(), config.getString("messages.economy.give.money-given-target"),
                                                    Placeholder.component("amount", Component.text(amountFormatted)));
                                        }

                                        // Send message to the command sender
                                        instance.sendMiniMessage(sender, config.getString("messages.economy.give.money-given-sender"),
                                                Placeholder.component("target", Component.text(target.getName())),
                                                Placeholder.component("amount", Component.text(amountFormatted)));
                                    }
                                    else  // If the deposit was unsuccessful...
                                    {
                                        String errorMessage = depositPlayerResponse.errorMessage;

                                        if (errorMessage.equals(Economy.getErrorWouldExceedMaxBalance()))
                                        {
                                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                                                    Placeholder.component("target", Component.text(target.getName())));
                                        }
                                        else if (errorMessage.equals(Economy.getErrorTooManyDecimalPlaces()))
                                        {
                                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                                                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                                                    Placeholder.component("decimal_places", Component.text(economy.fractionalDigits())));
                                        }
                                        else if (errorMessage.equals(Economy.getErrorNotGreaterThanZero()))
                                        {
                                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.not-greater-than-zero-amount"));
                                        }
                                    }
                                }
                                else
                                {
                                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("usage", Component.text("/economy give <player> <amount>")));
                                }
                            }
                            else
                            {
                                instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-account-other"),
                                        Placeholder.component("target", Component.text(target.getName())));
                            }
                        }
                        else
                        {
                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.not-joined-before"),
                                    Placeholder.component("username", Component.text(args[1])));
                        }
                    }
                    else
                    {
                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("usage", Component.text("/economy give <player> <amount>")));
                    }
                }
                else
                {
                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-permission"));
                }
            }
            else if (args[0].equalsIgnoreCase("take"))
            {
                if (sender.hasPermission("theosiseconomy.command.economy.take"))
                {
                    if (args.length > 1)
                    {
                        // Try to get target player
                        OfflinePlayer target = Bukkit.getPlayer(args[1]);
                        if (target == null) // Target is not online, so now we check the cache to see if they have joined before...
                        {
                            UUID uuid = instance.getPlayerCache().get(args[1]);
                            if (uuid != null)
                            {
                                target = Bukkit.getOfflinePlayer(uuid);
                            }
                        }

                        if (target != null)
                        {
                            net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

                            if (economy.hasAccount(target))
                            {
                                if (args.length > 2)
                                {
                                    // Try to parse BigDecimal amount from the command argument
                                    BigDecimal amount;
                                    try
                                    {
                                        amount = new BigDecimal(args[2]);
                                    }
                                    catch (NumberFormatException exception)
                                    {
                                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.invalid-amount"),
                                                Placeholder.component("amount", Component.text(args[2])));

                                        return true;
                                    }

                                    double amountAsDouble = amount.doubleValue(); // For passing into the Economy methods, as they only take doubles

                                    // Try to withdraw the amount from the payer
                                    EconomyResponse withdrawPlayerResponse = economy.withdrawPlayer(target, amountAsDouble);

                                    if (withdrawPlayerResponse.type == ResponseType.SUCCESS)
                                    {
                                        FileConfiguration config = instance.getConfig();

                                        String amountFormatted = economy.format(withdrawPlayerResponse.amount);

                                        // Send message to the target player, if online
                                        if (target.isOnline())
                                        {
                                            instance.sendMiniMessage(target.getPlayer(), config.getString("messages.economy.take.money-taken-target"),
                                                    Placeholder.component("amount", Component.text(amountFormatted)));
                                        }

                                        // Send message to the command sender
                                        instance.sendMiniMessage(sender, config.getString("messages.economy.take.money-taken-sender"),
                                                Placeholder.component("target", Component.text(target.getName())),
                                                Placeholder.component("amount", Component.text(amountFormatted)));
                                    }
                                    else // If the withdrawal was unsuccessful...
                                    {
                                        String errorMessage = withdrawPlayerResponse.errorMessage;

                                        if (errorMessage.equals(Economy.getErrorInsufficientFunds()))
                                        {
                                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.insufficient-funds-other"),
                                                    Placeholder.component("target", Component.text(target.getName())),
                                                    Placeholder.component("amount", Component.text(economy.format(amountAsDouble))));
                                        }
                                        else if (errorMessage.equals(Economy.getErrorTooManyDecimalPlaces()))
                                        {
                                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                                                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                                                    Placeholder.component("decimal_places", Component.text(economy.fractionalDigits())));
                                        }
                                        else if (errorMessage.equals(Economy.getErrorNotGreaterThanZero()))
                                        {
                                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.not-greater-than-zero-amount"));
                                        }
                                    }
                                }
                                else
                                {
                                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("usage", Component.text("/economy take <player> <amount>")));
                                }
                            }
                            else
                            {
                                instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-account-other"),
                                        Placeholder.component("target", Component.text(target.getName())));
                            }
                        }
                        else
                        {
                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.not-joined-before"),
                                    Placeholder.component("username", Component.text(args[1])));
                        }
                    }
                    else
                    {
                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("usage", Component.text("/economy take <player> <amount>")));
                    }
                }
                else
                {
                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-permission"));
                }
            }
            else if (args[0].equalsIgnoreCase("reset"))
            {
                if (sender.hasPermission("theosiseconomy.command.economy.reset"))
                {
                    if (args.length > 1)
                    {
                        // Try to get target player
                        OfflinePlayer target = Bukkit.getPlayer(args[1]);
                        if (target == null) // Target is not online, so now we check the cache to see if they have joined before...
                        {
                            UUID uuid = instance.getPlayerCache().get(args[1]);
                            if (uuid != null)
                            {
                                target = Bukkit.getOfflinePlayer(uuid);
                            }
                        }

                        if (target != null)
                        {
                            net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

                            if (economy.hasAccount(target))
                            {
                                BigDecimal defaultBalance = new BigDecimal(instance.getConfig().getString("settings.currency.default-balance")).stripTrailingZeros();

                                if (defaultBalance.compareTo(BigDecimal.ZERO) >= 0)
                                {
                                    if (defaultBalance.scale() <= economy.fractionalDigits())
                                    {
                                        FileConfiguration config = instance.getConfig();

                                        if (defaultBalance.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) <= 0)
                                        {
                                            UUID uuid = target.getUniqueId();
                                            PlayerAccount account = TheosisEconomy.getPlayerAccounts().get(uuid);

                                            // Set the player's balance
                                            account.setBalance(defaultBalance);

                                            // Mark for saving
                                            instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

                                            String defaultBalanceFormatted = economy.format(defaultBalance.doubleValue());

                                            // Log the change to the console if config.yml says to do so
                                            if (config.getBoolean("settings.logging.balance-reset.log"))
                                            {
                                                instance.getLogger().log(Level.INFO, config.getString("settings.logging.balance-reset.message")
                                                        .replace("<player>", target.getName())
                                                        .replace("<uuid>", uuid.toString())
                                                        .replace("<default_balance>", defaultBalance.toPlainString()));
                                            }

                                            // Send message to the target player, if online
                                            if (target.isOnline())
                                            {
                                                instance.sendMiniMessage(target.getPlayer(), config.getString("messages.economy.reset.balance-reset-target"),
                                                        Placeholder.component("default_balance", Component.text(defaultBalanceFormatted)));
                                            }

                                            // Send message to the command sender
                                            instance.sendMiniMessage(sender, config.getString("messages.economy.reset.balance-reset-sender"),
                                                    Placeholder.component("target", Component.text(target.getName())),
                                                    Placeholder.component("default_balance", Component.text(defaultBalanceFormatted)));
                                        }
                                        else
                                        {
                                            instance.sendMiniMessage(sender, config.getString("messages.error.default-balance-exceeds-max-balance"),
                                                    Placeholder.component("target", Component.text(target.getName())));
                                        }
                                    }
                                    else
                                    {
                                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.too-many-decimal-places-default-balance"),
                                                Placeholder.component("target", Component.text(target.getName())));
                                    }
                                }
                                else
                                {
                                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.negative-default-balance"),
                                            Placeholder.component("target", Component.text(target.getName())));
                                }
                            }
                            else
                            {
                                instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-account-other"),
                                        Placeholder.component("target", Component.text(target.getName())));
                            }
                        }
                        else
                        {
                            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.not-joined-before"),
                                    Placeholder.component("username", Component.text(args[1])));
                        }
                    }
                    else
                    {
                        instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("usage", Component.text("/economy reset <player>")));
                    }
                }
                else
                {
                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-permission"));
                }
            }
            else if (args[0].equalsIgnoreCase("reload"))
            {
                if (sender.hasPermission("theosiseconomy.command.economy.reload"))
                {
                    instance.reload();

                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.economy.reload"));
                }
                else
                {
                    instance.sendMiniMessage(sender, instance.getConfig().getString("messages.error.no-permission"));
                }
            }
            else
            {
                instance.sendMiniMessage(sender, instance.getConfig().getString("messages.economy.help"));
            }
        }
        else
        {
            instance.sendMiniMessage(sender, instance.getConfig().getString("messages.economy.help"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1)
        {
            if (sender.hasPermission("theosiseconomy.command.economy.set") && "set".toUpperCase().startsWith(args[0].toUpperCase()))
            {
                suggestions.add("set");
            }
            if (sender.hasPermission("theosiseconomy.command.economy.give") && "give".toUpperCase().startsWith(args[0].toUpperCase()))
            {
                suggestions.add("give");
            }
            if (sender.hasPermission("theosiseconomy.command.economy.take") && "take".toUpperCase().startsWith(args[0].toUpperCase()))
            {
                suggestions.add("take");
            }
            if (sender.hasPermission("theosiseconomy.command.economy.reset") && "reset".toUpperCase().startsWith(args[0].toUpperCase()))
            {
                suggestions.add("reset");
            }
            if (sender.hasPermission("theosiseconomy.command.economy.reload") && "reload".toUpperCase().startsWith(args[0].toUpperCase()))
            {
                suggestions.add("reload");
            }
        }
        else if (args.length == 2 && ((args[0].equalsIgnoreCase("set") && sender.hasPermission("theosiseconomy.command.economy.set")) || (args[0].equalsIgnoreCase("give") && sender.hasPermission("theosiseconomy.command.economy.give")) || (args[0].equalsIgnoreCase("take") && sender.hasPermission("theosiseconomy.command.economy.take")) || (args[0].equalsIgnoreCase("reset") && sender.hasPermission("theosiseconomy.command.economy.reset"))))
        {
            for (Player player : Bukkit.getOnlinePlayers())
            {
                String playerName = player.getName();
                if (playerName.toUpperCase().startsWith(args[1].toUpperCase()))
                {
                    suggestions.add(playerName);
                }
            }
        }

        return !suggestions.isEmpty() ? suggestions : null;
    }

}