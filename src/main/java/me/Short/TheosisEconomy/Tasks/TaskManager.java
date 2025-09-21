package me.Short.TheosisEconomy.Tasks;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public abstract class TaskManager {
    public abstract void run();

    private BukkitTask bukkitTask;
    private ScheduledTask scheduledTask;

    public BukkitTask getBukkitTask() { return bukkitTask; }
    public ScheduledTask getScheduledTask() { return scheduledTask; }

    public void runTaskTimer(JavaPlugin plugin, long delay, long period){
        if(!TheosisEconomy.isFolia()){
            bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this::run, delay, period);
        }else{
            Consumer<ScheduledTask> consumer = (x) -> run();
            scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, consumer, Math.max(delay, 1), period);
        }
    }

    public void safetyTaskCancel(){
        if(bukkitTask != null && !bukkitTask.isCancelled()) bukkitTask.cancel();
        if(scheduledTask != null && !scheduledTask.isCancelled()) scheduledTask.cancel();
    }
}
