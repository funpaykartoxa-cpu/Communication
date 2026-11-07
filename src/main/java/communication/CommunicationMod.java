package communication;

import communication.command.PartyCommands;
import communication.config.CommunicationConfig;
import communication.friend.FriendMarkManager;
import communication.input.CommunicationKeys;
import communication.network.BackendClient;
import communication.party.PartyManager;
import communication.party.PartyOverlay;
import communication.ui.CommunicationScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.InputUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point. Wires the party channel, friend marks, the ESC overlay
 * and the compact settings screen into the Fabric client lifecycle.
 */
public final class CommunicationMod implements ClientModInitializer {
    public static final String MOD_ID = "communication";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CommunicationMod instance;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private CommunicationConfig config;
    private PartyManager party;
    private FriendMarkManager friends;
    private BackendClient backend;
    private boolean menuDown;

    public static CommunicationMod get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        CommunicationKeys.register();
        this.config = CommunicationConfig.load();
        CommunicationKeys.applyConfig(this.config);
        this.party = new PartyManager();
        this.friends = new FriendMarkManager();
        this.backend = new BackendClient(this::onPacket, this::identity);
        this.backend.start();

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (this.config == null || !this.config.enabled) {
                return;
            }
            this.party.renderMarkerLabels(context);
            this.friends.renderLabels(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(this.party::renderWorld);
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !PartyCommands.handle(message));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.backend.close());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GameMenuScreen)) {
                return;
            }
            ScreenEvents.afterRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) -> {
                if (this.config != null && this.config.enabled) {
                    PartyOverlay.render(context, mouseX, mouseY);
                }
            });
            ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, button) ->
                    !PartyOverlay.mouseClicked(mouseX, mouseY, button));
        });

        LOGGER.info("Communication initialized (party, friend marks, ESC overlay, settings, controls key binds)");
    }

    private void tick() {
        if (this.config == null || this.party == null || this.friends == null) {
            return;
        }
        this.party.tick();
        this.friends.tick();
        CommunicationKeys.syncFromBindings(this.config);

        boolean partyPressed = CommunicationKeys.consumePartyPress();
        boolean friendPressed = CommunicationKeys.consumeFriendPress();

        boolean inWorld = this.mc.player != null && this.mc.world != null;
        if (!inWorld) {
            this.menuDown = false;
            return;
        }

        long handle = this.mc.getWindow().getHandle();
        boolean menuPressed = false;
        if (handle != 0L) {
            boolean menu = InputUtil.isKeyPressed(handle, CommunicationKeys.MENU_KEY);
            menuPressed = menu && !this.menuDown;
            this.menuDown = menu;
        } else {
            this.menuDown = false;
        }

        if (menuPressed && this.mc.currentScreen == null) {
            this.mc.setScreen(new CommunicationScreen());
            return;
        }
        if (this.mc.currentScreen != null) {
            return;
        }
        if (!this.config.enabled) {
            return;
        }

        if (partyPressed) {
            if (this.party.isActive()) {
                this.party.sendMark();
            } else {
                communication.util.ChatUtil.send("&7Вы не в пати.");
            }
        }
        if (friendPressed) {
            this.friends.sendMark();
        }
    }

    private void onPacket(BackendClient.Packet packet) {
        if (packet == null || this.config == null || !this.config.enabled) {
            return;
        }
        switch (packet.id()) {
            case "party" -> this.party.onPacket(packet.payload());
            case "friend" -> this.friends.onPacket(packet.payload());
            default -> {
            }
        }
    }

    private String identity() {
        String username = this.mc.getSession() == null ? "" : this.mc.getSession().getUsername();
        if (username == null || username.isBlank()) {
            return "communication";
        }
        return username.trim();
    }

    public MinecraftClient mc() {
        return this.mc;
    }

    public CommunicationConfig config() {
        return this.config;
    }

    public PartyManager party() {
        return this.party;
    }

    public FriendMarkManager friends() {
        return this.friends;
    }

    public BackendClient backend() {
        return this.backend;
    }
}
