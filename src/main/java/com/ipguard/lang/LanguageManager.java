package com.ipguard.lang;

import com.ipguard.IpGuardPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageManager {

    private static final List<String> SUPPORTED_LANGUAGES = Collections.unmodifiableList(
            Arrays.asList("en", "ru", "uk", "de", "es")
    );

    private final IpGuardPlugin plugin;
    private final Map<String, Map<String, String>> translations = new ConcurrentHashMap<>();
    private final Map<String, String> playerLocaleCache = new ConcurrentHashMap<>();

    public LanguageManager(IpGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        translations.clear();
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        for (String lang : SUPPORTED_LANGUAGES) {
            String resourcePath = "lang/messages_" + lang + ".yml";
            File targetFile = new File(langDir, "messages_" + lang + ".yml");
            if (!targetFile.exists()) {
                plugin.saveResource(resourcePath, false);
            }

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(targetFile);
            InputStream defaultStream = plugin.getResource(resourcePath);
            if (defaultStream != null) {
                YamlConfiguration defaultYaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );
                yaml.setDefaults(defaultYaml);
            }

            Map<String, String> map = new HashMap<>();
            for (String key : yaml.getKeys(true)) {
                if (!yaml.isConfigurationSection(key)) {
                    String val = yaml.getString(key);
                    if (val != null) {
                        map.put(key, val);
                    }
                }
            }
            translations.put(lang, map);
        }
    }

    public List<String> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public boolean isValidLanguageOption(String option) {
        if (option == null) {
            return false;
        }
        String lower = option.toLowerCase(Locale.ROOT);
        return "auto".equals(lower) || SUPPORTED_LANGUAGES.contains(lower);
    }

    public void cachePlayerLocale(String playerName, String rawLocale) {
        if (playerName == null || rawLocale == null) {
            return;
        }
        String code = extractLangCode(rawLocale);
        if (SUPPORTED_LANGUAGES.contains(code)) {
            playerLocaleCache.put(playerName.toLowerCase(Locale.ROOT), code);
        }
    }

    public String resolveLanguage(String configuredLang, CommandSender sender) {
        if (configuredLang != null && !"auto".equalsIgnoreCase(configuredLang)) {
            String lower = configuredLang.toLowerCase(Locale.ROOT);
            if (SUPPORTED_LANGUAGES.contains(lower)) {
                return lower;
            }
        }
        if (sender instanceof Player) {
            Player player = (Player) sender;
            try {
                String playerLocale = player.getLocale();
                String code = extractLangCode(playerLocale);
                if (SUPPORTED_LANGUAGES.contains(code)) {
                    return code;
                }
            } catch (Throwable ignored) {
            }
        }
        return resolveSystemLanguage();
    }

    public String resolveLanguageForPlayerName(String configuredLang, String playerName) {
        if (configuredLang != null && !"auto".equalsIgnoreCase(configuredLang)) {
            String lower = configuredLang.toLowerCase(Locale.ROOT);
            if (SUPPORTED_LANGUAGES.contains(lower)) {
                return lower;
            }
        }
        if (playerName != null) {
            String cached = playerLocaleCache.get(playerName.toLowerCase(Locale.ROOT));
            if (cached != null && SUPPORTED_LANGUAGES.contains(cached)) {
                return cached;
            }
        }
        return resolveSystemLanguage();
    }

    public String resolveSystemLanguage() {
        String sys = Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT);
        if (SUPPORTED_LANGUAGES.contains(sys)) {
            return sys;
        }
        return "en";
    }

    public String getRaw(String lang, String key) {
        Map<String, String> langMap = translations.get(lang);
        if (langMap != null && langMap.containsKey(key)) {
            return langMap.get(key);
        }
        Map<String, String> enMap = translations.get("en");
        if (enMap != null && enMap.containsKey(key)) {
            return enMap.get(key);
        }
        return key;
    }

    public String getColored(String lang, String key) {
        return colorize(getRaw(lang, key));
    }

    private String extractLangCode(String localeStr) {
        if (localeStr == null || localeStr.isEmpty()) {
            return "en";
        }
        String lower = localeStr.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf('_');
        if (idx > 0) {
            return lower.substring(0, idx);
        }
        int dashIdx = lower.indexOf('-');
        if (dashIdx > 0) {
            return lower.substring(0, dashIdx);
        }
        return lower;
    }

    public static String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
