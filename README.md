# IpGuard

![Minecraft Versions](https://img.shields.io/badge/Minecraft-1.16.5%20--%2026.3-success)
![Java](https://img.shields.io/badge/Java-11%20--%2025%2B-orange)
![Platforms](https://img.shields.io/badge/Platform-Spigot%20%7C%20Paper%20%7C%20Purpur%20%7C%20Folia-blue)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)

Lightweight and thread-safe account IP verification and Anti-ForceOP protection plugin for Minecraft servers (**1.16.5 — 26.3**).  
Protects privileged player accounts and server operators (OP) by allowing access only from whitelisted IP addresses.

Лёгкий и потокобезопасный плагин защиты аккаунтов по IP-адресу и защиты от взлома OP (Anti-ForceOP) для серверов Minecraft (**1.16.5 — 26.3**).  
Позволяет ограничить вход на указанные ники и использование прав оператора (OP) только с разрешённых IP-адресов.

---

## Features / Возможности

- **Wide Compatibility / Широкая совместимость**: Works out of the box on **Spigot, Paper, Purpur, Pufferfish, and Folia** from **1.16.5** up to **26.3** (compiled for Java 11+, runs on Java 11, 16, 17, 21, 25+).
- **Anti-ForceOP Protection / Защита от взлома OP**: Automatically checks all players with OP privileges (on login, on every command/action, and via a real-time background watchdog). If a player gains OP through an exploit without having a whitelisted IP in `protected-players`, their action is cancelled, OP is revoked (`auto-deop-unauthorized`), and they are kicked immediately.
- **Pre-Login Protection / Защита на этапе подключения**: Uses `AsyncPlayerPreLoginEvent` (`LOWEST` priority) so unauthorized connections are rejected before entering the server — attackers cannot kick an already online player with the same nickname.
- **Case-Insensitive Matching / Независимость от регистра**: Prevents bypass attempts via different letter casing on offline-mode servers.
- **Built-in Localization / Встроенная локализация**: Includes **English (`en`)**, **Russian (`ru`)**, **Ukrainian (`uk`)**, **German (`de`)**, **Spanish (`es`)**, plus automatic client/system language detection (`auto`).
- **In-Game & Console Management / Управление командами**: Add/remove allowed IPs, switch languages, and reload config without restarting the server.

---

## Commands & Permissions / Команды и права

**Permission / Право:** `ipguard.admin` *(default: `op`)*

| Command / Команда | Description / Описание |
|---|---|
| `/ipguard reload` | Reload `config.yml` and language files / Перезагрузить конфиг и языковые файлы |
| `/ipguard list` | List protected accounts and allowed IPs / Показать список защищённых ников и их IP |
| `/ipguard addip <player> <ip>` | Add an allowed IP for a nickname / Добавить разрешённый IP для ника |
| `/ipguard removeip <player> <ip>` | Remove an allowed IP from a nickname / Удалить разрешённый IP у ника |
| `/ipguard lang <en\|ru\|uk\|de\|es\|auto>` | Switch plugin language / Изменить язык плагина |

---

## Configuration / Конфигурация (`config.yml`)

```yaml
# Available: "en", "ru", "uk", "de", "es", "auto"
language: "en"

log-blocked-attempts: true
notify-admins-in-game: true

# Require all OP accounts to have a whitelisted IP in 'protected-players'
protect-op-accounts: true
auto-deop-unauthorized: true
op-check-interval-ticks: 20

# Example:
# protected-players:
#   ExamplePlayer:
#     - "127.0.0.1"
#     - "192.168.1.10"
protected-players: {}
```

All localization files are automatically generated in `plugins/IpGuard/lang/messages_<code>.yml` and can be customized freely.

---

## Building from Source / Сборка из исходников

```bash
./gradlew build
```

The compiled JAR will be located in `build/libs/IpGuard-1.0.0.jar`.
