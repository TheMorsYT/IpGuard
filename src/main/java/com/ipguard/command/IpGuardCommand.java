package com.ipguard.command;

import com.ipguard.config.IpGuardConfig;
import com.ipguard.lang.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IpGuardCommand implements CommandExecutor, TabCompleter {

    private final IpGuardConfig config;
    private final LanguageManager languageManager;

    public IpGuardCommand(IpGuardConfig config, LanguageManager languageManager) {
        this.config = config;
        this.languageManager = languageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        if (!sender.hasPermission("ipguard.admin")) {
            sender.sendMessage(config.getMessage(sender, "no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                config.load();
                sender.sendMessage(config.getMessage(sender, "reloaded"));
                break;
            case "list":
                handleList(sender);
                break;
            case "addip":
                handleAddIp(sender, label, args);
                break;
            case "removeip":
                handleRemoveIp(sender, label, args);
                break;
            case "lang":
                handleLang(sender, label, args);
                break;
            default:
                sendHelp(sender, label);
                break;
        }
        return true;
    }

    private void handleList(CommandSender sender) {
        Map<String, Set<String>> snapshot = config.getProtectedPlayersSnapshot();
        if (snapshot.isEmpty()) {
            sender.sendMessage(config.getMessage(sender, "list-empty"));
            return;
        }
        sender.sendMessage(config.getMessage(sender, "list-header"));
        String noIpsText = config.getMessage(sender, "list-no-ips");
        String entryTemplate = config.getMessage(sender, "list-entry");
        for (Map.Entry<String, Set<String>> entry : snapshot.entrySet()) {
            String ips = entry.getValue().isEmpty()
                    ? noIpsText
                    : LanguageManager.colorize("&a" + String.join("&7, &a", entry.getValue()));
            sender.sendMessage(
                    entryTemplate
                            .replace("%player%", entry.getKey())
                            .replace("%ips%", ips)
            );
        }
    }

    private void handleAddIp(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(config.getMessage(sender, "usage-addip").replace("%label%", label));
            return;
        }
        String targetPlayer = args[1];
        String ip = IpGuardConfig.cleanIp(args[2]);
        boolean added = config.addAllowedIp(targetPlayer, ip);
        String key = added ? "ip-added" : "ip-already-exists";
        sender.sendMessage(
                config.getMessage(sender, key)
                        .replace("%player%", targetPlayer)
                        .replace("%ip%", ip)
        );
    }

    private void handleRemoveIp(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(config.getMessage(sender, "usage-removeip").replace("%label%", label));
            return;
        }
        String targetPlayer = args[1];
        String ip = IpGuardConfig.cleanIp(args[2]);
        boolean removed = config.removeAllowedIp(targetPlayer, ip);
        String key = removed ? "ip-removed" : "ip-not-found";
        sender.sendMessage(
                config.getMessage(sender, key)
                        .replace("%player%", targetPlayer)
                        .replace("%ip%", ip)
        );
    }

    private void handleLang(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(config.getMessage(sender, "usage-lang").replace("%label%", label));
            return;
        }
        String targetLang = args[1].toLowerCase(Locale.ROOT);
        if (!languageManager.isValidLanguageOption(targetLang)) {
            sender.sendMessage(config.getMessage(sender, "lang-invalid").replace("%lang%", args[1]));
            return;
        }
        config.setLanguage(targetLang);
        sender.sendMessage(config.getMessage(sender, "lang-changed").replace("%lang%", targetLang));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(config.getMessage(sender, "help-header"));
        sender.sendMessage(config.getMessage(sender, "help-reload").replace("%label%", label));
        sender.sendMessage(config.getMessage(sender, "help-list").replace("%label%", label));
        sender.sendMessage(config.getMessage(sender, "help-addip").replace("%label%", label));
        sender.sendMessage(config.getMessage(sender, "help-removeip").replace("%label%", label));
        sender.sendMessage(config.getMessage(sender, "help-lang").replace("%label%", label));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {
        if (!sender.hasPermission("ipguard.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartingWith(Arrays.asList("reload", "list", "addip", "removeip", "lang"), args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("addip") || args[0].equalsIgnoreCase("removeip")) {
                List<String> players = new ArrayList<>(config.getProtectedPlayersSnapshot().keySet());
                return filterStartingWith(players, args[1]);
            }
            if (args[0].equalsIgnoreCase("lang")) {
                List<String> langs = new ArrayList<>(languageManager.getSupportedLanguages());
                langs.add("auto");
                return filterStartingWith(langs, args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("removeip")) {
            Map<String, Set<String>> snapshot = config.getProtectedPlayersSnapshot();
            for (Map.Entry<String, Set<String>> entry : snapshot.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(args[1])) {
                    return filterStartingWith(new ArrayList<>(entry.getValue()), args[2]);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartingWith(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(opt);
            }
        }
        return result;
    }
}
