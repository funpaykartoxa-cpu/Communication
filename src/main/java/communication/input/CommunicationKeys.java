package communication.input;

import communication.config.CommunicationConfig;
import communication.util.ChatUtil;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Vanilla key bindings for the two marks. They are registered in the
 * "Communication" category, so they can be rebound in
 * Options -> Controls -> Key Binds as well as in the mod's own screen.
 */
public final class CommunicationKeys {
    /** Right Shift, reserved for opening the Communication screen. */
    public static final int MENU_KEY = 344;

    private static final String CATEGORY = "key.categories.communication";

    private static final KeyBinding PARTY_MARK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.communication.party_mark",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    ));

    private static final KeyBinding FRIEND_MARK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.communication.friend_mark",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    ));

    private CommunicationKeys() {
    }

    /**
     * Forces class initialization, which registers both key bindings with
     * Fabric before the vanilla controls screen is first opened.
     */
    public static void register() {
        // Static initializers do the work.
    }

    /** Copies bindings from the JSON config into the vanilla key bindings. */
    public static void applyConfig(CommunicationConfig config) {
        setKey(PARTY_MARK, config.partyMarkBind);
        setKey(FRIEND_MARK, config.friendMarkBind);
        KeyBinding.updateKeysByCode();

        int party = code(PARTY_MARK);
        int friend = code(FRIEND_MARK);
        boolean changed = config.partyMarkBind != party || config.friendMarkBind != friend;
        config.partyMarkBind = party;
        config.friendMarkBind = friend;
        if (changed) {
            config.save();
        }
    }

    /**
     * Copies bindings changed in Options -> Controls back into the config.
     * Right Shift cannot be used for a mark because it opens the mod menu.
     */
    public static void syncFromBindings(CommunicationConfig config) {
        int party = code(PARTY_MARK);
        int friend = code(FRIEND_MARK);
        boolean changed = false;

        if (party == MENU_KEY) {
            setKey(PARTY_MARK, -1);
            party = -1;
            changed = true;
            ChatUtil.send("&eRight Shift зарезервирован под меню Communication и не может быть меткой.");
        }
        if (friend == MENU_KEY) {
            setKey(FRIEND_MARK, -1);
            friend = -1;
            changed = true;
            ChatUtil.send("&eRight Shift зарезервирован под меню Communication и не может быть меткой.");
        }

        if (config.partyMarkBind != party) {
            config.partyMarkBind = party;
            changed = true;
        }
        if (config.friendMarkBind != friend) {
            config.friendMarkBind = friend;
            changed = true;
        }
        if (changed) {
            KeyBinding.updateKeysByCode();
            config.save();
        }
    }

    /** Applies a bind chosen in the mod's settings screen. */
    public static void setBind(boolean friend, int keyCode) {
        setKey(friend ? FRIEND_MARK : PARTY_MARK, keyCode);
        KeyBinding.updateKeysByCode();
    }

    public static int partyCode() {
        return code(PARTY_MARK);
    }

    public static int friendCode() {
        return code(FRIEND_MARK);
    }

    /** Returns true once per physical press and clears the queued presses. */
    public static boolean consumePartyPress() {
        boolean pressed = false;
        while (PARTY_MARK.wasPressed()) {
            pressed = true;
        }
        return pressed;
    }

    /** Returns true once per physical press and clears the queued presses. */
    public static boolean consumeFriendPress() {
        boolean pressed = false;
        while (FRIEND_MARK.wasPressed()) {
            pressed = true;
        }
        return pressed;
    }

    private static int code(KeyBinding binding) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        return key == null ? GLFW.GLFW_KEY_UNKNOWN : key.getCode();
    }

    private static void setKey(KeyBinding binding, int keyCode) {
        int safe = keyCode < 0 || keyCode == MENU_KEY ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
        binding.setBoundKey(safe == GLFW.GLFW_KEY_UNKNOWN
                ? InputUtil.UNKNOWN_KEY
                : InputUtil.fromKeyCode(safe, 0));
    }
}
