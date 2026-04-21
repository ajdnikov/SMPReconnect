package velo.reconnect;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final Path dataDirectory;
    private final Logger logger;
    private CommentedConfigurationNode rootNode;

    // Config settings
    private List<String> blacklistedServers;
    private Map<String, String> defaultSpawns;
    private String generalFallback;
    private int pingDelaySeconds;
    private int messageIntervalSeconds;
    private String reconnectingMessage;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public boolean loadConfig() {
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (Exception e) {
                logger.error("Could not create plugin data directory", e);
                return false;
            }
        }

        Path configFile = dataDirectory.resolve("config.yml");
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .build();

        try {
            if (!Files.exists(configFile)) {
                // Generate default config
                rootNode = loader.createNode();

                rootNode.node("blacklisted_servers").setList(String.class, Arrays.asList("lobby", "login", "auth"));
                rootNode.node("blacklisted_servers")
                        .comment("Servers where offline players shouldn't trigger an auto-reconnect logic.");

                rootNode.node("fallback_servers_map").node("bedwars").set("bedwars_hub");
                rootNode.node("fallback_servers_map").node("survival").set("lobby");
                rootNode.node("fallback_servers_map")
                        .comment("Specific fallback server to redirect a player to, when specific server goes down.");

                rootNode.node("general_fallback").set("lobby");
                rootNode.node("general_fallback")
                        .comment("The fallback server to use if it's not specified in fallback_servers_map.");

                rootNode.node("ping_delay_seconds").set(5);
                rootNode.node("ping_delay_seconds").comment(
                        "How often (in seconds) should the plugin ping crashed servers to see if they are back up.");

                rootNode.node("message_interval_seconds").set(15);
                rootNode.node("message_interval_seconds")
                        .comment("How often (in seconds) should waiting players receive a notification message.");

                rootNode.node("reconnecting_message")
                        .set("§aThe server is still starting up! You will be connected shortly...");
                rootNode.node("reconnecting_message").comment("The periodic message sent to waiting players.");

                loader.save(rootNode);
                logger.info("Generated default config.yml!");
            } else {
                rootNode = loader.load();
            }

            // Load values
            if (rootNode.node("blacklisted_servers").virtual()) {
                blacklistedServers = Arrays.asList("lobby", "login", "auth");
            } else {
                blacklistedServers = rootNode.node("blacklisted_servers").getList(String.class);
                if (blacklistedServers == null) {
                    blacklistedServers = new java.util.ArrayList<>();
                }
            }

            defaultSpawns = new HashMap<>();
            rootNode.node("fallback_servers_map").childrenMap().forEach((k, v) -> {
                if (k != null && v != null) {
                    defaultSpawns.put(k.toString(), v.getString());
                }
            });

            generalFallback = rootNode.node("general_fallback").getString("lobby");
            pingDelaySeconds = rootNode.node("ping_delay_seconds").getInt(5);
            messageIntervalSeconds = rootNode.node("message_interval_seconds").getInt(15);
            reconnectingMessage = rootNode.node("reconnecting_message")
                    .getString("§aThe server is still starting up! You will be connected shortly...");

            return true;
        } catch (ConfigurateException e) {
            logger.error("Could not load config.yml", e);
            return false;
        }
    }

    public List<String> getBlacklistedServers() {
        return blacklistedServers;
    }

    public String getFallbackServerFor(String crashedServer) {
        return defaultSpawns.getOrDefault(crashedServer, generalFallback);
    }

    public int getPingDelaySeconds() {
        return pingDelaySeconds;
    }

    public int getMessageIntervalSeconds() {
        return messageIntervalSeconds;
    }

    public String getReconnectingMessage() {
        return reconnectingMessage;
    }
}
