🚀 SMP-Reconnect: Never let your players leave the server!
Does your server need a restart occasionally? Do you experience sub-server crashes that kick players off the network? SMP-Reconnect is the solution every modern Velocity proxy needs!

This plugin ensures that players remain connected to the proxy when a server crashes or shuts down. Instead of being kicked, players are redirected to a fallback server (e.g., lobby) and automatically returned once the target server is available again.

✨ Key Features
⚡ Automatic Redirection (Anti-Kick): If a server becomes unreachable, players aren't kicked but safely redirected to a predefined lobby.

📡 Intelligent Pinging: The plugin constantly monitors the status of the crashed server in the background. As soon as the server is "alive," it prepares the players for return.

🔄 Automatic Reconnect: Once the server is ready, the plugin automatically moves players back to their original server. No manual action is required from the player!

💬 Real-time Notifications: Players receive updates about the reconnection status while waiting, so they are always informed.

🛡️ Smart Filtering: The plugin distinguishes between a server crash and a manual kick or ban. If a player was intentionally kicked, the plugin will NOT attempt to reconnect them.

⚙️ Fully Customizable: Everything from ping delays, chat messages, to blacklisted servers is configurable in the config.yml.

🛠️ Commands & Permissions

Command	Description	Permission

/reconnectreload	Reloads the configuration from config.yml.	smpreconnect.admin

/reconnectdebug	Toggles detailed console logs for easier troubleshooting.	smpreconnect.admin

📂 Configuration (config.yml)

The plugin allows precise control over its behavior:


blacklisted_servers: A list of servers where you don't want auto-reconnect (e.g., Login, Auth, Lobby).

fallback_servers_map: Define a specific lobby for each individual server.

general_fallback: The default lobby for any server not explicitly defined.

ping_delay_seconds: How often the plugin should check the server status.

message_interval_seconds: The interval between notification messages for waiting players.

reconnecting_message: Customize the message seen by waiting players.

🚀 Installation

Drop the .jar file into the plugins folder of your Velocity proxy.

Restart or start the proxy to generate the default configuration.

Edit config.yml to fit your needs.

Use /reconnectreload and enjoy a seamless player experience!



