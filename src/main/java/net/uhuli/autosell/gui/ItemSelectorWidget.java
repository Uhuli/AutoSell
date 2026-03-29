package net.uhuli.autosell.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ItemSelectorWidget extends AbstractWidget {

    private static final int SCROLLBAR_W = 5;
    private static final int SCROLLBAR_PAD = 2;
    private static final int ITEM_SIZE = 20;
    private static final int ITEM_PAD = 2;

    private static final int C_BG = 0x50000000;
    private static final int C_BORDER = 0xFF1A1A22;
    private static final int C_SLOT_HOVER = 0x35FFFFFF;
    private static final int C_SLOT_NONE = 0x15000000;
    private static final int C_SEL_FILL = 0x5500E5FF;
    private static final int C_SEL_BORDER = 0xFF00E5FF;
    private static final int C_SCROLL_RAIL = 0xFF111118;
    private static final int C_SCROLL_GRIP = 0xFF555566;
    private static final int C_SCROLL_DRAG = 0xFF00E5FF;

    private final AutoSellScreen parent;
    private final List<Item> allItems;
    private final int itemsPerRow;
    private final int visibleRows;
    private List<Item> filteredItems;
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

        this.allItems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) allItems.add(item);
        }
        this.filteredItems = new ArrayList<>(allItems);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics,
                                            int mouseX, int mouseY, float partialTick) {

        // Background & border
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), C_BG);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), C_BORDER);

        int startIdx = scrollOffset * itemsPerRow;
        int maxVisible = itemsPerRow * visibleRows;
        Item selected = parent.getSelectedItem();
        hoveredIndex = -1;

        for (int i = 0; i < maxVisible; i++) {
            int absIdx = startIdx + i;
            if (absIdx >= filteredItems.size()) break;

            int col = i % itemsPerRow;
            int row = i / itemsPerRow;
            int slotX = getX() + 4 + col * (ITEM_SIZE + ITEM_PAD);
            int slotY = getY() + 4 + row * (ITEM_SIZE + ITEM_PAD);

            Item item = filteredItems.get(absIdx);
            boolean isHovered = mouseX >= slotX && mouseX < slotX + ITEM_SIZE
                    && mouseY >= slotY && mouseY < slotY + ITEM_SIZE;
            boolean isSelected = (item == selected);

            if (isHovered) hoveredIndex = absIdx;

            // Slot background
            int slotFill = isSelected ? C_SEL_FILL : (isHovered ? C_SLOT_HOVER : C_SLOT_NONE);
            graphics.fill(slotX, slotY, slotX + ITEM_SIZE, slotY + ITEM_SIZE, slotFill);

            // Selection border
            if (isSelected) {
                graphics.outline(slotX, slotY, ITEM_SIZE, ITEM_SIZE, C_SEL_BORDER);
            }

            // Item icon (2 px inset so it's centered in the 20 px slot)
            graphics.item(new ItemStack(item), slotX + 2, slotY + 2);
        }

        drawScrollbar(graphics);

        if (hoveredIndex >= 0 && hoveredIndex < filteredItems.size()) {
            graphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    new ItemStack(filteredItems.get(hoveredIndex)),
                    mouseX, mouseY);
        }

        String countLabel = filteredItems.size() == allItems.size()
                ? filteredItems.size() + " items"
                : filteredItems.size() + " / " + allItems.size();
        Component countComp = Component.literal(countLabel)
                .withStyle(s -> s.withColor(0x33333F));
        int tw = Minecraft.getInstance().font.width(countComp);
        graphics.text(Minecraft.getInstance().font,
                countComp,
                getX() + getWidth() - SCROLLBAR_W - SCROLLBAR_PAD * 2 - 4 - tw,
                getY() + getHeight() - 10,
                0xFFFFFF);
    }

    private void drawScrollbar(@NonNull GuiGraphicsExtractor graphics) {
        int totalRows = totalRows();
        if (totalRows <= visibleRows) return;

        int railX = getX() + getWidth() - SCROLLBAR_W - SCROLLBAR_PAD;
        int railY = getY() + SCROLLBAR_PAD;
        int railH = getHeight() - SCROLLBAR_PAD * 2;

        // Rail
        graphics.fill(railX, railY, railX + SCROLLBAR_W, railY + railH, C_SCROLL_RAIL);

        // Grip
        int maxScroll = totalRows - visibleRows;
        int gripH = Math.max(12, (int) ((float) visibleRows / totalRows * railH));
        int gripY = railY + (int) ((float) scrollOffset / maxScroll * (railH - gripH));

        int gripColor = draggingScrollbar ? C_SCROLL_DRAG : C_SCROLL_GRIP;
        graphics.fill(railX, gripY, railX + SCROLLBAR_W, gripY + gripH, gripColor);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (isMouseOverScrollbar(event.x(), event.y())) {
            draggingScrollbar = true;
        } else if (hoveredIndex >= 0 && hoveredIndex < filteredItems.size()) {
            parent.selectItem(filteredItems.get(hoveredIndex));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        draggingScrollbar = false;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (!draggingScrollbar) return;

        int maxScroll = Math.max(0, totalRows() - visibleRows);
        float relY = (float) (event.y() - getY()) / getHeight();
        scrollOffset = Mth.clamp(Math.round(relY * maxScroll), 0, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        int maxScroll = Math.max(0, totalRows() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset + (scrollY < 0 ? 1 : -1), 0, maxScroll);
        return true;
    }

    public void updateFilter(@NonNull String searchText) {
        scrollOffset = 0;
        String query = searchText.strip().toLowerCase(Locale.ROOT);

        if (query.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
            return;
        }

        filteredItems = new ArrayList<>();
        for (Item item : allItems) {
            String name = item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT);
            // Also match by registry ID
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (name.contains(query) || id.contains(query)) {
                filteredItems.add(item);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal("Item Selector"));
    }

    private int totalRows() {
        return (int) Math.ceil((double) filteredItems.size() / itemsPerRow);
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        int railX = getX() + getWidth() - SCROLLBAR_W - SCROLLBAR_PAD * 2;
        return mouseX >= railX && mouseX <= getX() + getWidth()
                && mouseY >= getY() && mouseY <= getY() + getHeight();
    }

}