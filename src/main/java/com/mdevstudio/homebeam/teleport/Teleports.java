package com.mdevstudio.homebeam.teleport;

import com.mdevstudio.homebeam.Homebeam;
import com.mdevstudio.homebeam.Permissions;
import com.mdevstudio.homebeam.config.Messages;
import com.mdevstudio.homebeam.config.PluginConfig;
import com.mdevstudio.homebeam.model.BeamColor;
import com.mdevstudio.homebeam.model.Home;
import com.mdevstudio.homebeam.model.ParticleEffect;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

// Warmup with a spiral around the player, then the jump. The pillar of the destination flashes on arrival.
public final class Teleports implements Listener {

    private static final int STEP_TICKS = 2;
    private static final int TICKS_PER_SECOND = 20;
    private static final int BAR_SEGMENTS = 12;
    private static final String BAR_SYMBOL = "■";
    private static final double SPIRAL_TURN_PER_TICK = 0.35;
    private static final double TRAIL_DROP = 0.35;
    private static final int RING_POINTS = 24;
    private static final float LOWEST_PITCH = 0.7f;
    private static final float PITCH_RANGE = 1.1f;

    public enum Start { STARTED, ALREADY_WARMING, COOLDOWN, NO_WORLD }

    private final Homebeam plugin;
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Map<UUID, Long> lastJump = new HashMap<>();

    private static final class Warmup {

        private final Home home;
        private final String beamKey;
        private final Location start;
        private final int totalTicks;
        private int elapsed;
        private ScheduledTask task;

        private Warmup(Home home, String beamKey, Location start, int totalTicks) {
            this.home = home;
            this.beamKey = beamKey;
            this.start = start;
            this.totalTicks = totalTicks;
        }
    }

    public Teleports(Homebeam plugin) {
        this.plugin = plugin;
    }

    public long cooldownLeft(Player player) {
        if (player.hasPermission(Permissions.BYPASS_COOLDOWN)) {
            return 0;
        }
        long readyAt = lastJump.getOrDefault(player.getUniqueId(), 0L)
                + plugin.settings().teleport().cooldownSeconds() * 1000L;
        return Math.max(0, (readyAt - System.currentTimeMillis() + 999) / 1000);
    }

    public Start begin(Player player, Home home, String beamKey) {
        if (warmups.containsKey(player.getUniqueId())) {
            return Start.ALREADY_WARMING;
        }
        if (cooldownLeft(player) > 0) {
            return Start.COOLDOWN;
        }
        if (plugin.getServer().getWorld(home.world()) == null) {
            return Start.NO_WORLD;
        }
        int seconds = player.hasPermission(Permissions.BYPASS_WARMUP) ? 0
                : plugin.settings().teleport().warmupSeconds();
        Warmup warmup = new Warmup(home, beamKey, player.getLocation(), seconds * TICKS_PER_SECOND);
        if (warmup.totalTicks == 0) {
            jump(player, warmup);
            return Start.STARTED;
        }
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, ignored -> step(player, warmup),
                () -> warmups.remove(player.getUniqueId()), 1, STEP_TICKS);
        if (task == null) {
            return Start.NO_WORLD;
        }
        warmup.task = task;
        warmups.put(player.getUniqueId(), warmup);
        return Start.STARTED;
    }

    public void cancelAll() {
        warmups.values().forEach(warmup -> warmup.task.cancel());
        warmups.clear();
    }

    private void step(Player player, Warmup warmup) {
        PluginConfig settings = plugin.settings();
        warmup.elapsed += STEP_TICKS;
        if (warmup.elapsed >= warmup.totalTicks) {
            stop(player);
            jump(player, warmup);
            return;
        }
        double progress = (double) warmup.elapsed / warmup.totalTicks;
        BeamColor color = settings.color(warmup.home.color());
        spiral(player.getLocation(), warmup.elapsed, progress, settings.effects().warmup(), color);
        if (warmup.elapsed % TICKS_PER_SECOND < STEP_TICKS) {
            play(player.getLocation(), settings.sounds().warmupTick(), LOWEST_PITCH + PITCH_RANGE * (float) progress);
        }
        int secondsLeft = (warmup.totalTicks - warmup.elapsed + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        player.sendActionBar(plugin.messages().component(Messages.TELEPORT_WARMUP,
                Placeholder.component("bar", bar(progress, color)),
                Placeholder.styling("tint", color.text()),
                Placeholder.unparsed("name", warmup.home.name()),
                Placeholder.unparsed("seconds", String.valueOf(secondsLeft))));
    }

    private void jump(Player player, Warmup warmup) {
        World world = plugin.getServer().getWorld(warmup.home.world());
        if (world == null) {
            plugin.messages().send(player, Messages.TELEPORT_WORLD_MISSING,
                    Placeholder.unparsed("world", warmup.home.world()));
            return;
        }
        PluginConfig settings = plugin.settings();
        BeamColor color = settings.color(warmup.home.color());
        Location from = player.getLocation();
        burst(from.clone().add(0, 1, 0), settings.effects().departure(), color);
        play(from, settings.sounds().departure(), 1f);
        lastJump.put(player.getUniqueId(), System.currentTimeMillis());
        player.teleportAsync(warmup.home.location(world), PlayerTeleportEvent.TeleportCause.PLUGIN)
                .thenAccept(moved -> player.getScheduler().run(plugin, ignored -> {
                    if (!moved) {
                        return;
                    }
                    Location to = player.getLocation();
                    burst(to.clone().add(0, 1, 0), settings.effects().arrival(), color);
                    ring(to, settings.effects().warmup(), color);
                    play(to, settings.sounds().arrival(), 1f);
                    plugin.messages().send(player, Messages.TELEPORT_DONE,
                            Placeholder.styling("tint", color.text()),
                            Placeholder.unparsed("name", warmup.home.name()));
                    plugin.beams().flash(player, warmup.beamKey);
                }, null));
    }

    private void cancel(Player player, String reasonKey) {
        if (stop(player)) {
            plugin.messages().send(player, reasonKey);
            player.sendActionBar(Component.empty());
            play(player.getLocation(), plugin.settings().sounds().cancel(), 1f);
        }
    }

    private boolean stop(Player player) {
        Warmup warmup = warmups.remove(player.getUniqueId());
        if (warmup == null) {
            return false;
        }
        warmup.task.cancel();
        return true;
    }

    private static void spiral(Location feet, int elapsed, double progress, ParticleEffect effect,
            BeamColor color) {
        Object particleData = effect.dataFor(color.rgb());
        World world = feet.getWorld();
        double height = progress * effect.height();
        for (int strand = 0; strand < effect.strands(); strand++) {
            double angle = elapsed * SPIRAL_TURN_PER_TICK + strand * Math.PI * 2 / effect.strands();
            for (double drop = 0; drop <= TRAIL_DROP; drop += TRAIL_DROP) {
                double trailAngle = angle - drop;
                world.spawnParticle(effect.particle(),
                        feet.getX() + Math.cos(trailAngle) * effect.radius(),
                        feet.getY() + Math.max(0, height - drop),
                        feet.getZ() + Math.sin(trailAngle) * effect.radius(),
                        1, 0, 0, 0, effect.speed(), particleData);
            }
        }
    }

    private static void ring(Location feet, ParticleEffect effect, BeamColor color) {
        Object particleData = effect.dataFor(color.rgb());
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = i * Math.PI * 2 / RING_POINTS;
            feet.getWorld().spawnParticle(effect.particle(),
                    feet.getX() + Math.cos(angle) * effect.radius(), feet.getY() + 0.1,
                    feet.getZ() + Math.sin(angle) * effect.radius(),
                    1, 0, 0, 0, 0, particleData);
        }
    }

    private static void burst(Location at, ParticleEffect effect, BeamColor color) {
        at.getWorld().spawnParticle(effect.particle(), at, effect.count(),
                effect.spread(), effect.spread() * 2, effect.spread(), effect.speed(), effect.dataFor(color.rgb()));
    }

    private static Component bar(double progress, BeamColor color) {
        int filled = (int) Math.round(progress * BAR_SEGMENTS);
        return Component.text(BAR_SYMBOL.repeat(filled), color.text())
                .append(Component.text(BAR_SYMBOL.repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
    }

    private static void play(Location at, Key sound, float pitch) {
        at.getWorld().playSound(Sound.sound(sound, Sound.Source.PLAYER, 1f, pitch), at.getX(), at.getY(), at.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock() && plugin.settings().teleport().cancelOnMove()
                && warmups.containsKey(event.getPlayer().getUniqueId())) {
            cancel(event.getPlayer(), Messages.TELEPORT_CANCEL_MOVE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.settings().teleport().cancelOnDamage()
                && warmups.containsKey(player.getUniqueId())) {
            cancel(player, Messages.TELEPORT_CANCEL_DAMAGE);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }
}
