package br.com.residenciasaldeoes.mixin;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(VillagerModel.class)
public abstract class BuilderVillagerArmsMixin {
    @org.spongepowered.asm.mixin.Unique private boolean kraftcity$hidden,kraftcity$previous;
    @Inject(method="setupAnim",at=@At("TAIL")) private void kraftcity$builderArms(Entity e,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){var arms=((VillagerModel<?>)(Object)this).root().getChild("arms");if(br.com.kraftcity.builder.client.BuilderClient.working(e)){if(!kraftcity$hidden){kraftcity$previous=arms.visible;kraftcity$hidden=true;}arms.visible=false;}else if(kraftcity$hidden){arms.visible=kraftcity$previous;kraftcity$hidden=false;}}
}
