/*
 * Copyright 2022 https://dejvokep.dev/
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
package dev.dejvokep.safenet.spigot.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.InternalManager;
import com.comphenix.protocol.injector.netty.manager.NetworkManagerInjector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import dev.dejvokep.safenet.core.authenticator.AuthenticationRequest;
import dev.dejvokep.safenet.core.authenticator.Authenticator;
import dev.dejvokep.safenet.spigot.SafeNetSpigot;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Listens for the {@link PacketType.Handshake.Client#SET_PROTOCOL} packet. This packet is the first one sent from the
 * client and is then used to read the <code>host</code> string and extract the properties.
 */
public class PacketHandler {

    /**
     * Default disconnect message used if the one provided in the config is invalid.
     */
    private static final String DEFAULT_DISCONNECT_MESSAGE = "Disconnected";

    /**
     * Message logged when a connection was denied.
     */
    private static final String MESSAGE_DENIED = "DENIED (code B%d): Failed to authenticate \"%s\": %s Data: %s";

    /**
     * Message logged when a connection was accepted.
     */
    private static final String MESSAGE_ACCEPTED = "ACCEPTED (code B%d): Authenticated \"%s\" with data: %s";

    // Protocol manager
    private final ProtocolManager protocolManager;
    // The plugin instance
    private final SafeNetSpigot plugin;

    // If to block pings
    private boolean blockPings;
    // Disconnect message
    private String disconnectMessage;

    /**
     * Registers the packet listener and handles the incoming connections.
     *
     * @param protocolManager the protocol manager used to register the listener and send packets
     * @param plugin          the main class
     */
    public PacketHandler(@NotNull ProtocolManager protocolManager, @NotNull SafeNetSpigot plugin) {
        // Set
        this.protocolManager = protocolManager;
        this.plugin = plugin;
        // Reload
        reload();
        // Authenticator
        final Authenticator authenticator = plugin.getAuthenticator();

        /*protocolManager.addPacketListener(new PacketAdapter(plugin.getPlugin(), PacketType.Handshake.Client.getInstance().values()) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                System.out.println(event.getPacketType());
            }
        });
        protocolManager.addPacketListener(new PacketAdapter(plugin.getPlugin(), PacketType.Login.Client.getInstance().values()) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                System.out.println(event.getPacketType());
            }
        });
        protocolManager.addPacketListener(new PacketAdapter(plugin.getPlugin(), PacketType.Play.Client.getInstance().values()) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                System.out.println(event.getPacketType());
            }
        });
        protocolManager.addPacketListener(new PacketAdapter(plugin.getPlugin(), PacketType.Status.Client.getInstance().values()) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                System.out.println(event.getPacketType());
            }
        });*/

        // Listen to the handshake packet
        protocolManager.addPacketListener(new PacketAdapter(plugin.getPlugin(), PacketType.Handshake.Client.SET_PROTOCOL) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    // If malformed
                    if (event.getPacket().getProtocols().size() == 0 || event.getPacket().getStrings().size() == 0) {
                        // Log
                        plugin.getLogger().info(String.format(MESSAGE_DENIED, AuthenticationRequest.Result.FAILED_MALFORMED_DATA.getCode(), Authenticator.UNKNOWN_DATA, AuthenticationRequest.Result.FAILED_MALFORMED_DATA.getMessage(), Authenticator.UNKNOWN_DATA));
                        disconnect(event);
                        return;
                    }
                    // If pinging and it is allowed
                    if (event.getPacket().getProtocols().read(0) == PacketType.Protocol.STATUS && !blockPings)
                        return;
                    // The strings
                    StructureModifier<String> strings = event.getPacket().getStrings();
                    // Host
                    String host = strings.readSafely(0);
                    // Authenticate
                    AuthenticationRequest request = authenticator.authenticate(host);

                    // If failed
                    if (!request.getResult().isPassed()) {
                        // Log
                        plugin.getLogger().info(String.format(MESSAGE_DENIED, request.getResult().getCode(), request.getPlayerId(), request.getResult().getMessage(), request.getShortenedHost()));
                        disconnect(event);
                        return;
                    }

                    // Set the host
                    strings.write(0, request.getHost());
                    // Log
                    plugin.getLogger().info(String.format(MESSAGE_ACCEPTED, AuthenticationRequest.Result.PASSED.getCode(), request.getPlayerId(), request.getShortenedHost()));
                } catch (Exception ex) {
                    // Log
                    plugin.getLogger().log(Level.SEVERE, "An exception occurred while processing a packet!", ex);
                    disconnect(event);
                }
            }
        });
    }

    /**
     * Disconnects the source of the given event.
     *
     * @param event the event
     */
    private void disconnect(@NotNull PacketEvent event) {
        try {
            // Create the disconnect packet
            PacketContainer disconnectPacket = new PacketContainer(PacketType.Login.Server.DISCONNECT);
            // Write defaults
            disconnectPacket.getModifier().writeDefaults();
            BaseComponent[] textComponent = TextComponent.fromLegacyText(disconnectMessage);
            String serialized = ComponentSerializer.toString(textComponent);
            WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromJson(serialized);
            // Set the message
            disconnectPacket.getChatComponents().write(0, wrappedChatComponent);
            // Send
            protocolManager.sendServerPacket(event.getPlayer(), disconnectPacket);
        } catch (Exception ignored) {
        }

        try {
            // Disconnect the player
            TemporaryPlayerFactory.getInjectorFromPlayer(event.getPlayer()).disconnect(disconnectMessage);
        } catch (Exception ex) {
            // Log
            plugin.getLogger().log(Level.SEVERE, "Failed to disconnect a player! Shutting down...", ex);
            // Shutdown
            Bukkit.shutdown();
        }
    }

    /**
     * Reloads the internal configuration.
     */
    public void reload() {
        // If to block ping packets
        blockPings = plugin.getConfiguration().getBoolean("block-pings");
        // Disconnect message
        disconnectMessage = ChatColor.translateAlternateColorCodes('&', plugin.getConfiguration().getString("disconnect-message", DEFAULT_DISCONNECT_MESSAGE));
    }

}