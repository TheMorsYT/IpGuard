package com.ipguard.listener;

import com.ipguard.IpGuardPlugin;
import com.ipguard.config.IpGuardConfig;
import com.ipguard.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class ConnectionListener implements Listener {

    private final IpGuardPlugin plugin;
    private final IpGuardConfig config;
    private final LanguageManager languageManager;

    public ConnectionListener(IpGuardPlugin plugin, IpGuardConfig config, LanguageManager languageManager) {
        this.plugin = plugin;
        this.config = config;
        this.languageManager = languageManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();
        InetAddress address = event.getAddress();
        String ip = address != null ? IpGuardConfig.cleanIp(address.getHostAddress()) : "unknown";

        if (config.isProtected(playerName) && !config.isIpAllowed(playerName, ip)) {
            String kickMessage = config.formatKickMessage(playerName, ip);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            logAndAlertBlocked(playerName, ip, false);
            return;
        }

        if (config.isProtectOpAccounts() && isKnownOperator(event.getUniqueId(), playerName)) {
            if (!config.isAuthorizedOp(playerName, ip)) {
                String kickMessage = config.formatOpKickMessage(playerName, ip);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
                logAndAlertBlocked(playerName, ip, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!config.isProtectOpAccounts()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isOp()) {
            return;
        }
        InetAddress address = event.getAddress();
        String ip = address != null ? IpGuardConfig.cleanIp(address.getHostAddress()) : extractPlayerIp(player);

        if (!config.isAuthorizedOp(player.getName(), ip)) {
            if (config.isAutoDeopUnauthorized()) {
                try {
                    player.setOp(false);
                } catch (Throwable ignored) {
                }
            }
            String kickMessage = config.formatOpKickMessage(player.getName(), ip);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
            logAndAlertBlocked(player.getName(), ip, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (checkAndEnforceOnlineOp(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (config.isProtectOpAccounts() && player.isOp()) {
            String ip = extractPlayerIp(player);
            if (!config.isAuthorizedOp(player.getName(), ip)) {
                event.setCancelled(true);
                dispatchPlayerTask(player, () -> checkAndEnforceOnlineOp(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (checkAndEnforceOnlineOp(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (checkAndEnforceOnlineOp(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (checkAndEnforceOnlineOp(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (who instanceof Player) {
            if (checkAndEnforceOnlineOp((Player) who)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            languageManager.cachePlayerLocale(player.getName(), player.getLocale());
        } catch (Throwable ignored) {
        }
        checkAndEnforceOnlineOp(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLocaleChange(PlayerLocaleChangeEvent event) {
        languageManager.cachePlayerLocale(event.getPlayer().getName(), event.getLocale());
    }

    public void inspectAllOnlinePlayers() {
        if (!config.isProtectOpAccounts()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                String ip = extractPlayerIp(player);
                if (!config.isAuthorizedOp(player.getName(), ip)) {
                    dispatchPlayerTask(player, () -> checkAndEnforceOnlineOp(player));
                }
            }
        }
    }

    public boolean checkAndEnforceOnlineOp(Player player) {
        if (!config.isProtectOpAccounts() || player == null || !player.isOp()) {
            return false;
        }
        String playerName = player.getName();
        String ip = extractPlayerIp(player);
        if (config.isAuthorizedOp(playerName, ip)) {
            return false;
        }

        if (config.isAutoDeopUnauthorized()) {
            try {
                player.setOp(false);
            } catch (Throwable ignored) {
            }
        }

        String kickMessage = config.formatOpKickMessage(playerName, ip);
        try {
            player.kickPlayer(kickMessage);
        } catch (Throwable ignored) {
        }

        logAndAlertBlocked(playerName, ip, true);
        return true;
    }

    private boolean isKnownOperator(UUID uuid, String playerName) {
        try {
            Set<OfflinePlayer> operators = Bukkit.getOperators();
            for (OfflinePlayer op : operators) {
                if (uuid != null && uuid.equals(op.getUniqueId())) {
                    return true;
                }
                String opName = op.getName();
                if (opName != null && opName.equalsIgnoreCase(playerName)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String extractPlayerIp(Player player) {
        try {
            InetSocketAddress socketAddress = player.getAddress();
            if (socketAddress != null && socketAddress.getAddress() != null) {
                return IpGuardConfig.cleanIp(socketAddress.getAddress().getHostAddress());
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private void logAndAlertBlocked(String playerName, String ip, boolean opViolation) {
        if (config.isLogBlockedAttempts()) {
            String logKey = opViolation ? "console-blocked-op-log" : "console-blocked-log";
            String logLine = config.getServerRawMessage(logKey)
                    .replace("%player%", playerName)
                    .replace("%ip%", ip);
            plugin.getLogger().warning(logLine);
        }

        if (config.isNotifyAdminsInGame()) {
            dispatchSync(() -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("ipguard.admin")) {
                        String alert = opViolation
                                ? config.formatOpAdminAlert(online, playerName, ip)
                                : config.formatAdminAlert(online, playerName, ip);
                        online.sendMessage(alert);
                    }
                }
            });
        }
    }

    private void dispatchPlayerTask(Player player, Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (UnsupportedOperationException e) {
            try {
                Method getScheduler = player.getClass().getMethod("getScheduler");
                Object entityScheduler = getScheduler.invoke(player);
                Method run = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                Consumer<Object> consumer = scheduledTask -> task.run();
                run.invoke(entityScheduler, plugin, consumer, null);
            } catch (Throwable ignored) {
                dispatchSync(task);
            }
        }
    }

    private void dispatchSync(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (UnsupportedOperationException e) {
            try {
                Method getScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object globalScheduler = getScheduler.invoke(null);
                Method execute = globalScheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
                execute.invoke(globalScheduler, plugin, task);
            } catch (Throwable ignored) {
                try {
                    Method getScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                    Object globalScheduler = getScheduler.invoke(null);
                    Method run = globalScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                    Consumer<Object> consumer = scheduledTask -> task.run();
                    run.invoke(globalScheduler, plugin, consumer);
                } catch (Throwable ignoredFallback) {
                }
            }
        }
    }
}
