package velo.reconnect;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class ForwardCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final ConfigManager configManager;
    private static final MinecraftChannelIdentifier FORWARD_CHANNEL = MinecraftChannelIdentifier.create("smpreconnect", "forward");

    public ForwardCommand(ProxyServer proxy, ConfigManager configManager) {
        this.proxy = proxy;
        this.configManager = configManager;
        this.proxy.getChannelRegistrar().register(FORWARD_CHANNEL);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        
        // Permission check
        if (!source.hasPermission("smpreconnect.forward")) {
            source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        // Whitelist check
        if (source instanceof Player && configManager.isWhitelistEnabled()) {
            Player player = (Player) source;
            if (!configManager.isWhitelisted(player.getUsername())) {
                source.sendMessage(Component.text("You are not on the command forwarder whitelist.", NamedTextColor.RED));
                return;
            }
        }

        String[] args = invocation.arguments();
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /vforward <server> <command...>", NamedTextColor.RED));
            return;
        }

        String targetServerName = args[0];
        String commandToForward = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        Optional<RegisteredServer> targetServerOpt = proxy.getServer(targetServerName);
        if (!targetServerOpt.isPresent()) {
            source.sendMessage(Component.text("Server '" + targetServerName + "' not found.", NamedTextColor.RED));
            return;
        }

        RegisteredServer targetServer = targetServerOpt.get();

        // Send the command via plugin message
        // The backend server needs a bridge plugin listening on smpreconnect:forward to execute this
        byte[] messageData = commandToForward.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean sent = targetServer.sendPluginMessage(FORWARD_CHANNEL, messageData);

        if (sent) {
            source.sendMessage(Component.text("Forwarded command to " + targetServerName + ": /" + commandToForward, NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text("Failed to forward command. Is the server online?", NamedTextColor.RED));
        }
    }
}
