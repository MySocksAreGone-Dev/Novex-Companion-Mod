package no.novex.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.Arrays;

/** Vanilla widgets and flat colors; no background shaders, blur or network work. */
public final class NovexScreen extends Screen {
    private enum Tab { Friends, Messages, Partners, Settings }
    private final Screen parent;
    private final NovexConfig config;
    private final SupabaseApi api;
    private java.util.List<SupabaseApi.Partner> partners=java.util.List.of();
    private String partnerStatus="";
    private boolean loading;
    private int partnerPage;
    private Tab tab = Tab.Friends;
    private String section = "Social";
    private int settingsPage;
    private String badgeStatus="";
    private static final long SESSION_START=System.nanoTime();
    private static final int TEXT = 0xFFE8EBF0, MUTED = 0xFFA5ADBA;
    public NovexScreen(Screen parent, NovexConfig config, SupabaseApi api) {
        super(Text.literal("Novex")); this.parent = parent; this.config = config; this.api=api;
    }
    @Override protected void init() {
        int left = Math.max(8, (width - 520) / 2);
        int available = width - left * 2;
        int tabWidth = (available - 12) / 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(target.name()), pressed -> {
                tab = target; settingsPage = 0; clearAndInit();
                if (target==Tab.Partners && partners.isEmpty()) loadPartners();
            }).dimensions(left + i * (tabWidth + 4), 43, tabWidth, 20).build());
            button.active = target != tab;
        }
        if (tab == Tab.Settings) addSettings(left, available);
        if (tab==Tab.Friends || tab==Tab.Messages) {
            var b=addDrawableChild(ButtonWidget.builder(Text.literal(api!=null && api.signedIn()?"Open Novex social":"Sign in to Novex"),ignored -> {
                var social=new SocialScreen(this,api);client.setScreen(social);if(api.signedIn())social.load("Friends");
            }).dimensions((width-180)/2,height/2+34,180,20).build());b.active=api!=null;
        }
        if (tab == Tab.Partners) addPartners(left, available);
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), pressed -> close())
            .dimensions((width - 100) / 2, height - 26, 100, 20).build());
    }
    private void loadPartners() {
        if (loading) return;
        if (api==null) { partnerStatus="Novex services are not configured."; return; }
        loading=true; partnerStatus="Loading partner servers..."; clearAndInit();
        api.partners().whenComplete((data,error) -> client.execute(() -> {
            loading=false;
            if (error!=null) partnerStatus="Unable to load partners. Please retry.";
            else { partners=data; partnerPage=0; partnerStatus=data.isEmpty()?"No partner servers published.":""; }
            if (client.currentScreen==this) clearAndInit();
        }));
    }
    private int partnerRows() { return Math.max(1,(height-150)/42); }
    private void addPartners(int left,int available) {
        var refresh=addDrawableChild(ButtonWidget.builder(Text.literal(loading?"Loading...":"Refresh"),button -> loadPartners())
            .dimensions(left,72,80,20).build());
        refresh.active=!loading;
        int rows=partnerRows(), pages=Math.max(1,(partners.size()+rows-1)/rows);
        partnerPage=Math.min(partnerPage,pages-1);
        if (pages>1) addDrawableChild(ButtonWidget.builder(Text.literal("Page "+(partnerPage+1)+"/"+pages),button -> {
            partnerPage=(partnerPage+1)%pages; clearAndInit();
        }).dimensions(left+86,72,100,20).build());
        for (int i=0;i<rows && partnerPage*rows+i<partners.size();i++) {
            var partner=partners.get(partnerPage*rows+i);
            addDrawableChild(ButtonWidget.builder(Text.literal("Join"),button -> ServerJoin.confirm(this,partner.name(),partner.address()))
                .dimensions(left+available-128,103+i*42,54,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Copy IP"),button -> {
                client.keyboard.setClipboard(partner.address()); partnerStatus="Server address copied.";
            }).dimensions(left+available-70,103+i*42,70,20).build());
        }
    }
    private void renderPartners(DrawContext context) {
        int left=Math.max(8,(width-520)/2), available=width-left*2;
        for (int i=0;i<partnerRows() && partnerPage*partnerRows()+i<partners.size();i++) {
            var partner=partners.get(partnerPage*partnerRows()+i);int y=104+i*42;
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(partner.name(),available-140),left,y,TEXT);
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(partner.description(),available-140),left,y+12,MUTED);
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(partner.address(),available-140),left,y+24,MUTED);
        }
        context.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(partnerStatus,width-20),width/2,height-40,MUTED);
    }
    private void addSettings(int left, int available) {
        String[] sections = {"Social", "Privacy", "Badges", "Interface"};
        int groupWidth = (available - 12) / 4;
        for (int i = 0; i < sections.length; i++) {
            String target = sections[i];
            var button = addDrawableChild(ButtonWidget.builder(Text.literal(target), pressed -> {
                section = target; settingsPage = 0; clearAndInit();
            }).dimensions(left + i * (groupWidth + 4), 77, groupWidth, 20).build());
            button.active = !section.equals(target);
        }
        var options = Arrays.stream(NovexConfig.Option.values()).filter(option -> option.section.equals(section)).toList();
        int rows = Math.max(1, (height - (section.equals("Badges")?218:164)) / 24);
        int pages = (options.size() + rows - 1) / rows;
        settingsPage = Math.min(settingsPage, pages - 1);
        for (int row = 0; row < rows; row++) {
            int index = settingsPage * rows + row;
            if (index >= options.size()) break;
            var option = options.get(index);
            var button = addDrawableChild(ButtonWidget.builder(label(option), pressed -> {
                config.toggle(option); NovexCompanion.social.privacyChanged(); clearAndInit();
            }).dimensions(left, 106 + row * 24, available, 20).build());
            if (option == NovexConfig.Option.SHARE_SERVER) button.active = config.get(NovexConfig.Option.ONLINE_STATUS);
            if (option == NovexConfig.Option.ALLOW_JOIN) button.active = config.get(NovexConfig.Option.SHARE_SERVER);
        }
        if(section.equals("Interface")) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Open screenshots folder"),b->{
                var folder=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots");
                if(java.nio.file.Files.isDirectory(folder))net.minecraft.util.Util.getOperatingSystem().open(folder.toFile());
            }).dimensions(left,136,available,20).build());
        }
        if(section.equals("Badges")) {
            var link=addDrawableChild(ButtonWidget.builder(Text.literal("Verify Minecraft badge"),pressed->client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(yes->{
                client.setScreen(this);if(!yes)return;
                badgeStatus="Verifying...";
                api.verifyMinecraft(client.getSession().getAccessToken()).whenComplete((value,error)->client.execute(()->badgeStatus=error==null?"Verified for 30 days.":"Verification failed. Check your Novex/Minecraft sign-in."));
            },Text.literal("Verify your Minecraft account?"),Text.literal("Send your current Minecraft session to Novex's verification service over HTTPS to verify your UUID? The token is not stored."))))
                .dimensions(left,height-78,(available-4)/2,20).build());link.active=api!=null&&api.signedIn();
            var unlink=addDrawableChild(ButtonWidget.builder(Text.literal("Remove my badge"),pressed->{
                api.rpc("companion_unlink_minecraft",new com.google.gson.JsonObject()).whenComplete((value,error)->client.execute(()->badgeStatus=error==null?"Badge removed.":"Unable to remove badge."));
            }).dimensions(left+(available+4)/2,height-78,(available-4)/2,20).build());unlink.active=api!=null&&api.signedIn();
        }
        if (pages > 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal("More settings (" + (settingsPage + 1) + "/" + pages + ")"), pressed -> {
                settingsPage = (settingsPage + 1) % pages; clearAndInit();
            }).dimensions(left, height - 54, available, 20).build());
        }
    }
    private Text label(NovexConfig.Option option) {
        return Text.literal(option.label + ": " + (config.get(option) ? "ON" : "OFF"));
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF0D0E10);
        context.fill(0, 0, width, 35, 0xFF17191C);
        context.drawCenteredTextWithShadow(textRenderer, "NOVEX", width / 2, 10, TEXT);
        context.drawCenteredTextWithShadow(textRenderer, "Companion", width / 2, 23, MUTED);
        if (tab == Tab.Partners) { renderPartners(context); }
        else if (tab != Tab.Settings) {
            int top = Math.max(79, height / 2 - 22);
            context.drawCenteredTextWithShadow(textRenderer, switch (tab) {
                case Friends -> api!=null && api.signedIn()?"Novex friends":"Your Novex account";
                case Messages -> "Novex messages";
                case Partners -> "Partner servers unavailable";
                default -> "";
            }, width / 2, top, TEXT);
            var message = Text.literal(switch (tab) {
                case Friends -> "Sign in with the same Novex account you use in the launcher.";
                case Messages -> "Read and send messages in your existing Novex conversations.";
                case Partners -> "No partner provider is connected. Your Minecraft server list stays unchanged.";
                default -> "";
            });
            int lineY = top + 20;
            for (var line : textRenderer.wrapLines(message, Math.min(380, width - 32))) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, lineY, MUTED); lineY += 12;
            }
        } else if (height >= 240) {
            String note = section.equals("Interface")?"Session: "+((System.nanoTime()-SESSION_START)/60000000000L)+" min · "+(client.isInSingleplayer()?"Singleplayer":"Multiplayer") : !badgeStatus.isEmpty()&&section.equals("Badges")?badgeStatus:config.saveError().isEmpty() ? "Preferences saved locally. Server sharing is opt-in." : config.saveError();
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(note, width - 20), width / 2, section.equals("Badges") ? height - 91 : height - 39, MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void close() { client.setScreen(parent); }
}
