package com.mdevstudio.homebeam.home;

import com.mdevstudio.homebeam.model.Home;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

final class HomeStorage {

    static final String FILE = "homes.yml";

    private final File file;
    private final Logger logger;
    private final Consumer<Runnable> async;
    private final AtomicLong revision = new AtomicLong();
    private long writtenRevision;

    HomeStorage(File dataFolder, Logger logger, Consumer<Runnable> async) {
        this.file = new File(dataFolder, FILE);
        this.logger = logger;
        this.async = async;
    }

    Map<UUID, PlayerHomes> load() {
        Map<UUID, PlayerHomes> players = new LinkedHashMap<>();
        if (!file.exists()) {
            return players;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
        if (root == null) {
            return players;
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                logger.error("homes.yml: '{}' is not a player UUID. Left in the file and not loaded.", rawId);
                continue;
            }
            if (section == null) {
                continue;
            }
            PlayerHomes player = new PlayerHomes(id, section.getString("name", rawId));
            player.setBeamsVisible(section.getBoolean("beams", true));
            String focus = section.getString("focus", "");
            player.setFocus(focus.isEmpty() ? null : focus);
            for (Map<?, ?> raw : section.getMapList("homes")) {
                try {
                    player.put(read(id, raw));
                } catch (IllegalArgumentException | ClassCastException e) {
                    logger.error("homes.yml: a home of {} is damaged ({}). Skipped.", player.name(), raw);
                }
            }
            players.put(id, player);
        }
        return players;
    }

    // The snapshot is taken on the caller's thread, so the background write never reads live state.
    void saveAsync(Collection<PlayerHomes> players) {
        String content = serialize(players);
        long current = revision.incrementAndGet();
        async.accept(() -> write(content, current));
    }

    void saveNow(Collection<PlayerHomes> players) {
        write(serialize(players), revision.incrementAndGet());
    }

    private static Home read(UUID owner, Map<?, ?> raw) {
        Object name = raw.get("name");
        Object world = raw.get("world");
        if (name == null || world == null) {
            throw new IllegalArgumentException("no name or world");
        }
        Set<UUID> guests = new HashSet<>();
        if (raw.get("guests") instanceof List<?> list) {
            for (Object guest : list) {
                guests.add(UUID.fromString(String.valueOf(guest)));
            }
        }
        return new Home(owner, String.valueOf(name), String.valueOf(world),
                number(raw, "x"), number(raw, "y"), number(raw, "z"),
                (float) number(raw, "yaw"), (float) number(raw, "pitch"),
                String.valueOf(raw.get("color")), Set.copyOf(guests));
    }

    private static double number(Map<?, ?> raw, String key) {
        return raw.get(key) instanceof Number value ? value.doubleValue() : 0;
    }

    private static String serialize(Collection<PlayerHomes> players) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Saved by Homebeam. Edit only while the server is stopped."));
        for (PlayerHomes player : players) {
            String path = "players." + player.id();
            yaml.set(path + ".name", player.name());
            yaml.set(path + ".beams", player.beamsVisible());
            yaml.set(path + ".focus", player.focus() == null ? "" : player.focus());
            List<Map<String, Object>> homes = new ArrayList<>();
            for (Home home : player.homes()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", home.name());
                entry.put("world", home.world());
                entry.put("x", round(home.x()));
                entry.put("y", round(home.y()));
                entry.put("z", round(home.z()));
                entry.put("yaw", round(home.yaw()));
                entry.put("pitch", round(home.pitch()));
                entry.put("color", home.color());
                entry.put("guests", home.guests().stream().map(UUID::toString).sorted().toList());
                homes.add(entry);
            }
            yaml.set(path + ".homes", homes);
        }
        return yaml.saveToString();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    // Background writes may finish out of order, and an older snapshot must never replace a newer one.
    private synchronized void write(String content, long snapshotRevision) {
        if (snapshotRevision < writtenRevision) {
            return;
        }
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File staging = new File(file.getParentFile(), FILE + ".tmp");
            Files.writeString(staging.toPath(), content, StandardCharsets.UTF_8);
            Files.move(staging.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            writtenRevision = snapshotRevision;
        } catch (IOException e) {
            logger.error("Could not save {}: {}", FILE, e.getMessage());
        }
    }
}
