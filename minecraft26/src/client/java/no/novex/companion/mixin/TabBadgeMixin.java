package no.novex.companion.mixin;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import no.novex.companion.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerTabOverlay.class)
abstract class TabBadgeMixin {
 @Inject(method="getNameForDisplay",at=@At("RETURN"),cancellable=true)
 private void novexBadge(PlayerInfo entry,CallbackInfoReturnable<Component> ci){ci.setReturnValue(BadgeManager.decorate(BadgeVisual.profileId(entry.getProfile()),ci.getReturnValue(),NovexConfig.Option.TAB_BADGES));}
}
