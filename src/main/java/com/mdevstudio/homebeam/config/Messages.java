package com.mdevstudio.homebeam.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {

    public static final String PLAYERS_ONLY = "players-only";
    public static final String RELOADED = "reloaded";
    public static final String HELP_HEADER = "help-header";
    public static final String HELP_LINE = "help-line";
    public static final String INVALID_NAME = "invalid-name";
    public static final String WORLD_BLOCKED = "world-blocked";
    public static final String LIMIT_REACHED = "limit-reached";
    public static final String HOME_SET = "home-set";
    public static final String HOME_MOVED = "home-moved";
    public static final String HOME_UNKNOWN = "home-unknown";
    public static final String HOME_DELETED = "home-deleted";
    public static final String HOME_RENAMED = "home-renamed";
    public static final String NAME_TAKEN = "name-taken";
    public static final String NO_HOMES = "no-homes";
    public static final String PICK_HOME = "pick-home";
    public static final String HOME_COLORED = "home-colored";
    public static final String COLOR_UNKNOWN = "color-unknown";
    public static final String LIST_HEADER = "list-header";
    public static final String LIST_LINE = "list-line";
    public static final String LIST_GUESTS = "list-guests";
    public static final String LIST_SHARED_HEADER = "list-shared-header";
    public static final String LIST_SHARED_LINE = "list-shared-line";
    public static final String LIST_EMPTY = "list-empty";
    public static final String UNLIMITED = "unlimited";
    public static final String LIST_HOVER = "list-hover";
    public static final String INVITE_DONE = "invite-done";
    public static final String INVITE_RECEIVED = "invite-received";
    public static final String INVITE_SELF = "invite-self";
    public static final String INVITE_ALREADY = "invite-already";
    public static final String UNINVITE_DONE = "uninvite-done";
    public static final String UNINVITE_UNKNOWN = "uninvite-unknown";
    public static final String GUEST_TELEPORT_DISABLED = "guest-teleport-disabled";
    public static final String SHARING_DISABLED = "sharing-disabled";
    public static final String BEAMS_SHOWN = "beams-shown";
    public static final String BEAMS_FOCUS = "beams-focus";
    public static final String BEAMS_HIDDEN = "beams-hidden";
    public static final String BEAMS_DISABLED = "beams-disabled";
    public static final String TELEPORT_DISABLED = "teleport-disabled";
    public static final String TELEPORT_WARMUP = "teleport-warmup";
    public static final String TELEPORT_DONE = "teleport-done";
    public static final String TELEPORT_CANCEL_MOVE = "teleport-cancel-move";
    public static final String TELEPORT_CANCEL_DAMAGE = "teleport-cancel-damage";
    public static final String TELEPORT_COOLDOWN = "teleport-cooldown";
    public static final String TELEPORT_WORLD_MISSING = "teleport-world-missing";
    public static final String TELEPORT_ALREADY = "teleport-already";
    public static final String DEATH_MARK_SET = "death-mark-set";
    public static final String DEATH_MARK_CLEARED = "death-mark-cleared";
    public static final String LABEL_OWN = "label-own";
    public static final String LABEL_GUEST = "label-guest";
    public static final String LABEL_DEATH = "label-death";
    public static final String DISTANCE_METERS = "distance-meters";
    public static final String DISTANCE_KILOMETERS = "distance-kilometers";
    public static final String HELP_PREFIX = "help-";
    public static final String COLOR_PREFIX = "colors.";

    static final Set<String> LANGUAGES = Set.of("ru", "en");

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final int METERS_IN_KILOMETER = 1000;

    private final YamlConfiguration lines;
    private final TagResolver prefix;
    private final Locale locale;

    private Messages(YamlConfiguration lines, Locale locale) {
        this.lines = lines;
        this.locale = locale;
        this.prefix = Placeholder.parsed("prefix", line("prefix"));
    }

    public static Messages load(JavaPlugin plugin, String language) {
        for (String available : LANGUAGES) {
            String path = path(available);
            if (!new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }

        YamlConfiguration lines = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path(language)));
        // Keys an admin deleted, or that appeared in an update, fall back to the bundled text.
        try (Reader bundled = new InputStreamReader(
                Objects.requireNonNull(plugin.getResource(path(language))), StandardCharsets.UTF_8)) {
            lines.setDefaults(YamlConfiguration.loadConfiguration(bundled));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Messages(lines, Locale.forLanguageTag(language));
    }

    public void send(CommandSender recipient, String key, TagResolver... placeholders) {
        recipient.sendMessage(component(key, placeholders));
    }

    public Component component(String key, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(line(key), prefix, TagResolver.resolver(placeholders));
    }

    public String raw(String key) {
        return line(key);
    }

    public String distance(double blocks) {
        if (blocks < METERS_IN_KILOMETER) {
            return line(DISTANCE_METERS).replace("<value>", String.valueOf(Math.round(blocks)));
        }
        return line(DISTANCE_KILOMETERS)
                .replace("<value>", String.format(locale, "%.1f", blocks / METERS_IN_KILOMETER));
    }

    public String colorName(String id) {
        String name = lines.getString(COLOR_PREFIX + id);
        return name != null ? name : id;
    }

    // getString(key, fallback) would skip the bundled defaults, so they are checked before falling back to the key.
    private String line(String key) {
        String line = lines.getString(key);
        return line != null ? line : key;
    }

    private static String path(String language) {
        return "lang/" + language + ".yml";
    }
}
