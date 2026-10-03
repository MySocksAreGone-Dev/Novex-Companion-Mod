package no.novex.companion;

import com.google.gson.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small paged views of the existing Novex social account. Refresh tokens are saved only in secure OS storage. */
final class SocialScreen extends Screen {
    private final Screen parent;
    private final SupabaseApi api;
    private String view="Friends",status="";
    private JsonArray rows=new JsonArray();
    private boolean busy;
    private boolean wasRestoring;
    @Override public void tick(){super.tick();if(wasRestoring&&!api.restoring()){wasRestoring=false;clearAndInit();if(api.signedIn())load("Friends");}}
    private int page;
    private UUID peer;
    private String peerName="";
    private TextFieldWidget input,password;
    SocialScreen(Screen parent,SupabaseApi api) { super(Text.literal("Novex Account"));this.parent=parent;this.api=api; }
    private int left() { return Math.max(10,(width-520)/2); }
    private int span() { return width-left()*2; }
    private int count() { return Math.max(1,(height-160)/34); }
    private ButtonWidget button(String label,int x,int y,int w,Runnable action) {
        var b=addDrawableChild(ButtonWidget.builder(Text.literal(label),ignored -> action.run()).dimensions(x,y,w,20).build());b.active=!busy;return b;
    }
    private TextFieldWidget field(String label,int x,int y,int w,int max) {
        var f=new TextFieldWidget(textRenderer,x,y,w,20,Text.literal(label));f.setMaxLength(max);f.setSuggestion(label);f.setChangedListener(value -> f.setSuggestion(value.isEmpty()?label:""));addDrawableChild(f);return f;
    }
    @Override protected void init() {
        int x=left(),w=span();
        wasRestoring=api.restoring();
        if(wasRestoring){button("Back",(width-100)/2,height-26,100,this::close);return;}
        if (!api.signedIn()) {
            input=field("Novex email",x,70,w,320);
            password=addDrawableChild(new PasswordField(textRenderer,x,100,w));
            button(busy?"Signing in...":"Sign in",x,130,w,() -> {
                String email=input.getText(),secret=password.getText();password.setText("");
                run(api.signIn(email,secret),() -> {status="Signed in to Novex.";load("Friends");});
            });
        } else {
            int bw=(w-12)/4;
            button("Friends",x,40,bw,() -> load("Friends"));
            button("Requests",x+bw+4,40,bw,() -> load("Requests"));
            button("Add friend",x+2*(bw+4),40,bw,() -> {view="Search";rows=new JsonArray();page=0;clearAndInit();});
            button("Sign out",x+3*(bw+4),40,bw,() -> {NovexCompanion.social.offline();api.signOut();NovexCompanion.social.invalidate();rows=new JsonArray();peer=null;status="Signed out.";clearAndInit();});
            if (view.equals("Search")) {
                input=field("Search Novex username",x,68,w-84,64);
                button("Search",x+w-80,68,80,() -> run(api.searchUsers(input.getText()),data -> {rows=data;page=0;clearAndInit();}));
            } else if (view.equals("Messages")) {
                input=field("Message "+peerName,x,height-76,w-70,4000);
                button("Send",x+w-66,height-76,66,() -> {
                    String content=input.getText();run(api.sendMessage(peer,content),() -> loadMessages());
                });
                button("Refresh",x,68,80,() -> loadMessages());
                if(rows.size()>=50)button("Older",x+84,68,70,()-> {
                    Instant before=Instant.parse(rows.get(rows.size()-1).getAsJsonObject().get("created_at").getAsString());
                    run(api.messages(peer,before),data->{rows=data;page=0;clearAndInit();});
                });
            } else button("Refresh",x,68,80,() -> load(view));
            int pages=Math.max(1,(rows.size()+count()-1)/count());page=Math.min(page,pages-1);
            if (pages>1) button((page+1)+" / "+pages,x+w-80,68,80,() -> {page=(page+1)%pages;clearAndInit();});
            for (int i=0;i<count() && page*count()+i<rows.size();i++) {
                JsonObject row=rows.get(page*count()+i).getAsJsonObject();int y=100+i*34;
                if (view.equals("Messages")) {
                    button("Read",x+w-50,y,50,()->client.setScreen(new MessageView(this,clean(row.get("content").getAsString()))));continue;
                }
                if (view.equals("Requests")) {
                    button("Accept",x+w-140,y,68,() -> run(api.acceptFriendRequest(id(row,"id")),() -> load("Requests")));
                    button("Decline",x+w-68,y,68,() -> run(api.rpc("companion_decline_request",body("request_id",id(row,"id"))),() -> load("Requests")));
                } else if (view.equals("Search")) {
                    button("Request",x+w-80,y,80,() -> run(api.sendFriendRequest(id(row,"id")),() -> {status="Friend request sent.";}));
                } else {
                    var presence=NovexCompanion.social.presence(id(row,"friend_id"));
                    if(presence!=null&&presence.has("allow_join")&&presence.get("allow_join").getAsBoolean()&&!presence.get("server_address").isJsonNull()) {
                        String address=presence.get("server_address").getAsString();
                        if(SupabaseApi.validAddress(address))button("Join",x+w-200,y,50,()->ServerJoin.confirm(this,name(row),address));
                    }
                    button("Message",x+w-146,y,76,() -> {peer=id(row,"friend_id");peerName=name(row);loadMessages();});
                    button("Remove",x+w-66,y,66,() -> client.setScreen(new ConfirmScreen(yes -> {
                        client.setScreen(this);if(yes)run(api.rpc("companion_remove_friend",body("peer_id",id(row,"friend_id"))),() -> load("Friends"));
                    },Text.literal("Remove friend?"),Text.literal(name(row)))));
                }
            }
        }
        button("Back",(width-100)/2,height-26,100,this::close);
    }
    void refreshFromEvent() { if(!busy&&!view.equals("Search")&&(input==null||input.getText().isEmpty())) {if(view.equals("Messages"))loadMessages();else load(view);} }
    void load(String target) {
        view=target;page=0;rows=new JsonArray();
        run(target.equals("Requests")?api.incomingRequests():api.friends(),data -> {rows=data;clearAndInit();});
    }
    private void loadMessages() {
        view="Messages";page=0;
        run(api.messages(peer,null),data -> {
            rows=data;
            if(!data.isEmpty()) {
                JsonObject body=body("peer_id",peer);body.addProperty("seen_at",data.get(0).getAsJsonObject().get("created_at").getAsString());
                api.rpc("companion_mark_read",body).thenRun(() -> client.execute(() -> NovexCompanion.social.invalidate())).exceptionally(error -> null);
            }
            clearAndInit();
        });
    }
    static JsonObject body(String key,UUID value) {var b=new JsonObject();b.addProperty(key,value.toString());return b;}
    private static UUID id(JsonObject row,String key) {return UUID.fromString(row.get(key).getAsString());}
    private static String name(JsonObject row) {
        var profile=row.has("profiles") && row.get("profiles").isJsonObject()?row.getAsJsonObject("profiles"):row;
        return profile.has("username")?clean(profile.get("username").getAsString()):"Novex user";
    }
    private static String clean(String text) {return text.replaceAll("[\\p{Cntrl}§]"," ");}
    private void run(CompletableFuture<?> future,Runnable done) {run(future,ignored -> done.run());}
    private <T> void run(CompletableFuture<T> future,java.util.function.Consumer<T> done) {
        busy=true;status="Loading...";clearAndInit();
        future.whenComplete((data,error) -> client.execute(() -> {
            busy=false;
            if(error!=null) {
                Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
                status=cause instanceof IllegalStateException?cause.getMessage():"Unable to load Novex. Please retry.";
            } else {status="";try {done.accept(data);}catch(RuntimeException ignored){status="Novex returned invalid data.";}}
            if(client.currentScreen==this)clearAndInit();
        }));
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta) {
        ctx.fill(0,0,width,height,0xFF0D0E10);
        ctx.drawCenteredTextWithShadow(textRenderer,"NOVEX · "+(api.signedIn()?view:"Sign in"),width/2,16,0xFFE8EBF0);
        if(api.signedIn()) {
            int w=span(),x=left();
            if(rows.isEmpty()&&!busy)ctx.drawCenteredTextWithShadow(textRenderer,view.equals("Search")?"Find an existing Novex account":"Nothing here yet",width/2,105,0xFFA5ADBA);
            for(int i=0;i<count()&&page*count()+i<rows.size();i++) {
                var row=rows.get(page*count()+i).getAsJsonObject();int y=102+i*34;
                if(view.equals("Messages")) {
                    String who=id(row,"sender_id").equals(api.userId())?"You":peerName;
                    String timestamp=row.get("created_at").getAsString();
                    ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(who+" · "+timestamp,w-58),x,y,0xFFA5ADBA);
                    ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(clean(row.get("content").getAsString()),w-58),x,y+12,0xFFE8EBF0);
                } else {
                    ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(name(row),w-206),x,y,0xFFE8EBF0);
                    if(view.equals("Friends"))ctx.drawTextWithShadow(textRenderer,NovexCompanion.social.presence(id(row,"friend_id"))==null?"Offline":"Playing Minecraft",x,y+12,0xFFA5ADBA);
                }
            }
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,"Your existing Novex account, not your Microsoft account",width/2,45,0xFFA5ADBA);
            if(height>230)ctx.drawCenteredTextWithShadow(textRenderer,api.restoring()?"Restoring your saved Novex session...":api.storageStatus(),width/2,164,0xFFA5ADBA);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status.isEmpty()&&api.signedIn()?api.storageStatus():status,width-20),width/2,height-42,0xFFA5ADBA);
        super.render(ctx,mx,my,delta);
    }
    @Override public void close() {if(password!=null)password.setText("");client.setScreen(parent);}
}
