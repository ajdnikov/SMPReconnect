package velo.reconnect;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "smp-reconnect", name = "SMP Reconnect", version = "1.0", authors = { "Ajdnikov" })
public class ReconnectPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;

    // Map to store UUIDs of players waiting to reconnect to a specific server
    private final Map<UUID, String> waitingPlayers = new ConcurrentHashMap<>();

    private boolean debugMode = false;
    private ScheduledTask pingerTask;
    private ScheduledTask messageTask;

    @Inject
    public ReconnectPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Enabling SMP Reconnect v1.0");
        logger.info(" ================================================== ");
        logger.info("   ____  __  __ ____    ____                                         _   ");
        logger.info("  / ___||  \\/  |  _ \\  |  _ \\ ___  ___ ___  _ __  _ __   ___  ___ | |_ ");
        logger.info("  \\___ \\| |\\/| | |_) | | |_) / _ \\/ __/ _ \\| '_ \\| '_ \\ / _ \\/ __|| __|");
        logger.info("   ___) | |  | |  __/  |  _ <  __/ (_| (_) | | | | | | |  __/ (__ | |_ ");
        logger.info("  |____/|_|  |_|_|     |_| \\_\\___|\\___\\___/|_| |_|_| |_|\\___|\\___| \\__|");
        logger.info(" ");
        logger.info("   Version:  1.0");
        logger.info("   Author:   Ajdnikov");
        logger.info(" ================================================== ");
        logger.info(" Initializing modules...");

        configManager = new ConfigManager(dataDirectory, logger);
        if (!configManager.loadConfig()) {
            logger.warn("Failed to load config.yml properly. Defaults will be used.");
        } else {
            logger.info("Config loaded successfully.");
        }

        // Register debug command
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("reconnectdebug").build(),
                new DebugCommand());

        // Register reload command
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("reconnectreload").build(),
                new ReloadCommand());

        // Register vforward command
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("vforward").build(),
                new ForwardCommand(server, configManager));

        // Register reconnect command for whitelist and other subcommands
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("reconnect").build(),
                new ReconnectCommand());

        startPingerTask();
        startMessageTask();

        logger.info("SMP Reconnect plugin enabled successfully!");
    }

    private void debugLog(String message) {
        if (debugMode) {
            logger.info("[DEBUG] " + message);
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        if (waitingPlayers.remove(event.getPlayer().getUniqueId()) != null) {
            debugLog("Player " + event.getPlayer().getUsername() + " disconnected, removed from waiting list.");
        }
    }

    @Subscribe
    public void onPlayerKicked(KickedFromServerEvent event) {
        String serverName = event.getServer().getServerInfo().getName();

        // Use PlainTextComponentSerializer to extract plain text
        String debugReasonText = event.getServerKickReason()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .orElse("Unknown");

        debugLog("Player " + event.getPlayer().getUsername() + " was kicked from server: " + serverName + ". Reason: "
                + debugReasonText);

        // Check blacklist
        if (configManager.getBlacklistedServers() != null
                && configManager.getBlacklistedServers().contains(serverName)) {
            debugLog("Server " + serverName + " is blacklisted. Ignored crash event for player "
                    + event.getPlayer().getUsername());
            return;
        }

        // Filter out intentional bans/kicks
        boolean isCrashOrShutdown = true;
        if (event.getServerKickReason().isPresent()) {
            Component reason = event.getServerKickReason().get();
            String reasonLower = PlainTextComponentSerializer.plainText().serialize(reason).toLowerCase();

            if (reason instanceof TranslatableComponent) {
                String key = ((TranslatableComponent) reason).key();
                if (key.contains("banned") || key.contains("kicked")) {
                    isCrashOrShutdown = false;
                }
            } else if (reasonLower.contains("ban ") || reasonLower.contains("banned") || reasonLower.contains("kick ")
                    || reasonLower.contains("kicked")) {
                isCrashOrShutdown = false;
            }
        }

        if (!isCrashOrShutdown) {
            debugLog("Player " + event.getPlayer().getUsername()
                    + " was kicked/banned explicitly. Not adding to reconnect queue.");
            return;
        }

        // Determine fallback server
        String fallbackName = configManager.getFallbackServerFor(serverName);
        Optional<RegisteredServer> fallbackServer = server.getServer(fallbackName);

        // If the configured fallback is offline or not found, try to find any available
        // server
        if (!fallbackServer.isPresent()
                || fallbackServer.get().getServerInfo().getName().equalsIgnoreCase(serverName)) {
            fallbackServer = server.getAllServers().stream()
                    .filter(s -> !s.getServerInfo().getName().equalsIgnoreCase(serverName))
                    .findFirst();
        }

        if (fallbackServer.isPresent()) {
            Player player = event.getPlayer();
            waitingPlayers.put(player.getUniqueId(), serverName);

            debugLog("Added player " + player.getUsername() + " to waiting list for server " + serverName
                    + ". Current waiting count: " + waitingPlayers.size());

            player.sendMessage(Component.text(
                    "The " + serverName
                            + " server went down. You will be automatically reconnected when it's back online!",
                    NamedTextColor.RED));

            debugLog("Redirecting player " + player.getUsername() + " to fallback server: "
                    + fallbackServer.get().getServerInfo().getName());
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(fallbackServer.get()));
        } else {
            logger.warn("No fallback server found for player " + event.getPlayer().getUsername()
                    + " - could not redirect!");
        }
    }

    private void startPingerTask() {
        if (pingerTask != null) {
            pingerTask.cancel();
        }
        int delay = configManager.getPingDelaySeconds();
        debugLog("Starting background pinger task every " + delay + " seconds.");
        pingerTask = server.getScheduler().buildTask(this, () -> {
            if (waitingPlayers.isEmpty()) {
                return;
            }

            debugLog("Pinger task running. Waiting players: " + waitingPlayers.size());

            // Group players by the target server they want to go to
            Set<String> targetServers = new HashSet<>(waitingPlayers.values());

            for (String targetServerName : targetServers) {
                Optional<RegisteredServer> smpServerOpt = server.getServer(targetServerName);
                if (smpServerOpt.isPresent()) {
                    RegisteredServer smp = smpServerOpt.get();

                    debugLog("Pinging " + targetServerName + "...");
                    smp.ping().thenAccept(ping -> {
                        if (ping != null && ping.getPlayers().isPresent()) {
                            debugLog("Ping to " + targetServerName
                                    + " successful! Players presence detected. Triggering reconnect.");
                            reconnectAllPlayers(smp, targetServerName);
                        } else {
                            debugLog("Ping to " + targetServerName
                                    + " successful, but player data not present (might be starting). Delaying...");
                        }
                    }).exceptionally(ex -> {
                        debugLog("Ping to " + targetServerName + " failed (server is still offline or unreachable): "
                                + ex.getMessage());
                        return null;
                    });
                } else {
                    debugLog("Error: " + targetServerName
                            + " server not found in Velocity configuration! Removing players from queue.");
                    // Remove players waiting for this non-existent server
                    waitingPlayers.entrySet().removeIf(entry -> entry.getValue().equals(targetServerName));
                }
            }
        }).repeat(Duration.ofSeconds(delay)).schedule();
    }

    private void startMessageTask() {
        if (messageTask != null) {
            messageTask.cancel();
        }
        int interval = configManager.getMessageIntervalSeconds();
        if (interval <= 0)
            return; // Feature disabled

        debugLog("Starting periodic chat message task every " + interval + " seconds.");
        messageTask = server.getScheduler().buildTask(this, () -> {
            if (waitingPlayers.isEmpty())
                return;

            String messageRaw = configManager.getReconnectingMessage();
            if (messageRaw == null || messageRaw.isEmpty())
                return;

            Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(messageRaw);

            for (UUID uuid : waitingPlayers.keySet()) {
                Optional<Player> playerOpt = server.getPlayer(uuid);
                playerOpt.ifPresent(player -> player.sendMessage(message));
            }

        }).repeat(Duration.ofSeconds(interval)).schedule();
    }

    private void reconnectAllPlayers(RegisteredServer targetServer, String targetServerName) {
        debugLog("Attempting to reconnect players to " + targetServerName);

        Set<UUID> playersToReconnect = new HashSet<>();

        for (Map.Entry<UUID, String> entry : waitingPlayers.entrySet()) {
            if (entry.getValue().equals(targetServerName)) {
                playersToReconnect.add(entry.getKey());
            }
        }

        for (UUID uuid : playersToReconnect) {
            // Remove them from the list before attempting connection 
            // so we don't spam requests if the pinger runs again while waiting.
            waitingPlayers.remove(uuid);

            Optional<Player> playerOpt = server.getPlayer(uuid);
            if (playerOpt.isPresent()) {
                Player player = playerOpt.get();
                debugLog("Reconnecting player: " + player.getUsername() + " to " + targetServerName);
                player.sendMessage(Component.text("Server " + targetServerName + " is back online! Reconnecting...",
                        NamedTextColor.GREEN));
                
                player.createConnectionRequest(targetServer).connect().thenAccept(result -> {
                    if (!result.isSuccessful() && result.getStatus() != ConnectionRequestBuilder.Status.ALREADY_CONNECTED) {
                        debugLog("Connection to " + targetServerName + " failed for " + player.getUsername() + " (" + result.getStatus() + "). Adding back to queue.");
                        waitingPlayers.put(uuid, targetServerName);
                    } else {
                        debugLog("Successfully reconnected " + player.getUsername() + " to " + targetServerName);
                    }
                }).exceptionally(ex -> {
                    debugLog("Exception during reconnect for " + player.getUsername() + ": " + ex.getMessage());
                    waitingPlayers.put(uuid, targetServerName);
                    return null;
                });
            } else {
                debugLog("Player with UUID " + uuid + " is no longer online.");
            }
        }
        
        debugLog("Processed connection requests for " + targetServerName + ". Remaining waiting total: "
                + waitingPlayers.size());
    }

    // Command handler for toggling debug mode
    private class DebugCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (invocation.source() instanceof Player
                    && !((Player) invocation.source()).hasPermission("smpreconnect.admin")) {
                invocation.source().sendMessage(
                        Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }

            debugMode = !debugMode;
            String status = debugMode ? "ENABLED" : "DISABLED";

            invocation.source().sendMessage(Component.text("SMP-Reconnect debug mode is now " + status,
                    debugMode ? NamedTextColor.GREEN : NamedTextColor.RED));
            logger.info("Debug mode toggled to: " + status + " by " +
                    (invocation.source() instanceof Player ? ((Player) invocation.source()).getUsername() : "Console"));
        }
    }

    // Command handler for reloading configuration
    private class ReloadCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (invocation.source() instanceof Player
                    && !((Player) invocation.source()).hasPermission("smpreconnect.admin")) {
                invocation.source().sendMessage(
                        Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }

            if (configManager.loadConfig()) {
                startPingerTask();
                startMessageTask();
                invocation.source().sendMessage(Component.text("SMP-Reconnect config reloaded successfully!", NamedTextColor.GREEN));
                logger.info("Config reloaded by " + (invocation.source() instanceof Player ? ((Player) invocation.source()).getUsername() : "Console"));
            } else {
                invocation.source().sendMessage(Component.text("Failed to reload SMP-Reconnect config! Check console for errors.", NamedTextColor.RED));
            }
        }
    }

    // Command handler for the /reconnect base command
    private class ReconnectCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            com.velocitypowered.api.command.CommandSource source = invocation.source();
            if (!source.hasPermission("smpreconnect.admin")) {
                source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }

            String[] args = invocation.arguments();
            if (args.length == 0) {
                source.sendMessage(Component.text("Usage: /reconnect whitelist <add/remove/list> [name]", NamedTextColor.RED));
                return;
            }

            if (args[0].equalsIgnoreCase("whitelist")) {
                if (args.length == 1) {
                    source.sendMessage(Component.text("Usage: /reconnect whitelist <add/remove/list> [name]", NamedTextColor.RED));
                    return;
                }

                String action = args[1].toLowerCase();
                if (action.equals("list")) {
                    java.util.Set<String> players = configManager.getWhitelistedPlayers();
                    source.sendMessage(Component.text("Whitelisted Players: " + String.join(", ", players), NamedTextColor.GREEN));
                    return;
                }

                if (args.length < 3) {
                    source.sendMessage(Component.text("Usage: /reconnect whitelist " + action + " <name>", NamedTextColor.RED));
                    return;
                }

                String targetName = args[2];
                if (action.equals("add")) {
                    if (configManager.addWhitelistedPlayer(targetName)) {
                        source.sendMessage(Component.text("Added " + targetName + " to the command whitelist.", NamedTextColor.GREEN));
                    } else {
                        source.sendMessage(Component.text(targetName + " is already whitelisted.", NamedTextColor.YELLOW));
                    }
                } else if (action.equals("remove")) {
                    if (configManager.removeWhitelistedPlayer(targetName)) {
                        source.sendMessage(Component.text("Removed " + targetName + " from the command whitelist.", NamedTextColor.GREEN));
                    } else {
                        source.sendMessage(Component.text(targetName + " is not whitelisted.", NamedTextColor.YELLOW));
                    }
                } else {
                    source.sendMessage(Component.text("Unknown whitelist action: " + action, NamedTextColor.RED));
                }
            } else {
                source.sendMessage(Component.text("Unknown subcommand. Usage: /reconnect whitelist <add/remove/list> [name]", NamedTextColor.RED));
            }
        }
    }
}
