package net.uhuli.autosell.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.uhuli.autosell.SharedConstants;
import net.uhuli.autosell.config.AutoSellConfig;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class AutoSellHandler {

    private static final long CHECK_INTERVAL_MS = 500;
    private static final long TRANSFER_DELAY_MS = 80;
    private static final int TOTAL_INVENTORY_SLOTS = 36;
    private static final int GUI_OPEN_TIMEOUT_TICKS = 80;
    private static final int GUI_STABILIZE_TICKS = 5;
    private static final int GUI_CLOSE_TICKS = 4;
    private static AutoSellHandler INSTANCE;
    private final Minecraft minecraft;
    private final List<Integer> slotsToTransfer = new ArrayList<>();
    private State currentState = State.IDLE;
    private boolean isActive = false;
    private boolean isProcessing = false;
    private int currentTransferIdx = 0;
    private int ticksSinceGuiOpen = 0;
    private int waitTicks = 0;
    private long lastCheckTime = 0;
    private long lastActionTime = 0;
    private int sessionSellCount = 0;

    private AutoSellHandler() {
        this.minecraft = Minecraft.getInstance();
    }

    public static AutoSellHandler getInstance() {
        if (INSTANCE == null) INSTANCE = new AutoSellHandler();
        return INSTANCE;
    }

    public void start() {
        isActive = true;
        isProcessing = false;
        sessionSellCount = 0;
        currentState = State.MONITORING;
        slotsToTransfer.clear();
        SharedConstants.LOGGER.info("AutoSell started");
        playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.6f);
    }

    public void stop() {
        isActive = false;
        isProcessing = false;
        currentState = State.IDLE;
        slotsToTransfer.clear();
        SharedConstants.LOGGER.info("AutoSell stopped – sold {} stack(s) this session", sessionSellCount);
        playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.75f);
    }

    public void tick() {
        if (!isActive || minecraft.player == null || minecraft.level == null) return;

        boolean inActiveTransfer = currentState == State.WAITING_FOR_GUI
                || currentState == State.TRANSFERRING_ITEMS
                || currentState == State.CLOSING_GUI;

        if (!inActiveTransfer && minecraft.screen != null) return;

        LocalPlayer player = minecraft.player;
        long now = System.currentTimeMillis();

        switch (currentState) {
            case MONITORING -> {
                if (now - lastCheckTime >= CHECK_INTERVAL_MS) {
                    lastCheckTime = now;
                    checkInventory(player);
                }
            }
            case WAITING_FOR_GUI -> tickWaitingForGui(player);
            case TRANSFERRING_ITEMS -> {
                if (now - lastActionTime >= TRANSFER_DELAY_MS) transferNextItem(player);
            }
            case CLOSING_GUI -> tickClosingGui(player);
            default -> { /* IDLE */ }
        }
    }

    private void checkInventory(LocalPlayer player) {
        if (isProcessing) return;

        AutoSellConfig cfg = AutoSellConfig.getInstance();
        Item target = cfg.getSelectedItem();
        if (target == null) return;

        int filled = 0, targetCount = 0;
        for (int i = 0; i < TOTAL_INVENTORY_SLOTS; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                filled++;
                if (stack.getItem() == target) targetCount++;
            }
        }

        double fillPct = (double) filled / TOTAL_INVENTORY_SLOTS * 100.0;
        if (fillPct >= cfg.inventoryThreshold && targetCount > 0) {
            startSellProcess(player);
        }
    }

    private void startSellProcess(@NonNull LocalPlayer player) {
        isProcessing = true;
        currentState = State.WAITING_FOR_GUI;
        ticksSinceGuiOpen = 0;
        waitTicks = 0;
        SharedConstants.LOGGER.info("Triggering /sell");
        player.connection.sendCommand("sell");
    }

    private void tickWaitingForGui(LocalPlayer player) {
        ticksSinceGuiOpen++;
        if (hasOpenContainer(player)) {
            waitTicks++;
            if (waitTicks >= GUI_STABILIZE_TICKS) {
                waitTicks = 0;
                ticksSinceGuiOpen = 0;
                currentState = State.TRANSFERRING_ITEMS;
                prepareItemTransfer(player);
                SharedConstants.LOGGER.info("Sell GUI stable – starting transfer");
            }
        } else if (ticksSinceGuiOpen > GUI_OPEN_TIMEOUT_TICKS) {
            SharedConstants.LOGGER.warn("Sell GUI timed out – aborting");
            resetToMonitoring();
        }
    }

    private void prepareItemTransfer(LocalPlayer player) {
        slotsToTransfer.clear();
        currentTransferIdx = 0;

        Item target = AutoSellConfig.getInstance().getSelectedItem();
        if (target == null) {
            currentState = State.CLOSING_GUI;
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack s = slot.getItem();
            if (!s.isEmpty() && s.getItem() == target && slot.mayPickup(player)) {
                slotsToTransfer.add(i);
            }
        }

        if (slotsToTransfer.isEmpty()) {
            SharedConstants.LOGGER.warn("No transferable stacks found – closing GUI");
            currentState = State.CLOSING_GUI;
        } else {
            SharedConstants.LOGGER.info("Queued {} stack(s) for transfer", slotsToTransfer.size());
            lastActionTime = System.currentTimeMillis();
        }
    }

    private void transferNextItem(LocalPlayer player) {
        if (currentTransferIdx >= slotsToTransfer.size()) {
            SharedConstants.LOGGER.info("Transfer complete ({} stacks)", slotsToTransfer.size());
            sessionSellCount += slotsToTransfer.size();
            currentState = State.CLOSING_GUI;
            waitTicks = 0;
            return;
        }

        if (!hasOpenContainer(player)) {
            SharedConstants.LOGGER.warn("Container closed unexpectedly during transfer");
            resetToMonitoring();
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        int slot = slotsToTransfer.get(currentTransferIdx);

        if (minecraft.gameMode != null && slot < menu.slots.size()) {
            minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
            SharedConstants.LOGGER.debug("Transferred slot {}", slot);
        }

        currentTransferIdx++;
        lastActionTime = System.currentTimeMillis();
    }

    private void tickClosingGui(LocalPlayer player) {
        if (hasOpenContainer(player)) player.closeContainer();

        waitTicks++;
        if (waitTicks >= GUI_CLOSE_TICKS) {
            playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
            resetToMonitoring();
        }
    }

    private void resetToMonitoring() {
        currentState = State.MONITORING;
        isProcessing = false;
        waitTicks = 0;
        ticksSinceGuiOpen = 0;
        slotsToTransfer.clear();
    }


    @Contract(pure = true)
    private boolean hasOpenContainer(@NonNull LocalPlayer player) {
        return player.containerMenu != player.inventoryMenu;
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (minecraft.player != null) {
            minecraft.player.playSound(sound, volume, pitch);
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public State getCurrentState() {
        return currentState;
    }

    public int getSessionSellCount() {
        return sessionSellCount;
    }

    public Component getStatusComponent() {
        if (!isActive) {
            return Component.translatable("autosell.status.inactive")
                    .withStyle(s -> s.withColor(0x666677));
        }
        return switch (currentState) {
            case MONITORING -> Component.translatable("autosell.status.monitoring")
                    .withStyle(s -> s.withColor(0x44DD66));
            case WAITING_FOR_GUI -> Component.translatable("autosell.status.waiting")
                    .withStyle(s -> s.withColor(0xFFAA00));
            case TRANSFERRING_ITEMS -> Component.translatable("autosell.status.selling")
                    .withStyle(s -> s.withColor(0x00E5FF));
            case CLOSING_GUI -> Component.translatable("autosell.status.finishing")
                    .withStyle(s -> s.withColor(0xFFAA00));
            default -> Component.literal("—").withStyle(s -> s.withColor(0x666677));
        };
    }

    public int getStatusDotColor() {
        if (!isActive) return 0xFF444455;
        return switch (currentState) {
            case MONITORING -> 0xFF44DD66;
            case WAITING_FOR_GUI,
                 CLOSING_GUI -> 0xFFFFAA00;
            case TRANSFERRING_ITEMS -> 0xFF00E5FF;
            default -> 0xFF444455;
        };
    }

    public enum State {
        IDLE, MONITORING, WAITING_FOR_GUI, TRANSFERRING_ITEMS, CLOSING_GUI
    }

}