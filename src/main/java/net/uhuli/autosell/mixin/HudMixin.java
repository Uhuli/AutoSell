package net.uhuli.autosell.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.uhuli.autosell.config.AutoSellConfig;
import net.uhuli.autosell.config.AutoSellConfig.HudCorner;
import net.uhuli.autosell.handler.AutoSellHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Hud.class)
public class HudMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderAutoSellHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        AutoSellHandler handler = AutoSellHandler.getInstance();
        if (!handler.isActive() || minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }

        final int ICON = 16;
        final int GAP = 2;
        final int PAD = 3;
        final int MARGIN = 5;
        final int MAX_PER_ROW = 8;

        AutoSellConfig config = AutoSellConfig.getInstance();
        List<ItemStack> stacks = config.getSelectedStacks();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int count = stacks.size();
        int perRow = Math.clamp(count, 1, MAX_PER_ROW);
        int rows = Math.max(1, Mth.positiveCeilDiv(count, perRow));

        // Never let the block eat more than a third of the screen.
        int maxRows = Math.max(1, (screenHeight / 3) / (ICON + GAP));
        boolean truncated = rows > maxRows;
        if (truncated) {
            rows = maxRows;
        }
        int shown = truncated ? rows * perRow - 1 : count;

        int width = PAD * 2 + perRow * ICON + (perRow - 1) * GAP;
        int height = PAD * 2 + rows * ICON + (rows - 1) * GAP;

        HudCorner corner = config.hudCorner;
        int boxX = corner.isLeft() ? MARGIN : screenWidth - width - MARGIN;
        int boxY = corner.isTop() ? MARGIN : screenHeight - height - MARGIN;

        graphics.fill(boxX, boxY, boxX + width, boxY + height, 0xAA000000);

        // Same colours as the status dot in the config screen.
        int borderColor = handler.getStatusDotColor();
        graphics.fill(boxX, boxY, boxX + width, boxY + 1, borderColor);
        graphics.fill(boxX, boxY + height - 1, boxX + width, boxY + height, borderColor);
        graphics.fill(boxX, boxY, boxX + 1, boxY + height, borderColor);
        graphics.fill(boxX + width - 1, boxY, boxX + width, boxY + height, borderColor);

        if (count == 0) {
            graphics.centeredText(minecraft.font, Component.literal("!"),
                    boxX + width / 2,
                    boxY + (height - minecraft.font.lineHeight) / 2 + 1,
                    0xFFFFAA00);
            return;
        }

        for (int i = 0; i < shown; i++) {
            int col = i % perRow;
            int row = i / perRow;
            graphics.item(stacks.get(i),
                    boxX + PAD + col * (ICON + GAP),
                    boxY + PAD + row * (ICON + GAP));
        }

        if (truncated) {
            Component overflow = Component.literal("+" + (count - shown));
            int cellX = boxX + PAD + (shown % perRow) * (ICON + GAP);
            int cellY = boxY + PAD + (shown / perRow) * (ICON + GAP);
            graphics.centeredText(minecraft.font, overflow,
                    cellX + ICON / 2,
                    cellY + (ICON - minecraft.font.lineHeight) / 2 + 1,
                    0xFFAAAABB);
        }
    }

}
