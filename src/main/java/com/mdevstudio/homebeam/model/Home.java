package com.mdevstudio.homebeam.model;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public record Home(
        UUID owner,
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String color,
        Set<UUID> guests) {

    public static Home at(UUID owner, String name, Location location, String color) {
        return new Home(owner, name, location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch(), color, Set.of());
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public String key() {
        return key(name);
    }

    public Location location(World in) {
        return new Location(in, x, y, z, yaw, pitch);
    }

    public Home movedTo(Location location) {
        return new Home(owner, name, location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch(), color, guests);
    }

    public Home renamed(String newName) {
        return new Home(owner, newName, world, x, y, z, yaw, pitch, color, guests);
    }

    public Home colored(String newColor) {
        return new Home(owner, name, world, x, y, z, yaw, pitch, newColor, guests);
    }

    public Home withGuests(Set<UUID> newGuests) {
        return new Home(owner, name, world, x, y, z, yaw, pitch, color, Set.copyOf(newGuests));
    }
}
