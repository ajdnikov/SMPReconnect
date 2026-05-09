# 🚀 SMP-Reconnect

**SMP-Reconnect** is a Velocity proxy plugin that ensures players remain connected to the proxy when a backend server crashes or shuts down. Instead of being kicked, players are redirected to a fallback server and automatically returned once the target server is available again.

## ✨ Features
*   **Automatic Redirection (Anti-Kick):** Redirects players to a lobby instead of kicking them during a crash.
*   **Intelligent Pinging:** Monitors crashed servers and detects when they are back online.
*   **Automatic Reconnect:** Moves players back to their original server automatically.
*   **Smart Filtering:** Ignores intentional kicks or bans.
*   **Customizable:** Full control over messages, delays, and fallback logic.

## 🚀 Installation
1. Download the `.jar` file from [GitHub](https://github.com/ajdnikov/SMPReconnect).
2. Drop it into the `plugins` folder of your **Velocity** proxy.
3. Start the proxy and configure `config.yml`.

## 🛠️ Commands
*   `/reconnectreload` - Reload configuration.
*   `/reconnectdebug` - Toggle debug mode.

---
**GitHub:** [Ajdnikov/SMPReconnect](https://github.com/ajdnikov/SMPReconnect)
**License:** [MIT](LICENSE)
**Disclaimer:** [Read here](DISCLAIMER.md)
**Discord:** [Join](https://discord.com/invite/xyjmcjMp4W)
