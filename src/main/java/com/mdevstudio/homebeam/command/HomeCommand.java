package com.mdevstudio.homebeam.command;

import com.mdevstudio.homebeam.Homebeam;
import com.mdevstudio.homebeam.Permissions;
import com.mdevstudio.homebeam.beam.Beams;
import com.mdevstudio.homebeam.config.Messages;
import com.mdevstudio.homebeam.config.PluginConfig;
import com.mdevstudio.homebeam.home.HomeBook;
import com.mdevstudio.homebeam.home.PlayerHomes;
import com.mdevstudio.homebeam.model.BeamColor;
import com.mdevstudio.homebeam.model.Home;
import com.mdevstudio.homebeam.teleport.Teleports;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public final class HomeCommand {

    public static final String NAME = "homebeam";

    private static final List<String> SUBCOMMANDS = List.of("tp", "set", "delete", "rename", "color", "list",
            "invite", "uninvite", "show", "hide", "reload", "help");
    private static final String OWNER_SEPARATOR = ":";
    private static final String FORBIDDEN_IN_NAMES = ":\"\\";

    private final Homebeam plugin;

    public HomeCommand(Homebeam plugin) {
        this.plugin = plugin;
    }

    public void register(Commands registrar) {
        var root = Commands.literal(NAME)
                .executes(context -> withPlayer(context, this::travelDefault))
                .then(Commands.argument("home", StringArgumentType.greedyString())
                        .requires(source -> source.getSender().hasPermission(Permissions.COMMAND + "tp"))
                        .suggests(suggest(this::travelTargets))
                        .executes(context -> withPlayer(context, player -> travel(player, greedy(context)))));
        for (String subcommand : SUBCOMMANDS) {
            for (String name : names(subcommand)) {
                root.then(subcommand(subcommand, name));
            }
        }
        registrar.register(root.build(), "Homes with light pillars", plugin.settings().commandAliases());
    }

    private LiteralArgumentBuilder<CommandSourceStack> subcommand(String subcommand, String name) {
        var node = Commands.literal(name)
                .requires(source -> source.getSender().hasPermission(Permissions.COMMAND + subcommand));
        return switch (subcommand) {
            case "tp" -> node.then(homeArgument(this::travelTargets)
                    .executes(context -> withPlayer(context, player -> travel(player, greedy(context)))));
            case "set" -> node.then(Commands.argument("home", StringArgumentType.greedyString())
                    .executes(context -> withPlayer(context, player -> set(player, greedy(context)))));
            case "delete" -> node.then(homeArgument(this::ownNames)
                    .executes(context -> withPlayer(context, player -> delete(player, greedy(context)))));
            case "rename" -> node.then(Commands.argument("old", StringArgumentType.string())
                    .suggests(suggest(player -> ownNames(player).stream().map(StringArgumentType::escapeIfRequired)
                            .toList()))
                    .then(Commands.argument("new", StringArgumentType.greedyString())
                            .executes(context -> withPlayer(context, player -> rename(player,
                                    StringArgumentType.getString(context, "old"), argument(context, "new"))))));
            case "color" -> node.then(Commands.argument("color", StringArgumentType.word())
                    .suggests(suggest(player -> plugin.settings().colors().keySet()))
                    .then(homeArgument(this::ownNames)
                            .executes(context -> withPlayer(context, player -> color(player,
                                    StringArgumentType.getString(context, "color"), greedy(context))))));
            case "invite" -> node.then(Commands.argument("player", ArgumentTypes.player())
                    .then(homeArgument(this::ownNames).executes(this::invite)));
            case "uninvite" -> node.then(Commands.argument("player", StringArgumentType.word())
                    .suggests(suggest(this::guestNames))
                    .then(homeArgument(this::ownNames)
                            .executes(context -> withPlayer(context, player -> uninvite(player,
                                    StringArgumentType.getString(context, "player"), greedy(context))))));
            case "show" -> node.executes(context -> withPlayer(context, player -> show(player, null)))
                    .then(homeArgument(this::ownNames)
                            .executes(context -> withPlayer(context, player -> show(player, greedy(context)))));
            case "hide" -> node.executes(context -> withPlayer(context, this::hide));
            case "list" -> node.executes(context -> withPlayer(context, this::list));
            case "reload" -> node.executes(context -> reload(context.getSource().getSender()));
            default -> node.executes(context -> help(context.getSource().getSender()));
        };
    }

    private int travelDefault(Player player) {
        Collection<Home> homes = book().of(player).homes();
        if (homes.isEmpty()) {
            messages().send(player, Messages.NO_HOMES);
            return 0;
        }
        if (homes.size() == 1) {
            return travel(player, homes.iterator().next().name());
        }
        messages().send(player, Messages.PICK_HOME);
        return list(player);
    }

    private int travel(Player player, String reference) {
        PluginConfig settings = plugin.settings();
        if (!settings.modules().teleport()) {
            messages().send(player, Messages.TELEPORT_DISABLED);
            return 0;
        }
        Home home = book().of(player).get(reference);
        String beamKey;
        if (home != null) {
            beamKey = Beams.ownKey(home);
        } else {
            home = sharedHome(player, reference);
            if (home == null) {
                messages().send(player, Messages.HOME_UNKNOWN, Placeholder.unparsed("name", reference));
                return 0;
            }
            if (!settings.teleport().guestsCanTeleport()) {
                messages().send(player, Messages.GUEST_TELEPORT_DISABLED);
                return 0;
            }
            beamKey = Beams.guestKey(home);
        }
        Teleports.Start start = plugin.teleports().begin(player, home, beamKey);
        return switch (start) {
            case STARTED -> Command.SINGLE_SUCCESS;
            case ALREADY_WARMING -> fail(player, Messages.TELEPORT_ALREADY);
            case COOLDOWN -> fail(player, Messages.TELEPORT_COOLDOWN,
                    Placeholder.unparsed("seconds", String.valueOf(plugin.teleports().cooldownLeft(player))));
            case NO_WORLD -> fail(player, Messages.TELEPORT_WORLD_MISSING, Placeholder.unparsed("world", home.world()));
        };
    }

    private int fail(Player player, String key, TagResolver... placeholders) {
        messages().send(player, key, placeholders);
        return 0;
    }

    private @Nullable Home sharedHome(Player player, String reference) {
        if (!plugin.settings().modules().sharing()) {
            return null;
        }
        int split = reference.indexOf(OWNER_SEPARATOR);
        if (split <= 0) {
            return null;
        }
        PlayerHomes owner = book().byName(reference.substring(0, split));
        Home home = owner == null ? null : owner.get(reference.substring(split + 1));
        return home != null && home.guests().contains(player.getUniqueId()) ? home : null;
    }

    private int set(Player player, String rawName) {
        PluginConfig settings = plugin.settings();
        String name = rawName.strip();
        if (!validName(name)) {
            messages().send(player, Messages.INVALID_NAME,
                    Placeholder.unparsed("max", String.valueOf(settings.homes().maxNameLength())));
            return 0;
        }
        Location here = player.getLocation();
        if (settings.homes().blockedWorlds().contains(here.getWorld().getName())) {
            messages().send(player, Messages.WORLD_BLOCKED);
            return 0;
        }
        PlayerHomes own = book().of(player);
        Home existing = own.get(name);
        if (existing != null) {
            book().put(own, existing.movedTo(here));
            messages().send(player, Messages.HOME_MOVED, tint(existing.color()),
                    Placeholder.unparsed("name", existing.name()));
            play(player, settings.sounds().homeSet());
            return Command.SINGLE_SUCCESS;
        }
        int limit = Permissions.homeLimit(player, settings.homes().defaultLimit());
        if (own.homes().size() >= limit) {
            messages().send(player, Messages.LIMIT_REACHED, Placeholder.unparsed("limit", String.valueOf(limit)));
            return 0;
        }
        List<String> palette = List.copyOf(settings.colors().keySet());
        String color = palette.get(own.homes().size() % palette.size());
        book().put(own, Home.at(player.getUniqueId(), name, here, color));
        messages().send(player, Messages.HOME_SET, tint(color), Placeholder.unparsed("name", name));
        play(player, settings.sounds().homeSet());
        return Command.SINGLE_SUCCESS;
    }

    private int delete(Player player, String name) {
        PlayerHomes own = book().of(player);
        Home removed = book().remove(own, name.strip());
        if (removed == null) {
            messages().send(player, Messages.HOME_UNKNOWN, Placeholder.unparsed("name", name));
            return 0;
        }
        messages().send(player, Messages.HOME_DELETED, Placeholder.unparsed("name", removed.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int rename(Player player, String oldName, String rawNew) {
        String newName = rawNew.strip();
        PlayerHomes own = book().of(player);
        Home home = own.get(oldName);
        if (home == null) {
            messages().send(player, Messages.HOME_UNKNOWN, Placeholder.unparsed("name", oldName));
            return 0;
        }
        if (!validName(newName)) {
            messages().send(player, Messages.INVALID_NAME,
                    Placeholder.unparsed("max", String.valueOf(plugin.settings().homes().maxNameLength())));
            return 0;
        }
        Home taken = own.get(newName);
        if (taken != null && taken != home) {
            messages().send(player, Messages.NAME_TAKEN, Placeholder.unparsed("name", taken.name()));
            return 0;
        }
        Home renamed = home.renamed(newName);
        book().replace(own, home.key(), renamed);
        if (home.key().equals(own.focus())) {
            book().setView(own, own.beamsVisible(), renamed.key());
        }
        messages().send(player, Messages.HOME_RENAMED, tint(home.color()),
                Placeholder.unparsed("old", home.name()), Placeholder.unparsed("name", newName));
        return Command.SINGLE_SUCCESS;
    }

    private int color(Player player, String colorId, String name) {
        PluginConfig settings = plugin.settings();
        if (!settings.colors().containsKey(colorId)) {
            messages().send(player, Messages.COLOR_UNKNOWN, Placeholder.unparsed("input", colorId),
                    Placeholder.unparsed("available", String.join(", ", settings.colors().keySet())));
            return 0;
        }
        PlayerHomes own = book().of(player);
        Home home = own.get(name.strip());
        if (home == null) {
            messages().send(player, Messages.HOME_UNKNOWN, Placeholder.unparsed("name", name));
            return 0;
        }
        book().replace(own, home.key(), home.colored(colorId));
        messages().send(player, Messages.HOME_COLORED, tint(colorId), Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("color_name", messages().colorName(colorId)));
        return Command.SINGLE_SUCCESS;
    }

    private int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            messages().send(sender, Messages.PLAYERS_ONLY);
            return 0;
        }
        if (!plugin.settings().modules().sharing()) {
            messages().send(player, Messages.SHARING_DISABLED);
            return 0;
        }
        Player guest = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource()).getFirst();
        String name = greedy(context);
        PlayerHomes own = book().of(player);
        Home home = own.get(name.strip());
        if (home == null) {
            messages().send(player, Messages.HOME_UNKNOWN, Placeholder.unparsed("name", name));
            return 0;
        }
        if (guest.equals(player)) {
            messages().send(player, Messages.INVITE_SELF);
            return 0;
        }
        if (home.guests().contains(guest.getUniqueId())) {
            messages().send(player, Messages.INVITE_ALREADY, Placeholder.unparsed("player", guest.getName()),
                    Placeholder.unparsed("name", home.name()));
            return 0;
        }
        Set<UUID> guests = new HashSet<>(home.guests());
        guests.add(guest.getUniqueId());
        book().replace(own, home.key(), home.withGuests(guests));
        messages().send(player, Messages.INVITE_DONE, tint(home.color()),
                Placeholder.unparsed("player", guest.getName()), Placeholder.unparsed("name", home.name()));
        messages().send(guest, Messages.INVITE_RECEIVED, tint(home.color()),
                Placeholder.unparsed("owner", player.getName()), Placeholder.unparsed("name", home.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int uninvite(Player player, String guestName, String name) {
        PlayerHomes own = book().of(player);
        Home home = own.get(name.strip());
        if (home == null) {
            messages().send(player, Messages.HOME_UNKNOWN, Placeholder.unparsed("name", name));
            return 0;
        }
        UUID guest = home.guests().stream()
                .filter(id -> guestName.equalsIgnoreCase(plugin.getServer().getOfflinePlayer(id).getName()))
                .findFirst().orElse(null);
        if (guest == null) {
            messages().send(player, Messages.UNINVITE_UNKNOWN, Placeholder.unparsed("player", guestName),
                    Placeholder.unparsed("name", home.name()));
            return 0;
        }
        Set<UUID> guests = new HashSet<>(home.guests());
        guests.remove(guest);
        book().replace(own, home.key(), home.withGuests(guests));
        messages().send(player, Messages.UNINVITE_DONE, Placeholder.unparsed("player", guestName),
                Placeholder.unparsed("name", home.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int show(Player player, @Nullable String name) {
        if (!plugin.settings().modules().beams()) {
            messages().send(player, Messages.BEAMS_DISABLED);
            return 0;
        }
        PlayerHomes own = book().of(player);
        if (name == null) {
            book().setView(own, true, null);
            messages().send(player, Messages.BEAMS_SHOWN);
            return Command.SINGLE_SUCCESS;
        }
        Home home = own.get(name.strip());
        if (home == null) {
            messages().send(player, Messages.HOME_UNKNOWN, Placeholder.unparsed("name", name));
            return 0;
        }
        book().setView(own, true, home.key());
        messages().send(player, Messages.BEAMS_FOCUS, tint(home.color()), Placeholder.unparsed("name", home.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int hide(Player player) {
        book().setView(book().of(player), false, null);
        messages().send(player, Messages.BEAMS_HIDDEN);
        return Command.SINGLE_SUCCESS;
    }

    private int list(Player player) {
        Messages messages = messages();
        PlayerHomes own = book().of(player);
        List<Home> shared = plugin.settings().modules().sharing()
                ? book().sharedWith(player.getUniqueId()) : List.of();
        if (own.homes().isEmpty() && shared.isEmpty()) {
            messages.send(player, Messages.LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        int limit = Permissions.homeLimit(player, plugin.settings().homes().defaultLimit());
        messages.send(player, Messages.LIST_HEADER,
                Placeholder.unparsed("count", String.valueOf(own.homes().size())),
                Placeholder.unparsed("limit", limit == Permissions.UNLIMITED
                        ? messages.raw(Messages.UNLIMITED) : String.valueOf(limit)));
        for (Home home : own.homes()) {
            messages.send(player, Messages.LIST_LINE, tint(home.color()), travelLink(home.name()),
                    Placeholder.unparsed("name", home.name()),
                    Placeholder.unparsed("world", home.world()),
                    Placeholder.unparsed("x", String.valueOf((int) Math.floor(home.x()))),
                    Placeholder.unparsed("y", String.valueOf((int) Math.floor(home.y()))),
                    Placeholder.unparsed("z", String.valueOf((int) Math.floor(home.z()))),
                    Placeholder.unparsed("distance", distanceTo(player, home)),
                    Placeholder.component("guests", home.guests().isEmpty() ? Component.empty()
                            : messages.component(Messages.LIST_GUESTS,
                                    Placeholder.unparsed("count", String.valueOf(home.guests().size())))));
        }
        if (!shared.isEmpty()) {
            messages.send(player, Messages.LIST_SHARED_HEADER);
            for (Home home : shared) {
                PlayerHomes owner = book().find(home.owner());
                String ownerName = owner == null ? "?" : owner.name();
                messages.send(player, Messages.LIST_SHARED_LINE, tint(home.color()),
                        travelLink(ownerName + OWNER_SEPARATOR + home.name()),
                        Placeholder.unparsed("owner", ownerName), Placeholder.unparsed("name", home.name()),
                        Placeholder.unparsed("distance", distanceTo(player, home)));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSender sender) {
        plugin.reload();
        messages().send(sender, Messages.RELOADED,
                Placeholder.unparsed("colors", String.valueOf(plugin.settings().colors().size())));
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandSender sender) {
        Messages messages = messages();
        messages.send(sender, Messages.HELP_HEADER);
        for (String subcommand : SUBCOMMANDS) {
            if (!sender.hasPermission(Permissions.COMMAND + subcommand)) {
                continue;
            }
            List<String> aliases = plugin.settings().aliasesOf(subcommand);
            messages.send(sender, Messages.HELP_LINE,
                    Placeholder.unparsed("command", subcommand),
                    Placeholder.unparsed("aliases", aliases.isEmpty() ? "-" : String.join(", ", aliases)),
                    Placeholder.unparsed("description", messages.raw(Messages.HELP_PREFIX + subcommand)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private boolean validName(String name) {
        return !name.isEmpty() && name.length() <= plugin.settings().homes().maxNameLength()
                && name.chars().noneMatch(c -> FORBIDDEN_IN_NAMES.indexOf(c) >= 0);
    }

    private String distanceTo(Player player, Home home) {
        if (!player.getWorld().getName().equals(home.world())) {
            return "";
        }
        return messages().distance(player.getLocation().distance(home.location(player.getWorld())));
    }

    private TagResolver tint(String colorId) {
        BeamColor color = plugin.settings().color(colorId);
        return Placeholder.styling("tint", color.text());
    }

    private TagResolver travelLink(String reference) {
        return Placeholder.styling("travel", ClickEvent.runCommand("/" + NAME + " tp " + reference),
                HoverEvent.showText(messages().component(Messages.LIST_HOVER)));
    }

    private List<String> ownNames(Player player) {
        return book().of(player).homes().stream().map(Home::name).toList();
    }

    private List<String> travelTargets(Player player) {
        List<String> targets = new ArrayList<>(ownNames(player));
        if (plugin.settings().modules().sharing()) {
            for (Home home : book().sharedWith(player.getUniqueId())) {
                PlayerHomes owner = book().find(home.owner());
                if (owner != null) {
                    targets.add(owner.name() + OWNER_SEPARATOR + home.name());
                }
            }
        }
        return targets;
    }

    private List<String> guestNames(Player player) {
        return book().of(player).homes().stream().flatMap(home -> home.guests().stream())
                .distinct()
                .map(id -> plugin.getServer().getOfflinePlayer(id))
                .map(OfflinePlayer::getName)
                .filter(Objects::nonNull)
                .toList();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> homeArgument(
            Function<Player, Collection<String>> names) {
        return Commands.argument("home", StringArgumentType.greedyString()).suggests(suggest(names));
    }

    private int withPlayer(CommandContext<CommandSourceStack> context, Function<Player, Integer> action) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            messages().send(sender, Messages.PLAYERS_ONLY);
            return 0;
        }
        return action.apply(player);
    }

    private List<String> names(String subcommand) {
        List<String> names = new ArrayList<>();
        names.add(subcommand);
        for (String alias : plugin.settings().aliasesOf(subcommand)) {
            String clean = alias.toLowerCase(Locale.ROOT);
            if (!clean.isBlank() && !names.contains(clean) && !SUBCOMMANDS.contains(clean)) {
                names.add(clean);
            }
        }
        return names;
    }

    private static SuggestionProvider<CommandSourceStack> suggest(Function<Player, Collection<String>> values) {
        return (context, builder) -> {
            if (context.getSource().getSender() instanceof Player player) {
                String typed = builder.getRemainingLowerCase();
                values.apply(player).stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(typed))
                        .forEach(builder::suggest);
            }
            return builder.buildFuture();
        };
    }

    private static String greedy(CommandContext<CommandSourceStack> context) {
        return argument(context, "home");
    }

    private static String argument(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private static void play(Player player, Key sound) {
        player.playSound(Sound.sound(sound, Sound.Source.PLAYER, 1f, 1f));
    }

    private HomeBook book() {
        return plugin.homes();
    }

    private Messages messages() {
        return plugin.messages();
    }
}
