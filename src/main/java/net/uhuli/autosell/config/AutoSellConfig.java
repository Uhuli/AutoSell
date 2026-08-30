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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AutoSellConfig {

    public static final String DEFAULT_SELL_COMMAND = "sell";
    public static final int MAX_SELECTED_ITEMS = 32;

    private static final String DEFAULT_ITEM_ID = "minecraft:cobblestone";
    private static final Path CONFIG_PATH = Path.of("config", "autosell.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutoSellConfig INSTANCE;

    public List<String> selectedItemIds = new ArrayList<>();
    public int inventoryThreshold = 90;
    public String sellCommand = DEFAULT_SELL_COMMAND;
    public HudCorner hudCorner = HudCorner.TOP_RIGHT;

    /** Pre-1.2 single-item field, folded into {@link #selectedItemIds} on load. */
    public String selectedItemId;

    private transient List<String> resolvedForIds;
    private transient Set<Item> resolvedItems = Set.of();
    private transient List<ItemStack> resolvedStacks = List.of();

    public enum HudCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public boolean isLeft() {
            return this == TOP_LEFT || this == BOTTOM_LEFT;
        }

        public boolean isTop() {
            return this == TOP_LEFT || this == TOP_RIGHT;
        }

        public HudCorner next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum ToggleResult { ADDED, REMOVED, LIMIT_REACHED }

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
                    SharedConstants.LOGGER.info("AutoSell config loaded ({} item(s) selected)",
                            config.selectedItemIds.size());
                    return config;
                }
            } catch (IOException e) {
                SharedConstants.LOGGER.error("Failed to load AutoSell config", e);
            }
        }
        SharedConstants.LOGGER.info("Using default AutoSell config");
        INSTANCE = new AutoSellConfig();
        INSTANCE.selectedItemIds.add(DEFAULT_ITEM_ID);
        return INSTANCE;
    }

    /** Repairs an outdated or hand-edited file. An empty selection is left empty. */
    private void normalize() {
        if (selectedItemIds == null) {
            selectedItemIds = new ArrayList<>();
        }
        if (selectedItemId != null && !selectedItemId.isBlank()) {
            selectedItemIds.addFirst(selectedItemId);
            SharedConstants.LOGGER.info("Migrated legacy selectedItemId '{}' into the item list", selectedItemId);
            selectedItemId = null;
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String id : selectedItemIds) {
            if (id != null && !id.isBlank()) unique.add(id.strip());
        }
        selectedItemIds = new ArrayList<>(unique.stream().limit(MAX_SELECTED_ITEMS).toList());

        sellCommand = normalizeCommand(sellCommand);
        if (hudCorner == null) hudCorner = HudCorner.TOP_RIGHT;
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

    /** Membership set for the inventory scan and the transfer queue. */
    public @NonNull Set<Item> getSelectedItems() {
        resolveIfNeeded();
        return resolvedItems;
    }

    /** Selected items in selection order, ready to render. Callers must not mutate them. */
    public @NonNull List<ItemStack> getSelectedStacks() {
        resolveIfNeeded();
        return resolvedStacks;
    }

    public boolean isSelected(@Nullable Item item) {
        return item != null && getSelectedItems().contains(item);
    }

    public @NonNull ToggleResult toggleSelected(@NonNull Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (selectedItemIds.remove(id)) {
            invalidateItemCache();
            save();
            return ToggleResult.REMOVED;
        }
        if (selectedItemIds.size() >= MAX_SELECTED_ITEMS) {
            return ToggleResult.LIMIT_REACHED;
        }
        selectedItemIds.add(id);
        invalidateItemCache();
        save();
        return ToggleResult.ADDED;
    }

    public void removeSelected(@NonNull Item item) {
        if (selectedItemIds.remove(BuiltInRegistries.ITEM.getKey(item).toString())) {
            invalidateItemCache();
            save();
        }
    }

    /** Ids are resolved once per change; the HUD and the screen query this every frame. */
    private void resolveIfNeeded() {
        if (selectedItemIds.equals(resolvedForIds)) {
            return;
        }
        resolvedForIds = List.copyOf(selectedItemIds);

        Set<Item> items = new LinkedHashSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (String id : resolvedForIds) {
            Item item = lookupItem(id);
            if (item != null && items.add(item)) {
                stacks.add(new ItemStack(item));
            }
        }
        resolvedItems = Set.copyOf(items);
        resolvedStacks = List.copyOf(stacks);
    }

    private void invalidateItemCache() {
        resolvedForIds = null;
        resolvedItems = Set.of();
        resolvedStacks = List.of();
    }

    private static @Nullable Item lookupItem(String itemId) {
        Identifier location = Identifier.tryParse(itemId);
        if (location == null) {
            SharedConstants.LOGGER.warn("Invalid item ID format: {}", itemId);
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
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

    public void cycleHudCorner() {
        this.hudCorner = hudCorner.next();
        save();
    }
}
