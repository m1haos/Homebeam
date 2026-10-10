package com.mdevstudio.homebeam.beam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

// Kept in memory only: a mark that outlives a restart would point at loot that has long despawned.
final class DeathMarks {

    // A mark clears only after its owner has walked away from it once. The player is still standing on the
    // spot for a tick after dying, and a respawn point next to it must not erase the mark either.
    static final class Mark {

        private final Location at;
        private final long expiresAt;
        private boolean armed;

        private Mark(Location at, long expiresAt) {
            this.at = at;
            this.expiresAt = expiresAt;
        }

        Location at() {
            return at;
        }

        boolean armed() {
            return armed;
        }

        void arm() {
            armed = true;
        }

        long minutesLeft(long now) {
            return Math.max(1, (expiresAt - now + 59_999) / 60_000);
        }
    }

    private final Map<UUID, Mark> marks = new HashMap<>();

    void put(UUID player, Location at, int durationSeconds) {
        marks.put(player, new Mark(at.clone(), System.currentTimeMillis() + durationSeconds * 1000L));
    }

    @Nullable Mark get(UUID player) {
        Mark mark = marks.get(player);
        if (mark != null && mark.expiresAt < System.currentTimeMillis()) {
            marks.remove(player);
            return null;
        }
        return mark;
    }

    void clear(UUID player) {
        marks.remove(player);
    }
}
