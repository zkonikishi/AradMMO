package cn.aradmmo.core.chat;

import cn.aradmmo.core.AradMmoPlugin;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ChatBungeeBridge implements PluginMessageListener {
    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private final AradMmoPlugin plugin;

    public ChatBungeeBridge(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerChannels() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, bridgeChannel(), this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, bridgeChannel());
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, bridgeChannel(), this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, bridgeChannel());
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
    }

    public void broadcast(Component component) {
        ChatService chat = plugin.chat();
        if (chat == null || !chat.bridgeEnabled()) {
            return;
        }

        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return;
        }

        try {
            String origin = chat.bridgeServerName();
            String payload = GsonComponentSerializer.gson().serialize(component);
            for (String target : chat.resolveBridgeTargets()) {
                sendForward(carrier, chat.bridgeChannel(), target, origin, payload);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to forward chat across BungeeCord: " + exception.getMessage());
        }
    }

    private void sendForward(Player carrier, String channel, String target, String origin, String payload) throws IOException {
        ByteArrayOutputStream messageBytes = new ByteArrayOutputStream();
        try (DataOutputStream messageOut = new DataOutputStream(messageBytes)) {
            messageOut.writeUTF(origin);
            byte[] jsonBytes = payload.getBytes(StandardCharsets.UTF_8);
            messageOut.writeInt(jsonBytes.length);
            messageOut.write(jsonBytes);
        }

        ByteArrayOutputStream bridgeBytes = new ByteArrayOutputStream();
        try (DataOutputStream bridgeOut = new DataOutputStream(bridgeBytes)) {
            bridgeOut.writeUTF("Forward");
            bridgeOut.writeUTF(target);
            bridgeOut.writeUTF(channel);
            byte[] forwarded = messageBytes.toByteArray();
            bridgeOut.writeShort(forwarded.length);
            bridgeOut.write(forwarded);
        }

        carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, bridgeBytes.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        ChatService chat = plugin.chat();
        if (chat == null || !chat.bridgeEnabled() || !bridgeChannel().equalsIgnoreCase(channel)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String origin = in.readUTF();
            int payloadSize = in.readInt();
            if (payloadSize <= 0) {
                return;
            }

            byte[] payload = new byte[payloadSize];
            in.readFully(payload);

            if (!origin.isBlank() && origin.equalsIgnoreCase(chat.bridgeServerName())) {
                return;
            }

            Component component = GsonComponentSerializer.gson().deserialize(
                    new String(payload, StandardCharsets.UTF_8));
            Component bridgedLine = Component.empty()
                    .append(chat.renderIncomingBridgePrefix(origin))
                    .append(component);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!chat.isChatEnabled(online)) {
                    continue;
                }
                online.sendMessage(bridgedLine);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to receive forwarded chat: " + exception.getMessage());
        }
    }

    private String bridgeChannel() {
        ChatService chat = plugin.chat();
        return chat == null ? "aradmmo:chat" : chat.bridgeChannel();
    }
}
