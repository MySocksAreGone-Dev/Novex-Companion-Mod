package no.novex.companion;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
/** Masks both visual text and narration; never calls vanilla's plaintext renderer. */
final class PasswordField extends TextFieldWidget {
    private final TextRenderer font;
    PasswordField(TextRenderer font,int x,int y,int width) {super(font,x,y,width,20,Text.literal("Password"));this.font=font;setMaxLength(4096);}
    @Override protected MutableText getNarrationMessage() {return Text.literal("Password, "+getText().length()+" characters");}
    @Override public void renderWidget(DrawContext ctx,int mx,int my,float delta) {
        ctx.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isFocused()?0xFF8C97A8:0xFF49515D);
        ctx.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,0xFF17191C);
        String masked=getText().isEmpty()?"Password":"*".repeat(Math.min(getText().length(),Math.max(1,(getWidth()-16)/6)));
        ctx.drawTextWithShadow(font,masked+(isFocused()?"|":""),getX()+5,getY()+6,0xFFE8EBF0);
    }
}
