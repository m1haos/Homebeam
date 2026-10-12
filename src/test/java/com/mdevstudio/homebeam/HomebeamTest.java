package com.mdevstudio.homebeam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mdevstudio.homebeam.home.HomeBook;
import com.mdevstudio.homebeam.home.PlayerHomes;
import com.mdevstudio.homebeam.model.Home;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HomebeamTest {

    private ServerMock server;
    private Homebeam plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Homebeam.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock join(String name) {
        PlayerMock player = new SchedulingPlayer(server, name);
        server.addPlayer(player);
        return player;
    }

    @Test
    void createsItsFilesOnFirstStart() {
        for (String file : List.of("config.yml", "lang/ru.yml", "lang/en.yml")) {
            assertTrue(plugin.getDataFolder().toPath().resolve(file).toFile().exists(), file);
        }
        assertEquals(8, plugin.settings().colors().size());
    }

    @Test
    void renameKeepsTheOrderOfHomes() {
        PlayerMock player = join("Nika");
        HomeBook book = plugin.homes();
        PlayerHomes own = book.of(player);
        for (String name : List.of("База", "Шахта", "Ферма")) {
            book.put(own, Home.at(player.getUniqueId(), name, player.getLocation(), "gold"));
        }
        Home mine = own.get("шахта");
        assertNotNull(mine);
        book.replace(own, mine.key(), mine.renamed("Старая шахта"));

        assertEquals(List.of("База", "Старая шахта", "Ферма"), own.homes().stream().map(Home::name).toList());
        assertNull(own.get("Шахта"));
    }

    @Test
    void guestsSeeSharedHomesAndHomesSurviveReload() {
        PlayerMock owner = join("Nika");
        PlayerMock guest = join("Misha");
        HomeBook book = plugin.homes();
        PlayerHomes own = book.of(owner);
        Home base = Home.at(owner.getUniqueId(), "База", owner.getLocation(), "aqua")
                .withGuests(Set.of(guest.getUniqueId()));
        book.put(own, base);
        assertEquals(1, book.sharedWith(guest.getUniqueId()).size());

        book.saveNow();
        book.load();

        PlayerHomes reloaded = book.find(owner.getUniqueId());
        assertNotNull(reloaded);
        Home restored = reloaded.get("база");
        assertNotNull(restored);
        assertEquals("aqua", restored.color());
        assertEquals(Set.of(guest.getUniqueId()), restored.guests());
        assertEquals(1, book.sharedWith(guest.getUniqueId()).size());
    }

    @Test
    void highestLimitPermissionWins() {
        PlayerMock player = join("Nika");
        assertEquals(3, Permissions.homeLimit(player, 3));
        player.addAttachment(plugin, "homebeam.limit.5", true);
        player.addAttachment(plugin, "homebeam.limit.10", true);
        assertEquals(10, Permissions.homeLimit(player, 3));
        player.addAttachment(plugin, "homebeam.limit.unlimited", true);
        assertEquals(Permissions.UNLIMITED, Permissions.homeLimit(player, 3));
    }
}
