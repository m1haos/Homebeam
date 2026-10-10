package com.mdevstudio.homebeam.beam;

import com.mdevstudio.homebeam.config.PluginConfig;
import com.mdevstudio.homebeam.model.BeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

// One pillar with its label, as seen by one player. Both entities are hidden from everyone else.
final class Marker {

    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);
    private static final float SEED_HEIGHT = 0.01f;
    private static final float VIEW_RANGE = 4f;
    private static final int LABEL_LINE_WIDTH = 240;
    private static final float FLASH_WIDTH = 4f;
    private static final float FLASH_DECAY = 0.2f;

    private final BlockDisplay beam;
    private final @Nullable TextDisplay label;
    private String colorId;
    private @Nullable Component text;
    private boolean grown;
    private float flash;

    private Marker(BlockDisplay beam, @Nullable TextDisplay label, String colorId) {
        this.beam = beam;
        this.label = label;
        this.colorId = colorId;
    }

    static Marker spawn(Plugin plugin, Player viewer, Placement placement, BeamColor color,
            PluginConfig.Beams settings, boolean withLabel) {
        World world = placement.base().getWorld();
        BlockDisplay beam = world.spawn(placement.base(), BlockDisplay.class, display -> {
            hide(display);
            display.setBlock(color.block().createBlockData());
            display.setTransformation(prism(settings.width(), SEED_HEIGHT, 0));
            display.setTeleportDuration(settings.updateInterval());
            paint(display, color, settings.glow());
        });
        TextDisplay label = !withLabel ? null : world.spawn(placement.label(), TextDisplay.class, display -> {
            hide(display);
            PluginConfig.Label style = settings.label();
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(LABEL_LINE_WIDTH);
            display.setDefaultBackground(false);
            display.setBackgroundColor(style.background());
            display.setSeeThrough(style.seeThrough());
            display.setShadowed(false);
            display.setTeleportDuration(settings.updateInterval());
            display.setTransformation(scale(placement.labelScale()));
        });
        viewer.showEntity(plugin, beam);
        if (label != null) {
            viewer.showEntity(plugin, label);
        }
        return new Marker(beam, label, color.id());
    }

    void place(Placement placement, BeamColor color, PluginConfig.Beams settings, float angle, float breath,
            Component labelText) {
        if (!color.id().equals(colorId)) {
            colorId = color.id();
            beam.setBlock(color.block().createBlockData());
            paint(beam, color, settings.glow());
        }
        move(beam, placement.base());

        float width = settings.width() * (1 + settings.pulse() * breath) * (1 + FLASH_WIDTH * flash);
        flash = Math.max(0, flash - FLASH_DECAY);
        beam.setInterpolationDelay(0);
        beam.setInterpolationDuration(grown ? settings.updateInterval() : settings.growTicks());
        beam.setTransformation(prism(width, settings.height(), angle));
        grown = true;

        if (label != null) {
            move(label, placement.label());
            label.setInterpolationDelay(0);
            label.setInterpolationDuration(settings.updateInterval());
            label.setTransformation(scale(placement.labelScale()));
            if (!labelText.equals(text)) {
                text = labelText;
                label.text(labelText);
            }
        }
    }

    void flash() {
        flash = 1;
    }

    boolean isIn(World world) {
        return beam.isValid() && beam.getWorld().equals(world);
    }

    void remove() {
        beam.remove();
        if (label != null) {
            label.remove();
        }
    }

    private static void move(Entity entity, Location to) {
        if (entity.getLocation().distanceSquared(to) > 1.0e-4) {
            entity.teleport(to);
        }
    }

    private static void hide(Display display) {
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setBrightness(FULL_BRIGHT);
        display.setViewRange(VIEW_RANGE);
    }

    private static void paint(BlockDisplay display, BeamColor color, boolean glow) {
        display.setGlowing(glow);
        display.setGlowColorOverride(color.rgb());
    }

    // A block display scales from its corner; the translation keeps the prism centred while it turns.
    private static Transformation prism(float width, float height, float angleDegrees) {
        Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(angleDegrees));
        Vector3f offset = new Vector3f(-width / 2, 0, -width / 2).rotate(turn);
        return new Transformation(offset, turn, new Vector3f(width, height, width), new Quaternionf());
    }

    private static Transformation scale(float factor) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(factor), new Quaternionf());
    }
}
