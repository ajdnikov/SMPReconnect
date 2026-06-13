package velo.reconnect.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

public class SMPReconnectBridge extends JavaPlugin implements PluginMessageListener {

    private static final String CHANNEL = "smpreconnect:forward";

    @Override
    public void onEnable() {
        // Register the incoming plugin channel
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getLogger().info("SMPReconnectBridge enabled. Listening on channel: " + CHANNEL);
    }

    @Override
    public void onDisable() {
        // Unregister the channel when disabling
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getLogger().info("SMPReconnectBridge disabled.");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Double check the channel name
        if (!channel.equals(CHANNEL)) {
            return;
        }

        // Convert the byte array to a String command
        String command = new String(message, StandardCharsets.UTF_8);
        getLogger().info("Received forwarded command to execute: /" + command);

        // Execute the command synchronously on the main server thread
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
        });
    }
}
