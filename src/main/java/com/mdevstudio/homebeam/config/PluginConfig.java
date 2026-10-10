package com.mdevstudio.homebeam.config;

import com.mdevstudio.homebeam.model.BeamColor;
import com.mdevstudio.homebeam.model.ParticleEffect;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public record PluginConfig(
        String language,
        Modules modules,
        List<String> commandAliases,
        Map<String, List<String>> subcommandAliases,
        Homes homes,
        Teleport teleport,
        Beams beams,
        DeathMark deathMark,
        Map<String, BeamColor> colors,
        Effects effects,
        Sounds sounds) {

    public record Modules(
            boolean beams,
            boolean labels,
            boolean horizon,
            boolean teleport,
            boolean sharing,
            boolean deathMark) {
    }

    public record Homes(int defaultLimit, int maxNameLength, Set<String> blockedWorlds) {
    }

    public record Teleport(
            int warmupSeconds,
            int cooldownSeconds,
            boolean cancelOnMove,
            boolean cancelOnDamage,
            boolean guestsCanTeleport) {
    }

    public record Label(
            double height,
            double perBlocks,
            float minScale,
            float maxScale,
            Color background,
            boolean seeThrough) {
    }

    public record Beams(
            float height,
            float width,
            float spinPerSecond,
            float pulse,
            boolean glow,
            int growTicks,
            double anchorDistance,
            int updateInterval,
            double baseParticlesRange,
            Label label) {
    }

    public record DeathMark(int durationSeconds, double clearRadius, String color) {
    }

    public record Effects(
            ParticleEffect warmup,
            ParticleEffect departure,
            ParticleEffect arrival,
            ParticleEffect beamBase) {
    }

    public record Sounds(
            Key homeSet,
            Key warmupTick,
            Key departure,
            Key arrival,
            Key cancel,
            Key deathMarkCleared) {
    }

    private static final int VERSION = 1;
    private static final String DEFAULT_LANGUAGE = "ru";
    private static final BeamColor FALLBACK_COLOR =
            new BeamColor("white", Material.WHITE_STAINED_GLASS, Color.fromRGB(0xF2F4FF));

    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration yaml = plugin.getConfig();
        Reader reader = new Reader(yaml, plugin.getSLF4JLogger());

        if (yaml.getInt("config-version") < VERSION) {
            // Admins keep their values; only keys added in newer versions are written in.
            yaml.options().copyDefaults(true);
            yaml.set("config-version", VERSION);
            plugin.saveConfig();
        }

        String language = yaml.getString("language", DEFAULT_LANGUAGE);
        if (!Messages.LANGUAGES.contains(language)) {
            reader.logger().warn("config.yml: language '{}' is not available, expected one of {}. Using '{}'.",
                    language, Messages.LANGUAGES, DEFAULT_LANGUAGE);
            language = DEFAULT_LANGUAGE;
        }

        Map<String, List<String>> subcommands = new LinkedHashMap<>();
        ConfigurationSection subSection = yaml.getConfigurationSection("commands.subcommands");
        if (subSection != null) {
            for (String name : subSection.getKeys(false)) {
                subcommands.put(name, subSection.getStringList(name));
            }
        }

        Map<String, BeamColor> colors = reader.colors();
        String deathColor = yaml.getString("death-mark.color", "red");
        if (!colors.containsKey(deathColor)) {
            String fallback = colors.keySet().iterator().next();
            reader.logger().warn("config.yml: death-mark.color '{}' is not in colors. Using '{}'.",
                    deathColor, fallback);
            deathColor = fallback;
        }

        return new PluginConfig(
                language,
                reader.modules(),
                yaml.getStringList("commands.aliases"),
                subcommands,
                new Homes(
                        reader.atLeast("homes.default-limit", 3, 0),
                        reader.atLeast("homes.max-name-length", 24, 1),
                        Set.copyOf(yaml.getStringList("homes.blocked-worlds"))),
                new Teleport(
                        reader.atLeast("teleport.warmup", 3, 0),
                        reader.atLeast("teleport.cooldown", 10, 0),
                        yaml.getBoolean("teleport.cancel-on-move", true),
                        yaml.getBoolean("teleport.cancel-on-damage", true),
                        yaml.getBoolean("teleport.guests-can-teleport", true)),
                reader.beams(),
                new DeathMark(
                        reader.atLeast("death-mark.duration", 600, 1),
                        reader.positive("death-mark.clear-radius", 4),
                        deathColor),
                colors,
                reader.effects(),
                reader.sounds());
    }

    public List<String> aliasesOf(String subcommand) {
        return subcommandAliases.getOrDefault(subcommand, List.of());
    }

    public BeamColor color(String id) {
        BeamColor color = colors.get(id);
        return color != null ? color : colors.values().iterator().next();
    }

    // Every warning names the exact key, the bad value and what is used instead.
    private record Reader(FileConfiguration yaml, Logger logger) {

        Modules modules() {
            return new Modules(
                    yaml.getBoolean("modules.beams", true),
                    yaml.getBoolean("modules.labels", true),
                    yaml.getBoolean("modules.horizon", true),
                    yaml.getBoolean("modules.teleport", true),
                    yaml.getBoolean("modules.sharing", true),
                    yaml.getBoolean("modules.death-mark", true));
        }

        Beams beams() {
            return new Beams(
                    (float) positive("beams.height", 64),
                    (float) positive("beams.width", 0.3),
                    (float) yaml.getDouble("beams.spin", 30),
                    (float) Math.clamp(yaml.getDouble("beams.pulse", 0.15), 0.0, 0.9),
                    yaml.getBoolean("beams.glow", true),
                    atLeast("beams.grow-ticks", 25, 0),
                    positive("beams.anchor-distance", 64),
                    atLeast("beams.update-interval", 4, 1),
                    Math.max(0, yaml.getDouble("beams.base-particles-range", 24)),
                    new Label(
                            yaml.getDouble("beams.label.height", 2.6),
                            positive("beams.label.per-blocks", 12),
                            (float) positive("beams.label.min-scale", 1.0),
                            (float) positive("beams.label.max-scale", 5.0),
                            argb("beams.label.background", 0x60101018),
                            yaml.getBoolean("beams.label.see-through", true)));
        }

        Effects effects() {
            return new Effects(
                    effect("warmup", Particle.DUST),
                    effect("departure", Particle.REVERSE_PORTAL),
                    effect("arrival", Particle.END_ROD),
                    effect("beam-base", Particle.DUST));
        }

        Sounds sounds() {
            return new Sounds(
                    sound("home-set", "block.amethyst_block.resonate"),
                    sound("warmup-tick", "block.note_block.chime"),
                    sound("departure", "block.respawn_anchor.deplete"),
                    sound("arrival", "block.beacon.power_select"),
                    sound("cancel", "block.note_block.bass"),
                    sound("death-mark-cleared", "block.amethyst_block.chime"));
        }

        int atLeast(String path, int fallback, int min) {
            int value = yaml.getInt(path, fallback);
            if (value < min) {
                logger.warn("config.yml: {} must be at least {}, got {}. Using {}.", path, min, value, fallback);
                return fallback;
            }
            return value;
        }

        double positive(String path, double fallback) {
            double value = yaml.getDouble(path, fallback);
            if (value <= 0) {
                logger.warn("config.yml: {} must be above 0, got {}. Using {}.", path, value, fallback);
                return fallback;
            }
            return value;
        }

        Color argb(String path, int fallback) {
            String raw = yaml.getString(path, "");
            try {
                return Color.fromARGB((int) Long.parseLong(raw.replace("#", ""), 16));
            } catch (NumberFormatException e) {
                logger.warn("config.yml: {} '{}' is not a #AARRGGBB colour. Using the default.", path, raw);
                return Color.fromARGB(fallback);
            }
        }

        Key sound(String name, String fallback) {
            String path = "sounds." + name;
            String id = yaml.getString(path);
            if (id == null || !Key.parseable(id)) {
                logger.warn("config.yml: {} '{}' is not a valid sound id. Using {}.", path, id, fallback);
                return Key.key(fallback);
            }
            return Key.key(id);
        }

        ParticleEffect effect(String name, Particle fallback) {
            String path = "effects." + name;
            ConfigurationSection section = yaml.getConfigurationSection(path);
            if (section == null) {
                section = yaml.createSection(path);
            }
            Particle particle = particle(section.getString("particle"), path + ".particle", fallback);
            return new ParticleEffect(
                    particle,
                    (float) section.getDouble("size", 1.0),
                    Math.max(1, section.getInt("count", 1)),
                    Math.max(0, section.getDouble("spread", 0)),
                    Math.max(0, section.getDouble("speed", 0)),
                    Math.max(1, section.getInt("strands", 1)),
                    Math.max(0, section.getDouble("radius", 0.8)),
                    Math.max(0, section.getDouble("height", 2.0)));
        }

        private Particle particle(@Nullable String name, String path, Particle fallback) {
            Particle particle = name == null ? null
                    : Registry.PARTICLE_TYPE.get(Key.key(name.toLowerCase(Locale.ROOT)));
            if (particle == null || !ParticleEffect.supports(particle)) {
                logger.warn("config.yml: {} '{}' is not a supported particle. Using {}.", path, name, fallback);
                return fallback;
            }
            return particle;
        }

        Map<String, BeamColor> colors() {
            Map<String, BeamColor> colors = new LinkedHashMap<>();
            ConfigurationSection section = yaml.getConfigurationSection("colors");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    String path = "colors." + id;
                    Material block = Material.matchMaterial(section.getString(id + ".block", ""));
                    if (block == null || !block.isBlock()) {
                        logger.warn("config.yml: {}.block is not a block. Colour skipped.", path);
                        continue;
                    }
                    String hex = section.getString(id + ".rgb", "");
                    try {
                        colors.put(id, new BeamColor(id, block,
                                Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16))));
                    } catch (IllegalArgumentException e) {
                        logger.warn("config.yml: {}.rgb '{}' is not a #RRGGBB colour. Colour skipped.", path, hex);
                    }
                }
            }
            if (colors.isEmpty()) {
                logger.warn("config.yml: no usable colours, every pillar will be white.");
                colors.put(FALLBACK_COLOR.id(), FALLBACK_COLOR);
            }
            return colors;
        }
    }
}
