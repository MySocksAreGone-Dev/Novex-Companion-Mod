package no.novex.companion;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

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
            if (!(screen instanceof PauseScreen menu) || !menu.showsPauseMenu() || client.level == null) return;
            Button button = Button.builder(Component.literal("Novex"),
                pressed -> ScreenAccess.show(client,new NovexScreen(screen, config, api))).bounds(0, 0, 120, 20).build();
            Screens.getWidgets(screen).add(button);
            Runnable place = () -> {
                int unread=social.unread();
                button.setMessage(Component.literal("Novex"+(config.get(NovexConfig.Option.UNREAD_COUNT)&&unread>0?" • "+Math.min(99,unread):"")));
                var occupied = screen.children().stream()
                    .filter(element -> element instanceof AbstractWidget widget && widget != button && widget.visible)
                    .map(element -> (AbstractWidget) element)
                    .map(widget -> new MenuPlacement.Box(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()))
                    .toList();
                var position = MenuPlacement.find(screen.width, screen.height, occupied);
                button.visible = position.isPresent();
                position.ifPresent(box -> {
                    button.setPosition(box.x(), box.y()); button.setWidth(box.width());
                });
            };
            place.run();
            ScreenEvents.afterExtract(screen).register((ignored, context, mouseX, mouseY, delta) -> {
                if (button.visible) VersionUi.logo(context, button.getX() + 5, button.getY() + 4);
            });
            // Also catches buttons added by other mods after our AFTER_INIT listener.
            ScreenEvents.afterTick(screen).register(ignored -> place.run());
        });
    }
}
