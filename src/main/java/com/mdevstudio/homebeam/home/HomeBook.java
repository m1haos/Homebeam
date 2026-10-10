package com.mdevstudio.homebeam.home;

import com.mdevstudio.homebeam.model.Home;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

// All homes on the server. Every change is written to homes.yml in the background right away:
// homes change rarely, and a crash should never cost a player the home they just set.
public final class HomeBook {

    private final Map<UUID, PlayerHomes> players = new HashMap<>();
    private final Map<UUID, List<Home>> sharedWith = new HashMap<>();
    private final HomeStorage storage;

    public HomeBook(File dataFolder, Logger logger, Consumer<Runnable> async) {
        this.storage = new HomeStorage(dataFolder, logger, async);
    }

    public void load() {
        players.clear();
        players.putAll(storage.load());
        reindex();
    }

    public void saveNow() {
        storage.saveNow(players.values());
    }

    public PlayerHomes of(Player player) {
        PlayerHomes homes = players.computeIfAbsent(player.getUniqueId(),
                id -> new PlayerHomes(id, player.getName()));
        if (!homes.name().equals(player.getName())) {
            homes.rename(player.getName());
            changed();
        }
        return homes;
    }

    public @Nullable PlayerHomes find(UUID id) {
        return players.get(id);
    }

    public @Nullable PlayerHomes byName(String name) {
        return players.values().stream().filter(homes -> homes.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public List<Home> sharedWith(UUID guest) {
        return sharedWith.getOrDefault(guest, List.of());
    }

    public int total() {
        return players.values().stream().mapToInt(homes -> homes.homes().size()).sum();
    }

    public void put(PlayerHomes owner, Home home) {
        owner.put(home);
        changed();
    }

    public void replace(PlayerHomes owner, String oldKey, Home home) {
        owner.replace(oldKey, home);
        changed();
    }

    public @Nullable Home remove(PlayerHomes owner, String name) {
        Home removed = owner.remove(name);
        if (removed != null) {
            changed();
        }
        return removed;
    }

    public void setView(PlayerHomes owner, boolean visible, @Nullable String focus) {
        owner.setBeamsVisible(visible);
        owner.setFocus(focus);
        changed();
    }

    private void changed() {
        reindex();
        storage.saveAsync(new ArrayList<>(players.values()));
    }

    private void reindex() {
        sharedWith.clear();
        for (PlayerHomes owner : players.values()) {
            for (Home home : owner.homes()) {
                for (UUID guest : home.guests()) {
                    sharedWith.computeIfAbsent(guest, id -> new ArrayList<>()).add(home);
                }
            }
        }
    }
}
