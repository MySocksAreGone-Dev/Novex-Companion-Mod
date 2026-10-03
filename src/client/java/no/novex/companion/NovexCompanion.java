package no.novex.companion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class NovexCompanion implements ClientModInitializer {
    static SocialRuntime social;
    @Override public void onInitializeClient() {
        NovexConfig config = new NovexConfig(FabricLoader.getInstance().getConfigDir().resolve("novex-companion.json"));
        SupabaseApi configured;
        try { configured=SupabaseApi.configured(); } catch (IllegalStateException ignored) { configured=null; }
        final SupabaseApi api=configured;
        if(api!=null)api.restore(new SessionVault(FabricLoader.getInstance().getConfigDir().resolve("novex-companion.json"),api.serviceOrigin())).exceptionally(error->null);
        social=new SocialRuntime(api,config);
        var badges=new BadgeManager(api,config);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(badges::tick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(social::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { social.offline(); config.close(); if (api!=null) api.close(); });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof GameMenuScreen menu) || !menu.shouldShowMenu() || client.world == null) return;
            ButtonWidget button = ButtonWidget.builder(Text.literal("Novex"),
                pressed -> client.setScreen(new NovexScreen(screen, config, api))).dimensions(0, 0, 120, 20).build();
            Screens.getButtons(screen).add(button);
            Runnable place = () -> {
                int unread=social.unread();
                button.setMessage(Text.literal("Novex"+(config.get(NovexConfig.Option.UNREAD_COUNT)&&unread>0?" • "+Math.min(99,unread):"")));
                var occupied = screen.children().stream()
                    .filter(element -> element instanceof ClickableWidget widget && widget != button && widget.visible)
                    .map(element -> (ClickableWidget) element)
                    .map(widget -> new MenuPlacement.Box(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()))
                    .toList();
                var position = MenuPlacement.find(screen.width, screen.height, occupied);
                button.visible = position.isPresent();
                position.ifPresent(box -> {
                    button.setPosition(box.x(), box.y()); button.setWidth(box.width());
                });
            };
            place.run();
            ScreenEvents.afterRender(screen).register((ignored, context, mouseX, mouseY, delta) -> {
                if (button.visible) VersionUi.logo(context, button.getX() + 5, button.getY() + 4);
            });
            // Also catches buttons added by other mods after our AFTER_INIT listener.
            ScreenEvents.afterTick(screen).register(ignored -> place.run());
        });
    }
}
