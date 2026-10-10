package com.mdevstudio.homebeam;

import com.mdevstudio.homebeam.beam.Beams;
import com.mdevstudio.homebeam.command.HomeCommand;
import com.mdevstudio.homebeam.config.Messages;
import com.mdevstudio.homebeam.config.PluginConfig;
import com.mdevstudio.homebeam.home.HomeBook;
import com.mdevstudio.homebeam.teleport.Teleports;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public class Homebeam extends JavaPlugin {

    private PluginConfig settings;
    private Messages messages;
    private HomeBook homes;
    private Beams beams;
    private Teleports teleports;

    @Override
    public void onEnable() {
        loadFiles();
        homes = new HomeBook(getDataFolder(), getSLF4JLogger(),
                task -> getServer().getAsyncScheduler().runNow(this, ignored -> task.run()));
        homes.load();
        beams = new Beams(this);
        teleports = new Teleports(this);

        getServer().getPluginManager().registerEvents(beams, this);
        getServer().getPluginManager().registerEvents(teleports, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new HomeCommand(this).register(event.registrar()));
        beams.startAll();
        getSLF4JLogger().info("{} homes, {} colours.", homes.total(), settings.colors().size());
    }

    @Override
    public void onDisable() {
        if (homes == null) {
            return;
        }
        teleports.cancelAll();
        beams.stopAll();
        homes.saveNow();
    }

    public void reload() {
        loadFiles();
        beams.restartAll();
    }

    private void loadFiles() {
        settings = PluginConfig.load(this);
        messages = Messages.load(this, settings.language());
    }

    public PluginConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public HomeBook homes() {
        return homes;
    }

    public Beams beams() {
        return beams;
    }

    public Teleports teleports() {
        return teleports;
    }
}
