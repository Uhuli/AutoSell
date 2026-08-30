package net.uhuli.autosell.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.item.ItemStack;
import net.uhuli.autosell.config.AutoSellConfig;
import net.uhuli.autosell.handler.AutoSellHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderAutoSellHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!AutoSellHandler.getInstance().isActive()
                || minecraft.player == null
                || minecraft.gui.screen() != null) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();

        int boxSize = 24;
        int boxX = screenWidth - boxSize - 5;
        int boxY = 5;

        graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xAA000000);

        int borderColor = 0xFF00FF00;
        graphics.fill(boxX, boxY, boxX + boxSize, boxY + 1, borderColor);
        graphics.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, borderColor);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxSize, borderColor);
        graphics.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, borderColor);

        ItemStack stack = AutoSellConfig.getInstance().getSelectedItemStack();
        if (!stack.isEmpty()) {
            graphics.item(stack, boxX + 4, boxY + 4);
        }
    }

}
