package communication.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Snapshot of one party member. The object is only touched from the client
 * thread: states arrive through {@code MinecraftClient.execute}, the command
 * runs on the client thread and the overlay is rendered on it as well.
 */
public final class PartyMember {
    public static final int INVENTORY_SIZE = 41;

    private final String uuid;
    private String name;
    private final boolean self;
    private String server = "";
    private String dimension = "";
    private int anarchy = -1;
    private float health = 20.0f;
    private float maxHealth = 20.0f;
    private float absorption;
    private long lastSeen;
    private final ItemStack[] inventory = new ItemStack[INVENTORY_SIZE];

    public PartyMember(String uuid, String name, boolean self) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name == null ? "" : name;
        this.self = self;
        Arrays.fill(this.inventory, ItemStack.EMPTY);
    }

    public void read(JsonObject object, boolean withItems) {
        if (object == null) {
            return;
        }
        if (object.has("name") && !object.get("name").isJsonNull()) {
            this.name = object.get("name").getAsString();
        }
        if (object.has("server") && !object.get("server").isJsonNull()) {
            this.server = object.get("server").getAsString();
        }
        if (object.has("dimension") && !object.get("dimension").isJsonNull()) {
            this.dimension = object.get("dimension").getAsString();
        }
        if (object.has("anarchy") && !object.get("anarchy").isJsonNull()) {
            this.anarchy = object.get("anarchy").getAsInt();
        }
        if (object.has("hp") && !object.get("hp").isJsonNull()) {
            this.health = object.get("hp").getAsFloat();
        }
        if (object.has("maxHp") && !object.get("maxHp").isJsonNull()) {
            this.maxHealth = Math.max(1.0f, object.get("maxHp").getAsFloat());
        }
        if (object.has("absorption") && !object.get("absorption").isJsonNull()) {
            this.absorption = object.get("absorption").getAsFloat();
        }
        if (withItems && object.has("items") && object.get("items").isJsonArray()) {
            Arrays.fill(this.inventory, ItemStack.EMPTY);
            JsonArray items = object.getAsJsonArray("items");
            for (JsonElement element : items) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject itemObject = element.getAsJsonObject();
                int slot = itemObject.has("s") ? itemObject.get("s").getAsInt() : -1;
                if (slot < 0 || slot >= INVENTORY_SIZE) {
                    continue;
                }
                String id = itemObject.has("i") ? itemObject.get("i").getAsString() : "";
                int count = itemObject.has("c") ? itemObject.get("c").getAsInt() : 1;
                ItemStack stack = decodeStack(itemObject);
                if (stack == null) {
                    Identifier identifier = Identifier.tryParse(id);
                    if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                        continue;
                    }
                    Item item = Registries.ITEM.get(identifier);
                    if (item == null) {
                        continue;
                    }
                    stack = new ItemStack(item);
                }
                stack.setCount(Math.max(1, Math.min(count, 99)));
                this.inventory[slot] = stack;
            }
        }
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * Restores the complete serialized stack (components, NBT, custom name,
     * custom model data) so item models from the active resource pack are
     * displayed exactly as the sender sees them.
     */
    private static ItemStack decodeStack(JsonObject itemObject) {
        if (!itemObject.has("n") || itemObject.get("n").isJsonNull()) {
            return null;
        }
        String encoded = itemObject.get("n").getAsString();
        if (encoded.isBlank()) {
            return null;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                return null;
            }
            byte[] data = Base64.getDecoder().decode(encoded);
            NbtCompound compound = NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(data)),
                    NbtSizeTracker.of(2L * 1024L * 1024L));
            Optional<ItemStack> decoded = ItemStack.fromNbt(client.world.getRegistryManager(), compound);
            return decoded.filter(stack -> !stack.isEmpty()).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getName() {
        return this.name;
    }

    public boolean isSelf() {
        return this.self;
    }

    public String getServer() {
        return this.server;
    }

    public String getDimension() {
        return this.dimension;
    }

    public int getAnarchy() {
        return this.anarchy;
    }

    public float getHealth() {
        return this.health;
    }

    public float getMaxHealth() {
        return this.maxHealth;
    }

    public float getAbsorption() {
        return this.absorption;
    }

    public long getLastSeen() {
        return this.lastSeen;
    }

    public ItemStack getStack(int slot) {
        return slot < 0 || slot >= INVENTORY_SIZE ? ItemStack.EMPTY : this.inventory[slot];
    }

    public boolean isFresh(long now, long timeout) {
        return this.self || now - this.lastSeen <= timeout;
    }
}
