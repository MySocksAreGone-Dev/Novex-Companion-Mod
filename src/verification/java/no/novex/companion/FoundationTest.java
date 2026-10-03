package no.novex.companion;

import java.nio.file.*;
import java.util.List;
import static no.novex.companion.NovexConfig.Option.*;

/** Dependency-free assertions run by Gradle check. */
public final class FoundationTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        ApiTest.run();
        SessionPersistenceTest.run();
        var first = MenuPlacement.find(427, 240, List.of()).orElseThrow();
        require(first.x() == 153 && first.y() == 214, "Bottom center preferred");
        var next = MenuPlacement.find(427, 240, List.of(first)).orElseThrow();
        require(!next.overlaps(first), "Avoid another mod's button");
        require(MenuPlacement.find(50, 240, List.of()).isEmpty(), "Tiny viewport hides safely");
        require(MenuPlacement.find(320, 240, List.of(new MenuPlacement.Box(0, 0, 320, 240))).isEmpty(), "Never overlap a full screen widget");
        for (int width : new int[] {320, 427, 640, 1920}) {
            for (int height : new int[] {180, 240, 480, 1080}) {
                var box = MenuPlacement.find(width, height, List.of()).orElseThrow();
                require(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= width && box.y() + box.height() <= height, "Stay inside resized viewport");
            }
        }
        Path dir = Files.createTempDirectory("novex-config-test");
        try {
            Path file = dir.resolve("novex-companion.json");
            try (var config = new NovexConfig(file)) {
                require(!config.get(SHARE_SERVER) && !config.get(ALLOW_JOIN), "Sharing must default off");
                config.toggle(SHARE_SERVER); config.toggle(ALLOW_JOIN);
                require(config.get(ALLOW_JOIN), "Join allowed after explicit sharing choice");
                config.toggle(ONLINE_STATUS);
                require(!config.get(SHARE_SERVER) && !config.get(ALLOW_JOIN), "Privacy dependencies respected");
                config.toggle(TAB_BADGES);
                require(config.get(CHAT_BADGES) && config.get(NAMETAG_BADGES), "Badge settings independent");
            }
            try (var config = new NovexConfig(file)) {
                require(!config.get(TAB_BADGES) && !config.get(ONLINE_STATUS), "Settings survive restart");
            }
            Files.writeString(file, "broken json");
            try (var config = new NovexConfig(file)) {
                require(config.get(TAB_BADGES), "Corrupt config falls back safely");
                config.toggle(UNREAD_COUNT);
            }
            try (var files = Files.list(dir)) {
                require(files.anyMatch(path -> path.getFileName().toString().contains(".invalid-")), "Preserve invalid file before saving");
            }
            Files.writeString(file, "{\"shareCurrentServer\":true,\"showOnlineStatus\":false,\"messageNotifications\":\"invalid\"}");
            try (var config = new NovexConfig(file)) {
                require(!config.get(SHARE_SERVER) && config.get(MESSAGE_NOTIFICATIONS), "Reject inconsistent or wrong-type settings");
            }
        } finally {
            try (var files = Files.list(dir)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(dir);
        }
        System.out.println("Foundation checks passed: placement, resize, privacy defaults, persistence, corrupt config.");
    }
}
