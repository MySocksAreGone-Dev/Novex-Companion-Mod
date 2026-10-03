package no.novex.companion;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/** Full wrapped message with paging, so long messages are never lost behind ellipsis. */
final class MessageView extends Screen {
 private final Screen parent;private final String content;private int page;
 MessageView(Screen parent,String content){super(Text.literal("Novex message"));this.parent=parent;this.content=content;}
 private int rows(){return Math.max(1,(height-80)/12);}
 @Override protected void init(){
  int pages=Math.max(1,(textRenderer.wrapLines(Text.literal(content),width-32).size()+rows()-1)/rows());page=Math.min(page,pages-1);
  addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close()).dimensions(width/2-104,height-28,100,20).build());
  var next=addDrawableChild(ButtonWidget.builder(Text.literal("Page "+(page+1)+"/"+pages),b->{page=(page+1)%pages;clearAndInit();}).dimensions(width/2+4,height-28,100,20).build());next.active=pages>1;
 }
 @Override public void render(DrawContext ctx,int x,int y,float delta){ctx.fill(0,0,width,height,0xFF0D0E10);ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,14,0xFFE8EBF0);var lines=textRenderer.wrapLines(Text.literal(content),width-32);for(int i=0;i<rows()&&page*rows()+i<lines.size();i++)ctx.drawTextWithShadow(textRenderer,lines.get(page*rows()+i),16,38+i*12,0xFFE8EBF0);super.render(ctx,x,y,delta);}
 @Override public void close(){client.setScreen(parent);}
}
