package no.novex.companion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

final class VersionUi {
    private static final Identifier ICON = Identifier.of("novex_companion", "icon.png");
    static void logo(DrawContext context, int x, int y) { context.drawTexture(ICON, x, y, 0, 0, 12, 12, 12, 12); }
    static void disconnect(net.minecraft.client.MinecraftClient client,net.minecraft.client.gui.screen.Screen screen) { client.disconnect(screen); }
}
