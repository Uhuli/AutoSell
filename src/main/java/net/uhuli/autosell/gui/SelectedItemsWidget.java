package net.uhuli.autosell.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

/** The row of selected items; clicking a chip removes it. */
public class SelectedItemsWidget extends AbstractWidget {

    private static final int CHIP = 16;
    private static final int CHIP_GAP = 2;
    /** Width kept free for the "+N" overflow marker. */
    private static final int OVERFLOW_W = 2 * (CHIP + CHIP_GAP);

    private static final int C_LABEL = 0x44444F;
    private static final int C_CHIP_BG = 0x18000000;
    private static final int C_CHIP_HOVER = 0x40FF4444;
    private static final int C_OVERFLOW = 0xFF555566;
    private static final int C_WHITE = 0xFFFFFFFF;

    private final AutoSellScreen parent;
    private final int labelWidth;

    public SelectedItemsWidget(int x, int y, int width, int height, AutoSellScreen parent) {
        super(x, y, width, height, Component.translatable("autosell.gui.selected.label"));
        this.parent = parent;
        this.labelWidth = font().width(getMessage());
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private int chipsX() {
        return getX() + labelWidth + 6;
    }

    private int chipsY() {
        return getY() + (getHeight() - CHIP) / 2;
    }

    /** Pure geometry, so hit-testing never depends on the last rendered frame. */
    private int visibleChipCount(int total) {
        int available = getX() + getWidth() - chipsX();
        int capacity = Math.max(0, (available + CHIP_GAP) / (CHIP + CHIP_GAP));
        if (total <= capacity) {
            return total;
        }
        int reserved = (OVERFLOW_W + CHIP_GAP) / (CHIP + CHIP_GAP);
        return Math.max(0, capacity - reserved);
    }

    /** Index of the chip under the cursor, or -1. */
    private int chipAt(double mouseX, double mouseY) {
        List<ItemStack> stacks = parent.getSelectedStacks();
        int shown = visibleChipCount(stacks.size());
        if (shown == 0) return -1;

        int localY = (int) mouseY - chipsY();
        if (localY < 0 || localY >= CHIP) return -1;

        int localX = (int) mouseX - chipsX();
        if (localX < 0) return -1;

        int stride = CHIP + CHIP_GAP;
        int index = localX / stride;
        if (index >= shown) return -1;
        if (localX % stride >= CHIP) return -1;
        return index;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics,
                                            int mouseX, int mouseY, float partialTick) {
        Font font = font();
        int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;

        graphics.text(font,
                getMessage().copy().withStyle(s -> s.withColor(C_LABEL)),
                getX(), textY, C_WHITE);

        List<ItemStack> stacks = parent.getSelectedStacks();
        if (stacks.isEmpty()) {
            graphics.text(font,
                    Component.translatable("autosell.gui.selected.none")
                            .withStyle(s -> s.withColor(0x333344)),
                    chipsX(), textY, C_WHITE);
            return;
        }

        int shown = visibleChipCount(stacks.size());
        int hovered = chipAt(mouseX, mouseY);
        int x = chipsX();
        int y = chipsY();

        for (int i = 0; i < shown; i++) {
            graphics.fill(x, y, x + CHIP, y + CHIP, i == hovered ? C_CHIP_HOVER : C_CHIP_BG);
            graphics.item(stacks.get(i), x, y);
            x += CHIP + CHIP_GAP;
        }

        if (shown < stacks.size()) {
            graphics.text(font,
                    Component.literal("+" + (stacks.size() - shown))
                            .withStyle(s -> s.withColor(C_OVERFLOW)),
                    x, textY, C_WHITE);
        }

        if (hovered >= 0) {
            graphics.setTooltipForNextFrame(font, stacks.get(hovered), mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int index = chipAt(event.x(), event.y());
        if (index < 0) return;

        List<ItemStack> stacks = parent.getSelectedStacks();
        if (index < stacks.size()) {
            parent.removeItem(stacks.get(index).getItem());
        }
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) {
        // The screen plays a pitched sound instead.
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        List<ItemStack> stacks = parent.getSelectedStacks();
        Component value = stacks.isEmpty()
                ? Component.translatable("autosell.gui.selected.none")
                : Component.translatable("autosell.gui.sold.count", stacks.size());
        output.add(NarratedElementType.TITLE,
                Component.translatable("autosell.narration.selected", value));
    }

}
