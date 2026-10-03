package no.novex.companion.mixin;
import no.novex.companion.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(MessageHandler.class)
abstract class ChatBadgeMixin {
 @ModifyVariable(method="onChatMessage",at=@At("HEAD"),argsOnly=true)
 private MessageType.Parameters novexBadge(MessageType.Parameters original,SignedMessage message,GameProfile profile,MessageType.Parameters parameters){
  return new MessageType.Parameters(original.type(),BadgeManager.decorate(BadgeVisual.profileId(profile),original.name(),NovexConfig.Option.CHAT_BADGES),original.targetName());
 }
}
