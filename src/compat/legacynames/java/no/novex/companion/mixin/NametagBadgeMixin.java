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
 @ModifyVariable(method="renderLabelIfPresent",at=@At("HEAD"),argsOnly=true)
 private Text novexBadge(Text original,Entity entity,Text text,net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider vertices,int light,float delta){
  return entity instanceof PlayerEntity?BadgeManager.decorate(entity.getUuid(),original,NovexConfig.Option.NAMETAG_BADGES):original;
 }
}
