package no.novex.companion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
/** Masks both visual text and narration; never calls vanilla's plaintext renderer. */
final class PasswordField extends EditBox {
    private final Font font;
    PasswordField(Font font,int x,int y,int width) {super(font,x,y,width,20,Component.literal("Password"));this.font=font;setMaxLength(4096);}
    @Override protected MutableComponent createNarrationMessage() {return Component.literal("Password, "+getValue().length()+" characters");}
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor ctx,int mx,int my,float delta) {
        ctx.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isFocused()?0xFF8C97A8:0xFF49515D);
        ctx.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,0xFF17191C);
        String masked=getValue().isEmpty()?"Password":"*".repeat(Math.min(getValue().length(),Math.max(1,(getWidth()-16)/6)));
        ctx.text(font,masked+(isFocused()?"|":""),getX()+5,getY()+6,0xFFE8EBF0);
    }
}
