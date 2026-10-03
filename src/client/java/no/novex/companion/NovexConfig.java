package no.novex.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only local preferences. No identities, credentials, server addresses or session data. */
public final class NovexConfig implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("Novex Companion");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public enum Option {
        MESSAGE_NOTIFICATIONS("messageNotifications", "Message notifications", "Social", true),
        REQUEST_NOTIFICATIONS("friendRequestNotifications", "Friend request notifications", "Social", true),
        ONLINE_NOTIFICATIONS("friendOnlineNotifications", "Friend online notifications", "Social", true),
        ONLINE_STATUS("showOnlineStatus", "Show online status", "Privacy", true),
        SHARE_SERVER("shareCurrentServer", "Share current server", "Privacy", false),
        ALLOW_JOIN("allowFriendsToJoin", "Allow friends to join", "Privacy", false),
        NAMETAG_BADGES("badgesAbovePlayers", "Badges above players", "Badges", true),
        TAB_BADGES("badgesInTab", "Badges in TAB", "Badges", true),
        CHAT_BADGES("badgesInChat", "Badges in chat", "Badges", true),
        UNREAD_COUNT("showUnreadCount", "Unread count on Novex button", "Interface", true);
        public final String key, label, section;
        public final boolean defaultValue;
        Option(String key, String label, String section, boolean defaultValue) {
            this.key = key; this.label = label; this.section = section; this.defaultValue = defaultValue;
        }
    }
    private final Path path;
    private final Map<String, Boolean> values = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Novex preferences"); thread.setDaemon(true); return thread;
    });
    private volatile String saveError = "";
    private boolean damagedFile;

    public NovexConfig(Path path) {
        this.path = path;
        for (Option option : Option.values()) values.put(option.key, option.defaultValue);
        if (Files.exists(path)) {
            try {
                if (Files.size(path) > 16384) throw new IOException("Preferences file exceeds size limit");
                JsonObject object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                for (Option option : Option.values()) {
                    var value = object.get(option.key);
                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean())
                        values.put(option.key, value.getAsBoolean());
                }
            } catch (IOException | RuntimeException e) {
                damagedFile = true;
                saveError = "Could not read preferences. Using defaults.";
                LOG.warn("Unable to read Novex preferences ({}). Using defaults.", e.getClass().getSimpleName());
            }
        }
        enforcePrivacy();
    }
    public boolean get(Option option) { return values.get(option.key); }
    public String saveError() { return saveError; }
    public void toggle(Option option) {
        values.put(option.key, !get(option));
        enforcePrivacy();
        String snapshot = JSON.toJson(values) + "\n";
        writer.execute(() -> save(snapshot));
    }
    private void enforcePrivacy() {
        if (!get(Option.ONLINE_STATUS)) values.put(Option.SHARE_SERVER.key, false);
        if (!get(Option.SHARE_SERVER)) values.put(Option.ALLOW_JOIN.key, false);
    }
    private void save(String snapshot) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            if (damagedFile && Files.exists(path)) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".invalid-" + System.currentTimeMillis()));
                damagedFile = false;
            }
            Files.writeString(temporary, snapshot, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            saveError = "";
        } catch (IOException e) {
            saveError = "Unable to save preferences. Check folder permissions.";
            LOG.warn("Unable to save Novex preferences ({}).", e.getClass().getSimpleName());
        }
    }
    @Override public void close() {
        writer.shutdown();
        try { if (!writer.awaitTermination(2, TimeUnit.SECONDS)) LOG.warn("Novex preferences are still saving."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
