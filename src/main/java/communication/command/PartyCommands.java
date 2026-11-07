package communication.command;

import communication.CommunicationMod;
import communication.config.CommunicationConfig;
import communication.party.PartyManager;
import communication.party.PartyMember;
import communication.util.ChatUtil;

import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code .party} dot-command before the chat message is sent. This
 * avoids registering a Brigadier command on servers that would reject it and
 * keeps the exact syntax of the original client.
 */
public final class PartyCommands {
    private PartyCommands() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * @return {@code true} when the message was a party command and must not
     * reach the server
     */
    public static boolean handle(String message) {
        if (message == null) {
            return false;
        }
        String trimmed = message.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(".party")) {
            return false;
        }
        if (trimmed.length() > 6 && !Character.isWhitespace(trimmed.charAt(6))) {
            return false;
        }
        CommunicationMod mod = CommunicationMod.get();
        if (mod == null) {
            return true;
        }
        PartyManager party = mod.party();
        if (party == null) {
            return true;
        }
        CommunicationConfig config = mod.config();
        if (config == null || !config.enabled) {
            ChatUtil.send("&cПати доступно только при включённом модуле &fCommunication&c.");
            return true;
        }

        String arguments = trimmed.substring(6).trim();
        String[] parts = arguments.isEmpty() ? new String[0] : arguments.split("\\s+");
        String action = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "create" -> create(party);
            case "join" -> {
                if (parts.length < 2) {
                    ChatUtil.send("&7Использование: &f.party join <код>");
                } else {
                    join(party, parts[1]);
                }
            }
            case "leave" -> leave(party);
            case "info" -> info(party);
            case "" -> help();
            default -> {
                ChatUtil.send("&7Неизвестная команда. &f.party &7— список команд.");
            }
        }
        return true;
    }

    private static void create(PartyManager party) {
        if (party.isActive()) {
            ChatUtil.send("&eВы уже в пати &f" + party.getCode() + "&e. Сначала выйдите: &f.party leave");
            return;
        }
        String code = party.create();
        if (code == null || code.isBlank()) {
            ChatUtil.send("&cНе удалось создать пати. Проверьте, что Communication включён.");
            return;
        }
        ChatUtil.send("&aПати создана! Код: &f&l" + code);
        ChatUtil.send("&7Передайте код друзьям: &f.party join " + code);
    }

    private static void join(PartyManager party, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (party.isActive() && party.getCode().equalsIgnoreCase(code)) {
            ChatUtil.send("&eВы уже в этой пати.");
            return;
        }
        if (!party.join(code)) {
            ChatUtil.send("&cНеверный код пати. Код состоит из 4-12 букв и цифр.");
            return;
        }
        ChatUtil.send("&aВы присоединились к пати &f&l" + party.getCode());
        ChatUtil.send("&7ESC покажет участников, HP и инвентарь.");
    }

    private static void leave(PartyManager party) {
        if (!party.isActive()) {
            ChatUtil.send("&7Вы не в пати.");
            return;
        }
        party.leave();
        ChatUtil.send("&7Вы вышли из пати.");
    }

    private static void info(PartyManager party) {
        if (!party.isActive()) {
            ChatUtil.send("&7Вы не в пати. Создайте: &f.party create &7или войдите: &f.party join <код>");
            return;
        }
        List<PartyMember> members = party.getMembersForDisplay();
        ChatUtil.send("&7Пати &f&l" + party.getCode() + " &7— участников: &f" + members.size());
        for (PartyMember member : members) {
            String name = member.getName() + (member.isSelf() ? " &8(вы)" : "");
            String health = String.format(Locale.US, "%.1f", member.getHealth());
            ChatUtil.send(" &8• &f" + name + " &7— &c" + health + " HP");
        }
    }

    private static void help() {
        ChatUtil.send("&7Команды пати:");
        ChatUtil.send(" &8• &f.party create &7— создать пати");
        ChatUtil.send(" &8• &f.party join <код> &7— войти в пати");
        ChatUtil.send(" &8• &f.party leave &7— выйти из пати");
        ChatUtil.send(" &8• &f.party info &7— участники и их HP");
        ChatUtil.send(" &8• &fМетка пати &7— клавиша в настройках Communication");
    }
}
