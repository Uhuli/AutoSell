package net.uhuli.autosell.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.uhuli.autosell.SharedConstants;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class AutoSellConfig {

    private static final File CONFIG_FILE = new File("config/autosell.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutoSellConfig INSTANCE;

    public String selectedItemId = "minecraft:cobblestone";
    public int inventoryThreshold = 90;
    public boolean isEnabled = false;

    public static AutoSellConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static @NonNull AutoSellConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                AutoSellConfig config = GSON.fromJson(reader, AutoSellConfig.class);
                if (config != null) {
                    INSTANCE = config;
                    SharedConstants.LOGGER.info("AutoSell config loaded");
                    return config;
                }
            } catch (IOException e) {
                SharedConstants.LOGGER.error("Failed to load AutoSell config", e);
            }
        }
        SharedConstants.LOGGER.info("Using default AutoSell config");
        AutoSellConfig defaultConfig = new AutoSellConfig();
        INSTANCE = defaultConfig;
        return defaultConfig;
    }

    public void save() {
        try {
            //noinspection ResultOfMethodCallIgnored
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            SharedConstants.LOGGER.error("Failed to save AutoSell config", e);
        }
    }

    /**
     * Resolves the selected item from the registry.
     * Uses proper ResourceLocation parsing instead of a slow stream filter.
     */
    public Item getSelectedItem() {
        try {
            Identifier location = Identifier.tryParse(selectedItemId);
            if (location == null) {
                SharedConstants.LOGGER.warn("Invalid item ID format: {}", selectedItemId);
                return null;
            }
            return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        } catch (Exception e) {
            SharedConstants.LOGGER.error("Failed to resolve item ID '{}': {}", selectedItemId, e.getMessage());
            return null;
        }
    }

    public void setSelectedItem(@NonNull Item item) {
        this.selectedItemId = BuiltInRegistries.ITEM.getKey(item).toString();
        save();
    }

    public void setInventoryThreshold(int threshold) {
        this.inventoryThreshold = Math.clamp(threshold, 1, 100);
        save();
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        save();
    }

    public void toggleEnabled() {
        setEnabled(!this.isEnabled);
    }
}