package no.novex.companion.mixin;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import no.novex.companion.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderer.class)
abstract class NametagBadgeMixin {
 @Inject(method="getNameTag",at=@At("RETURN"),cancellable=true)
 private void novexBadge(Entity entity,CallbackInfoReturnable<Component> ci){if(entity instanceof Player)ci.setReturnValue(BadgeManager.decorate(entity.getUUID(),ci.getReturnValue(),NovexConfig.Option.NAMETAG_BADGES));}
}
