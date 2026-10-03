package no.novex.companion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
final class VersionUi {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("novex_companion", "icon.png");
    static void logo(GuiGraphicsExtractor context, int x, int y) { context.blit(RenderPipelines.GUI_TEXTURED, ICON, x, y, 0, 0, 12, 12, 12, 12); }
    static void disconnect(net.minecraft.client.Minecraft client,net.minecraft.client.gui.screens.Screen screen) { client.disconnect(screen, false); }
}
