package br.com.residenciasaldeoes.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface SoldierModelPartAccessor {
    @Accessor("children") Map<String,ModelPart> kraftcity$children();
    @Accessor("cubes") List<ModelPart.Cube> kraftcity$cubes();
}
