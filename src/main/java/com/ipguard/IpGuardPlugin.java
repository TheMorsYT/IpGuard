package com.ipguard;

import com.ipguard.command.IpGuardCommand;
import com.ipguard.config.IpGuardConfig;
import com.ipguard.lang.LanguageManager;
import com.ipguard.listener.ConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class IpGuardPlugin extends JavaPlugin {

    private LanguageManager languageManager;
    private IpGuardConfig ipGuardConfig;
    private ConnectionListener connectionListener;

    @Override
    public void onEnable() {
        this.languageManager = new LanguageManager(this);
        this.ipGuardConfig = new IpGuardConfig(this, this.languageManager);
        this.ipGuardConfig.load();

        this.connectionListener = new ConnectionListener(this, this.ipGuardConfig, this.languageManager);
        getServer().getPluginManager().registerEvents(this.connectionListener, this);

        PluginCommand command = getCommand("ipguard");
        if (command != null) {
            IpGuardCommand executor = new IpGuardCommand(this.ipGuardConfig, this.languageManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        startOpWatchdog();

        String enabledMsg = this.ipGuardConfig.getServerRawMessage("console-enabled")
                .replace("%count%", String.valueOf(this.ipGuardConfig.getProtectedPlayersSnapshot().size()))
                .replace("%lang%", this.ipGuardConfig.getLanguage());
        getLogger().info(enabledMsg);
    }

    @Override
    public void onDisable() {
        try {
            Bukkit.getScheduler().cancelTasks(this);
        } catch (Throwable ignored) {
        }
        if (this.ipGuardConfig != null) {
            getLogger().info(this.ipGuardConfig.getServerRawMessage("console-disabled"));
        }
    }

    private void startOpWatchdog() {
        long interval = this.ipGuardConfig.getOpCheckIntervalTicks();
        Runnable task = () -> {
            if (this.connectionListener != null) {
                this.connectionListener.inspectAllOnlinePlayers();
            }
        };

        try {
            Bukkit.getScheduler().runTaskTimer(this, task, interval, interval);
        } catch (UnsupportedOperationException e) {
            try {
                Method getScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object globalScheduler = getScheduler.invoke(null);
                Method runAtFixedRate = globalScheduler.getClass().getMethod(
                        "runAtFixedRate",
                        Plugin.class,
                        Consumer.class,
                        long.class,
                        long.class
                );
                Consumer<Object> consumer = scheduledTask -> task.run();
                runAtFixedRate.invoke(globalScheduler, this, consumer, interval, interval);
            } catch (Throwable ignored) {
            }
        }
    }

    public IpGuardConfig getIpGuardConfig() {
        return ipGuardConfig;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}
