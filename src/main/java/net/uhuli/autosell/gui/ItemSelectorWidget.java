package net.uhuli.autosell.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.uhuli.autosell.SharedConstants;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItemSelectorWidget extends AbstractWidget {

    private static final int SCROLLBAR_W = 5;
    private static final int SCROLLBAR_PAD = 2;
    private static final int ITEM_SIZE = 20;
    private static final int ITEM_PAD = 2;
    private static final int MIN_GRIP_H = 12;

    private static final int C_BG = 0x50000000;
    private static final int C_BORDER = 0xFF1A1A22;
    private static final int C_SLOT_HOVER = 0x35FFFFFF;
    private static final int C_SLOT_NONE = 0x15000000;
    private static final int C_SEL_FILL = 0x5500E5FF;
    private static final int C_SEL_BORDER = 0xFF00E5FF;
    private static final int C_SCROLL_RAIL = 0xFF111118;
    private static final int C_SCROLL_GRIP = 0xFF555566;
    private static final int C_SCROLL_DRAG = 0xFF00E5FF;

    /** Built once and reused; item names are too expensive to resolve per keystroke. */
    private static List<Entry> indexCache;
    private static String indexCacheLanguage;

    private final AutoSellScreen parent;
    private final List<Entry> allItems;
    private final int itemsPerRow;
    private final int visibleRows;
    private List<Entry> filteredItems;
    private String currentQuery = "";
    private boolean onlySelected = false;
    private int scrollOffset = 0;
    private int hoveredIndex = -1;
    private boolean draggingScrollbar = false;

    public ItemSelectorWidget(int x, int y, int width, int height, AutoSellScreen parent) {
        super(x, y, width, height, Component.empty());
        this.parent = parent;

        // Leave room for the scrollbar on the right
        int usableW = width - SCROLLBAR_W - SCROLLBAR_PAD * 2 - 4;
        this.itemsPerRow = Math.max(1, usableW / (ITEM_SIZE + ITEM_PAD));
        this.visibleRows = Math.max(1, height / (ITEM_SIZE + ITEM_PAD));

        this.allItems = index();
        this.filteredItems = this.allItems;
    }

    /** Display name and registry path pre-lowercased for filtering. */
    private record Entry(Item item, ItemStack stack, String name, String id) {}

    private static @NonNull List<Entry> index() {
        String language = Minecraft.getInstance().options.languageCode;
        if (indexCache == null || !language.equals(indexCacheLanguage)) {
            indexCache = buildIndex();
            indexCacheLanguage = language;
        }
        return indexCache;
    }

    private static @NonNull List<Entry> buildIndex() {
        // The creative search tab keeps technical items out of the picker.
        Map<Item, ItemStack> stacks = new LinkedHashMap<>();
        try {
            for (ItemStack stack : CreativeModeTabs.searchTab().getSearchTabDisplayItems()) {
                if (!stack.isEmpty()) stacks.putIfAbsent(stack.getItem(), stack.copy());
            }
        } catch (RuntimeException e) {
            SharedConstants.LOGGER.warn("Creative tab contents unavailable, using full registry: {}", e.getMessage());
        }

        if (stacks.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) stacks.putIfAbsent(item, new ItemStack(item));
            }
        }

        List<Entry> entries = new ArrayList<>(stacks.size());
        for (Map.Entry<Item, ItemStack> entry : stacks.entrySet()) {
            ItemStack stack = entry.getValue();
            entries.add(new Entry(
                    entry.getKey(),
                    stack,
                    stack.getHoverName().getString().toLowerCase(Locale.ROOT),
                    BuiltInRegistries.ITEM.getKey(entry.getKey()).getPath()));
        }
        return List.copyOf(entries);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics,
                                            int mouseX, int mouseY, float partialTick) {

        // Background & border
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), C_BG);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), C_BORDER);

        int startIdx = scrollOffset * itemsPerRow;
        int maxVisible = itemsPerRow * visibleRows;
        hoveredIndex = indexAt(mouseX, mouseY);

        for (int i = 0; i < maxVisible; i++) {
            int absIdx = startIdx + i;
            if (absIdx >= filteredItems.size()) break;

            int col = i % itemsPerRow;
            int row = i / itemsPerRow;
            int slotX = getX() + 4 + col * (ITEM_SIZE + ITEM_PAD);
            int slotY = getY() + 4 + row * (ITEM_SIZE + ITEM_PAD);

            Entry entry = filteredItems.get(absIdx);
            boolean isHovered = absIdx == hoveredIndex;
            boolean isSelected = parent.isSelected(entry.item());

            // Slot background
            int slotFill = isSelected ? C_SEL_FILL : (isHovered ? C_SLOT_HOVER : C_SLOT_NONE);
            graphics.fill(slotX, slotY, slotX + ITEM_SIZE, slotY + ITEM_SIZE, slotFill);

            // Selection border
            if (isSelected) {
                graphics.outline(slotX, slotY, ITEM_SIZE, ITEM_SIZE, C_SEL_BORDER);
            }

            // Item icon (2 px inset so it's centered in the 20 px slot)
            graphics.item(entry.stack(), slotX + 2, slotY + 2);
        }

        drawScrollbar(graphics);

        if (hoveredIndex >= 0) {
            graphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    filteredItems.get(hoveredIndex).stack(),
                    mouseX, mouseY);
        }

        Component countComp = (filteredItems.size() == allItems.size()
                ? Component.translatable("autosell.gui.items.count", filteredItems.size())
                : Component.translatable("autosell.gui.items.filtered", filteredItems.size(), allItems.size()))
                .withStyle(s -> s.withColor(0x33333F));
        int tw = Minecraft.getInstance().font.width(countComp);
        graphics.text(Minecraft.getInstance().font,
                countComp,
                getX() + getWidth() - SCROLLBAR_W - SCROLLBAR_PAD * 2 - 4 - tw,
                getY() + getHeight() - 10,
                0xFFFFFF);
    }

    private void drawScrollbar(@NonNull GuiGraphicsExtractor graphics) {
        if (!isScrollbarVisible()) return;

        int railX = railX();
        int railY = railY();
        int railH = railH();

        // Rail
        graphics.fill(railX, railY, railX + SCROLLBAR_W, railY + railH, C_SCROLL_RAIL);

        // Grip
        int maxScroll = maxScroll();
        int gripH = gripH();
        int gripY = railY + (maxScroll == 0 ? 0 : (int) ((float) scrollOffset / maxScroll * (railH - gripH)));

        int gripColor = draggingScrollbar ? C_SCROLL_DRAG : C_SCROLL_GRIP;
        graphics.fill(railX, gripY, railX + SCROLLBAR_W, gripY + gripH, gripColor);
    }

    /** Index into {@link #filteredItems} under the cursor, or -1. */
    private int indexAt(double mouseX, double mouseY) {
        if (isMouseOverScrollbar(mouseX, mouseY)) return -1;

        int localX = (int) mouseX - (getX() + 4);
        int localY = (int) mouseY - (getY() + 4);
        if (localX < 0 || localY < 0) return -1;

        int stride = ITEM_SIZE + ITEM_PAD;
        int col = localX / stride;
        int row = localY / stride;
        // Reject the padding gap between slots
        if (col >= itemsPerRow || row >= visibleRows) return -1;
        if (localX % stride >= ITEM_SIZE || localY % stride >= ITEM_SIZE) return -1;

        int absIdx = (scrollOffset + row) * itemsPerRow + col;
        return absIdx < filteredItems.size() ? absIdx : -1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (isMouseOverScrollbar(event.x(), event.y())) {
            draggingScrollbar = true;
            updateScrollFromMouse(event.y());
            return;
        }

        int clicked = indexAt(event.x(), event.y());
        if (clicked >= 0) {
            parent.toggleItem(filteredItems.get(clicked).item());
        }
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) {
        // The screen plays a pitched sound instead.
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        draggingScrollbar = false;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (draggingScrollbar) updateScrollFromMouse(event.y());
    }

    /** Centres the grip on the cursor. */
    private void updateScrollFromMouse(double mouseY) {
        int track = railH() - gripH();
        int maxScroll = maxScroll();
        if (track <= 0 || maxScroll <= 0) {
            scrollOffset = 0;
            return;
        }
        double rel = (mouseY - railY() - gripH() / 2.0) / track;
        scrollOffset = Mth.clamp((int) Math.round(rel * maxScroll), 0, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scrollOffset = Mth.clamp(scrollOffset + (scrollY < 0 ? 1 : -1), 0, maxScroll());
        return true;
    }

    public void updateFilter(@NonNull String searchText) {
        currentQuery = searchText.strip().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    public void setOnlySelected(boolean onlySelected) {
        this.onlySelected = onlySelected;
        applyFilter();
    }

    public boolean isOnlySelected() {
        return onlySelected;
    }

    /** Call after the selection changed. */
    public void refresh() {
        if (onlySelected) applyFilter();
    }

    private void applyFilter() {
        if (currentQuery.isEmpty() && !onlySelected) {
            filteredItems = allItems;
        } else {
            List<Entry> matches = new ArrayList<>();
            for (Entry entry : allItems) {
                if (onlySelected && !parent.isSelected(entry.item())) continue;
                if (!currentQuery.isEmpty()
                        && !entry.name().contains(currentQuery)
                        && !entry.id().contains(currentQuery)) continue;
                matches.add(entry);
            }
            filteredItems = matches;
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("autosell.narration.selector"));
    }

    private int totalRows() {
        return Mth.positiveCeilDiv(filteredItems.size(), itemsPerRow);
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows);
    }

    private boolean isScrollbarVisible() {
        return totalRows() > visibleRows;
    }

    private int railX() {
        return getX() + getWidth() - SCROLLBAR_W - SCROLLBAR_PAD;
    }

    private int railY() {
        return getY() + SCROLLBAR_PAD;
    }

    private int railH() {
        return getHeight() - SCROLLBAR_PAD * 2;
    }

    private int gripH() {
        int totalRows = totalRows();
        if (totalRows <= 0) return MIN_GRIP_H;
        return Math.max(MIN_GRIP_H, (int) ((float) visibleRows / totalRows * railH()));
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        if (!isScrollbarVisible()) return false;
        return mouseX >= railX() - SCROLLBAR_PAD && mouseX <= railX() + SCROLLBAR_W + SCROLLBAR_PAD
                && mouseY >= railY() && mouseY <= railY() + railH();
    }

}
