package com.mdevstudio.homebeam.beam;

import com.mdevstudio.homebeam.config.PluginConfig;
import com.mdevstudio.homebeam.model.ParticleEffect;
import com.mdevstudio.homebeam.model.Waypoint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

// The pillars of one player. Each update moves them to their place and drops the ones no longer wanted.
final class BeamView {

    private static final double TICKS_PER_SECOND = 20;
    private static final double BREATH_PERIOD_TICKS = 60;
    private static final double SPARK_RADIUS = 0.45;
    private static final double SPARK_LIFT = 0.15;

    private final Plugin plugin;
    private final Map<String, Marker> markers = new HashMap<>();
    private long ticks;

    BeamView(Plugin plugin) {
        this.plugin = plugin;
    }

    void update(Player viewer, List<Waypoint> waypoints, PluginConfig settings) {
        PluginConfig.Beams beams = settings.beams();
        ticks += beams.updateInterval();
        float angle = (float) (beams.spinPerSecond() * ticks / TICKS_PER_SECOND % 360);
        float breath = (float) Math.sin(ticks * 2 * Math.PI / BREATH_PERIOD_TICKS);

        Set<String> wanted = new HashSet<>();
        for (Waypoint waypoint : waypoints) {
            Placement placement = place(viewer, waypoint, settings);
            if (placement == null) {
                continue;
            }
            wanted.add(waypoint.key());
            Marker marker = markers.get(waypoint.key());
            if (marker != null && !marker.isIn(viewer.getWorld())) {
                marker.remove();
                marker = null;
            }
            if (marker == null) {
                markers.put(waypoint.key(), Marker.spawn(plugin, viewer, placement, waypoint.color(), beams,
                        settings.modules().labels()));
                // The client must see the seed state before the change, or the pillar pops up without growing.
                continue;
            }
            double distance = viewer.getLocation().distance(waypoint.base());
            marker.place(placement, waypoint.color(), beams, angle, breath, waypoint.label().apply(distance));
            if (placement.anchored()) {
                sparks(viewer, waypoint, settings, distance);
            }
        }
        markers.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().remove();
            return true;
        });
    }

    void flash(String key) {
        Marker marker = markers.get(key);
        if (marker != null) {
            marker.flash();
        }
    }

    void clear() {
        markers.values().forEach(Marker::remove);
        markers.clear();
    }

    private static @Nullable Placement place(Player viewer, Waypoint waypoint, PluginConfig settings) {
        Location home = waypoint.base();
        Location eye = viewer.getEyeLocation();
        if (!home.getWorld().equals(eye.getWorld())) {
            return null;
        }
        PluginConfig.Beams beams = settings.beams();
        PluginConfig.Label style = beams.label();
        double distance = eye.distance(home);
        boolean loaded = home.getWorld().isChunkLoaded(home.getBlockX() >> 4, home.getBlockZ() >> 4);
        if (distance <= beams.anchorDistance() && loaded) {
            Location label = home.clone().add(0, style.height(), 0);
            return new Placement(home, label, labelScale(distance, style), true);
        }
        if (!settings.modules().horizon()) {
            return null;
        }
        // Far homes are drawn on a ring around the player, so the pillar always points the right way
        // and never leaves the loaded area.
        Vector flat = home.toVector().subtract(eye.toVector()).setY(0);
        if (flat.lengthSquared() < 1.0e-6) {
            return null;
        }
        flat.normalize().multiply(beams.anchorDistance());
        Location base = eye.clone().add(flat);
        // Right after a long teleport the ring may lie in chunks that are still loading.
        if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
            return null;
        }
        base.setY(Math.max(home.getY(), eye.getY() - beams.height() / 2));
        Location label = base.clone();
        label.setY(eye.getY() + style.height());
        return new Placement(base, label, labelScale(beams.anchorDistance(), style), false);
    }

    private static float labelScale(double distance, PluginConfig.Label style) {
        return (float) Math.clamp(distance / style.perBlocks(), style.minScale(), style.maxScale());
    }

    private void sparks(Player viewer, Waypoint waypoint, PluginConfig settings, double distance) {
        if (distance > settings.beams().baseParticlesRange()) {
            return;
        }
        ParticleEffect effect = settings.effects().beamBase();
        Object particleData = effect.dataFor(waypoint.color().rgb());
        Location base = waypoint.base();
        for (int i = 0; i < effect.count(); i++) {
            double angle = Math.random() * Math.PI * 2;
            viewer.spawnParticle(effect.particle(),
                    base.getX() + Math.cos(angle) * SPARK_RADIUS,
                    base.getY() + SPARK_LIFT + Math.random(),
                    base.getZ() + Math.sin(angle) * SPARK_RADIUS,
                    1, 0, 0.1, 0, effect.speed(), particleData);
        }
    }
}
