/*
 * Copyright 2024 https://dejvokep.dev/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.dejvokep.safenet.spigot;

import com.tcoded.folialib.FoliaLib;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import dev.dejvokep.safenet.core.PassphraseVault;
import dev.dejvokep.safenet.spigot.authentication.Authenticator;
import dev.dejvokep.safenet.spigot.command.PluginCommand;
import dev.dejvokep.safenet.spigot.disconnect.DisconnectHandler;
import dev.dejvokep.safenet.spigot.listener.ListenerPusher;
import dev.dejvokep.safenet.spigot.listener.handshake.AbstractHandshakeListener;
import dev.dejvokep.safenet.spigot.listener.handshake.paper.PaperHandshakeListener;
import dev.dejvokep.safenet.spigot.listener.handshake.spigot.SpigotHandshakeListener;
import dev.dejvokep.safenet.spigot.listener.session.SessionListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;


public class SafeNetSpigot extends JavaPlugin {
    private static final String PROTOCOL_LIB_VERSION = "5.0.0 or newer";
    private static final String PAPER_HANDSHAKE_EVENT = "com.destroystokyo.paper.event.player.PlayerHandshakeEvent";
    private YamlDocument config;
    private PassphraseVault passphraseVault;
    private AbstractHandshakeListener handshakeListener;
    private DisconnectHandler disconnectHandler;
    private ListenerPusher listenerPusher;

    private Authenticator authenticator;
    private boolean paperServer;
    private FoliaLib foliaLib;

    @Override
    public void onEnable() {
        FoliaLib foliaLib = new FoliaLib(this);

        try {
            config = YamlDocument.create(
                    new File(getDataFolder(), "config.yml"),
                    Objects.requireNonNull(getResource("spigot-config.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build()
            );
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Failed to initialize the config file! Shutting down...", ex);
            foliaLib.getScheduler().runNextTick(t -> Bukkit.shutdown());
            return;
        }

        passphraseVault = new PassphraseVault(config, getLogger());
        disconnectHandler = new DisconnectHandler(this);
        authenticator = new Authenticator(this);
        listenerPusher = new ListenerPusher(this);

        getCommand("safenet").setExecutor(new PluginCommand(this));
        getCommand("sn").setExecutor(new PluginCommand(this));

        paperServer = classExists(PAPER_HANDSHAKE_EVENT);

        if (!paperServer) {
            if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib") || isUnsupportedProtocolLib()) {
                getLogger().severe(String.format("This version of SafeNET requires ProtocolLib %s to run! Shutting down...", PROTOCOL_LIB_VERSION));
                foliaLib.getScheduler().runNextTick(t -> Bukkit.shutdown());
                return;
            }
            try {
                handshakeListener = new SpigotHandshakeListener(this);
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "An unknown error has occurred whilst registering the packet listener! Shutting down...", ex);
                foliaLib.getScheduler().runNextTick(t -> Bukkit.shutdown());
                return;
            }
            new SessionListener(this);
            getLogger().info("Spigot native server components available; handshakes will be handled via the packet listener and sessions will be validated using the API.");
        } else {
            getLogger().info("Paper server components available; handshakes will be handled via the API and sessions will not be validated.");
            handshakeListener = new PaperHandshakeListener(this);
        }

        foliaLib.getScheduler().runTimer(() -> {
            try {
                getLogger().info("Thank you for downloading SafeNET!");
                passphraseVault.printStatus();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "An error occurred while executing delayed task.", e);
            }
        }, 1, 20); // Tính theo ticks
    }

    @Override
    public void onDisable() {
        foliaLib.getScheduler().cancelAllTasks();
    }

    public boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
    private boolean isUnsupportedProtocolLib() {
        return !classExists("com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory");
    }
    @NotNull
    public Authenticator getAuthenticator() {
        return authenticator;
    }
    @NotNull
    public PassphraseVault getPassphraseVault() {
        return passphraseVault;
    }
    @NotNull
    public AbstractHandshakeListener getHandshakeListener() {
        return handshakeListener;
    }
    @NotNull
    public DisconnectHandler getDisconnectHandler() {
        return disconnectHandler;
    }
    @NotNull
    public ListenerPusher getEventPusher() {
        return listenerPusher;
    }
    public YamlDocument getConfiguration() {
        return config;
    }
    public boolean isPaperServer() {
        return paperServer;
    }
}
