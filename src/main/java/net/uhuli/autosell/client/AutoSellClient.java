package net.uhuli.autosell.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.uhuli.autosell.SharedConstants;
import net.uhuli.autosell.config.AutoSellConfig;
import net.uhuli.autosell.gui.AutoSellScreen;
import net.uhuli.autosell.handler.AutoSellHandler;

public class AutoSellClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SharedConstants.LOGGER.info("Initializing AutoSell...");

        KeyBindings.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeyInput(client);

            if (client.player != null && client.level != null && !client.isPaused()) {
                AutoSellHandler.getInstance().tick();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
            SharedConstants.LOGGER.info("Disconnected from server, stopping AutoSell");
            AutoSellHandler.getInstance().stop();
            AutoSellConfig.getInstance().setEnabled(false);
        });

        AutoSellConfig.getInstance();

        SharedConstants.LOGGER.info("AutoSell initialized successfully");
    }


    private void handleKeyInput(Minecraft client) {
        if (KeyBindings.getOpenGuiKey().consumeClick()) {
            final Gui gui = client.gui;
            if (gui.screen() == null) {
                gui.setScreen(new AutoSellScreen());
            } else if (gui.screen() instanceof AutoSellScreen) {
                gui.setScreen(null);
            }
        }
    }

}