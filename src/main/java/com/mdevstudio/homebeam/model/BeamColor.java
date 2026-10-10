package com.mdevstudio.homebeam.model;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

public record BeamColor(String id, Material block, Color rgb) {

    public TextColor text() {
        return TextColor.color(rgb.asRGB());
    }
}
