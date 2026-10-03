package no.novex.companion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
final class ServerJoin {
    static void confirm(Screen parent,String name,String address) {
        if(!SupabaseApi.validAddress(address))return;
        var client=MinecraftClient.getInstance();
        client.setScreen(new ConfirmScreen(yes->{
            if(!yes){client.setScreen(parent);return;}
            var landing=new TitleScreen();VersionUi.disconnect(client,landing);
            ConnectScreen.connect(landing,client,ServerAddress.parse(address),new ServerInfo(name,address,ServerInfo.ServerType.OTHER),false,null);
        },Text.literal("Join "+name+"?"),Text.literal("Leave your current world and connect to "+address+"?")));
    }
}
