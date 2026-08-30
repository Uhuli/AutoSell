package net.uhuli.autosell.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.uhuli.autosell.config.AutoSellConfig;
import net.uhuli.autosell.handler.AutoSellHandler;
import org.jspecify.annotations.NonNull;

public class AutoSellScreen extends Screen {

    private static final int PANEL_W = 340;

    private static final int Y_TITLE = 10;
    private static final int Y_SEP_TITLE = 23;
    private static final int Y_SEARCH_BG = 28;
    private static final int SEARCH_H = 22;
    private static final int SLIDER_H = 18;
    private static final int Y_GRID = 62;
    private static final int GRID_H = 95;
    private static final int Y_SEP_GRID = Y_GRID + GRID_H + 3;
    private static final int Y_ITEM_DISPLAY = Y_SEP_GRID + 5;
    private static final int Y_SEP_SLIDER = Y_ITEM_DISPLAY + 18;
    private static final int Y_SLIDER = Y_SEP_SLIDER + 5;
    private static final int Y_SEP_CMD = Y_SLIDER + SLIDER_H + 4;
    private static final int Y_CMD = Y_SEP_CMD + 5;
    private static final int CMD_H = 20;
    private static final int Y_SEP_STATUS = Y_CMD + CMD_H + 4;
    private static final int Y_STATUS = Y_SEP_STATUS + 5;
    private static final int Y_SEP_BUTTON = Y_STATUS + 15;
    private static final int Y_BUTTON = Y_SEP_BUTTON + 5;
    private static final int BUTTON_H = 26;
    private static final int PANEL_H = Y_BUTTON + BUTTON_H + 12;

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

    private final AutoSellConfig config;
    private final AutoSellHandler handler;

    private ItemSelectorWidget itemSelector;
    private EditBox commandBox;

    private int panelX, panelY;
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
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - PANEL_H) / 2;

        EditBox searchBox = new EditBox(
                this.font,
                panelX + 34, panelY + Y_SEARCH_BG + 7,
                PANEL_W - 52, 16,
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
                PANEL_W - 30, GRID_H,
                this);
        this.addRenderableWidget(this.itemSelector);

        this.addRenderableWidget(new ThresholdSlider(
                panelX + 15, panelY + Y_SLIDER,
                PANEL_W - 30, SLIDER_H));

        int cmdLabelW = this.font.width(Component.translatable("autosell.gui.command.label"));
        this.cmdBoxX = panelX + 15 + cmdLabelW + 6;
        this.cmdBoxW = PANEL_W - 15 - (cmdBoxX - panelX);

        this.commandBox = new EditBox(
                this.font,
                cmdBoxX + 10, panelY + Y_CMD + 6,
                cmdBoxW - 14, 12,
                Component.translatable("autosell.gui.command.label"));
        this.commandBox.setBordered(false);
        this.commandBox.setMaxLength(64);
        this.commandBox.setValue(config.sellCommand);
        this.commandBox.setHint(Component.literal(AutoSellConfig.DEFAULT_SELL_COMMAND)
                .withStyle(s -> s.withColor(C_LABEL)));
        this.addRenderableWidget(this.commandBox);

        Button toggleButton = Button.builder(getToggleLabel(), btn -> {
            applyCommandInput();
            if (handler.isActive()) {
                handler.stop();
                btn.setMessage(getToggleLabel());
            } else {
                handler.start();
                this.onClose();
            }
        }).bounds(panelX + 15, panelY + Y_BUTTON, PANEL_W - 30, BUTTON_H).build();
        this.addRenderableWidget(toggleButton);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float partialTick) {

        graphics.fill(0, 0, this.width, this.height, 0xBB000000);

        graphics.fillGradient(panelX, panelY,
                panelX + PANEL_W, panelY + PANEL_H,
                C_BG_TOP, C_BG_BOT);

        graphics.outline(panelX, panelY, PANEL_W, PANEL_H, C_BORDER);
        graphics.outline(panelX + 1, panelY + 1, PANEL_W - 2, PANEL_H - 2, 0xFF13131A);

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

        drawSeparator(graphics, panelY + Y_SEP_GRID);
        drawItemDisplay(graphics);
        drawSeparator(graphics, panelY + Y_SEP_SLIDER);
        drawSeparator(graphics, panelY + Y_SEP_CMD);
        drawSeparator(graphics, panelY + Y_SEP_STATUS);
        drawStatusRow(graphics);
        drawSeparator(graphics, panelY + Y_SEP_BUTTON);
    }

    private void drawCommandRow(GuiGraphicsExtractor graphics) {
        int y = panelY + Y_CMD;

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

    private void drawItemDisplay(GuiGraphicsExtractor graphics) {
        int y = panelY + Y_ITEM_DISPLAY + 3;

        ItemStack stack = config.getSelectedItemStack();
        graphics.text(this.font,
                Component.translatable("autosell.gui.selected.label")
                        .withStyle(s -> s.withColor(C_LABEL)),
                panelX + 15, y, C_WHITE);

        if (!stack.isEmpty()) {
            graphics.item(stack, panelX + 82, y - 4);
            graphics.text(this.font, stack.getHoverName(), panelX + 100, y, C_WHITE);
        } else {
            graphics.text(this.font,
                    Component.translatable("autosell.gui.selected.none")
                            .withStyle(s -> s.withColor(0x333344)),
                    panelX + 82, y, C_WHITE);
        }
    }

    private void drawStatusRow(GuiGraphicsExtractor graphics) {
        int y = panelY + Y_STATUS;

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

    public void selectItem(Item item) {
        config.setSelectedItem(item);
        playUiSound(1.4F);
    }

    public Item getSelectedItem() {
        return config.getSelectedItem();
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
