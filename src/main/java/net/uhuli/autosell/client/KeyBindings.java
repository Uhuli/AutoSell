package net.uhuli.autosell.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class KeyBindings {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("autosell", "category")
    );
    private static final KeyMapping OPEN_KEY = new KeyMapping(
            "autosell.key.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY
    );

    static void register() {
        KeyMappingHelper.registerKeyMapping(OPEN_KEY);
    }

    public static KeyMapping getOpenGuiKey() {
        return OPEN_KEY;
    }

}