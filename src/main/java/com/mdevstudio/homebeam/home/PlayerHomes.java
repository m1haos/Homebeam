package com.mdevstudio.homebeam.home;

import com.mdevstudio.homebeam.model.Home;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// Everything stored for one player: homes in the order they were set, the last known name for friends'
// labels, and whether this player wants to see pillars at all.
public final class PlayerHomes {

    private final UUID id;
    private final Map<String, Home> homes = new LinkedHashMap<>();
    private String name;
    private boolean beamsVisible = true;
    private @Nullable String focus;

    PlayerHomes(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    void rename(String newName) {
        name = newName;
    }

    public Collection<Home> homes() {
        return homes.values();
    }

    public @Nullable Home get(String homeName) {
        return homes.get(Home.key(homeName));
    }

    void put(Home home) {
        homes.put(home.key(), home);
    }

    // Renaming keeps the home in its place in the list, so /home list and colour order stay the same.
    void replace(String oldKey, Home home) {
        List<Home> ordered = new ArrayList<>(homes.values());
        homes.clear();
        for (Home existing : ordered) {
            Home kept = existing.key().equals(oldKey) ? home : existing;
            homes.put(kept.key(), kept);
        }
    }

    @Nullable Home remove(String homeName) {
        return homes.remove(Home.key(homeName));
    }

    public boolean beamsVisible() {
        return beamsVisible;
    }

    void setBeamsVisible(boolean visible) {
        beamsVisible = visible;
    }

    public @Nullable String focus() {
        return focus;
    }

    void setFocus(@Nullable String homeKey) {
        focus = homeKey;
    }
}
