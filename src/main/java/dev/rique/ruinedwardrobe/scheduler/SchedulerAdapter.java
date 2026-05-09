package dev.rique.ruinedwardrobe.scheduler;

import org.bukkit.entity.Player;

public interface SchedulerAdapter {

    TaskHandle runAsync(Runnable runnable);

    TaskHandle runGlobal(Runnable runnable);

    TaskHandle runPlayer(Player player, Runnable runnable);

    TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks);

    void shutdown();

    interface TaskHandle {
        void cancel();
    }
}

