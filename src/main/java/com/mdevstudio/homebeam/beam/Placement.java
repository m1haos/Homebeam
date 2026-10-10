package com.mdevstudio.homebeam.beam;

import org.bukkit.Location;

// Where a pillar stands for one player right now: on the home itself, or on the horizon in its direction.
record Placement(Location base, Location label, float labelScale, boolean anchored) {
}
