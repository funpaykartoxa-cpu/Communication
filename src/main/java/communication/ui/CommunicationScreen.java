package communication.ui;

import communication.CommunicationMod;
import communication.config.CommunicationConfig;
import communication.input.CommunicationKeys;
import communication.party.PartyManager;
import communication.render.ColorUtil;
import communication.render.Fonts;
import communication.render.RoundedRenderer;
import communication.render.Theme;
import communication.util.ChatUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Compact settings window for the Communication mod. It only exposes the
 * options that belong to this mod: the master switch, party controls, the two
 * mark binds and the mark volume. The visual language (dark rounded panels,
 * MSDF text, blue accent) matches the rest of the Delta interface without
 * pulling in any of its other modules.
 */
public final class CommunicationScreen extends Screen {
    private static final float PANEL_WIDTH = 420.0f;
    private static final float PANEL_HEIGHT = 310.0f;
    private static final float MARGIN = 18.0f;
    private static final int PRIMARY = Theme.PRIMARY;
    private static final int TEXT = Theme.TEXT;
    private static final int MUTED = Theme.MUTED;
    private static final int OPEN_KEY = CommunicationKeys.MENU_KEY;

    private static final float MASTER_X = 16.0f;
    private static final float MASTER_Y = 58.0f;
    private static final float MASTER_W = PANEL_WIDTH - 32.0f;
    private static final float MASTER_H = 34.0f;

    private static final float CREATE_X = 20.0f;
    private static final float CREATE_Y = 110.0f;
    private static final float CREATE_W = 88.0f;
    private static final float ROW_H = 22.0f;
    private static final float INPUT_X = 116.0f;
    private static final float INPUT_W = 148.0f;
    private static final float JOIN_X = 272.0f;
    private static final float JOIN_W = 64.0f;
    private static final float LEAVE_X = 344.0f;
    private static final float LEAVE_W = 56.0f;

    private static final float BIND_X = 250.0f;
    private static final float BIND_W = 150.0f;
    private static final float BIND_H = 18.0f;
    private static final float BIND_ROW_1 = 178.0f;
    private static final float BIND_ROW_2 = 206.0f;

    private static final float VOLUME_ROW = 234.0f;
    private static final float TRACK_X = 250.0f;
    private static final float TRACK_W = 110.0f;
    private static final float TRACK_Y = 243.0f;
    private static final float TRACK_H = 6.0f;

    private final CommunicationConfig config;
    private final PartyManager party;

    private String joinCode = "";
    private boolean joinFocused;
    private boolean draggingVolume;
    private BindTarget listening;

    private float originX;
    private float originY;
    private float scale = 1.0f;
    private DrawContext context;

    public CommunicationScreen() {
        super(Text.literal("Communication"));
        CommunicationMod mod = CommunicationMod.get();
        this.config = mod == null ? new CommunicationConfig() : mod.config();
        this.party = mod == null ? null : mod.party();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout();
        this.context = context;
        context.fill(0, 0, this.width, this.height, 0x66000000);
        float localMouseX = toLocalX(mouseX);
        float localMouseY = toLocalY(mouseY);

        context.getMatrices().push();
        context.getMatrices().translate(this.originX, this.originY, 0.0f);
        context.getMatrices().scale(this.scale, this.scale, 1.0f);

        RoundedRenderer.fill(context.getMatrices(), 0.0f, 0.0f, PANEL_WIDTH, PANEL_HEIGHT, 9.0f,
                Theme.BACKGROUND_GUI);
        RoundedRenderer.outline(context.getMatrices(), 0.5f, 0.5f, PANEL_WIDTH - 1.0f, PANEL_HEIGHT - 1.0f,
                9.0f, 1.0f, Theme.OUTLINE);

        drawHeader(context, localMouseX, localMouseY);
        drawMaster(context, localMouseX, localMouseY);
        drawParty(context, localMouseX, localMouseY);
        drawBinds(context, localMouseX, localMouseY);
        drawVolume(context, localMouseX, localMouseY);
        drawFooter(context);

        context.getMatrices().pop();
    }

    private void drawHeader(DrawContext context, float mouseX, float mouseY) {
        RoundedRenderer.fill(context.getMatrices(), 20.0f, 14.0f, 3.0f, 19.0f, 1.5f, PRIMARY);
        label("COMMUNICATION", 30.0f, 23.0f, 12.0f, TEXT);
        label("Пати, метки и оверлей ESC", 30.0f, 38.0f, 6.5f, MUTED);
        labelRight("v1.0.0", PANEL_WIDTH - 48.0f, 23.0f, 6.5f, MUTED);

        boolean closeHovered = isInside(mouseX, mouseY, PANEL_WIDTH - 36.0f, 12.0f, 20.0f, 20.0f);
        RoundedRenderer.fill(context.getMatrices(), PANEL_WIDTH - 36.0f, 12.0f, 20.0f, 20.0f, 5.0f,
                closeHovered ? Theme.PANEL_HOVER : Theme.PANEL);
        labelCentered("X", PANEL_WIDTH - 26.0f, 22.0f, 8.5f, closeHovered ? Theme.DANGER : MUTED);

        RoundedRenderer.fill(context.getMatrices(), 20.0f, 50.0f, PANEL_WIDTH - 40.0f, 1.0f, 0.0f,
                Theme.OUTLINE);
    }

    private void drawMaster(DrawContext context, float mouseX, float mouseY) {
        boolean hovered = isInside(mouseX, mouseY, MASTER_X, MASTER_Y, MASTER_W, MASTER_H);
        RoundedRenderer.fill(context.getMatrices(), MASTER_X, MASTER_Y, MASTER_W, MASTER_H, 6.0f,
                hovered ? Theme.PANEL_HOVER : Theme.PANEL);
        if (hovered) {
            RoundedRenderer.outline(context.getMatrices(), MASTER_X, MASTER_Y, MASTER_W, MASTER_H, 6.0f,
                    1.0f, Theme.PRIMARY_SOFT);
        }
        RoundedRenderer.fill(context.getMatrices(), 28.0f, 70.0f, 10.0f, 10.0f, 5.0f,
                this.config.enabled ? PRIMARY : Theme.MUTED_DARK);
        label("Communication", 46.0f, 69.0f, 8.5f, this.config.enabled ? TEXT : MUTED);
        label(this.config.enabled ? "Функции включены" : "Функции выключены", 46.0f, 81.0f, 6.0f, MUTED);
        drawSwitch(context, PANEL_WIDTH - 62.0f, 68.0f, this.config.enabled,
                isInside(mouseX, mouseY, PANEL_WIDTH - 62.0f, 68.0f, 34.0f, 14.0f));
    }

    private void drawParty(DrawContext context, float mouseX, float mouseY) {
        label("ПАТИ", 20.0f, 100.0f, 6.5f, MUTED);
        boolean active = this.party != null && this.party.isActive();
        String status = active
                ? "Код " + this.party.getCode() + "  /  участников: " + this.party.memberCount()
                : "Вы не в пати";
        labelRight(status, PANEL_WIDTH - 20.0f, 100.0f, 6.5f, active ? TEXT : MUTED);

        button(context, CREATE_X, CREATE_Y, CREATE_W, ROW_H, "Создать",
                isInside(mouseX, mouseY, CREATE_X, CREATE_Y, CREATE_W, ROW_H), !active);

        boolean inputHovered = isInside(mouseX, mouseY, INPUT_X, CREATE_Y, INPUT_W, ROW_H);
        RoundedRenderer.fill(context.getMatrices(), INPUT_X, CREATE_Y, INPUT_W, ROW_H, 4.5f, Theme.PANEL);
        if (this.joinFocused) {
            RoundedRenderer.outline(context.getMatrices(), INPUT_X, CREATE_Y, INPUT_W, ROW_H, 4.5f, 1.0f, PRIMARY);
        } else if (inputHovered) {
            RoundedRenderer.outline(context.getMatrices(), INPUT_X, CREATE_Y, INPUT_W, ROW_H, 4.5f, 1.0f,
                    Theme.OUTLINE_STRONG);
        }
        String inputText = this.joinCode.isEmpty() ? "КОД ПАТИ" : this.joinCode;
        int inputColor = this.joinCode.isEmpty() ? Theme.MUTED_DARK : TEXT;
        label(inputText, INPUT_X + 8.0f, CREATE_Y + ROW_H / 2.0f, 7.0f, inputColor);
        if (this.joinFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = INPUT_X + 8.0f + Fonts.gt().width(this.joinCode, 7.0f) + 1.0f;
            RoundedRenderer.fill(context.getMatrices(), caretX, CREATE_Y + 6.0f, 1.0f, 10.0f, 0.5f, PRIMARY);
        }

        button(context, JOIN_X, CREATE_Y, JOIN_W, ROW_H, "Войти",
                isInside(mouseX, mouseY, JOIN_X, CREATE_Y, JOIN_W, ROW_H), !this.joinCode.isEmpty());
        button(context, LEAVE_X, CREATE_Y, LEAVE_W, ROW_H, "Выйти",
                isInside(mouseX, mouseY, LEAVE_X, CREATE_Y, LEAVE_W, ROW_H), active);

        String info;
        if (active) {
            info = "Друзьям: .party join " + this.party.getCode() + "   /   код копируется по ЛКМ в ESC";
        } else {
            info = "Создайте пати или введите код из 4-12 букв и цифр";
        }
        label(info, 20.0f, 145.0f, 6.0f, MUTED);
        RoundedRenderer.fill(context.getMatrices(), 20.0f, 157.0f, PANEL_WIDTH - 40.0f, 1.0f, 0.0f,
                Theme.OUTLINE);
    }

    private void drawBinds(DrawContext context, float mouseX, float mouseY) {
        label("МЕТКИ", 20.0f, 167.0f, 6.5f, MUTED);
        labelRight("здесь или в управлении", PANEL_WIDTH - 20.0f, 167.0f, 6.0f, MUTED);

        drawBindRow(context, mouseX, mouseY, BIND_ROW_1, "Метка пати", BindTarget.PARTY,
                this.config.partyMarkBind);
        drawBindRow(context, mouseX, mouseY, BIND_ROW_2, "Метка друзьям", BindTarget.FRIEND,
                this.config.friendMarkBind);
    }

    private void drawBindRow(DrawContext context, float mouseX, float mouseY, float y, String title,
                             BindTarget target, int bind) {
        boolean hovered = isInside(mouseX, mouseY, 16.0f, y, PANEL_WIDTH - 32.0f, 24.0f);
        RoundedRenderer.fill(context.getMatrices(), 16.0f, y, PANEL_WIDTH - 32.0f, 24.0f, 5.0f,
                hovered ? Theme.PANEL_HOVER : Theme.PANEL);
        label(title, 28.0f, y + 12.0f, 7.5f, TEXT);

        boolean chipHovered = isInside(mouseX, mouseY, BIND_X, y + 3.0f, BIND_W, BIND_H);
        boolean listeningHere = this.listening == target;
        int chipColor = listeningHere ? Theme.PRIMARY_SOFT
                : chipHovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED;
        RoundedRenderer.fill(context.getMatrices(), BIND_X, y + 3.0f, BIND_W, BIND_H, 4.0f, chipColor);
        RoundedRenderer.outline(context.getMatrices(), BIND_X, y + 3.0f, BIND_W, BIND_H, 4.0f, 1.0f,
                listeningHere ? PRIMARY : Theme.OUTLINE);
        String value = listeningHere ? "НАЖМИТЕ КЛАВИШУ" : bindName(bind);
        labelCentered(value, BIND_X + BIND_W / 2.0f, y + 12.0f, 6.5f,
                listeningHere ? PRIMARY : bind < 0 ? Theme.MUTED_DARK : TEXT);
    }

    private void drawVolume(DrawContext context, float mouseX, float mouseY) {
        boolean hovered = isInside(mouseX, mouseY, 16.0f, VOLUME_ROW, PANEL_WIDTH - 32.0f, 24.0f);
        RoundedRenderer.fill(context.getMatrices(), 16.0f, VOLUME_ROW, PANEL_WIDTH - 32.0f, 24.0f, 5.0f,
                hovered ? Theme.PANEL_HOVER : Theme.PANEL);
        label("Громкость метки пати", 28.0f, VOLUME_ROW + 12.0f, 7.5f, TEXT);

        RoundedRenderer.fill(context.getMatrices(), TRACK_X, TRACK_Y, TRACK_W, TRACK_H, 3.0f,
                Theme.PANEL_RAISED);
        float ratio = MathHelper.clamp(this.config.partyMarkVolume, 0.0f, 1.0f);
        float filled = Math.max(TRACK_H, TRACK_W * ratio);
        RoundedRenderer.fill(context.getMatrices(), TRACK_X, TRACK_Y, filled, TRACK_H, 3.0f, PRIMARY);
        float knobX = TRACK_X + TRACK_W * ratio - 3.0f;
        RoundedRenderer.fill(context.getMatrices(), knobX, TRACK_Y - 2.0f, 6.0f, TRACK_H + 4.0f, 3.0f,
                ColorUtil.lerpColor(PRIMARY, TEXT, 0.35f));

        String value = Math.round(ratio * 100.0f) + "%";
        labelRight(value, PANEL_WIDTH - 28.0f, VOLUME_ROW + 12.0f, 7.0f, TEXT);
    }

    private void drawFooter(DrawContext context) {
        labelCentered("Right Shift — открыть меню   /   ESC — закрыть   /   клавиши — в управлении",
                PANEL_WIDTH / 2.0f, 284.0f, 6.0f, MUTED);
    }

    private void drawSwitch(DrawContext context, float x, float y, boolean on, boolean hovered) {
        RoundedRenderer.fill(context.getMatrices(), x, y, 34.0f, 14.0f, 7.0f,
                on ? PRIMARY : Theme.PANEL_RAISED);
        if (hovered) {
            RoundedRenderer.outline(context.getMatrices(), x, y, 34.0f, 14.0f, 7.0f, 1.0f,
                    on ? ColorUtil.applyAlphaToColor(TEXT, 0.6f) : Theme.OUTLINE_STRONG);
        }
        float knobX = on ? x + 20.0f : x + 4.0f;
        RoundedRenderer.fill(context.getMatrices(), knobX, y + 3.0f, 8.0f, 8.0f, 4.0f, TEXT);
    }

    private void button(DrawContext context, float x, float y, float width, float height, String title,
                        boolean hovered, boolean enabled) {
        int background = !enabled ? Theme.PANEL : hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED;
        RoundedRenderer.fill(context.getMatrices(), x, y, width, height, 4.5f, background);
        RoundedRenderer.outline(context.getMatrices(), x, y, width, height, 4.5f, 1.0f,
                hovered && enabled ? Theme.PRIMARY_SOFT : Theme.OUTLINE);
        int color = !enabled ? Theme.MUTED_DARK : hovered ? TEXT : TEXT;
        labelCentered(title, x + width / 2.0f, y + height / 2.0f, 7.0f, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        updateLayout();
        float x = toLocalX(mouseX);
        float y = toLocalY(mouseY);
        if (x < 0.0f || y < 0.0f || x > PANEL_WIDTH || y > PANEL_HEIGHT) {
            close();
            return true;
        }
        if (isInside(x, y, PANEL_WIDTH - 36.0f, 12.0f, 20.0f, 20.0f)) {
            close();
            return true;
        }
        if (isInside(x, y, MASTER_X, MASTER_Y, MASTER_W, MASTER_H)) {
            this.config.enabled = !this.config.enabled;
            this.config.save();
            return true;
        }
        if (isInside(x, y, CREATE_X, CREATE_Y, CREATE_W, ROW_H)) {
            createParty();
            return true;
        }
        if (isInside(x, y, INPUT_X, CREATE_Y, INPUT_W, ROW_H)) {
            this.joinFocused = true;
            return true;
        }
        if (isInside(x, y, JOIN_X, CREATE_Y, JOIN_W, ROW_H)) {
            joinParty();
            return true;
        }
        if (isInside(x, y, LEAVE_X, CREATE_Y, LEAVE_W, ROW_H)) {
            leaveParty();
            return true;
        }
        if (isInside(x, y, BIND_X, BIND_ROW_1 + 3.0f, BIND_W, BIND_H)) {
            this.listening = BindTarget.PARTY;
            this.joinFocused = false;
            return true;
        }
        if (isInside(x, y, BIND_X, BIND_ROW_2 + 3.0f, BIND_W, BIND_H)) {
            this.listening = BindTarget.FRIEND;
            this.joinFocused = false;
            return true;
        }
        if (isInside(x, y, TRACK_X - 6.0f, TRACK_Y - 8.0f, TRACK_W + 12.0f, TRACK_H + 16.0f)) {
            this.draggingVolume = true;
            updateVolume(x);
            return true;
        }
        this.joinFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingVolume && button == 0) {
            updateLayout();
            updateVolume(toLocalX(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingVolume) {
            this.draggingVolume = false;
            this.config.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.listening != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                assignBind(this.listening, -1);
            } else if (keyCode == OPEN_KEY) {
                return true;
            } else {
                assignBind(this.listening, keyCode);
            }
            this.listening = null;
            return true;
        }
        if (keyCode == OPEN_KEY) {
            close();
            return true;
        }
        if (this.joinFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                joinParty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!this.joinCode.isEmpty()) {
                    this.joinCode = this.joinCode.substring(0, this.joinCode.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.joinFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clipboard = this.client == null ? "" : this.client.keyboard.getClipboard();
                appendCode(clipboard);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.joinFocused && this.listening == null) {
            appendCode(String.valueOf(chr));
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void appendCode(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder(this.joinCode);
        for (int index = 0; index < value.length() && builder.length() < 12; index++) {
            char character = Character.toUpperCase(value.charAt(index));
            if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
                builder.append(character);
            }
        }
        this.joinCode = builder.toString();
    }

    private void createParty() {
        if (this.party == null) {
            return;
        }
        if (this.party.isActive()) {
            ChatUtil.send("&eВы уже в пати &f" + this.party.getCode() + "&e. Сначала выйдите.");
            return;
        }
        String code = this.party.create();
        if (code == null || code.isBlank()) {
            ChatUtil.send("&cНе удалось создать пати.");
            return;
        }
        ChatUtil.send("&aПати создана! Код: &f&l" + code);
        ChatUtil.send("&7Передайте код друзьям: &f.party join " + code);
    }

    private void joinParty() {
        if (this.party == null) {
            return;
        }
        String code = this.joinCode.trim();
        if (code.isEmpty()) {
            ChatUtil.send("&7Использование: &f.party join <код>");
            return;
        }
        if (this.party.isActive() && this.party.getCode().equalsIgnoreCase(code)) {
            ChatUtil.send("&eВы уже в этой пати.");
            return;
        }
        if (!this.party.join(code)) {
            ChatUtil.send("&cНеверный код пати. Код состоит из 4-12 букв и цифр.");
            return;
        }
        ChatUtil.send("&aВы присоединились к пати &f&l" + this.party.getCode());
        this.joinCode = "";
        this.joinFocused = false;
    }

    private void leaveParty() {
        if (this.party == null || !this.party.isActive()) {
            ChatUtil.send("&7Вы не в пати.");
            return;
        }
        this.party.leave();
        ChatUtil.send("&7Вы вышли из пати.");
    }

    private void updateVolume(float localX) {
        float ratio = MathHelper.clamp((localX - TRACK_X) / TRACK_W, 0.0f, 1.0f);
        this.config.partyMarkVolume = Math.round(ratio * 20.0f) / 20.0f;
    }

    private void assignBind(BindTarget target, int keyCode) {
        int safe = keyCode == OPEN_KEY ? -1 : keyCode;
        if (target == BindTarget.PARTY) {
            this.config.partyMarkBind = safe;
        } else {
            this.config.friendMarkBind = safe;
        }
        CommunicationKeys.setBind(target == BindTarget.FRIEND, safe);
        this.config.save();
    }

    private String bindName(int keyCode) {
        if (keyCode < 0) {
            return "НЕ НАЗНАЧЕНА";
        }
        try {
            String name = InputUtil.fromKeyCode(keyCode, 0).getLocalizedText().getString();
            return name == null || name.isBlank() ? "KEY " + keyCode : name.toUpperCase(Locale.ROOT);
        } catch (Exception exception) {
            return "KEY " + keyCode;
        }
    }

    private void updateLayout() {
        float widthScale = (this.width - MARGIN * 2.0f) / PANEL_WIDTH;
        float heightScale = (this.height - MARGIN * 2.0f) / PANEL_HEIGHT;
        this.scale = Math.min(1.0f, Math.min(widthScale, heightScale));
        this.scale = Math.max(0.35f, this.scale);
        this.originX = (this.width - PANEL_WIDTH * this.scale) / 2.0f;
        this.originY = (this.height - PANEL_HEIGHT * this.scale) / 2.0f;
    }

    private float toLocalX(double mouseX) {
        return (float) ((mouseX - this.originX) / this.scale);
    }

    private float toLocalY(double mouseY) {
        return (float) ((mouseY - this.originY) / this.scale);
    }

    private void label(String value, float x, float centerY, float size, int color) {
        Fonts.gt().draw(this.context.getMatrices(), value, x,
                Fonts.gt().textY(value, size, centerY), size, color);
    }

    private void labelCentered(String value, float centerX, float centerY, float size, int color) {
        label(value, centerX - Fonts.gt().width(value, size) / 2.0f, centerY, size, color);
    }

    private void labelRight(String value, float right, float centerY, float size, int color) {
        label(value, right - Fonts.gt().width(value, size), centerY, size, color);
    }

    private static boolean isInside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum BindTarget {
        PARTY,
        FRIEND
    }
}
