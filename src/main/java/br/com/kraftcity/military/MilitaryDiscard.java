package br.com.kraftcity.military;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.logistics.DiscardBinding;
import br.com.kraftcity.logistics.DiscardChests;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class MilitaryDiscard {
    static boolean tick(ServerLevel level,EmploymentData.Station station,Villager worker,MilitaryArea area,Player owner){
        var target=DiscardBinding.boundPosition(level,station);
        if(target==null||!level.hasChunkAt(target))return false;
        var center=Vec3.atCenterOf(target);
        boolean following=Military.mode(station)==Military.Mode.FOLLOW;
        if(following&&(owner==null||!area.contains(owner.position())||worker.distanceToSqr(owner)>49
            ||owner.distanceToSqr(center)>25))return false;

        var weapon=station.equipment.getItem(0).getItem();
        boolean ranged=weapon instanceof BowItem||weapon instanceof CrossbowItem;
        var offers=new int[SoldierMenu.STORAGE];
        for(int i=0;i<offers.length;i++){
            var stack=station.getItem(i);
            if(!ranged||!(stack.getItem() instanceof ArrowItem))offers[i]=stack.getCount();
        }
        if(!DiscardChests.hasOffers(offers)||!DiscardChests.accepts(level,station,worker,target,offers))return false;

        if(worker.distanceToSqr(center)<=3){
            var hit=level.clip(new ClipContext(worker.getEyePosition(),center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,worker));
            if(hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(target)){
                worker.getNavigation().stop();
                DiscardChests.deposit(level,station,worker,target,offers,"military_discard");
                return false;
            }
        }
        for(var side:Direction.Plane.HORIZONTAL){
            var beside=target.relative(side);
            for(var feet:new net.minecraft.core.BlockPos[]{beside,beside.below()}){
                var destination=Vec3.atBottomCenterOf(feet);
                if((!following||owner.distanceToSqr(destination)<=49)&&worker.distanceToSqr(destination)>.25
                    &&area.move(worker,destination,MilitaryArea.WALK))return true;
            }
        }
        return false;
    }

    private MilitaryDiscard(){}
}
