package no.novex.companion.mixin;
import no.novex.companion.*;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerListHud.class)
abstract class TabBadgeMixin {
 @Inject(method="getPlayerName",at=@At("RETURN"),cancellable=true)
 private void novexBadge(PlayerListEntry entry,CallbackInfoReturnable<Text> ci){ci.setReturnValue(BadgeManager.decorate(BadgeVisual.profileId(entry.getProfile()),ci.getReturnValue(),NovexConfig.Option.TAB_BADGES));}
}
