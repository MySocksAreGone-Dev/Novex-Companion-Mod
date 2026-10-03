package no.novex.companion.mixin;
import no.novex.companion.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderer.class)
abstract class NametagBadgeMixin {
 @Inject(method="getDisplayName",at=@At("RETURN"),cancellable=true)
 private void novexBadge(Entity entity,CallbackInfoReturnable<Text> ci){if(entity instanceof PlayerEntity)ci.setReturnValue(BadgeManager.decorate(entity.getUuid(),ci.getReturnValue(),NovexConfig.Option.NAMETAG_BADGES));}
}
