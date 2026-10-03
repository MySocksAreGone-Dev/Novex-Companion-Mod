package no.novex.companion;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Only lightweight clocks run on client ticks; all service calls are asynchronous. */
final class SocialRuntime {
    private final SupabaseApi api; private final NovexConfig config;
    private final UUID sessionId=UUID.randomUUID();
    private UUID account;
    private long nextCheck,nextPresence,nextNotice;
    private boolean checking,presenceBusy,initialized;
    private int unread,requests;
    private Map<UUID,JsonObject> presence=Map.of();
    SocialRuntime(SupabaseApi api,NovexConfig config) {this.api=api;this.config=config;}
    int unread() {return unread+requests;}
    JsonObject presence(UUID peer) {return presence.get(peer);}
    void invalidate() {nextCheck=0;}
    void tick(Minecraft client) {
        if(api==null)return;
        UUID current=api.userId();
        if(!Objects.equals(account,current)) {account=current;unread=requests=0;presence=Map.of();initialized=false;nextCheck=nextPresence=0;}
        if(current==null)return;
        api.realtime(()->client.execute(()-> {nextCheck=Math.min(nextCheck,System.currentTimeMillis()+1500);if(ScreenAccess.current(client) instanceof SocialScreen screen)screen.refreshFromEvent();}));
        long now=System.currentTimeMillis();
        if(now>=nextPresence&&!presenceBusy) {
            nextPresence=now+60000;presenceBusy=true;
            var body=new JsonObject();body.addProperty("session_id",sessionId.toString());
            boolean online=config.get(NovexConfig.Option.ONLINE_STATUS)&&client.level!=null;
            body.addProperty("online",online);
            var server=client.getCurrentServer();
            if(online&&config.get(NovexConfig.Option.SHARE_SERVER)&&server!=null&&SupabaseApi.validAddress(server.ip)) {
                body.addProperty("server_address",server.ip);body.addProperty("allow_join",config.get(NovexConfig.Option.ALLOW_JOIN));
            }
            api.rpc("companion_presence",body).whenComplete((v,e)->client.execute(()->presenceBusy=false));
        }
        if(now>=nextCheck&&!checking) {
            nextCheck=now+30000;checking=true;
            var a=api.rpc("companion_unread",new JsonObject());var b=api.incomingRequests();var c=api.rpc("companion_friends_presence",new JsonObject());
            CompletableFuture.allOf(a,b,c).whenComplete((v,error)->client.execute(()-> {
                checking=false;if(error!=null||!Objects.equals(account,current))return;
                try {
                    int count=0;for(var row:a.join().getAsJsonArray())count+=Math.min(9999,row.getAsJsonObject().get("unread").getAsInt());
                    int incoming=b.join().size();Map<UUID,JsonObject> next=new HashMap<>();
                    for(var row:c.join().getAsJsonArray())next.put(UUID.fromString(row.getAsJsonObject().get("user_id").getAsString()),row.getAsJsonObject());
                    String notice=null;
                    if(initialized&&count>unread&&config.get(NovexConfig.Option.MESSAGE_NOTIFICATIONS))notice="New Novex message";
                    else if(initialized&&incoming>requests&&config.get(NovexConfig.Option.REQUEST_NOTIFICATIONS))notice="New Novex friend request";
                    else if(initialized&&next.keySet().stream().anyMatch(id->!presence.containsKey(id))&&config.get(NovexConfig.Option.ONLINE_NOTIFICATIONS))notice="A Novex friend is playing Minecraft";
                    if(notice!=null&&System.currentTimeMillis()>nextNotice&&client.level!=null) {
                        ScreenAccess.notice(client,Component.literal(notice));nextNotice=System.currentTimeMillis()+10000;
                    }
                    unread=count;requests=incoming;presence=Map.copyOf(next);initialized=true;
                }catch(RuntimeException ignored){/* Invalid remote data must not interrupt Minecraft. */}
            }));
        }
    }
    void privacyChanged() {nextPresence=0;}
    void offline() {
        if(api==null||api.userId()==null)return;
        var body=new JsonObject();body.addProperty("session_id",sessionId.toString());body.addProperty("online",false);
        api.rpc("companion_presence",body).exceptionally(error->null);
    }
}
