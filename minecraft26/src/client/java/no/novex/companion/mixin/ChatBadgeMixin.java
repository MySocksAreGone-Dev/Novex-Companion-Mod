package no.novex.companion.mixin;
import no.novex.companion.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ChatListener.class)
abstract class ChatBadgeMixin {
 @ModifyVariable(method="handlePlayerChatMessage",at=@At("HEAD"),argsOnly=true)
 private ChatType.Bound novexBadge(ChatType.Bound original,PlayerChatMessage message,GameProfile profile,ChatType.Bound parameters){
  return new ChatType.Bound(original.chatType(),BadgeManager.decorate(BadgeVisual.profileId(profile),original.name(),NovexConfig.Option.CHAT_BADGES),original.targetName());
 }
}
