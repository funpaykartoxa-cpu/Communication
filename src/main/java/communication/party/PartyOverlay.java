package communication.party;

import communication.CommunicationMod;
import communication.render.Fonts;
import communication.render.Theme;
import communication.render.ColorUtil;
import communication.util.ChatUtil;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Party card shown on top of the vanilla game menu (ESC).
 *
 * <p>Only the other party members are rendered here: the local player already
 * knows their own inventory, so their card is omitted. Every member gets a
 * compact line with a head, vanilla Minecraft hearts, their inventory and a
 * vertical armor column beside it. The whole panel uses the client's own MSDF
 * font and is drawn without a background. Hovering a slot shows the usual item
 * tooltip; hovering the party code shows a copy hint and clicking it puts the
 * code into the clipboard.</p>
 */
public final class PartyOverlay {
    private static final int MARGIN = 12;
    private static final int PANEL_WIDTH = 196;
    private static final int HEADER_HEIGHT = 26;
    private static final int CARD_HEIGHT = 96;
    private static final int EMPTY_HEIGHT = 22;
    private static final int SLOT_SIZE = 16;
    private static final int GRID_LEFT = 6;
    private static final int GRID_TOP = 28;
    private static final int GRID_WIDTH = 9 * SLOT_SIZE;
    private static final int ARMOR_LEFT = GRID_LEFT + GRID_WIDTH + 4;
    private static final int ARMOR_LEFT_SECOND = ARMOR_LEFT + SLOT_SIZE;
    private static final int MAX_MEMBERS = 8;
    private static final int HEART_SIZE = 9;
    private static final int HEART_SPACING = 10;
    private static final int HEART_ROW_Y = 15;
    private static final int NAME_LEFT = 26;
    private static final float HEADER_SIZE = 8.0f;
    private static final float CODE_SIZE = 8.0f;
    private static final float NAME_SIZE = 7.5f;
    private static final float HEALTH_SIZE = 7.0f;
    private static final float LOCATION_SIZE = 6.5f;
    private static final float LABEL_SIZE = 6.0f;
    private static final float FOOTER_SIZE = 6.5f;
    private static final int SHADOW_COLOR = ColorUtil.convertToARGB(0, 0, 0, 150);
    private static final int ANARCHY_COLOR = ColorUtil.convertToARGB(245, 85, 85, 255);
    private static final int COPIED_COLOR = ColorUtil.convertToARGB(95, 220, 115, 255);
    private static final Identifier HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.ofVanilla("hud/heart/half");
    private static final Identifier HEART_ABSORBING_FULL = Identifier.ofVanilla("hud/heart/absorbing_full");
    private static final Identifier HEART_ABSORBING_HALF = Identifier.ofVanilla("hud/heart/absorbing_half");
    private static final Identifier SHULKER_PREVIEW_TEXTURE = Identifier.of("communication", "pictures/minecraft/3x9.png");
    private static final int SHULKER_PREVIEW_WIDTH = 175;
    private static final int SHULKER_PREVIEW_HEIGHT = 70;

    private static long copiedAt;
    private static String copiedCode = "";

    private PartyOverlay() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void render(DrawContext context, int mouseX, int mouseY) {
        CommunicationMod mod = CommunicationMod.get();
        if (mod == null || mod.mc().player == null || mod.mc().world == null) {
            return;
        }
        PartyManager party = mod.party();
        if (party == null || !party.isActive()) {
            return;
        }

        List<PartyMember> allMembers = party.getMembersForDisplay();
        List<PartyMember> members = new ArrayList<>(allMembers.size());
        for (PartyMember member : allMembers) {
            if (!member.isSelf()) {
                members.add(member);
            }
        }

        Geometry geometry = geometry(members.size(), mod.mc().getWindow().getScaledWidth(),
                mod.mc().getWindow().getScaledHeight());
        if (geometry.scale() <= 0.05f) {
            return;
        }
        float localMouseX = (mouseX - geometry.originX()) / geometry.scale();
        float localMouseY = (mouseY - geometry.originY()) / geometry.scale();

        int primary = Theme.PRIMARY;
        int text = Theme.TEXT;
        int muted = Theme.MUTED;

        context.getMatrices().push();
        context.getMatrices().translate(geometry.originX(), geometry.originY(), 0.0f);
        context.getMatrices().scale(geometry.scale(), geometry.scale(), 1.0f);

        drawHeader(context, party, allMembers.size(), primary, text, muted, localMouseX, localMouseY);

        ItemStack hovered = null;
        int hoveredX = 0;
        int hoveredY = 0;

        for (int index = 0; index < geometry.shown(); index++) {
            PartyMember member = members.get(index);
            int cardY = HEADER_HEIGHT + index * CARD_HEIGHT;
            drawMember(context, member, cardY, text, muted, localMouseX, localMouseY);

            int gridY = cardY + GRID_TOP;
            for (int row = 0; row < 4; row++) {
                int rowY = gridY + (row < 3 ? row * SLOT_SIZE : 3 * SLOT_SIZE + 2);
                for (int column = 0; column < 9; column++) {
                    int slot = row < 3 ? 9 + row * 9 + column : column;
                    int slotX = GRID_LEFT + column * SLOT_SIZE;
                    ItemStack hover = drawSlot(context, member.getStack(slot), slotX, rowY, localMouseX, localMouseY);
                    if (!hover.isEmpty()) {
                        hovered = hover;
                        hoveredX = mouseX;
                        hoveredY = mouseY;
                    }
                }
            }

            int[] armorSlots = {39, 38, 37, 36};
            for (int armorIndex = 0; armorIndex < armorSlots.length; armorIndex++) {
                int slotY = gridY + armorIndex * SLOT_SIZE;
                ItemStack hover = drawSlot(context, member.getStack(armorSlots[armorIndex]),
                        ARMOR_LEFT, slotY, localMouseX, localMouseY);
                if (!hover.isEmpty()) {
                    hovered = hover;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }

            int offhandY = gridY + 3 * SLOT_SIZE + 2;
            ItemStack offhandHover = drawSlot(context, member.getStack(40),
                    ARMOR_LEFT_SECOND, offhandY, localMouseX, localMouseY);
            if (!offhandHover.isEmpty()) {
                hovered = offhandHover;
                hoveredX = mouseX;
                hoveredY = mouseY;
            }
        }

        if (geometry.shown() == 0) {
            String waiting = "Ожидание участников...";
            float waitingWidth = width(waiting, 7.0f);
            drawText(context, waiting, (PANEL_WIDTH - waitingWidth) / 2.0f,
                    textY(waiting, 7.0f, HEADER_HEIGHT + 10.0f), 7.0f, muted);
        }

        if (geometry.hidden() > 0) {
            String footer = "и ещё " + geometry.hidden() + " участник(ов)";
            drawText(context, footer, GRID_LEFT,
                    textY(footer, FOOTER_SIZE, HEADER_HEIGHT + geometry.shown() * CARD_HEIGHT + 7.0f),
                    FOOTER_SIZE, muted);
        }

        context.getMatrices().pop();

        if (hovered != null && !hovered.isEmpty()) {
            context.drawItemTooltip(mod.mc().textRenderer, hovered, hoveredX, hoveredY);
            drawShulkerPreview(context, hovered, hoveredX, hoveredY);
        }
    }

    /**
     * Draws the 3x9 container of a hovered shulker box on top of the overlay,
     * mirroring the Shulker Preview module: the panel opens above the cursor
     * and tints its slots with the shulker colour.
     */
    private static void drawShulkerPreview(DrawContext context, ItemStack stack, int mouseX, int mouseY) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
            return;
        }
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) {
            return;
        }
        List<ItemStack> contents = container.stream().toList();
        if (contents.isEmpty()) {
            return;
        }
        CommunicationMod mod = CommunicationMod.get();
        if (mod == null) {
            return;
        }

        int screenWidth = mod.mc().getWindow().getScaledWidth();
        int screenHeight = mod.mc().getWindow().getScaledHeight();
        float x = mouseX + 10.0f;
        float y = mouseY - SHULKER_PREVIEW_HEIGHT - 14.0f;
        if (x + SHULKER_PREVIEW_WIDTH > screenWidth - 4) {
            x = screenWidth - SHULKER_PREVIEW_WIDTH - 4;
        }
        if (x < 4.0f) {
            x = 4.0f;
        }
        if (y < 4.0f) {
            y = mouseY + 14.0f;
        }
        if (y + SHULKER_PREVIEW_HEIGHT > screenHeight - 4) {
            y = screenHeight - SHULKER_PREVIEW_HEIGHT - 4;
        }

        int tint = ColorUtil.combineColorWithAlpha(blockItem.getBlock().getDefaultMapColor().color, 255);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 500.0f);
        context.drawTexture(RenderLayer::getGuiTextured, SHULKER_PREVIEW_TEXTURE,
                0, 0, 0.0f, 0.0f, SHULKER_PREVIEW_WIDTH, SHULKER_PREVIEW_HEIGHT, 256, 256, tint);
        for (int index = 0; index < Math.min(contents.size(), 27); index++) {
            ItemStack item = contents.get(index);
            if (item == null || item.isEmpty()) {
                continue;
            }
            int slotX = 9 + (index % 9) * 18 + 1;
            int slotY = 9 + (index / 9) * 18 + 1;
            context.getMatrices().push();
            context.getMatrices().translate(slotX, slotY, 0.0f);
            context.getMatrices().scale(0.75f, 0.75f, 1.0f);
            context.drawItem(item, 0, 0);
            context.drawStackOverlay(mod.mc().textRenderer, item, 0, 0);
            context.getMatrices().pop();
        }
        context.getMatrices().pop();
    }

    /**
     * Handles a click on the party code in the ESC overlay.
     *
     * @return {@code true} when the click copied the code and should not reach
     * the vanilla screen
     */
    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        CommunicationMod mod = CommunicationMod.get();
        if (mod == null || mod.mc().player == null || mod.mc().world == null) {
            return false;
        }
        PartyManager party = mod.party();
        if (party == null || !party.isActive()) {
            return false;
        }
        String code = party.getCode();
        if (code == null || code.isBlank()) {
            return false;
        }

        List<PartyMember> members = new ArrayList<>();
        for (PartyMember member : party.getMembersForDisplay()) {
            if (!member.isSelf()) {
                members.add(member);
            }
        }
        Geometry geometry = geometry(members.size(), mod.mc().getWindow().getScaledWidth(),
                mod.mc().getWindow().getScaledHeight());
        if (geometry.scale() <= 0.05f) {
            return false;
        }

        float localMouseX = (float) ((mouseX - geometry.originX()) / geometry.scale());
        float localMouseY = (float) ((mouseY - geometry.originY()) / geometry.scale());
        float codeWidth = width(code, CODE_SIZE);
        float codeX = PANEL_WIDTH - 6.0f - codeWidth;
        if (!isInside(localMouseX, localMouseY, codeX - 2.0f, 2.0f, codeWidth + 4.0f, 12.0f)) {
            return false;
        }

        mod.mc().keyboard.setClipboard(code);
        copiedCode = code;
        copiedAt = System.currentTimeMillis();
        ChatUtil.send("&aКод пати скопирован: &f" + code);
        return true;
    }

    private static Geometry geometry(int otherMembers, int screenWidth, int screenHeight) {
        int shown = Math.min(otherMembers, MAX_MEMBERS);
        int hidden = otherMembers - shown;
        int contentHeight = shown == 0
                ? EMPTY_HEIGHT
                : shown * CARD_HEIGHT + (hidden > 0 ? 12 : 0);
        int totalHeight = HEADER_HEIGHT + contentHeight + 6;
        float heightScale = Math.min(1.0f, (screenHeight - MARGIN * 2.0f) / totalHeight);
        float widthScale = Math.min(1.0f, (screenWidth / 2.0f - 108.0f - MARGIN) / PANEL_WIDTH);
        float scale = Math.min(heightScale, widthScale);
        float originX = MARGIN;
        float originY = Math.max(MARGIN, (screenHeight - totalHeight * scale) / 2.0f);
        return new Geometry(scale, originX, originY, shown, hidden, totalHeight);
    }

    private static void drawHeader(DrawContext context, PartyManager party, int memberCount,
                                   int primary, int text, int muted, float mouseX, float mouseY) {
        drawText(context, "ПАТИ", 6.0f, textY("ПАТИ", HEADER_SIZE, 9.5f), HEADER_SIZE, primary);

        String code = party.getCode();
        if (code == null) {
            code = "";
        }
        float codeWidth = width(code, CODE_SIZE);
        float codeX = PANEL_WIDTH - 6.0f - codeWidth;
        drawText(context, code, codeX, textY(code, CODE_SIZE, 9.5f), CODE_SIZE, text);
        float labelWidth = width("КОД", LABEL_SIZE);
        drawText(context, "КОД", codeX - 4.0f - labelWidth,
                textY("КОД", LABEL_SIZE, 9.5f), LABEL_SIZE, muted);

        boolean hovered = isInside(mouseX, mouseY, codeX - 2.0f, 2.0f, codeWidth + 4.0f, 12.0f);
        if (hovered) {
            context.fill((int) (codeX - 1.0f), 14, (int) (codeX + codeWidth + 1.0f), 15, primary);
            drawRightAligned(context, "ЛКМ: копировать", PANEL_WIDTH - 6.0f, 18.5f, LABEL_SIZE, primary);
        } else if (code.equals(copiedCode) && System.currentTimeMillis() - copiedAt < 1_500L) {
            drawRightAligned(context, "Скопировано!", PANEL_WIDTH - 6.0f, 18.5f, LABEL_SIZE, COPIED_COLOR);
        }

        drawText(context, "В пати: " + memberCount, 6.0f,
                textY("В пати: " + memberCount, 6.5f, 18.5f), 6.5f, muted);
    }

    private static void drawMember(DrawContext context, PartyMember member, int cardY,
                                   int text, int muted, float mouseX, float mouseY) {
        PlayerSkinDrawer.draw(context, skin(member), GRID_LEFT, cardY + 4, SLOT_SIZE);

        float health = Math.max(0.0f, member.getHealth());
        float maxHealth = Math.max(1.0f, member.getMaxHealth());
        float absorption = Math.max(0.0f, member.getAbsorption());
        String healthText = String.format(Locale.US, "%.1f/%.0f", health, maxHealth);
        if (absorption > 0.01f) {
            healthText = healthText + String.format(Locale.US, " +%.0f", absorption);
        }
        float healthWidth = width(healthText, HEALTH_SIZE);
        int healthColor = healthColor(health, maxHealth);
        drawText(context, healthText, PANEL_WIDTH - 6.0f - healthWidth,
                textY(healthText, HEALTH_SIZE, cardY + 9.0f), HEALTH_SIZE, healthColor);

        String name = member.getName();
        float nameWidth = Math.max(24.0f, PANEL_WIDTH - 6.0f - healthWidth - 8.0f - NAME_LEFT);
        name = trim(name, nameWidth, NAME_SIZE);
        drawText(context, name, NAME_LEFT, textY(name, NAME_SIZE, cardY + 9.0f), NAME_SIZE, text);

        int heartsEnd = drawHearts(context, cardY + HEART_ROW_Y, health, maxHealth, absorption);
        int anarchy = member.getAnarchy();
        String location = anarchy >= 0 ? "anarchy " + anarchy : dimensionLabel(member);
        if (!location.isEmpty()) {
            float locationWidth = width(location, LOCATION_SIZE);
            float locationX = PANEL_WIDTH - 6.0f - locationWidth;
            if (locationX >= heartsEnd + 4) {
                drawText(context, location, locationX,
                        textY(location, LOCATION_SIZE, cardY + HEART_ROW_Y + HEART_SIZE / 2.0f),
                        LOCATION_SIZE, anarchy >= 0 ? ANARCHY_COLOR : muted);
            }
        }
    }

    private static ItemStack drawSlot(DrawContext context, ItemStack stack, int x, int y,
                                      float mouseX, float mouseY) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        context.drawItem(stack, x, y);
        context.drawStackOverlay(CommunicationMod.get().mc().textRenderer, stack, x, y);
        return isInside(mouseX, mouseY, x, y) ? stack : ItemStack.EMPTY;
    }

    private static int drawHearts(DrawContext context, int y, float health, float maxHealth, float absorption) {
        int startX = NAME_LEFT;
        int maxIcons = (PANEL_WIDTH - 6 - startX) / HEART_SPACING;
        int healthIcons = MathHelper.ceil(maxHealth / 2.0f);
        if (healthIcons > maxIcons) {
            Identifier single = health >= 2.0f ? HEART_FULL : health > 0.0f ? HEART_HALF : HEART_CONTAINER;
            drawHeart(context, startX, y, single);
            return startX + HEART_SPACING;
        }

        for (int index = 0; index < healthIcons; index++) {
            int x = startX + index * HEART_SPACING;
            drawHeart(context, x, y, HEART_CONTAINER);
            float filled = MathHelper.clamp(health - index * 2.0f, 0.0f, 2.0f);
            if (filled >= 2.0f) {
                drawHeart(context, x, y, HEART_FULL);
            } else if (filled >= 1.0f) {
                drawHeart(context, x, y, HEART_HALF);
            }
        }

        int drawn = healthIcons;
        int absorptionIcons = MathHelper.ceil(absorption / 2.0f);
        for (int index = 0; index < absorptionIcons && drawn < maxIcons; index++, drawn++) {
            int x = startX + drawn * HEART_SPACING;
            float filled = MathHelper.clamp(absorption - index * 2.0f, 0.0f, 2.0f);
            drawHeart(context, x, y, filled >= 2.0f ? HEART_ABSORBING_FULL : HEART_ABSORBING_HALF);
        }
        return startX + drawn * HEART_SPACING;
    }

    private static void drawHeart(DrawContext context, int x, int y, Identifier sprite) {
        context.drawGuiTexture(RenderLayer::getGuiTextured, sprite, x, y, HEART_SIZE, HEART_SIZE);
    }

    private static String dimensionLabel(PartyMember member) {
        String dimension = member.getDimension();
        if (dimension == null || dimension.isBlank()) {
            return "";
        }
        int colon = dimension.indexOf(':');
        String shortDimension = colon >= 0 ? dimension.substring(colon + 1) : dimension;
        shortDimension = shortDimension.replace('_', ' ');
        if (shortDimension.length() > 12) {
            shortDimension = shortDimension.substring(0, 12);
        }
        return shortDimension;
    }

    private static int healthColor(float health, float maxHealth) {
        float ratio = MathHelper.clamp(health / maxHealth, 0.0f, 1.0f);
        if (ratio > 0.6f) {
            return ColorUtil.convertToARGB(95, 220, 115, 255);
        }
        if (ratio > 0.3f) {
            return ColorUtil.convertToARGB(245, 205, 75, 255);
        }
        return ColorUtil.convertToARGB(245, 85, 85, 255);
    }

    private static SkinTextures skin(PartyMember member) {
        CommunicationMod mod = CommunicationMod.get();
        try {
            UUID uuid = UUID.fromString(member.getUuid());
            if (mod != null && mod.mc().getNetworkHandler() != null) {
                PlayerListEntry entry = mod.mc().getNetworkHandler().getPlayerListEntry(uuid);
                if (entry != null) {
                    return entry.getSkinTextures();
                }
            }
            return DefaultSkinHelper.getSkinTextures(uuid);
        } catch (Exception ignored) {
            return DefaultSkinHelper.getSkinTextures(mod == null ? new UUID(0L, 0L) : mod.mc().player.getUuid());
        }
    }

    private static void drawRightAligned(DrawContext context, String value, float right, float centerY,
                                         float size, int color) {
        drawText(context, value, right - width(value, size), textY(value, size, centerY), size, color);
    }

    private static void drawText(DrawContext context, String value, float x, float y, float size, int color) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Fonts.gt().draw(context.getMatrices(), value, x + 0.75f, y + 0.75f, size, SHADOW_COLOR);
        Fonts.gt().draw(context.getMatrices(), value, x, y, size, color);
    }

    private static float width(String value, float size) {
        return value == null || value.isEmpty() ? 0.0f : Fonts.gt().width(value, size);
    }

    private static float textY(String value, float size, float centerY) {
        return Fonts.gt().textY(value, size, centerY);
    }

    private static boolean isInside(float mouseX, float mouseY, int x, int y) {
        return isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private static boolean isInside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String trim(String value, float maxWidth, float size) {
        if (width(value, size) <= maxWidth) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            if (width(builder.toString() + value.charAt(index) + "...", size) > maxWidth) {
                break;
            }
            builder.append(value.charAt(index));
        }
        return builder.append("...").toString();
    }

    private record Geometry(float scale, float originX, float originY, int shown, int hidden, int totalHeight) {
    }
}
