package me.wiefferink.errorsink.spigot.tools;

import java.util.Arrays;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import me.wiefferink.errorsink.common.Log;
import me.wiefferink.errorsink.spigot.SpigotErrorSink;

public class Analytics {

    /**
     * Start analytics tracking.
     */
    public static void start() {
        // bStats statistics
        try {
            Metrics metrics = new Metrics((JavaPlugin) SpigotErrorSink.getInstance(), 1234  );

            // Messages sent since the last collection time (15 minutes)
            metrics.addCustomChart(new SingleLineChart("messages_sent", () -> SpigotErrorSink.getInstance().getAndResetMessageSent()));

            // Number of rules defined for each category
            for (String ruleKey : Arrays.asList("events.filters", "events.rules", "breadcrumbs.rules", "breadcrumbs.filters")) {
                metrics.addCustomChart(new SingleLineChart(ruleKey.replace(".", "_"), () -> {
                    ConfigurationSection ruleSection = SpigotErrorSink.getInstance().getConfig().getConfigurationSection(ruleKey);
                    if (ruleSection != null) {
                        return ruleSection.getKeys(false).size();
                    }
                    return 0;
                }));
            }

            Log.debug("Started bstats.org statistics service");
        } catch (Exception e) {
            Log.debug("Could not start bstats.org statistics service");
        }
    }

}
