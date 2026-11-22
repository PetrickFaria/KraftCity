package br.com.kraftcity.military.client;

import br.com.residenciasaldeoes.mixin.SoldierModelPartAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;

/** Anchors supplied by the active Fresh Animations 1.8 model geometry. */
final class FreshVillagerParts {
    record Anchor(List<ModelPart> chain,ModelPart.Cube geometry){
        void transform(PoseStack poses,boolean head){
            for(var part:chain)part.translateAndRotate(poses);
            poses.translate((geometry.minX+geometry.maxX)/32F,(head?geometry.maxY:geometry.minY)/16F,
                (geometry.minZ+geometry.maxZ)/32F);
        }
    }

    static Anchor head(ModelPart root){
        return find("root",root,"nose",8,10,8,false,new ArrayList<>());
    }

    static Anchor leg(ModelPart root,String name){
        var animated=find("root",root,name+"2",4,12,4,false,new ArrayList<>());
        return animated!=null?animated:find("root",root,name,4,12,4,false,new ArrayList<>());
    }

    private static Anchor find(String key,ModelPart part,String id,float width,float height,float depth,
                               boolean named,List<ModelPart> chain){
        if(!part.visible)return null;
        chain.add(part);
        named|=key.contains(id);
        var properties=(SoldierModelPartAccessor)(Object)part;
        if(named&&!part.skipDraw){
            for(var cube:properties.kraftcity$cubes())
                if(Math.abs(cube.maxX-cube.minX-width)<.1F&&Math.abs(cube.maxY-cube.minY-height)<.1F
                    &&Math.abs(cube.maxZ-cube.minZ-depth)<.1F)return new Anchor(List.copyOf(chain),cube);
        }
        for(var child:properties.kraftcity$children().entrySet()){
            var anchor=find(child.getKey(),child.getValue(),id,width,height,depth,named,chain);
            if(anchor!=null)return anchor;
        }
        chain.remove(chain.size()-1);
        return null;
    }

    private FreshVillagerParts(){}
}
