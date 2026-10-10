package com.mdevstudio.homebeam.beam;

import com.mdevstudio.homebeam.Homebeam;
import com.mdevstudio.homebeam.Permissions;
import com.mdevstudio.homebeam.config.Messages;
import com.mdevstudio.homebeam.config.PluginConfig;
import com.mdevstudio.homebeam.home.PlayerHomes;
import com.mdevstudio.homebeam.model.BeamColor;
import com.mdevstudio.homebeam.model.Home;
import com.mdevstudio.homebeam.model.Waypoint;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class Beams implements Listener {

    private static final String OWN = "own:";
    private static final String GUEST = "guest:";
    private static final String DEATH = "death";

    private final Homebeam plugin;
    private final Map<UUID, BeamView> views = new HashMap<>();
    private final Map<UUID, ScheduledTask> tasks = new HashMap<>();
    private final DeathMarks deathMarks = new DeathMarks();

    public Beams(Homebeam plugin) {
        this.plugin = plugin;
    }

    public static String ownKey(Home home) {
        return OWN + home.key();
    }

    public static String guestKey(Home home) {
        return GUEST + home.owner() + ":" + home.key();
    }

    public void startAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::start);
    }

    // Settings such as the update interval or the label style apply to pillars spawned after a reload.
    public void restartAll() {
        stopAll();
        startAll();
    }

    public void stopAll() {
        new ArrayList<>(tasks.keySet()).forEach(this::stop);
    }

    public void flash(Player viewer, String key) {
        BeamView view = views.get(viewer.getUniqueId());
        if (view != null) {
            view.flash(key);
        }
    }

    private void start(Player player) {
        stop(player.getUniqueId());
        UUID id = player.getUniqueId();
        BeamView view = new BeamView(plugin);
        views.put(id, view);
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, ignored -> tick(player, view),
                view::clear, 1, plugin.settings().beams().updateInterval());
        if (task != null) {
            tasks.put(id, task);
        }
    }

    private void stop(UUID id) {
        ScheduledTask task = tasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        BeamView view = views.remove(id);
        if (view != null) {
            view.clear();
        }
    }

    private void tick(Player player, BeamView view) {
        PluginConfig settings = plugin.settings();
        checkDeathMark(player, settings);
        if (!settings.modules().beams()) {
            view.clear();
            return;
        }
        view.update(player, waypoints(player, settings), settings);
    }

    private List<Waypoint> waypoints(Player player, PluginConfig settings) {
        List<Waypoint> waypoints = new ArrayList<>();
        PlayerHomes own = plugin.homes().find(player.getUniqueId());
        if (own != null && !own.beamsVisible()) {
            return waypoints;
        }
        World world = player.getWorld();
        String focus = own == null ? null : own.focus();
        if (own != null) {
            boolean focusExists = focus != null && own.get(focus) != null;
            for (Home home : own.homes()) {
                if (home.world().equals(world.getName()) && (!focusExists || home.key().equals(focus))) {
                    waypoints.add(waypoint(ownKey(home), home, world, settings, Messages.LABEL_OWN, null));
                }
            }
        }
        if (settings.modules().sharing() && focus == null) {
            for (Home home : plugin.homes().sharedWith(player.getUniqueId())) {
                PlayerHomes owner = plugin.homes().find(home.owner());
                if (home.world().equals(world.getName()) && owner != null) {
                    waypoints.add(waypoint(guestKey(home), home, world, settings, Messages.LABEL_GUEST, owner));
                }
            }
        }
        DeathMarks.Mark mark = settings.modules().deathMark() ? deathMarks.get(player.getUniqueId()) : null;
        if (mark != null && mark.at().getWorld().equals(world)) {
            BeamColor color = settings.color(settings.deathMark().color());
            Messages messages = plugin.messages();
            waypoints.add(new Waypoint(DEATH, mark.at(), color, distance -> messages.component(Messages.LABEL_DEATH,
                    Placeholder.styling("tint", color.text()),
                    Placeholder.unparsed("distance", messages.distance(distance)),
                    Placeholder.unparsed("minutes", String.valueOf(mark.minutesLeft(System.currentTimeMillis()))))));
        }
        return waypoints;
    }

    private Waypoint waypoint(String key, Home home, World world, PluginConfig settings, String template,
            PlayerHomes owner) {
        BeamColor color = settings.color(home.color());
        Messages messages = plugin.messages();
        Location base = new Location(world, Math.floor(home.x()) + 0.5, home.y(), Math.floor(home.z()) + 0.5);
        Component head = Component.object(ObjectContents.playerHead(home.owner()));
        String ownerName = owner == null ? "" : owner.name();
        return new Waypoint(key, base, color, distance -> messages.component(template,
                Placeholder.styling("tint", color.text()),
                Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("owner", ownerName),
                Placeholder.component("head", head),
                Placeholder.unparsed("distance", messages.distance(distance))));
    }

    private void checkDeathMark(Player player, PluginConfig settings) {
        DeathMarks.Mark mark = deathMarks.get(player.getUniqueId());
        if (mark == null || !mark.at().getWorld().equals(player.getWorld())) {
            return;
        }
        double distance = player.getLocation().distance(mark.at());
        double radius = settings.deathMark().clearRadius();
        if (distance > radius * 2) {
            mark.arm();
        } else if (distance <= radius && mark.armed() && !player.isDead()) {
            deathMarks.clear(player.getUniqueId());
            plugin.messages().send(player, Messages.DEATH_MARK_CLEARED);
            play(player, settings.sounds().deathMarkCleared());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.homes().of(event.getPlayer());
        start(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        BeamView view = views.get(event.getPlayer().getUniqueId());
        if (view != null) {
            view.clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PluginConfig settings = plugin.settings();
        if (!settings.modules().deathMark() || !player.hasPermission(Permissions.DEATH_MARK)) {
            return;
        }
        int duration = settings.deathMark().durationSeconds();
        deathMarks.put(player.getUniqueId(), player.getLocation(), duration);
        plugin.messages().send(player, Messages.DEATH_MARK_SET,
                Placeholder.unparsed("minutes", String.valueOf(Math.max(1, duration / 60))));
    }

    private static void play(Player player, Key sound) {
        player.playSound(Sound.sound(sound, Sound.Source.PLAYER, 1f, 1f));
    }
}
