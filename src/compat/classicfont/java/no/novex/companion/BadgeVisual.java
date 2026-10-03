package no.novex.companion;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import com.mojang.authlib.GameProfile;
public final class BadgeVisual {
 public static java.util.UUID profileId(GameProfile profile){return profile.getId();}
 static Text prefix(Text original){
  var id=Identifier.of("novex_companion","badge");
  return Text.empty().append(Text.literal("\uE000").setStyle(Style.EMPTY.withFont(id).withColor(0xFFFFFF))).append(" ").append(original.copy());
 }
}
