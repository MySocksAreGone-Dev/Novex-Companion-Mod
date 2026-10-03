package no.novex.companion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class ScreenAccess {
 static void show(Minecraft client,Screen screen){client.setScreen(screen);}
 static Screen current(Minecraft client){return client.screen;}
 static void notice(Minecraft client,Component text){client.gui.setOverlayMessage(text,false);}
}
