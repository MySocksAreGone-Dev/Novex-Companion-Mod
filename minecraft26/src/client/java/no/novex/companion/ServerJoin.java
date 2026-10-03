package no.novex.companion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
final class ServerJoin {
    static void confirm(Screen parent,String name,String address) {
        if(!SupabaseApi.validAddress(address))return;
        var client=Minecraft.getInstance();
        ScreenAccess.show(client,new ConfirmScreen(yes->{
            if(!yes){ScreenAccess.show(client,parent);return;}
            var landing=new TitleScreen();VersionUi.disconnect(client,landing);
            ConnectScreen.startConnecting(landing,client,ServerAddress.parseString(address),new ServerData(name,address,ServerData.Type.OTHER),false,null);
        },Component.literal("Join "+name+"?"),Component.literal("Leave your current world and connect to "+address+"?")));
    }
}
