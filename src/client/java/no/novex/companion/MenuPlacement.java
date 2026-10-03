package no.novex.companion;

import java.util.List;
import java.util.Optional;

/** Pure geometry: never assumes vanilla widget order or replaces another mod's controls. */
public final class MenuPlacement {
    public record Box(int x, int y, int width, int height) {
        boolean overlaps(Box other) {
            return x < other.x + other.width + 2 && x + width + 2 > other.x
                && y < other.y + other.height + 2 && y + height + 2 > other.y;
        }
    }
    private MenuPlacement() {}
    public static Optional<Box> find(int width, int height, List<Box> occupied) {
        int buttonWidth = Math.min(120, width - 12);
        if (buttonWidth < 60) return Optional.empty();
        for (int y = height - 26; y >= 6; y -= 24) {
            int center = (width - buttonWidth) / 2;
            for (int shift : new int[] {0, -24, 24, -48, 48}) {
                Box candidate = new Box(center + shift, y, buttonWidth, 20);
                if (candidate.x < 6 || candidate.x + candidate.width > width - 6) continue;
                if (occupied.stream().noneMatch(candidate::overlaps)) return Optional.of(candidate);
            }
        }
        return Optional.empty(); // No safe space: preserve every existing control.
    }
}
