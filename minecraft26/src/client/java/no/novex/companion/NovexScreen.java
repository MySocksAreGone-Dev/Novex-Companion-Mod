package no.novex.companion;

import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        super(Component.literal("Novex")); this.parent = parent; this.config = config; this.api=api;
    }
    @Override protected void init() {
        int left = Math.max(8, (width - 520) / 2);
        int available = width - left * 2;
        int tabWidth = (available - 12) / 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            Button button = addRenderableWidget(Button.builder(Component.literal(target.name()), pressed -> {
                tab = target; settingsPage = 0; rebuildWidgets();
                if (target==Tab.Partners && partners.isEmpty()) loadPartners();
            }).bounds(left + i * (tabWidth + 4), 43, tabWidth, 20).build());
            button.active = target != tab;
        }
        if (tab == Tab.Settings) addSettings(left, available);
        if (tab==Tab.Friends || tab==Tab.Messages) {
            var b=addRenderableWidget(Button.builder(Component.literal(api!=null && api.signedIn()?"Open Novex social":"Sign in to Novex"),ignored -> {
                var social=new SocialScreen(this,api);ScreenAccess.show(minecraft,social);if(api.signedIn())social.load("Friends");
            }).bounds((width-180)/2,height/2+34,180,20).build());b.active=api!=null;
        }
        if (tab == Tab.Partners) addPartners(left, available);
        addRenderableWidget(Button.builder(Component.literal("Back"), pressed -> onClose())
            .bounds((width - 100) / 2, height - 26, 100, 20).build());
    }
    private void loadPartners() {
        if (loading) return;
        if (api==null) { partnerStatus="Novex services are not configured."; return; }
        loading=true; partnerStatus="Loading partner servers..."; rebuildWidgets();
        api.partners().whenComplete((data,error) -> minecraft.execute(() -> {
            loading=false;
            if (error!=null) partnerStatus="Unable to load partners. Please retry.";
            else { partners=data; partnerPage=0; partnerStatus=data.isEmpty()?"No partner servers published.":""; }
            if (ScreenAccess.current(minecraft)==this) rebuildWidgets();
        }));
    }
    private int partnerRows() { return Math.max(1,(height-150)/42); }
    private void addPartners(int left,int available) {
        var refresh=addRenderableWidget(Button.builder(Component.literal(loading?"Loading...":"Refresh"),button -> loadPartners())
            .bounds(left,72,80,20).build());
        refresh.active=!loading;
        int rows=partnerRows(), pages=Math.max(1,(partners.size()+rows-1)/rows);
        partnerPage=Math.min(partnerPage,pages-1);
        if (pages>1) addRenderableWidget(Button.builder(Component.literal("Page "+(partnerPage+1)+"/"+pages),button -> {
            partnerPage=(partnerPage+1)%pages; rebuildWidgets();
        }).bounds(left+86,72,100,20).build());
        for (int i=0;i<rows && partnerPage*rows+i<partners.size();i++) {
            var partner=partners.get(partnerPage*rows+i);
            addRenderableWidget(Button.builder(Component.literal("Join"),button -> ServerJoin.confirm(this,partner.name(),partner.address()))
                .bounds(left+available-128,103+i*42,54,20).build());
            addRenderableWidget(Button.builder(Component.literal("Copy IP"),button -> {
                minecraft.keyboardHandler.setClipboard(partner.address()); partnerStatus="Server address copied.";
            }).bounds(left+available-70,103+i*42,70,20).build());
        }
    }
    private void renderPartners(GuiGraphicsExtractor context) {
        int left=Math.max(8,(width-520)/2), available=width-left*2;
        for (int i=0;i<partnerRows() && partnerPage*partnerRows()+i<partners.size();i++) {
            var partner=partners.get(partnerPage*partnerRows()+i);int y=104+i*42;
            context.text(font,font.plainSubstrByWidth(partner.name(),available-140),left,y,TEXT);
            context.text(font,font.plainSubstrByWidth(partner.description(),available-140),left,y+12,MUTED);
            context.text(font,font.plainSubstrByWidth(partner.address(),available-140),left,y+24,MUTED);
        }
        context.centeredText(font,font.plainSubstrByWidth(partnerStatus,width-20),width/2,height-40,MUTED);
    }
    private void addSettings(int left, int available) {
        String[] sections = {"Social", "Privacy", "Badges", "Interface"};
        int groupWidth = (available - 12) / 4;
        for (int i = 0; i < sections.length; i++) {
            String target = sections[i];
            var button = addRenderableWidget(Button.builder(Component.literal(target), pressed -> {
                section = target; settingsPage = 0; rebuildWidgets();
            }).bounds(left + i * (groupWidth + 4), 77, groupWidth, 20).build());
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
            var button = addRenderableWidget(Button.builder(label(option), pressed -> {
                config.toggle(option); NovexCompanion.social.privacyChanged(); rebuildWidgets();
            }).bounds(left, 106 + row * 24, available, 20).build());
            if (option == NovexConfig.Option.SHARE_SERVER) button.active = config.get(NovexConfig.Option.ONLINE_STATUS);
            if (option == NovexConfig.Option.ALLOW_JOIN) button.active = config.get(NovexConfig.Option.SHARE_SERVER);
        }
        if(section.equals("Interface")) {
            addRenderableWidget(Button.builder(Component.literal("Open screenshots folder"),b->{
                var folder=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots");
                if(java.nio.file.Files.isDirectory(folder))PlatformActions.openFolder(folder);
            }).bounds(left,136,available,20).build());
        }
        if(section.equals("Badges")) {
            var link=addRenderableWidget(Button.builder(Component.literal("Verify Minecraft badge"),pressed->ScreenAccess.show(minecraft,new net.minecraft.client.gui.screens.ConfirmScreen(yes->{
                ScreenAccess.show(minecraft,this);if(!yes)return;
                badgeStatus="Verifying...";
                api.verifyMinecraft(minecraft.getUser().getAccessToken()).whenComplete((value,error)->minecraft.execute(()->badgeStatus=error==null?"Verified for 30 days.":"Verification failed. Check your Novex/Minecraft sign-in."));
            },Component.literal("Verify your Minecraft account?"),Component.literal("Send your current Minecraft session to Novex's verification service over HTTPS to verify your UUID? The token is not stored."))))
                .bounds(left,height-78,(available-4)/2,20).build());link.active=api!=null&&api.signedIn();
            var unlink=addRenderableWidget(Button.builder(Component.literal("Remove my badge"),pressed->{
                api.rpc("companion_unlink_minecraft",new com.google.gson.JsonObject()).whenComplete((value,error)->minecraft.execute(()->badgeStatus=error==null?"Badge removed.":"Unable to remove badge."));
            }).bounds(left+(available+4)/2,height-78,(available-4)/2,20).build());unlink.active=api!=null&&api.signedIn();
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("More settings (" + (settingsPage + 1) + "/" + pages + ")"), pressed -> {
                settingsPage = (settingsPage + 1) % pages; rebuildWidgets();
            }).bounds(left, height - 54, available, 20).build());
        }
    }
    private Component label(NovexConfig.Option option) {
        return Component.literal(option.label + ": " + (config.get(option) ? "ON" : "OFF"));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF0D0E10);
        context.fill(0, 0, width, 35, 0xFF17191C);
        context.centeredText(font, "NOVEX", width / 2, 10, TEXT);
        context.centeredText(font, "Companion", width / 2, 23, MUTED);
        if (tab == Tab.Partners) { renderPartners(context); }
        else if (tab != Tab.Settings) {
            int top = Math.max(79, height / 2 - 22);
            context.centeredText(font, switch (tab) {
                case Friends -> api!=null && api.signedIn()?"Novex friends":"Your Novex account";
                case Messages -> "Novex messages";
                case Partners -> "Partner servers unavailable";
                default -> "";
            }, width / 2, top, TEXT);
            var message = Component.literal(switch (tab) {
                case Friends -> "Sign in with the same Novex account you use in the launcher.";
                case Messages -> "Read and send messages in your existing Novex conversations.";
                case Partners -> "No partner provider is connected. Your Minecraft server list stays unchanged.";
                default -> "";
            });
            int lineY = top + 20;
            for (var line : font.split(message, Math.min(380, width - 32))) {
                context.centeredText(font, line, width / 2, lineY, MUTED); lineY += 12;
            }
        } else if (height >= 240) {
            String note = section.equals("Interface")?"Session: "+((System.nanoTime()-SESSION_START)/60000000000L)+" min · "+(minecraft.isLocalServer()?"Singleplayer":"Multiplayer") : !badgeStatus.isEmpty()&&section.equals("Badges")?badgeStatus:config.saveError().isEmpty() ? "Preferences saved locally. Server sharing is opt-in." : config.saveError();
            context.centeredText(font, font.plainSubstrByWidth(note, width - 20), width / 2, section.equals("Badges") ? height - 91 : height - 39, MUTED);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    @Override public void onClose() { ScreenAccess.show(minecraft,parent); }
}
