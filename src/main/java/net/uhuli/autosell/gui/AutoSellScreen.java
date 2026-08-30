package net.uhuli.autosell.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.uhuli.autosell.config.AutoSellConfig;
import net.uhuli.autosell.config.AutoSellConfig.HudCorner;
import net.uhuli.autosell.config.AutoSellConfig.ToggleResult;
import net.uhuli.autosell.handler.AutoSellHandler;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class AutoSellScreen extends Screen {

    private static final int PANEL_W = 340;

    private static final int Y_TITLE = 10;
    private static final int Y_SEP_TITLE = 23;
    private static final int Y_SEARCH_BG = 28;
    private static final int SEARCH_H = 22;
    private static final int Y_GRID = 62;
    private static final int MIN_GRID_H = 26;
    private static final int MAX_GRID_H = 95;
    private static final int CHIPS_H = 18;
    private static final int SLIDER_H = 18;
    private static final int CMD_H = 20;
    private static final int STATUS_H = 15;
    private static final int BUTTON_H = 26;
    private static final int ICON_SIZE = 20;

    /** Panel height minus the grid — the grid is the only elastic row. */
    private static final int FIXED_H = Y_GRID
            + 3 + 5 + CHIPS_H
            + 4 + 5 + SLIDER_H
            + 4 + 5 + CMD_H
            + 4 + 5 + STATUS_H
            + 5 + BUTTON_H + 12;

    private static final int C_ACCENT = 0xFF00E5FF;
    private static final int C_ACCENT_DIM = 0x2200E5FF;
    private static final int C_BG_TOP = 0xF7101013;
    private static final int C_BG_BOT = 0xF70A0A0D;
    private static final int C_BORDER = 0xFF1E1E26;
    private static final int C_SEP = 0xFF18181F;
    private static final int C_PANEL = 0x28000000;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GREEN = 0xFF44DD66;
    private static final int C_RED = 0xFFFF4444;
    private static final int C_LABEL = 0x44444F;
    private static final int C_MUTED = 0xFF555566;

    private final AutoSellConfig config;
    private final AutoSellHandler handler;

    private ItemSelectorWidget itemSelector;
    private EditBox commandBox;
    private Button toggleButton;

    private int panelX, panelY, panelH, gridH;
    private int ySepGrid, yChips, ySepSlider, ySlider, ySepCmd, yCmd, ySepStatus, yStatus, ySepButton, yButton;
    private int cmdBoxX, cmdBoxW;
    private String lastSearchText = "";

    public AutoSellScreen() {
        super(Component.translatable("autosell.gui.title"));
        this.config = AutoSellConfig.getInstance();
        this.handler = AutoSellHandler.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        layout();

        int searchRight = panelX + PANEL_W - 15;
        int filterX = searchRight - 1 - ICON_SIZE;

        EditBox searchBox = new EditBox(
                this.font,
                panelX + 34, panelY + Y_SEARCH_BG + 7,
                filterX - 4 - (panelX + 34), 16,
                Component.translatable("autosell.gui.search.hint"));
        searchBox.setBordered(false);
        searchBox.setHint(Component.translatable("autosell.gui.search.hint")
                .withStyle(s -> s.withColor(C_LABEL)));
        searchBox.setMaxLength(50);
        searchBox.setResponder(text -> {
            if (!text.equals(lastSearchText)) {
                lastSearchText = text;
                if (itemSelector != null) itemSelector.updateFilter(text);
            }
        });
        this.addRenderableWidget(searchBox);

        this.itemSelector = new ItemSelectorWidget(
                panelX + 15, panelY + Y_GRID,
                PANEL_W - 30, gridH,
                this);
        this.addRenderableWidget(this.itemSelector);

        this.addRenderableWidget(new OnlySelectedToggle(filterX, panelY + Y_SEARCH_BG + 1));

        this.addRenderableWidget(new SelectedItemsWidget(
                panelX + 15, panelY + yChips,
                PANEL_W - 30, CHIPS_H,
                this));

        this.addRenderableWidget(new ThresholdSlider(
                panelX + 15, panelY + ySlider,
                PANEL_W - 30 - ICON_SIZE - 3, SLIDER_H));
        this.addRenderableWidget(new HudCornerButton(
                panelX + PANEL_W - 15 - ICON_SIZE, panelY + ySlider - 1));

        int cmdLabelW = this.font.width(Component.translatable("autosell.gui.command.label"));
        this.cmdBoxX = panelX + 15 + cmdLabelW + 6;
        this.cmdBoxW = PANEL_W - 15 - (cmdBoxX - panelX);

        this.commandBox = new EditBox(
                this.font,
                cmdBoxX + 10, panelY + yCmd + 6,
                cmdBoxW - 14, 12,
                Component.translatable("autosell.gui.command.label"));
        this.commandBox.setBordered(false);
        this.commandBox.setMaxLength(64);
        this.commandBox.setValue(config.sellCommand);
        this.commandBox.setHint(Component.literal(AutoSellConfig.DEFAULT_SELL_COMMAND)
                .withStyle(s -> s.withColor(C_LABEL)));
        this.addRenderableWidget(this.commandBox);

        this.toggleButton = Button.builder(getToggleLabel(), btn -> {
            applyCommandInput();
            if (handler.isActive()) {
                handler.stop();
                updateToggleButton();
            } else {
                handler.start();
                this.onClose();
            }
        }).bounds(panelX + 15, panelY + yButton, PANEL_W - 30, BUTTON_H).build();
        this.addRenderableWidget(this.toggleButton);
        updateToggleButton();
    }

    /** Starting needs a selection; stopping stays possible so the user is never trapped. */
    private void updateToggleButton() {
        if (toggleButton == null) return;
        boolean hasItems = !config.getSelectedStacks().isEmpty();
        toggleButton.active = handler.isActive() || hasItems;
        toggleButton.setMessage(getToggleLabel());
        toggleButton.setTooltip(hasItems
                ? null
                : Tooltip.create(Component.translatable("autosell.gui.toggle.no_items")));
    }

    /** The grid absorbs whatever height is left, so the panel always fits the window. */
    private void layout() {
        this.gridH = Mth.clamp(this.height - 20 - FIXED_H, MIN_GRID_H, MAX_GRID_H);
        this.panelH = FIXED_H + gridH;
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = Math.max(2, (this.height - panelH) / 2);

        this.ySepGrid = Y_GRID + gridH + 3;
        this.yChips = ySepGrid + 5;
        this.ySepSlider = yChips + CHIPS_H + 4;
        this.ySlider = ySepSlider + 5;
        this.ySepCmd = ySlider + SLIDER_H + 4;
        this.yCmd = ySepCmd + 5;
        this.ySepStatus = yCmd + CMD_H + 4;
        this.yStatus = ySepStatus + 5;
        this.ySepButton = yStatus + STATUS_H;
        this.yButton = ySepButton + 5;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float partialTick) {

        graphics.fill(0, 0, this.width, this.height, 0xBB000000);

        graphics.fillGradient(panelX, panelY,
                panelX + PANEL_W, panelY + panelH,
                C_BG_TOP, C_BG_BOT);

        graphics.outline(panelX, panelY, PANEL_W, panelH, C_BORDER);
        graphics.outline(panelX + 1, panelY + 1, PANEL_W - 2, panelH - 2, 0xFF13131A);

        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, C_ACCENT);
        graphics.fill(panelX, panelY + 2, panelX + PANEL_W, panelY + 6, C_ACCENT_DIM);

        graphics.centeredText(this.font, this.title,
                this.width / 2, panelY + Y_TITLE, C_ACCENT);

        drawSeparator(graphics, panelY + Y_SEP_TITLE);

        graphics.fill(panelX + 15, panelY + Y_SEARCH_BG,
                panelX + PANEL_W - 15, panelY + Y_SEARCH_BG + SEARCH_H, C_PANEL);
        graphics.outline(panelX + 15, panelY + Y_SEARCH_BG,
                PANEL_W - 30, SEARCH_H, C_BORDER);
        graphics.text(this.font,
                Component.literal("⌕").withStyle(s -> s.withColor(0x333344)),
                panelX + 19, panelY + Y_SEARCH_BG + 5, C_WHITE);

        drawCommandRow(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        drawSeparator(graphics, panelY + ySepGrid);
        drawSeparator(graphics, panelY + ySepSlider);
        drawSeparator(graphics, panelY + ySepCmd);
        drawSeparator(graphics, panelY + ySepStatus);
        drawStatusRow(graphics);
        drawSeparator(graphics, panelY + ySepButton);
    }

    private void drawCommandRow(GuiGraphicsExtractor graphics) {
        int y = panelY + yCmd;

        graphics.text(this.font,
                Component.translatable("autosell.gui.command.label")
                        .withStyle(s -> s.withColor(C_LABEL)),
                panelX + 15, y + 6, C_WHITE);

        graphics.fill(cmdBoxX, y, cmdBoxX + cmdBoxW, y + CMD_H, C_PANEL);
        graphics.outline(cmdBoxX, y, cmdBoxW, CMD_H, C_BORDER);
        graphics.text(this.font,
                Component.literal("/").withStyle(s -> s.withColor(0x333344)),
                cmdBoxX + 4, y + 6, C_WHITE);
    }

    private void drawStatusRow(GuiGraphicsExtractor graphics) {
        int y = panelY + yStatus;

        graphics.text(this.font,
                Component.translatable("autosell.gui.status.label")
                        .withStyle(s -> s.withColor(C_LABEL)),
                panelX + 15, y, C_WHITE);

        int dotColor = handler.getStatusDotColor();
        graphics.fill(panelX + 56, y + 2, panelX + 61, y + 7, dotColor);

        graphics.text(this.font, handler.getStatusComponent(), panelX + 66, y, C_WHITE);

        if (handler.isActive() && handler.getSessionSellCount() > 0) {
            Component countComp = Component.translatable("autosell.gui.sold.count", handler.getSessionSellCount())
                    .withStyle(s -> s.withColor(0x44DD66));
            int tw = this.font.width(countComp);
            graphics.text(this.font, countComp, panelX + PANEL_W - 15 - tw, y, C_WHITE);
        }
    }

    private void drawSeparator(GuiGraphicsExtractor graphics, int y) {
        graphics.fill(panelX + 10, y, panelX + PANEL_W - 10, y + 1, C_SEP);
    }

    private static void playUiSound(float pitch) {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    public boolean isSelected(Item item) {
        return config.isSelected(item);
    }

    public @NonNull List<ItemStack> getSelectedStacks() {
        return config.getSelectedStacks();
    }

    /** Pitch tells adding and removing apart by ear. */
    public void toggleItem(@NonNull Item item) {
        ToggleResult result = config.toggleSelected(item);
        switch (result) {
            case ADDED -> playUiSound(1.4F);
            case REMOVED -> playUiSound(0.8F);
            case LIMIT_REACHED -> playUiSound(0.5F);
        }
        if (result != ToggleResult.LIMIT_REACHED) {
            itemSelector.refresh();
            updateToggleButton();
        }
    }

    public void removeItem(@NonNull Item item) {
        config.removeSelected(item);
        playUiSound(0.8F);
        itemSelector.refresh();
        updateToggleButton();
    }

    private void applyCommandInput() {
        if (commandBox == null) return;
        String typed = commandBox.getValue();
        if (!typed.equals(config.sellCommand)) {
            config.setSellCommand(typed);
            commandBox.setValue(config.sellCommand);
        }
    }

    @Override
    public void removed() {
        applyCommandInput();
        config.save();
        // Deferred to close rather than to the last removed chip, so clearing the
        // list to re-pick does not stop a running session.
        if (handler.isActive() && config.getSelectedStacks().isEmpty()) {
            handler.stop();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private @NonNull Component getToggleLabel() {
        return handler.isActive()
                ? Component.translatable("autosell.gui.toggle.stop")
                  .withStyle(s -> s.withColor(C_RED).withBold(true))
                : Component.translatable("autosell.gui.toggle.start")
                  .withStyle(s -> s.withColor(C_GREEN).withBold(true));
    }

    private abstract class PanelIconButton extends AbstractWidget {

        PanelIconButton(int x, int y, Component tooltip) {
            super(x, y, ICON_SIZE, ICON_SIZE, tooltip);
            setTooltip(Tooltip.create(tooltip));
        }

        protected abstract boolean isHighlighted();

        protected abstract void drawIcon(GuiGraphicsExtractor graphics);

        protected abstract void press();

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics,
                                                int mouseX, int mouseY, float partialTick) {
            boolean on = isHighlighted();
            int fill = on ? C_ACCENT_DIM : (isHovered() ? 0x35FFFFFF : C_PANEL);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
            graphics.outline(getX(), getY(), getWidth(), getHeight(), on ? C_ACCENT : C_BORDER);
            drawIcon(graphics);
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            press();
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    /** Restricts the grid to items that are already selected. */
    private class OnlySelectedToggle extends PanelIconButton {

        OnlySelectedToggle(int x, int y) {
            super(x, y, Component.translatable("autosell.gui.filter.selected"));
        }

        @Override
        protected boolean isHighlighted() {
            return itemSelector != null && itemSelector.isOnlySelected();
        }

        @Override
        protected void drawIcon(@NonNull GuiGraphicsExtractor graphics) {
            Component check = Component.literal("✓")
                    .withStyle(s -> s.withColor(isHighlighted() ? C_ACCENT : C_MUTED));
            graphics.centeredText(AutoSellScreen.this.font, check,
                    getX() + getWidth() / 2,
                    getY() + (getHeight() - AutoSellScreen.this.font.lineHeight) / 2 + 1,
                    C_WHITE);
        }

        @Override
        protected void press() {
            itemSelector.setOnlySelected(!itemSelector.isOnlySelected());
        }
    }

    /** Icon is a box with the active corner filled — no font glyph needed. */
    private class HudCornerButton extends PanelIconButton {

        HudCornerButton(int x, int y) {
            super(x, y, Component.translatable("autosell.gui.hud.corner"));
        }

        @Override
        protected boolean isHighlighted() {
            return false;
        }

        @Override
        protected void drawIcon(@NonNull GuiGraphicsExtractor graphics) {
            HudCorner corner = config.hudCorner;
            int inset = 4;
            int boxX = getX() + inset;
            int boxY = getY() + inset;
            int boxSize = getWidth() - inset * 2;

            graphics.outline(boxX, boxY, boxSize, boxSize, C_MUTED);

            int quad = 4;
            int qx = corner.isLeft() ? boxX + 1 : boxX + boxSize - quad - 1;
            int qy = corner.isTop() ? boxY + 1 : boxY + boxSize - quad - 1;
            graphics.fill(qx, qy, qx + quad, qy + quad, C_ACCENT);
        }

        @Override
        protected void press() {
            config.cycleHudCorner();
        }
    }

    private class ThresholdSlider extends AbstractSliderButton {

        ThresholdSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                    (config.inventoryThreshold - 1) / 99.0D);
            updateMessage();
        }

        private int thresholdPercent() {
            return (int) (this.value * 99.0D) + 1;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(
                    Component.translatable("autosell.gui.threshold.label")
                            .withStyle(s -> s.withColor(0x888899))
                            .append(Component.literal(": ")
                                    .withStyle(s -> s.withColor(0x888899)))
                            .append(Component.literal(thresholdPercent() + "%")
                                    .withStyle(s -> s.withColor(C_ACCENT))));
        }

        @Override
        protected void applyValue() {
            config.applyInventoryThreshold(thresholdPercent());
        }

        @Override
        public void onRelease(@NonNull MouseButtonEvent event) {
            super.onRelease(event);
            config.save();
        }
    }

}
