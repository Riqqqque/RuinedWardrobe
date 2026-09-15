package dev.rique.ruinedwardrobe.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SchedulerAdapter {

    TaskHandle runAsync(Runnable runnable);

    TaskHandle runGlobal(Runnable runnable);

    TaskHandle runPlayer(Player player, Runnable runnable);

    default TaskHandle runPlayer(Player player, Runnable runnable, Runnable retired) {
        return runPlayer(player, runnable);
    }

    TaskHandle runLocation(Location location, Runnable runnable);

    TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks);

    void shutdown();

    interface TaskHandle {
        void cancel();
    }
}

