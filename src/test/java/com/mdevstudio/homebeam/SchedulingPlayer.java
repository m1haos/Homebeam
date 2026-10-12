package com.mdevstudio.homebeam;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

// MockBukkit's entity scheduler returns tasks whose cancel() is not implemented, and Homebeam cancels
// the beam task of every online player on disable. Without this, unmock() stops halfway and the next
// test fails with "Already mocking".
final class SchedulingPlayer extends PlayerMock {

    private final EntityScheduler scheduler;

    SchedulingPlayer(ServerMock server, String name) {
        super(server, name);
        this.scheduler = new Scheduler(server.getScheduler());
    }

    @Override
    public EntityScheduler getScheduler() {
        return scheduler;
    }

    private final class Scheduler implements EntityScheduler {

        private final BukkitScheduler bukkit;

        private Scheduler(BukkitScheduler bukkit) {
            this.bukkit = bukkit;
        }

        @Override
        public boolean execute(Plugin plugin, Runnable run, @Nullable Runnable retired, long delayTicks) {
            schedule(plugin, ignored -> run.run(), retired, delayTicks, 0);
            return isValid();
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired) {
            return schedule(plugin, task, retired, 0, 0);
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired,
                long delayTicks) {
            return schedule(plugin, task, retired, delayTicks, 0);
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired,
                long initialDelayTicks, long periodTicks) {
            return schedule(plugin, task, retired, initialDelayTicks, periodTicks);
        }

        private ScheduledTask schedule(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired,
                long delay, long period) {
            CancellableTask scheduled = new CancellableTask(plugin, period > 0);
            Runnable body = () -> {
                if (isValid()) {
                    task.accept(scheduled);
                } else if (retired != null) {
                    retired.run();
                }
            };
            scheduled.handle = period > 0
                    ? bukkit.runTaskTimer(plugin, body, delay, period)
                    : bukkit.runTaskLater(plugin, body, delay);
            return scheduled;
        }
    }

    private static final class CancellableTask implements ScheduledTask {

        private final Plugin plugin;
        private final boolean repeating;
        private @Nullable BukkitTask handle;

        private CancellableTask(Plugin plugin, boolean repeating) {
            this.plugin = plugin;
            this.repeating = repeating;
        }

        @Override
        public Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return repeating;
        }

        @Override
        public CancelledState cancel() {
            BukkitTask task = handle;
            if (task == null || task.isCancelled()) {
                return CancelledState.CANCELLED_ALREADY;
            }
            task.cancel();
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public ExecutionState getExecutionState() {
            BukkitTask task = handle;
            return task != null && task.isCancelled() ? ExecutionState.CANCELLED : ExecutionState.IDLE;
        }
    }
}
