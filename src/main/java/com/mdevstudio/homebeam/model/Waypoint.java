package com.mdevstudio.homebeam.model;

import java.util.function.DoubleFunction;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

// Anything a player sees as a pillar: own homes, friends' homes, the place of death.
// The label depends on the distance, so it is built by the view on every update.
public record Waypoint(String key, Location base, BeamColor color, DoubleFunction<Component> label) {
}
