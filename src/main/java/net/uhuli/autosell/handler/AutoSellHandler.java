package net.uhuli.autosell.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
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
import java.util.Set;

public class AutoSellHandler {

    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final int TRANSFER_DELAY_TICKS = 2;
    private static final int TOTAL_INVENTORY_SLOTS = 36;
    private static final int GUI_OPEN_TIMEOUT_TICKS = 80;
    private static final int GUI_STABILIZE_TICKS = 5;
    private static final int GUI_CLOSE_TICKS = 4;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static AutoSellHandler INSTANCE;
    private final Minecraft minecraft;
    private final List<Integer> slotsToTransfer = new ArrayList<>();
    private State currentState = State.IDLE;
    private boolean isActive = false;
    private boolean isProcessing = false;
    private int currentTransferIdx = 0;
    private int ticksSinceGuiOpen = 0;
    private int waitTicks = 0;
    private int ticksSinceCheck = 0;
    private int ticksSinceTransfer = 0;
    private int consecutiveFailures = 0;
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
        ticksSinceCheck = CHECK_INTERVAL_TICKS;
        consecutiveFailures = 0;
        slotsToTransfer.clear();
        SharedConstants.LOGGER.info("AutoSell started");
        playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.6f);
    }

    public void stop() {
        boolean wasActive = isActive;
        isActive = false;
        isProcessing = false;
        currentState = State.IDLE;
        slotsToTransfer.clear();
        if (wasActive) {
            SharedConstants.LOGGER.info("AutoSell stopped – sold {} stack(s) this session", sessionSellCount);
            playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.75f);
        }
    }

    public void tick() {
        if (!isActive || minecraft.player == null || minecraft.level == null) return;

        boolean inActiveTransfer = currentState == State.WAITING_FOR_GUI
                || currentState == State.TRANSFERRING_ITEMS
                || currentState == State.CLOSING_GUI;

        if (!inActiveTransfer && minecraft.gui.screen() != null) return;

        LocalPlayer player = minecraft.player;

        switch (currentState) {
            case MONITORING -> {
                if (++ticksSinceCheck >= CHECK_INTERVAL_TICKS) {
                    ticksSinceCheck = 0;
                    checkInventory(player);
                }
            }
            case WAITING_FOR_GUI -> tickWaitingForGui(player);
            case TRANSFERRING_ITEMS -> {
                if (++ticksSinceTransfer >= TRANSFER_DELAY_TICKS) transferNextItem(player);
            }
            case CLOSING_GUI -> tickClosingGui(player);
            default -> { /* IDLE */ }
        }
    }

    private void checkInventory(LocalPlayer player) {
        if (isProcessing) return;

        AutoSellConfig cfg = AutoSellConfig.getInstance();
        Set<Item> targets = cfg.getSelectedItems();
        if (targets.isEmpty()) return;

        int filled = 0, targetCount = 0;
        for (int i = 0; i < TOTAL_INVENTORY_SLOTS; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                filled++;
                if (targets.contains(stack.getItem())) targetCount++;
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
        String command = AutoSellConfig.getInstance().sellCommand;
        SharedConstants.LOGGER.info("Triggering /{}", command);
        player.connection.sendCommand(command);
    }

    private void tickWaitingForGui(LocalPlayer player) {
        ticksSinceGuiOpen++;
        if (hasOpenContainer(player)) {
            waitTicks++;
            if (waitTicks >= GUI_STABILIZE_TICKS) {
                waitTicks = 0;
                ticksSinceGuiOpen = 0;
                consecutiveFailures = 0;
                currentState = State.TRANSFERRING_ITEMS;
                prepareItemTransfer(player);
                SharedConstants.LOGGER.info("Sell GUI stable – starting transfer");
            }
        } else if (ticksSinceGuiOpen > GUI_OPEN_TIMEOUT_TICKS) {
            consecutiveFailures++;
            SharedConstants.LOGGER.warn("Sell GUI timed out – aborting ({}/{})",
                    consecutiveFailures, MAX_CONSECUTIVE_FAILURES);
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                abortAfterRepeatedFailures();
            } else {
                resetToMonitoring();
            }
        }
    }

    /** Otherwise the state machine retries for as long as the inventory stays full. */
    private void abortAfterRepeatedFailures() {
        String command = AutoSellConfig.getInstance().sellCommand;
        SharedConstants.LOGGER.warn("'/{}' failed {} times in a row – stopping AutoSell",
                command, consecutiveFailures);
        stop();
        minecraft.gui.chatListener().handleSystemMessage(
                Component.translatable("autosell.message.command_failed", command, MAX_CONSECUTIVE_FAILURES)
                        .withStyle(s -> s.withColor(0xFF5555)),
                false);
    }

    private void prepareItemTransfer(LocalPlayer player) {
        slotsToTransfer.clear();
        currentTransferIdx = 0;
        ticksSinceTransfer = 0;

        Set<Item> targets = AutoSellConfig.getInstance().getSelectedItems();
        if (targets.isEmpty()) {
            currentState = State.CLOSING_GUI;
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            // Container slots holding the target item belong to the shop; quick-moving
            // those would pull items in instead of selling them.
            if (slot.container != player.getInventory()) continue;

            ItemStack s = slot.getItem();
            if (!s.isEmpty() && targets.contains(s.getItem()) && slot.mayPickup(player)) {
                slotsToTransfer.add(i);
            }
        }

        if (slotsToTransfer.isEmpty()) {
            SharedConstants.LOGGER.warn("No transferable stacks found – closing GUI");
            currentState = State.CLOSING_GUI;
        } else {
            SharedConstants.LOGGER.info("Queued {} stack(s) for transfer", slotsToTransfer.size());
        }
    }

    private void transferNextItem(LocalPlayer player) {
        ticksSinceTransfer = 0;

        if (currentTransferIdx >= slotsToTransfer.size()) {
            SharedConstants.LOGGER.info("Transfer complete ({} stacks)", currentTransferIdx);
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
            sessionSellCount++;
            SharedConstants.LOGGER.debug("Transferred slot {}", slot);
        }

        currentTransferIdx++;
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
        ticksSinceCheck = 0;
        ticksSinceTransfer = 0;
        slotsToTransfer.clear();
    }

    @Contract(pure = true)
    private boolean hasOpenContainer(@NonNull LocalPlayer player) {
        return player.containerMenu != player.inventoryMenu;
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        if (minecraft.player != null) {
            minecraft.player.playSound(sound, volume, pitch);
        }
    }

    public boolean isActive() {
        return isActive;
    }

    /** Running with nothing selected — it would idle forever, so it is surfaced. */
    public boolean isMisconfigured() {
        return isActive && AutoSellConfig.getInstance().getSelectedItems().isEmpty();
    }

    public int getSessionSellCount() {
        return sessionSellCount;
    }

    public Component getStatusComponent() {
        if (!isActive) {
            return Component.translatable("autosell.status.inactive")
                    .withStyle(s -> s.withColor(0x666677));
        }
        if (isMisconfigured()) {
            return Component.translatable("autosell.status.no_items")
                    .withStyle(s -> s.withColor(0xFFAA00));
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
        if (isMisconfigured()) return 0xFFFFAA00;
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
