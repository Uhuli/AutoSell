package net.uhuli.autosell.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.uhuli.autosell.SharedConstants;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoSellConfig {

    public static final String DEFAULT_SELL_COMMAND = "sell";

    private static final Path CONFIG_PATH = Path.of("config", "autosell.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutoSellConfig INSTANCE;

    public String selectedItemId = "minecraft:cobblestone";
    public int inventoryThreshold = 90;
    public String sellCommand = DEFAULT_SELL_COMMAND;

    private transient String resolvedForId;
    private transient Item resolvedItem;
    private transient ItemStack resolvedStack = ItemStack.EMPTY;

    public static AutoSellConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static @NonNull AutoSellConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                AutoSellConfig config = GSON.fromJson(reader, AutoSellConfig.class);
                if (config != null) {
                    config.normalize();
                    INSTANCE = config;
                    SharedConstants.LOGGER.info("AutoSell config loaded");
                    return config;
                }
            } catch (IOException e) {
                SharedConstants.LOGGER.error("Failed to load AutoSell config", e);
            }
        }
        SharedConstants.LOGGER.info("Using default AutoSell config");
        INSTANCE = new AutoSellConfig();
        return INSTANCE;
    }

    /** Repairs an outdated or hand-edited file. */
    private void normalize() {
        if (selectedItemId == null || selectedItemId.isBlank()) {
            selectedItemId = "minecraft:cobblestone";
        }
        sellCommand = normalizeCommand(sellCommand);
        inventoryThreshold = Math.clamp(inventoryThreshold, 1, 100);
        invalidateItemCache();
    }

    public void save() {
        try {
            Path parent = CONFIG_PATH.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            SharedConstants.LOGGER.error("Failed to save AutoSell config", e);
        }
    }

    /** Resolved once per change; the HUD and the screen query this every frame. */
    public @Nullable Item getSelectedItem() {
        resolveIfNeeded();
        return resolvedItem;
    }

    /** Ready-to-render stack, or EMPTY when the id does not resolve. Do not mutate. */
    public @NonNull ItemStack getSelectedItemStack() {
        resolveIfNeeded();
        return resolvedStack;
    }

    private void resolveIfNeeded() {
        if (selectedItemId.equals(resolvedForId)) {
            return;
        }
        resolvedForId = selectedItemId;
        resolvedItem = lookupItem(selectedItemId);
        resolvedStack = resolvedItem == null ? ItemStack.EMPTY : new ItemStack(resolvedItem);
    }

    private void invalidateItemCache() {
        resolvedForId = null;
        resolvedItem = null;
        resolvedStack = ItemStack.EMPTY;
    }

    private static @Nullable Item lookupItem(String itemId) {
        Identifier location = Identifier.tryParse(itemId);
        if (location == null) {
            SharedConstants.LOGGER.warn("Invalid item ID format: {}", itemId);
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    public void setSelectedItem(@NonNull Item item) {
        this.selectedItemId = BuiltInRegistries.ITEM.getKey(item).toString();
        invalidateItemCache();
        save();
    }

    /** Clamps and stores the threshold without touching the disk, for slider dragging. */
    public void applyInventoryThreshold(int threshold) {
        this.inventoryThreshold = Math.clamp(threshold, 1, 100);
    }

    public void setSellCommand(@Nullable String command) {
        this.sellCommand = normalizeCommand(command);
        save();
    }

    private static @NonNull String normalizeCommand(@Nullable String command) {
        if (command == null) {
            return DEFAULT_SELL_COMMAND;
        }
        String trimmed = command.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).strip();
        }
        return trimmed.isEmpty() ? DEFAULT_SELL_COMMAND : trimmed;
    }
}
