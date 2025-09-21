package me.Short.TheosisEconomy.Managers;

import me.Short.TheosisEconomy.TheosisEconomy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class BaltopManager
{
    // Config options that may need to be retrieved in the "updateBaltop" method later
    private boolean baltopConsiderExcludePermission;
    private boolean baltopExcludeBannedPlayers;
    private BigDecimal baltopMinBalance;

    // Combined total of all players' balances - updated in "updateBaltop"
    private BigDecimal combinedTotalBalance;

    // Baltop
    private Map<String, BigDecimal> baltop = new LinkedHashMap<>();

    // Constructor
    public BaltopManager() {
        // Set initial "combinedTotalBalance" to 0
        this.combinedTotalBalance = BigDecimal.ZERO;

        // Other runtime loads
        reload();
    }

    // Reload
    public void reload(){
        // Get config options from config.yml here, so they don't need to be gotten in the async "updateBaltop" method later
        baltopConsiderExcludePermission = TheosisEconomy.getInstance().getConfig().getBoolean("settings.baltop.consider-exclude-permission");
        baltopExcludeBannedPlayers = TheosisEconomy.getInstance().getConfig().getBoolean("settings.baltop.exclude-banned-players");
        baltopMinBalance = new BigDecimal(TheosisEconomy.getInstance().getConfig().getString("settings.baltop.min-balance"));
    }

    // Getter & Setter for "baltop"
    public Map<String, BigDecimal> getBaltop() {
        return baltop;
    }
    public void setBaltop(Map<String, BigDecimal> baltop) {
        this.baltop = baltop;
    }

    // Getter & Setter for "combinedTotalBalance"
    public BigDecimal getCombinedTotalBalance() {
        return combinedTotalBalance;
    }
    public void setCombinedTotalBalance(BigDecimal combinedTotalBalance){
        this.combinedTotalBalance = combinedTotalBalance;
    }

    // Getter for "baltopConsiderExcludePermission"
    public Boolean isBaltopConsiderExcludePermission() {
        return baltopConsiderExcludePermission;
    }
    // Getter for "baltopExcludeBannedPlayers"
    public Boolean isBaltopExcludeBannedPlayers() {
        return baltopExcludeBannedPlayers;
    }
    // Getter for "baltopMinBalance"
    public BigDecimal getBaltopMinBalance(){
        return baltopMinBalance;
    }
}
