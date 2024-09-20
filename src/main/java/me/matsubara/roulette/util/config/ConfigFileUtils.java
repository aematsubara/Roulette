package me.matsubara.roulette.util.config;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.apache.commons.io.FileUtils;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class ConfigFileUtils {

    public static void updateConfig(JavaPlugin plugin,
                                    String folderName,
                                    String fileName,
                                    Consumer<File> reloadAfterUpdating,
                                    Consumer<File> resetConfiguration,
                                    Function<FileConfiguration, List<String>> ignoreSection,
                                    List<ConfigChanges> changes) {
        File file = new File(folderName, fileName);

        FileConfiguration config = reloadConfig(plugin, file, resetConfiguration);
        if (config == null) {
            plugin.getLogger().severe("Can't find {" + file.getName() + "}!");
            return;
        }

        for (ConfigChanges change : changes) {
            handleConfigChanges(plugin, file, config, change.predicate(), change.consumer(), change.newVersion());
        }

        try {
            ConfigUpdater.update(
                    plugin,
                    fileName,
                    file,
                    ignoreSection.apply(config));
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        reloadAfterUpdating.accept(file);
    }

    private static void handleConfigChanges(JavaPlugin plugin,
                                            @NotNull File file,
                                            FileConfiguration config,
                                            @NotNull Predicate<FileConfiguration> predicate,
                                            Consumer<FileConfiguration> consumer,
                                            int newVersion) {
        if (!predicate.test(config)) return;

        int previousVersion = config.getInt("config-version", -1);
        plugin.getLogger().info("Updated {%s} config to v{%s} (from v{%s})".formatted(file.getName(), newVersion, previousVersion));

        consumer.accept(config);
        config.set("config-version", newVersion);

        try {
            config.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public static @Nullable FileConfiguration reloadConfig(JavaPlugin plugin, @NotNull File file, @Nullable Consumer<File> error) {
        File backup = null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String time = format.format(new Date(System.currentTimeMillis()));

            // When error is null, that means that the file has already regenerated, so we don't need to create a backup.
            if (error != null) {
                backup = new File(file.getParentFile(), file.getName().split("\\.")[0] + "_" + time + ".bak");
                FileUtils.copyFile(file, backup);
            }

            FileConfiguration configuration = new YamlConfiguration();
            configuration.load(file);

            if (backup != null) FileUtils.deleteQuietly(backup);

            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            Logger logger = plugin.getLogger();

            logger.severe("An error occurred while reloading the file {" + file.getName() + "}.");

            boolean errorLogged = false;
            if (backup != null && exception instanceof InvalidConfigurationException invalid) {
                errorLogged = true;

                Throwable cause = invalid.getCause();
                if (cause instanceof MarkedYAMLException marked) {
                    handleError(backup, marked.getProblemMark().getLine());
                } else {
                    errorLogged = false;
                }
            }

            if (errorLogged) {
                logger.severe("The file will be restarted and a copy of the old file will be saved indicating which line had an error.");
            } else {
                logger.severe("The file will be restarted and a copy of the old file will be saved.");
            }

            if (error == null) {
                exception.printStackTrace();
                return null;
            }

            // Only replace the file if an exception ocurrs.
            FileUtils.deleteQuietly(file);
            error.accept(file);

            return reloadConfig(plugin, file, null);
        }
    }

    private static void handleError(@NotNull File backup, int line) {
        try {
            Path path = backup.toPath();

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            lines.set(line, lines.get(line) + " # <--------------------< ERROR <--------------------<");

            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}