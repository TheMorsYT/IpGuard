package com.ipguard.config;

import com.ipguard.IpGuardPlugin;
import com.ipguard.lang.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IpGuardConfig {

    private final IpGuardPlugin plugin;
    private final LanguageManager languageManager;
    private final Map<String, Set<String>> protectedPlayers = new ConcurrentHashMap<>();
    private final Map<String, String> originalNames = new ConcurrentHashMap<>();

    private volatile String language = "en";
    private volatile boolean logBlockedAttempts = true;
    private volatile boolean notifyAdminsInGame = true;
    private volatile boolean protectOpAccounts = true;
    private volatile boolean autoDeopUnauthorized = true;
    private volatile long opCheckIntervalTicks = 20L;

    public IpGuardConfig(IpGuardPlugin plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
    }

    public synchronized void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        languageManager.load();

        FileConfiguration cfg = plugin.getConfig();

        protectedPlayers.clear();
        originalNames.clear();

        ConfigurationSection section = cfg.getConfigurationSection("protected-players");
        if (section != null) {
            for (String playerKey : section.getKeys(false)) {
                String lowerKey = playerKey.toLowerCase(Locale.ROOT);
                Set<String> ips = ConcurrentHashMap.newKeySet();

                if (section.isList(playerKey)) {
                    for (String ip : section.getStringList(playerKey)) {
                        String cleaned = cleanIp(ip);
                        if (!cleaned.isEmpty()) {
                            ips.add(cleaned);
                        }
                    }
                } else {
                    String singleIp = section.getString(playerKey, "");
                    String cleaned = cleanIp(singleIp);
                    if (!cleaned.isEmpty()) {
                        ips.add(cleaned);
                    }
                }

                protectedPlayers.put(lowerKey, ips);
                originalNames.put(lowerKey, playerKey);
            }
        }

        String configuredLang = cfg.getString("language", "en");
        if (languageManager.isValidLanguageOption(configuredLang)) {
            this.language = configuredLang.toLowerCase(Locale.ROOT);
        } else {
            this.language = "en";
        }

        this.logBlockedAttempts = cfg.getBoolean("log-blocked-attempts", true);
        this.notifyAdminsInGame = cfg.getBoolean("notify-admins-in-game", true);
        this.protectOpAccounts = cfg.getBoolean("protect-op-accounts", true);
        this.autoDeopUnauthorized = cfg.getBoolean("auto-deop-unauthorized", true);
        long interval = cfg.getLong("op-check-interval-ticks", 20L);
        this.opCheckIntervalTicks = Math.max(5L, interval);
    }

    public boolean isProtected(String playerName) {
        return protectedPlayers.containsKey(playerName.toLowerCase(Locale.ROOT));
    }

    public boolean isIpAllowed(String playerName, String ipAddress) {
        String lowerKey = playerName.toLowerCase(Locale.ROOT);
        Set<String> allowedIps = protectedPlayers.get(lowerKey);
        if (allowedIps == null) {
            return true;
        }
        return matchesAllowedSet(allowedIps, ipAddress);
    }

    public boolean isAuthorizedOp(String playerName, String ipAddress) {
        String lowerKey = playerName.toLowerCase(Locale.ROOT);
        Set<String> allowedIps = protectedPlayers.get(lowerKey);
        if (allowedIps == null || allowedIps.isEmpty()) {
            return false;
        }
        return matchesAllowedSet(allowedIps, ipAddress);
    }

    private boolean matchesAllowedSet(Set<String> allowedIps, String ipAddress) {
        String cleanedIp = cleanIp(ipAddress);
        if (allowedIps.contains(cleanedIp)) {
            return true;
        }
        if (isLoopback(cleanedIp) && (allowedIps.contains("127.0.0.1") || allowedIps.contains("::1"))) {
            return true;
        }
        return false;
    }

    public synchronized boolean addAllowedIp(String playerName, String ipAddress) {
        String lowerKey = playerName.toLowerCase(Locale.ROOT);
        String displayKey = originalNames.computeIfAbsent(lowerKey, k -> playerName);
        Set<String> ips = protectedPlayers.computeIfAbsent(lowerKey, k -> ConcurrentHashMap.newKeySet());

        String cleaned = cleanIp(ipAddress);
        boolean added = ips.add(cleaned);
        if (added) {
            savePlayerIpsToConfig(displayKey, ips);
        }
        return added;
    }

    public synchronized boolean removeAllowedIp(String playerName, String ipAddress) {
        String lowerKey = playerName.toLowerCase(Locale.ROOT);
        Set<String> ips = protectedPlayers.get(lowerKey);
        if (ips == null) {
            return false;
        }
        String cleaned = cleanIp(ipAddress);
        boolean removed = ips.remove(cleaned);
        if (removed) {
            String displayKey = originalNames.getOrDefault(lowerKey, playerName);
            savePlayerIpsToConfig(displayKey, ips);
        }
        return removed;
    }

    public synchronized void setLanguage(String newLang) {
        this.language = newLang.toLowerCase(Locale.ROOT);
        plugin.getConfig().set("language", this.language);
        plugin.saveConfig();
    }

    public String getLanguage() {
        return language;
    }

    private void savePlayerIpsToConfig(String displayKey, Set<String> ips) {
        FileConfiguration cfg = plugin.getConfig();
        List<String> sortedList = new ArrayList<>(ips);
        Collections.sort(sortedList);
        cfg.set("protected-players." + displayKey, sortedList);
        plugin.saveConfig();
    }

    public Map<String, Set<String>> getProtectedPlayersSnapshot() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : protectedPlayers.entrySet()) {
            String display = originalNames.getOrDefault(entry.getKey(), entry.getKey());
            result.put(display, new LinkedHashSet<>(entry.getValue()));
        }
        return result;
    }

    public boolean isLogBlockedAttempts() {
        return logBlockedAttempts;
    }

    public boolean isNotifyAdminsInGame() {
        return notifyAdminsInGame;
    }

    public boolean isProtectOpAccounts() {
        return protectOpAccounts;
    }

    public boolean isAutoDeopUnauthorized() {
        return autoDeopUnauthorized;
    }

    public long getOpCheckIntervalTicks() {
        return opCheckIntervalTicks;
    }

    public String getMessage(CommandSender sender, String key) {
        String resolvedLang = languageManager.resolveLanguage(this.language, sender);
        return languageManager.getColored(resolvedLang, key);
    }

    public String getServerMessage(String key) {
        String resolvedLang = languageManager.resolveLanguage(this.language, null);
        return languageManager.getColored(resolvedLang, key);
    }

    public String getServerRawMessage(String key) {
        String resolvedLang = languageManager.resolveLanguage(this.language, null);
        return languageManager.getRaw(resolvedLang, key);
    }

    public String formatKickMessage(String playerName, String ip) {
        String resolvedLang = languageManager.resolveLanguageForPlayerName(this.language, playerName);
        return languageManager.getColored(resolvedLang, "kick-unauthorized-ip")
                .replace("%player%", playerName)
                .replace("%ip%", ip);
    }

    public String formatOpKickMessage(String playerName, String ip) {
        String resolvedLang = languageManager.resolveLanguageForPlayerName(this.language, playerName);
        return languageManager.getColored(resolvedLang, "kick-unauthorized-op")
                .replace("%player%", playerName)
                .replace("%ip%", ip);
    }

    public String formatAdminAlert(CommandSender recipient, String playerName, String ip) {
        String resolvedLang = languageManager.resolveLanguage(this.language, recipient);
        return languageManager.getColored(resolvedLang, "admin-alert")
                .replace("%player%", playerName)
                .replace("%ip%", ip);
    }

    public String formatOpAdminAlert(CommandSender recipient, String playerName, String ip) {
        String resolvedLang = languageManager.resolveLanguage(this.language, recipient);
        return languageManager.getColored(resolvedLang, "admin-alert-op")
                .replace("%player%", playerName)
                .replace("%ip%", ip);
    }

    public static String cleanIp(String rawIp) {
        if (rawIp == null) {
            return "";
        }
        String ip = rawIp.trim();
        if (ip.startsWith("/")) {
            ip = ip.substring(1);
        }
        return ip;
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }
}
