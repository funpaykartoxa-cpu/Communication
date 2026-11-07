package communication.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import communication.CommunicationMod;
import communication.config.CommunicationConfig;
import communication.network.BackendClient;
import communication.render.ColorUtil;
import communication.render.Fonts;
import communication.render.RoundedRenderer;
import communication.render.Theme;
import communication.util.ChatUtil;
import communication.util.ServerUtil;
import communication.util.SoundUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side party channel. The backend broker is a broadcast channel, so a
 * party is a shared secret: every member encrypts their state with a key
 * derived from the party code. Clients that do not know the code cannot read
 * the payload.
 */
public final class PartyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("communication");
    private static final String PACKET_ID = "party";
    private static final int STATE_INTERVAL_TICKS = 20;
    private static final long FULL_STATE_INTERVAL_MS = 5_000L;
    private static final long MEMBER_TIMEOUT_MS = 7_000L;
    private static final long JOIN_RESPONSE_INTERVAL_MS = 2_000L;
    private static final long MARK_DURATION_MS = 5_000L;
    private static final double MARK_RAY_DISTANCE = 128.0d;
    private static final double OCCLUDED_LINE_HEIGHT = 48.0d;
    private static final double OCCLUDED_LINE_RADIUS = 0.12d;
    private static final double CIRCLE_OUTER_RADIUS = 0.55d;
    private static final double CIRCLE_INNER_RADIUS = 0.40d;
    private static final int CIRCLE_SEGMENTS = 64;
    private static final double MARKER_HEIGHT = 2.15d;
    private static final float MARKER_TEXT_SIZE = 6.25f;
    private static final float MARKER_PILL_HEIGHT = 11.5f;
    private static final float MARKER_RADIUS = 58.0f;
    private static final int MARK_COLOR = 0xFFFF3C3C;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<String, PartyMember> members = new LinkedHashMap<>();
    private final List<PartyMark> marks = new ArrayList<>();
    private String code = "";
    private String selfUuid = "";
    private String selfName = "";
    private long ticks;
    private long lastFullStateAt;
    private long lastJoinResponseAt;
    private String lastInventorySignature = "";

    public boolean isCommunicationEnabled() {
        CommunicationConfig config = CommunicationMod.get() == null ? null : CommunicationMod.get().config();
        return config != null && config.enabled;
    }

    public boolean isActive() {
        return isCommunicationEnabled() && !this.code.isEmpty();
    }

    public String getCode() {
        return this.code;
    }

    public boolean isMember(UUID uuid) {
        if (uuid == null || !isActive()) {
            return false;
        }
        String id = uuid.toString();
        return id.equals(this.selfUuid) || this.members.containsKey(id);
    }

    public String create() {
        if (!isCommunicationEnabled()) {
            return "";
        }
        this.code = PartyCrypto.generateCode();
        this.members.clear();
        this.marks.clear();
        this.lastInventorySignature = "";
        this.lastFullStateAt = 0L;
        updateSelf();
        sendState(true);
        LOGGER.info("Party created: {}", this.code);
        return this.code;
    }

    public boolean join(String rawCode) {
        if (!isCommunicationEnabled()) {
            return false;
        }
        String normalized = PartyCrypto.normalize(rawCode);
        if (!normalized.matches("[A-Z0-9]{4,12}")) {
            return false;
        }
        if (isActive()) {
            if (this.selfUuid.isBlank()) {
                updateSelf();
            }
            sendJson(basePacket("leave").toString());
        }
        this.code = normalized;
        this.members.clear();
        this.marks.clear();
        this.lastInventorySignature = "";
        this.lastFullStateAt = 0L;
        updateSelf();
        sendJoin();
        sendState(true);
        LOGGER.info("Party joined: {}", this.code);
        return true;
    }

    public void leave() {
        if (!isActive()) {
            return;
        }
        if (this.selfUuid.isBlank()) {
            updateSelf();
        }
        JsonObject packet = basePacket("leave");
        sendJson(packet.toString());
        this.code = "";
        this.members.clear();
        this.marks.clear();
        this.lastInventorySignature = "";
        this.lastFullStateAt = 0L;
    }

    public List<PartyMember> getMembersForDisplay() {
        if (this.mc.player != null && !this.members.containsKey(this.mc.player.getUuidAsString())) {
            updateSelf();
        }
        List<PartyMember> list = new ArrayList<>(this.members.values());
        list.sort(Comparator.comparingInt((PartyMember member) -> member.isSelf() ? 0 : 1)
                .thenComparing(PartyMember::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public int memberCount() {
        return this.members.size();
    }

    /**
     * Sends a one-shot mark to the block under the player's crosshair. The
     * mark is rendered for five seconds as a pulsing ring on every member's
     * screen and then disappears on its own.
     */
    public boolean sendMark() {
        if (!isActive() || this.mc.player == null || this.mc.world == null) {
            return false;
        }
        Vec3d eye = this.mc.player.getEyePos();
        Vec3d end = eye.add(this.mc.player.getRotationVec(1.0f).multiply(MARK_RAY_DISTANCE));
        BlockHitResult hit = this.mc.world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, this.mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            ChatUtil.send("&cНаведитесь на блок, чтобы отправить метку");
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        long now = System.currentTimeMillis();
        JsonObject packet = basePacket("mark");
        packet.addProperty("x", pos.getX());
        packet.addProperty("y", pos.getY());
        packet.addProperty("z", pos.getZ());
        sendJson(packet.toString());
        String name = this.selfName.isBlank() ? this.mc.player.getGameProfile().getName() : this.selfName;
        this.marks.add(new PartyMark(name, pos.getX(), pos.getY(), pos.getZ(), now + MARK_DURATION_MS));
        LOGGER.info("Party mark sent at block {}, {}, {}", pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    public void tick() {
        this.ticks++;
        long now = System.currentTimeMillis();
        this.marks.removeIf(mark -> mark.expiresAt() <= now);
        if (!isActive() || this.mc.player == null) {
            return;
        }
        if (this.ticks % STATE_INTERVAL_TICKS != 0L) {
            return;
        }
        sendState(false);
        pruneMembers();
    }

    public void onPacket(String payload) {
        if (!isActive()) {
            return;
        }
        try {
            JsonElement outerElement = JsonParser.parseString(payload);
            if (!outerElement.isJsonObject()) {
                return;
            }
            JsonObject outer = outerElement.getAsJsonObject();
            String encoded = outer.has("d") && !outer.get("d").isJsonNull() ? outer.get("d").getAsString() : "";
            if (encoded.isBlank()) {
                return;
            }
            String decoded = PartyCrypto.decrypt(this.code, encoded);
            JsonElement innerElement = JsonParser.parseString(decoded);
            if (!innerElement.isJsonObject()) {
                return;
            }
            JsonObject inner = innerElement.getAsJsonObject();
            String type = inner.has("type") ? inner.get("type").getAsString() : "";
            switch (type) {
                case "state" -> handleState(inner);
                case "join" -> handleJoin(inner);
                case "leave" -> handleLeave(inner);
                case "mark" -> handleMark(inner);
                default -> {
                }
            }
        } catch (Exception ignored) {
            // Packets from other parties fail authentication and are ignored.
        }
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isActive() || this.mc.player == null || this.mc.world == null) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        if (matrices == null || camera == null) {
            return;
        }
        long now = System.currentTimeMillis();
        this.marks.removeIf(mark -> mark.expiresAt() <= now);
        if (this.marks.isEmpty()) {
            return;
        }
        for (PartyMark mark : this.marks) {
            long remaining = mark.expiresAt() - now;
            if (remaining <= 0L) {
                continue;
            }
            float life = MathHelper.clamp(remaining / (float) MARK_DURATION_MS, 0.0f, 1.0f);
            float fade = Math.min(1.0f, life * 3.0f);
            float pulse = 0.8f + 0.2f * (float) Math.sin(now / 90.0d);
            float alpha = MathHelper.clamp(fade * pulse, 0.05f, 1.0f);
            if (isMarkOccluded(mark)) {
                drawOccludedBeam(matrices, mark, camera, ColorUtil.applyAlphaToColor(MARK_COLOR, alpha * 0.65f));
            }
            drawMarkCircle(matrices, mark, camera, ColorUtil.applyAlphaToColor(MARK_COLOR, alpha), now);
        }
    }

    private boolean isMarkOccluded(PartyMark mark) {
        try {
            Vec3d start = this.mc.gameRenderer.getCamera().getPos();
            Vec3d end = new Vec3d(mark.x() + 0.5d, mark.y() + 0.9d, mark.z() + 0.5d);
            BlockHitResult hit = this.mc.world.raycast(new RaycastContext(start, end,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, this.mc.player));
            return hit.getType() == HitResult.Type.BLOCK
                    && !hit.getBlockPos().equals(new BlockPos(mark.x(), mark.y(), mark.z()));
        } catch (Exception exception) {
            return false;
        }
    }

    private void drawOccludedBeam(MatrixStack matrices, PartyMark mark, Camera camera, int color) {
        double centerX = mark.x() + 0.5d - camera.getPos().x;
        double centerZ = mark.z() + 0.5d - camera.getPos().z;
        double minY = mark.y() - camera.getPos().y;
        double maxY = minY + OCCLUDED_LINE_HEIGHT;
        float minX = (float) (centerX - OCCLUDED_LINE_RADIUS);
        float maxX = (float) (centerX + OCCLUDED_LINE_RADIUS);
        float minZ = (float) (centerZ - OCCLUDED_LINE_RADIUS);
        float maxZ = (float) (centerZ + OCCLUDED_LINE_RADIUS);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        // Four thin side faces plus caps, drawn as one translucent beam.
        side(buffer, matrix, minX, (float) minY, minZ, maxX, (float) minY, maxZ, (float) maxY, color);
        side(buffer, matrix, maxX, (float) minY, minZ, minX, (float) minY, maxZ, (float) maxY, color);
        side(buffer, matrix, minX, (float) minY, minZ, maxX, (float) minY, minZ, (float) maxY, color);
        side(buffer, matrix, minX, (float) minY, maxZ, maxX, (float) minY, maxZ, (float) maxY, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void side(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1,
                      float x2, float y2, float z2, float topY, int color) {
        buffer.vertex(matrix, x1, y1, z1).color(color);
        buffer.vertex(matrix, x2, y2, z2).color(color);
        buffer.vertex(matrix, x2, topY, z2).color(color);
        buffer.vertex(matrix, x1, topY, z1).color(color);
    }

    private void drawMarkCircle(MatrixStack matrices, PartyMark mark, Camera camera, int color, long now) {
        Vec3d cameraPos = camera.getPos();
        float centerX = (float) ((mark.x() + 0.5d) - cameraPos.x);
        float centerY = (float) ((mark.y() + 1.02d) - cameraPos.y);
        float centerZ = (float) ((mark.z() + 0.5d) - cameraPos.z);
        float pulse = 1.0f + 0.05f * (float) Math.sin(now / 240.0d);
        float outer = (float) (CIRCLE_OUTER_RADIUS * pulse);
        float inner = (float) (CIRCLE_INNER_RADIUS * pulse);
        int[] rgba = ColorUtil.b(color);
        float red = rgba[0] / 255.0f;
        float green = rgba[1] / 255.0f;
        float blue = rgba[2] / 255.0f;
        float alpha = rgba[3] / 255.0f;
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int index = 0; index <= CIRCLE_SEGMENTS; index++) {
            double angle = (Math.PI * 2.0d * index) / CIRCLE_SEGMENTS;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            fill.vertex(matrix, centerX + (cos * inner), centerY, centerZ + (sin * inner))
                    .color(red, green, blue, alpha * 0.35f);
            fill.vertex(matrix, centerX + (cos * outer), centerY, centerZ + (sin * outer))
                    .color(red, green, blue, alpha * 0.9f);
        }
        BufferRenderer.drawWithGlobalProgram(fill.end());

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        RenderSystem.lineWidth(2.0f);
        BufferBuilder outline = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int index = 0; index < CIRCLE_SEGMENTS; index++) {
            double a0 = (Math.PI * 2.0d * index) / CIRCLE_SEGMENTS;
            double a1 = (Math.PI * 2.0d * (index + 1)) / CIRCLE_SEGMENTS;
            outline.vertex(matrix, centerX + ((float) Math.cos(a0) * outer), centerY, centerZ + ((float) Math.sin(a0) * outer))
                    .color(red, green, blue, alpha);
            outline.vertex(matrix, centerX + ((float) Math.cos(a1) * outer), centerY, centerZ + ((float) Math.sin(a1) * outer))
                    .color(red, green, blue, alpha);
        }
        BufferRenderer.drawWithGlobalProgram(outline.end());
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.lineWidth(1.0f);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void renderMarkerLabels(DrawContext context) {
        if (!isActive() || this.mc.player == null || this.mc.world == null) {
            return;
        }
        if (this.mc.options.hudHidden || this.mc.player.isSpectator()) {
            return;
        }
        long now = System.currentTimeMillis();
        this.marks.removeIf(mark -> mark.expiresAt() <= now);
        for (PartyMark mark : this.marks) {
            drawMarkerLabel(context, mark);
        }
    }

    private void drawMarkerLabel(DrawContext context, PartyMark mark) {
        double centerX = mark.x() + 0.5d;
        double centerZ = mark.z() + 0.5d;
        double targetY = mark.y() + MARKER_HEIGHT;
        double distance = this.mc.player.getEyePos().distanceTo(new Vec3d(centerX, targetY, centerZ));
        double dx = centerX - this.mc.player.getX();
        double dz = centerZ - this.mc.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float angle = MathHelper.wrapDegrees(targetYaw - this.mc.player.getYaw());
        float radians = (float) Math.toRadians(angle);
        float screenX = (this.mc.getWindow().getScaledWidth() / 2.0f) + ((float) Math.sin(radians) * MARKER_RADIUS);
        float screenY = (this.mc.getWindow().getScaledHeight() / 2.0f) - ((float) Math.cos(radians) * MARKER_RADIUS);

        String suffix = String.format(Locale.US, "%.1fм", distance);
        Text text = Text.literal(mark.name().toUpperCase(Locale.ROOT))
                .append(Text.literal("  /  ").setStyle(Style.EMPTY.withColor(Theme.PRIMARY)))
                .append(Text.literal(suffix));
        float width = 14.0f + Fonts.gt().width(text, MARKER_TEXT_SIZE);
        float scaledWidth = this.mc.getWindow().getScaledWidth();
        float x = MathHelper.clamp(screenX - (width / 2.0f), 2.0f, Math.max(2.0f, scaledWidth - width - 2.0f));
        float y = screenY - (MARKER_PILL_HEIGHT / 2.0f);

        RoundedRenderer.fill(context.getMatrices(), x, y, width, MARKER_PILL_HEIGHT, 3.5f,
                ColorUtil.applyAlphaToColor(Theme.BACKGROUND_HUD, 0.92f));
        Fonts.icons().draw(context.getMatrices(), "F", x + 3.0f, y + 3.0f, 5.5f, Theme.PRIMARY);
        Fonts.gt().draw(context.getMatrices(), text, x + 3.0f + 5.5f + 2.5f,
                y + ((MARKER_PILL_HEIGHT - MARKER_TEXT_SIZE) / 2.0f) - 0.25f, MARKER_TEXT_SIZE);
    }

    private void handleState(JsonObject object) {
        String uuid = string(object, "uuid");
        if (uuid.isBlank() || uuid.equals(this.selfUuid)) {
            return;
        }
        String name = string(object, "name");
        if (name.isBlank()) {
            name = "?";
        }
        String memberName = name;
        boolean isNew = !this.members.containsKey(uuid);
        PartyMember member = this.members.computeIfAbsent(uuid, key -> new PartyMember(key, memberName, false));
        member.read(object, true);
        if (isNew) {
            ChatUtil.send("&a" + name + " &7присоединился к пати");
            respondWithState();
        }
    }

    private void handleMark(JsonObject object) {
        String uuid = string(object, "uuid");
        if (uuid.isBlank() || uuid.equals(this.selfUuid)) {
            return;
        }
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        String dimension = string(object, "dimension");
        String currentDimension = this.mc.world.getRegistryKey().getValue().toString();
        if (!dimension.isBlank() && !dimension.equals(currentDimension)) {
            return;
        }
        if (!markOnSameServer(string(object, "server"), integer(object, "anarchy", -1))) {
            return;
        }
        int x = integer(object, "x", Integer.MIN_VALUE);
        int y = integer(object, "y", Integer.MIN_VALUE);
        int z = integer(object, "z", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            return;
        }
        long now = System.currentTimeMillis();
        String name = string(object, "name");
        if (name.isBlank()) {
            name = "?";
        }
        this.marks.add(new PartyMark(name, x, y, z, now + MARK_DURATION_MS));
        SoundUtil.play("party_mark.wav", markSoundVolume());
        LOGGER.info("Party mark from {} at block {}, {}, {}", name, x, y, z);
    }

    private boolean markOnSameServer(String server, int anarchy) {
        String currentServer = ServerUtil.serverAddress();
        if (!server.isBlank() && !currentServer.isBlank() && !ServerUtil.sameServer(server, currentServer)) {
            return false;
        }
        int currentAnarchy = ServerUtil.anarchy();
        return anarchy < 0 || currentAnarchy < 0 || anarchy == currentAnarchy;
    }

    private float markSoundVolume() {
        CommunicationConfig config = CommunicationMod.get() == null ? null : CommunicationMod.get().config();
        return config == null ? 0.8f : MathHelper.clamp(config.partyMarkVolume, 0.0f, 1.0f);
    }

    private void handleJoin(JsonObject object) {
        String uuid = string(object, "uuid");
        if (uuid.isBlank() || uuid.equals(this.selfUuid)) {
            return;
        }
        String name = string(object, "name");
        if (name.isBlank()) {
            name = "?";
        }
        String memberName = name;
        boolean isNew = !this.members.containsKey(uuid);
        PartyMember member = this.members.computeIfAbsent(uuid, key -> new PartyMember(key, memberName, false));
        member.read(object, false);
        if (isNew) {
            ChatUtil.send("&a" + name + " &7присоединился к пати");
        }
        respondWithState();
    }

    private void handleLeave(JsonObject object) {
        String uuid = string(object, "uuid");
        if (uuid.isBlank() || uuid.equals(this.selfUuid)) {
            return;
        }
        PartyMember removed = this.members.remove(uuid);
        if (removed != null) {
            ChatUtil.send("&7" + removed.getName() + " &7вышел из пати");
        }
    }

    private void respondWithState() {
        long now = System.currentTimeMillis();
        if (now - this.lastJoinResponseAt < JOIN_RESPONSE_INTERVAL_MS) {
            return;
        }
        this.lastJoinResponseAt = now;
        sendState(true);
    }

    private void pruneMembers() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PartyMember>> iterator = this.members.entrySet().iterator();
        while (iterator.hasNext()) {
            PartyMember member = iterator.next().getValue();
            if (member.isSelf() || member.isFresh(now, MEMBER_TIMEOUT_MS)) {
                continue;
            }
            iterator.remove();
            ChatUtil.send("&7" + member.getName() + " &7отключился от пати");
        }
    }

    private void sendJoin() {
        JsonObject packet = basePacket("join");
        sendJson(packet.toString());
    }

    private void sendState(boolean forceFull) {
        if (!isActive() || this.mc.player == null) {
            return;
        }
        updateSelf();
        String signature = inventorySignature();
        long now = System.currentTimeMillis();
        boolean full = forceFull
                || !signature.equals(this.lastInventorySignature)
                || now - this.lastFullStateAt >= FULL_STATE_INTERVAL_MS;
        JsonObject packet = basePacket("state");
        addSelfData(packet, full);
        sendJson(packet.toString());
        if (full) {
            this.lastInventorySignature = signature;
            this.lastFullStateAt = now;
        }
    }

    private void updateSelf() {
        if (this.mc.player == null) {
            return;
        }
        String uuid = this.mc.player.getUuidAsString();
        String name = this.mc.player.getGameProfile().getName();
        this.selfUuid = uuid;
        this.selfName = name;
        this.members.entrySet().removeIf(entry -> entry.getValue().isSelf() && !entry.getKey().equals(uuid));
        PartyMember self = this.members.get(uuid);
        if (self == null) {
            self = new PartyMember(uuid, name, true);
            this.members.put(uuid, self);
        }
        JsonObject snapshot = basePacket("state");
        addSelfData(snapshot, true);
        self.read(snapshot, true);
    }

    private void addSelfData(JsonObject packet, boolean withItems) {
        if (this.mc.player == null) {
            return;
        }
        packet.addProperty("hp", this.mc.player.getHealth());
        packet.addProperty("maxHp", this.mc.player.getMaxHealth());
        packet.addProperty("absorption", this.mc.player.getAbsorptionAmount());
        packet.addProperty("ts", System.currentTimeMillis());
        if (!withItems) {
            return;
        }
        JsonArray items = new JsonArray();
        PlayerInventory inventory = this.mc.player.getInventory();
        for (int slot = 0; slot < PartyMember.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("s", slot);
            item.addProperty("i", Registries.ITEM.getId(stack.getItem()).toString());
            item.addProperty("c", stack.getCount());
            item.addProperty("n", encodeStack(stack));
            items.add(item);
        }
        packet.add("items", items);
    }

    /**
     * Serializes the whole stack, including custom names, lore, enchantments
     * and any resource-pack component data. The payload is binary NBT encoded
     * as Base64 so custom item models from the active resource pack are
     * restored exactly on the receiving client.
     */
    private String encodeStack(ItemStack stack) {
        if (this.mc.world == null) {
            return "";
        }
        try {
            NbtElement element = stack.toNbtAllowEmpty(this.mc.world.getRegistryManager());
            if (!(element instanceof NbtCompound compound)) {
                return "";
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.writeCompound(compound, output);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception exception) {
            LOGGER.warn("Party item encode failed", exception);
            return "";
        }
    }

    private String inventorySignature() {
        if (this.mc.player == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        PlayerInventory inventory = this.mc.player.getInventory();
        for (int slot = 0; slot < PartyMember.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            builder.append(slot)
                    .append(':')
                    .append(Registries.ITEM.getId(stack.getItem()))
                    .append(':')
                    .append(stack.getCount())
                    .append(':')
                    .append(stack.getComponentChanges().hashCode())
                    .append(';');
        }
        return builder.toString();
    }

    private JsonObject basePacket(String type) {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", type);
        packet.addProperty("name", this.selfName);
        packet.addProperty("uuid", this.selfUuid);
        packet.addProperty("server", ServerUtil.serverAddress());
        packet.addProperty("anarchy", ServerUtil.anarchy());
        String dimension = ServerUtil.dimension();
        if (!dimension.isBlank()) {
            packet.addProperty("dimension", dimension);
        }
        return packet;
    }

    private void sendJson(String json) {
        if (!isActive()) {
            return;
        }
        CommunicationMod mod = CommunicationMod.get();
        if (mod == null) {
            return;
        }
        BackendClient backend = mod.backend();
        if (backend == null) {
            return;
        }
        try {
            JsonObject outer = new JsonObject();
            outer.addProperty("d", PartyCrypto.encrypt(this.code, json));
            backend.send(PACKET_ID, outer.toString());
        } catch (Exception exception) {
            LOGGER.warn("Party packet send failed", exception);
        }
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private record PartyMark(String name, int x, int y, int z, long expiresAt) {
    }
}
