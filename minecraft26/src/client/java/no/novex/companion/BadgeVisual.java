package no.novex.companion;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import com.mojang.authlib.GameProfile;
public final class BadgeVisual {
 public static java.util.UUID profileId(GameProfile profile){return profile.id();}
 static Component prefix(Component original){
  var id=Identifier.fromNamespaceAndPath("novex_companion","badge");
  return Component.empty().append(Component.literal("\uE000").setStyle(Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(id)).withColor(0xFFFFFF))).append(" ").append(original.copy());
 }
}
