package br.com.kraftcity.military;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.*;
import net.minecraft.world.damagesource.DamageSource;

/** Server authority. Existing worker UUID, residential claim and station inventory are retained. */
public final class Military {
    public enum Mode { FREE, GUARD, FOLLOW }
    public enum Shift { DAY, NIGHT }
    public static final EquipmentSlot[] GEAR={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final Set<UUID> TRANSFERRING=new HashSet<>();
    public static boolean transferring(UUID id){return TRANSFERRING.contains(id);}
    public static EmploymentData.Station station(LivingEntity v){if(!(v.level instanceof ServerLevel l))return null;var data=EmploymentData.get(l);var active=data.assigned(v.getUUID());if(active!=null)return active;
        return v instanceof SoldierZombie?data.stations().stream().filter(s->s.military.getBoolean("zombie")&&s.military.hasUUID("identity")&&s.military.getUUID("identity").equals(v.getUUID())).findFirst().orElse(null):null;}
    public static boolean military(EmploymentData.Station s){return s!=null&&s.profession==Profession.SOLDIER;}
    public static boolean isSoldier(LivingEntity v){return military(station(v));}
    public static Mode mode(EmploymentData.Station s){String value=s.military.getString("mode");if(value.equals("KEEP")||value.equals("MANTER"))return Mode.GUARD;try{return Mode.valueOf(value);}catch(Exception e){return Mode.FREE;}}
    public static Shift shift(EmploymentData.Station s){return s.military.getString("shift").equals("NIGHT")?Shift.NIGHT:Shift.DAY;}
    public static int level(long milliXp){return 1+(int)Math.min(4,milliXp/75000);}
    public static int level(ServerLevel l,UUID id){
        var data=WorkforceData.get(l);
        return Math.max(levelForKills(data.soldierKills(id)),Math.min(5,data.progress(id,Profession.SOLDIER.id()).level()));
    }
    public static int levelForKills(long kills){return kills>=125?5:kills>=100?4:kills>=75?3:kills>=50?2:1;}
    public static int progressWidth(int level,long kills,int width){
        if(level>=5)return width;
        int start=level==1?0:level*25;
        int end=level==1?50:start+25;
        return (int)(width*Math.max(0,Math.min(end-start,kills-start))/(end-start));
    }
    private static boolean rangedWeapon(ItemStack stack){return stack.getItem() instanceof BowItem||stack.getItem() instanceof CrossbowItem;}
    public static double health(int level){return 14+4*(Math.min(5,Math.max(1,level))-1);}
    public static void trained(ServerLevel l,EmploymentData.Station s,Villager v,ServerPlayer player){
        if(!s.military.hasUUID("player"))s.military.putUUID("player",player.getUUID());
        s.military.putUUID("identity",v.getUUID());s.military.remove("target");s.military.remove("zombie");v.getPersistentData().putBoolean("KCSoldier",true);
        if(!v.getPersistentData().contains("KraftCityOriginalHealth"))v.getPersistentData().putDouble("KraftCityOriginalHealth",v.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
        stats(l,v);bind(s,v);s.setChanged();
    }
    public static void stats(ServerLevel l,LivingEntity v){
        if(v instanceof Villager&&!v.getPersistentData().contains("KraftCityOriginalHealth"))
            v.getPersistentData().putDouble("KraftCityOriginalHealth",20);
        var a=v.getAttribute(Attributes.MAX_HEALTH);if(a!=null)a.setBaseValue(health(level(l,v.getUUID())));if(v.getHealth()>v.getMaxHealth())v.setHealth(v.getMaxHealth());}
    public static boolean onDuty(ServerLevel l,EmploymentData.Station s){long t=Math.floorMod(l.getDayTime(),24000);boolean night=t>=13000&&t<23000;return shift(s)==Shift.NIGHT?night:!night;}
    public static void residentHealth(ServerLevel level,Villager villager){
        if(isSoldier(villager)){
            stats(level,villager);
            return;
        }
        var data=villager.getPersistentData();
        var health=villager.getAttribute(Attributes.MAX_HEALTH);
        if(health==null)return;
        boolean managed=ResidenceData.get(level).owner(villager.getUUID())!=null||Employment.active(villager);
        if(managed){
            if(!data.contains("KraftCityOriginalHealth"))data.putDouble("KraftCityOriginalHealth",health.getBaseValue());
            health.setBaseValue(14);
        }else if(data.contains("KraftCityOriginalHealth")){
            health.setBaseValue(data.getDouble("KraftCityOriginalHealth"));
            data.remove("KraftCityOriginalHealth");
        }
        if(villager.getHealth()>villager.getMaxHealth())villager.setHealth(villager.getMaxHealth());
    }
    public static void bind(EmploymentData.Station s,Mob v){for(int i=0;i<6;i++){var gear=s.equipment.getItem(i);if(gear.isDamageableItem()&&gear.getDamageValue()>=gear.getMaxDamage())s.equipment.setItem(i,ItemStack.EMPTY);if(v.getItemBySlot(GEAR[i])!=s.equipment.getItem(i)){
            if(i==0){v.stopUsingItem();s.military.remove("drawAt");}
            v.setItemSlot(GEAR[i],s.equipment.getItem(i));
        }v.setDropChance(GEAR[i],0);}v.setCanPickUpLoot(false);v.setLeftHanded(false);}
    public static void release(ServerLevel l,UUID worker){var s=EmploymentData.get(l).assigned(worker);if(!military(s))return;s.military.remove("target");
        for(var world:l.getServer().getAllLevels())if(world.getEntity(worker) instanceof SoldierZombie z){for(var slot:GEAR)z.setItemSlot(slot,ItemStack.EMPTY);}else if(world.getEntity(worker) instanceof Villager v){v.stopUsingItem();for(var slot:GEAR)v.setItemSlot(slot,ItemStack.EMPTY);v.getPersistentData().remove("KCSoldier");}
        s.setChanged();
    }
    public static boolean selected(EmploymentData.Station s,EntityType<?> type){
        var key=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
        if(key==null)return false;
        var choices=s.military.getCompound("blacklist");
        if(s.military.contains("blacklist",net.minecraft.nbt.Tag.TAG_COMPOUND))return choices.getBoolean(key.toString());
        return key.getNamespace().equals("minecraft")&&type!=EntityType.CREEPER&&(MilitaryNetwork.category(type,null)==2||type==EntityType.SPIDER||type==EntityType.CAVE_SPIDER);
    }
    public static void select(EmploymentData.Station s,EntityType<?> type,boolean on){
        var key=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
        if(key==null)return;
        var choices=s.military.getCompound("blacklist");
        if(!s.military.contains("blacklist",net.minecraft.nbt.Tag.TAG_COMPOUND)){
            for(var candidate:net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES)
                if(selected(s,candidate))choices.putBoolean(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(candidate).toString(),true);
        }
        choices.putBoolean(key.toString(),on);
        s.military.put("blacklist",choices);
        s.setChanged();
    }
    public static boolean protectedArea(ServerLevel level,LivingEntity target){
        return protectedArea(level,target,null);
    }

    private static boolean protectedArea(ServerLevel level,LivingEntity target,MilitaryArea area){
        for(var home:ResidenceData.get(level).homes()){
            if(!home.dimension.equals(level.dimension())||level.hasChunkAt(home.pos)
                &&!(level.getBlockEntity(home.pos) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity))continue;
            int radius=home.size/2;
            var bounds=new AABB(home.pos.offset(-radius,1,-radius),home.pos.offset(radius+1,home.size+1,radius+1));
            if((area==null||area.intersects(bounds))&&distanceTo(bounds,target.position())<=25)return true;
        }
        for(var post:EmploymentData.get(level).stations()){
            if(!post.active||post.worker==null||post.sector==Profession.Sector.MILITARY||!post.dimension.equals(level.dimension())
                ||level.hasChunkAt(post.pos)&&(!(level.getBlockEntity(post.pos) instanceof WorkBlockEntity be)||!post.id.equals(be.identity())))continue;
            var bounds=post.sector==Profession.Sector.INDUSTRIAL
                ?br.com.kraftcity.industry.IndustrialWork.area(level,post):new AABB(post.pos);
            if((area==null||area.intersects(bounds))&&distanceTo(bounds,target.position())<=25)return true;
            var work=WorkforceData.get(level).worker(post.worker).flatMap(br.com.kraftcity.workforce.WorkerRecord::area);
            if(work.isPresent()&&work.get().dimension().equals(level.dimension())){
                var region=new AABB(work.get().min(),work.get().max().offset(1,1,1));
                if((area==null||area.intersects(region))&&distanceTo(region,target.position())<=25)return true;
            }
        }
        var construction=br.com.kraftcity.builder.BuilderData.get(level);
        for(var job:construction.jobs.values()){
            if(job.cancelled||!job.dimension.equals(level.dimension()))continue;
            var paper=construction.papers.get(job.paper);
            var blueprint=paper==null?null:construction.library.get(paper.schema);
            if(blueprint==null||job.done.cardinality()>=blueprint.cells.size())continue;
            var bounds=new AABB(job.origin,job.origin.offset(blueprint.transformedSize(job.rotation)));
            if((area==null||area.intersects(bounds))&&distanceTo(bounds,target.position())<=25)return true;
        }
        return false;
    }

    private static double distanceTo(AABB box,Vec3 p){
        double dx=Math.max(0,Math.max(box.minX-p.x,p.x-box.maxX));
        double dy=Math.max(0,Math.max(box.minY-p.y,p.y-box.maxY));
        double dz=Math.max(0,Math.max(box.minZ-p.z,p.z-box.maxZ));
        return dx*dx+dy*dy+dz*dz;
    }

    private static Player linkedPlayer(ServerLevel level,EmploymentData.Station station){
        return station.military.hasUUID("player")?level.getPlayerByUUID(station.military.getUUID("player")):null;
    }

    private static boolean struck(LivingEntity victim,LivingEntity attacker){
        return victim.getLastHurtByMob()==attacker&&victim.tickCount-victim.getLastHurtByMobTimestamp()<100;
    }

    private static boolean ally(ServerLevel level,Villager soldier,LivingEntity candidate){
        return candidate==soldier||candidate instanceof Player||soldier.isAlliedTo(candidate)||candidate.isAlliedTo(soldier)
            ||candidate instanceof Villager&&(ResidenceData.get(level).owner(candidate.getUUID())!=null||Employment.active((Villager)candidate));
    }

    private static int priority(ServerLevel level,EmploymentData.Station station,Villager soldier,LivingEntity candidate,MilitaryArea area){
        if(!(candidate instanceof Mob)||!candidate.isAlive()||candidate.isInvulnerable()||!area.contains(candidate.position())||ally(level,soldier,candidate))
            return Integer.MAX_VALUE;
        if(!selected(station,candidate.getType()))return Integer.MAX_VALUE;
        if(mode(station)==Mode.FOLLOW){
            var player=linkedPlayer(level,station);
            if(player!=null&&area.contains(player.position())){
                if(struck(player,candidate))return 0;
                var orders=station.military.getCompound("orders");
                String key=candidate.getUUID().toString();
                if(candidate.distanceToSqr(player)<=100&&orders.contains(key)&&level.getGameTime()-orders.getLong(key)>=0
                    &&level.getGameTime()-orders.getLong(key)<=MilitaryModule.CONTRIBUTION_TICKS.get())return 0;
            }
            if(struck(soldier,candidate))return 1;
            return player!=null&&area.contains(player.position())&&candidate.distanceToSqr(player)<=100?2:Integer.MAX_VALUE;
        }
        boolean nearResident=false;
        for(var resident:level.getEntitiesOfClass(Villager.class,candidate.getBoundingBox().inflate(10),
            villager->villager!=soldier&&villager.isAlive()&&area.contains(villager.position())&&villager.distanceToSqr(candidate)<=100
                &&(ResidenceData.get(level).owner(villager.getUUID())!=null||Employment.active(villager)))){
            if(struck(resident,candidate))return 0;
            nearResident=true;
        }
        if(nearResident)return 1;
        if(protectedArea(level,candidate,area))return 2;
        if(struck(soldier,candidate))return 3;
        int localRange=Math.min(10,MilitaryModule.DETECTION.get());
        return soldier.distanceToSqr(candidate)<=localRange*localRange?4:Integer.MAX_VALUE;
    }

    public static boolean authorized(ServerLevel l,EmploymentData.Station s,Villager v,LivingEntity target){
        var area=new MilitaryArea(s);
        return area.contains(v.position())&&onDuty(l,s)&&!v.isSleeping()&&Employment.valid(l,v.getUUID())
            &&priority(l,s,v,target,area)!=Integer.MAX_VALUE;
    }

    public static LivingEntity target(ServerLevel l,EmploymentData.Station s,Villager v){
        var area=new MilitaryArea(s);
        LivingEntity best=null;
        int rank=Integer.MAX_VALUE;
        boolean ranged=rangedWeapon(s.equipment.getItem(0));
        UUID previous=s.military.hasUUID("target")?s.military.getUUID("target"):null;
        double distance=Double.POSITIVE_INFINITY;
        var mode=mode(s);
        boolean protectsTerritory=mode==Mode.FREE||mode==Mode.GUARD;
        var search=protectsTerritory?area.bounds():v.getBoundingBox().inflate(Math.min(32,MilitaryModule.DETECTION.get()));
        for(var candidate:l.getEntitiesOfClass(Mob.class,search,mob->mob!=v&&selected(s,mob.getType()))){
            int nextRank=priority(l,s,v,candidate,area);
            if(!eligible(l,s,v,candidate,area))continue;
            boolean visible=v.hasLineOfSight(candidate);
            boolean remembered=!visible&&candidate.getUUID().equals(previous)&&MilitaryPursuit.remembered(v,candidate);
            if(nextRank==Integer.MAX_VALUE&&!remembered)continue;
            boolean territorial=protectsTerritory&&nextRank<=2;
            if(!visible&&!remembered&&(!territorial||MilitaryPursuit.seenBefore(v,candidate)))continue;
            double nextDistance=v.distanceToSqr(candidate);
            if(ranged&&candidate.getUUID().equals(previous))nextDistance=Math.max(0,nextDistance-2);
            boolean local=ranged&&nextDistance<=100;
            boolean previousLocal=ranged&&distance<=100;
            boolean better=local||previousLocal
                ?local&&(!previousLocal||nextDistance<distance)
                :nextRank<rank||nextRank==rank&&nextDistance<distance;
            if(best==null||better){
                if(!territorial&&!remembered&&!canEngage(s,v,candidate,area,nextDistance,true))continue;
                best=candidate;rank=nextRank;distance=nextDistance;
            }
        }
        return best;
    }

    private static boolean eligible(ServerLevel level,EmploymentData.Station station,Villager soldier,LivingEntity candidate,MilitaryArea area){
        return candidate instanceof Mob&&candidate.isAlive()&&!candidate.isInvulnerable()&&area.contains(candidate.position())
            &&!ally(level,soldier,candidate)&&selected(station,candidate.getType());
    }

    private static boolean canEngage(EmploymentData.Station station,Villager soldier,LivingEntity target,
                                     MilitaryArea area,double distance,boolean visible){
        if(visible&&distance<=4)return true;
        var weapon=station.equipment.getItem(0);
        boolean ranged=weapon.getItem() instanceof BowItem||weapon.getItem() instanceof CrossbowItem;
        if(visible&&ranged&&distance<=36&&(ammo(station)>=0||CrossbowItem.isCharged(weapon)))return true;
        return area.canReach(soldier,target.position());
    }

    public static Vec3 limitMovement(Villager soldier,Vec3 movement){
        if(!(soldier.level instanceof ServerLevel level)||!soldier.getPersistentData().getBoolean("KCSoldier"))return movement;
        var station=station(soldier);
        if(!military(station)||!onDuty(level,station))return movement;
        return MilitaryLoot.limitMovement(soldier,new MilitaryArea(station).limit(soldier.position(),movement));
    }

    public static AABB areaBounds(BlockPos post){
        return MilitaryArea.boundsAt(post);
    }

    public static boolean insideArea(EmploymentData.Station station,Vec3 point){
        return military(station)&&new MilitaryArea(station).contains(point);
    }

    public static void clearTask(Villager soldier,EmploymentData.Station station){
        MilitaryLoot.finish(soldier);
        MilitaryCombatMovement.clear(soldier);
        MilitaryArea.clearRoutes(soldier);
        soldier.getNavigation().stop();
        soldier.stopUsingItem();
        station.military.remove("target");
        station.military.remove("drawAt");
        station.military.remove("retreatUntil");
    }

    public static void tick(ServerLevel l,EmploymentData.Station s,Villager v){
        if(!Employment.valid(l,v.getUUID()))return;
        bind(s,v);
        stats(l,v);
        if(!onDuty(l,s)){
            if(!s.military.getBoolean("resting"))clearTask(v,s);
            s.military.putBoolean("resting",true);
            if(!br.com.residenciasaldeoes.VillagerResidenceEvents.ownsWorkerRest(v))sleep(l,s,v);
            heal(l,v);
            return;
        }
        s.military.remove("resting");
        if(v.isSleeping()){v.stopSleeping();v.getNavigation().stop();}
        heal(l,v);
        var area=new MilitaryArea(s);
        var navigation=v.getNavigation();
        if(area.contains(v.position())&&!area.permits(navigation.getPath()))navigation.stop();
        if(!area.contains(v.position())){
            area.move(v,area.nearest(v.position()),MilitaryArea.WALK);
            return;
        }
        LivingEntity threat=s.military.hasUUID("target")&&l.getEntity(s.military.getUUID("target")) instanceof LivingEntity current
            &&eligible(l,s,v,current,area)&&(priority(l,s,v,current,area)!=Integer.MAX_VALUE||MilitaryPursuit.remembered(v,current))
            ?current:null;
        if(threat!=null&&!v.hasLineOfSight(threat)&&MilitaryPursuit.seenBefore(v,threat)
            &&!MilitaryPursuit.remembered(v,threat))threat=null;
        if(v.tickCount%10==0||threat==null&&s.military.hasUUID("target")){
            MilitaryPursuit.prune(v,l);
            threat=target(l,s,v);
        }
        if(threat!=null){
            if(!s.military.hasUUID("target")||!s.military.getUUID("target").equals(threat.getUUID()))clearTask(v,s);
            s.military.putUUID("target",threat.getUUID());
            if(v.hasLineOfSight(threat)){
                MilitaryPursuit.observe(v,threat,true);
                combat(l,s,v,threat);
            }else{
                if(!MilitaryPursuit.seenBefore(v,threat))MilitaryPursuit.observe(v,threat,false);
                v.stopUsingItem();
                s.military.remove("drawAt");
                var last=MilitaryPursuit.lastPosition(v,threat);
                boolean leavingPost=mode(s)==Mode.GUARD&&area.leaveGuardPost(s.pos,v,last);
                if(!leavingPost&&!area.approach(v,last,MilitaryArea.CHASE))v.getNavigation().stop();
            }
            s.setChanged();
            return;
        }
        if(s.military.hasUUID("target"))clearTask(v,s);
        if(rangedWeapon(s.equipment.getItem(0))&&retreat(l,s,v,area))return;
        if(mode(s)!=Mode.FOLLOW&&MilitaryLoot.collect(l,s,v,area,true))return;
        MilitaryLoot.finish(v);
        if(MilitaryDiscard.tick(l,s,v,area,mode(s)==Mode.FOLLOW?linkedPlayer(l,s):null))return;
        if(mode(s)==Mode.FOLLOW){
            var owner=linkedPlayer(l,s);
            if(owner!=null){
                boolean inside=area.contains(owner.position());
                var destination=inside?owner.position():area.nearest(owner.position());
                if(v.position().distanceToSqr(destination)>(inside?49:1)){
                    if(!area.approach(v,destination,MilitaryArea.WALK)&&v.tickCount%10==0)navigation.stop();
                    return;
                }
            }
            navigation.stop();
            MilitaryLoot.collect(l,s,v,area,false);
        }else if(mode(s)==Mode.GUARD){
            var point=Vec3.atBottomCenterOf(s.pos.above());
            if(v.distanceToSqr(point)>.25)area.approach(v,point,MilitaryArea.WALK);
            else if(v.distanceToSqr(point)>.0025){
                navigation.stop();
                v.getMoveControl().setWantedPosition(point.x,point.y,point.z,MilitaryArea.WALK);
            }else{
                navigation.stop();
                if(v.distanceToSqr(point)>1e-8)v.setPos(point.x,point.y,point.z);
                v.setDeltaMovement(Vec3.ZERO);
                if(l.hasChunkAt(s.pos)&&l.getBlockState(s.pos).hasProperty(WorkBlock.FACING)){
                    var facing=l.getBlockState(s.pos).getValue(WorkBlock.FACING);
                    float yaw=facing.toYRot();
                    v.setYRot(yaw);v.setYHeadRot(yaw);v.setYBodyRot(yaw);v.setXRot(0);
                    v.getLookControl().setLookAt(v.getX()+facing.getStepX()*5,v.getEyeY(),v.getZ()+facing.getStepZ()*5);
                }
            }
        }else if(navigation.isDone()&&v.tickCount%10==0){
            for(int attempt=0;attempt<6;attempt++){
                var destination=v.blockPosition().offset(v.getRandom().nextInt(25)-12,v.getRandom().nextInt(5)-2,v.getRandom().nextInt(25)-12);
                if(area.move(v,area.nearest(Vec3.atBottomCenterOf(destination)),MilitaryArea.WALK))break;
            }
        }
    }
    private static void heal(ServerLevel level,Villager villager){
        if(villager.isSleeping())return;
        int interval=MilitaryModule.HEAL_TICKS.get();
        if(villager.tickCount%interval==0&&villager.getHealth()<villager.getMaxHealth())villager.heal(1);
    }
    private static void sleep(ServerLevel l,EmploymentData.Station s,Villager v){
        if(v.isSleeping())return;var data=ResidenceData.get(l);var home=data.owner(v.getUUID());if(home==null||!l.hasChunkAt(home.pos))return;
        BlockPos bed=data.bedClaim(v.getUUID())==null?null:data.bedClaim(v.getUUID()).bed();
        if(bed==null||!(l.getBlockState(bed).getBlock() instanceof BedBlock)){
            bed=null;int r=home.size/2;for(var p:BlockPos.betweenClosed(home.pos.offset(-r,-2,-r),home.pos.offset(r,4,r)))if(l.hasChunkAt(p)&&l.getBlockState(p).getBlock() instanceof BedBlock&&l.getBlockState(p).getValue(BedBlock.PART)==BedPart.HEAD&&(data.bedOwner(l.dimension(),p)==null||data.ownsBed(l.dimension(),p,v.getUUID()))&&data.reserveBed(v.getUUID(),l.dimension(),home.pos,p)){bed=p.immutable();break;}
        }
        if(bed==null){if(v.distanceToSqr(Vec3.atCenterOf(home.pos.above()))>4)v.getNavigation().moveTo(home.pos.getX()+.5,home.pos.getY()+1,home.pos.getZ()+.5,.7);else v.getNavigation().stop();return;}if(v.distanceToSqr(Vec3.atCenterOf(bed))<=4){v.getNavigation().stop();v.startSleeping(bed);}else v.getNavigation().moveTo(bed.getX()+.5,bed.getY(),bed.getZ()+.5,.7);
    }
    public static void combat(ServerLevel l,EmploymentData.Station s,Villager v,LivingEntity t){
        var area=new MilitaryArea(s);
        if(!area.contains(v.position())||!area.contains(t.position()))return;
        double distance=v.distanceToSqr(t);ItemStack weapon=s.equipment.getItem(0);
        boolean ranged=rangedWeapon(weapon);
        faceThreat(v,t,ranged||distance<=4);
        if(ranged&&(ammo(s)>=0||weapon.getItem() instanceof CrossbowItem&&CrossbowItem.isCharged(weapon))){ranged(l,s,v,t,weapon,distance);return;}
        MilitaryCombatMovement.clear(v);
        long now=l.getGameTime();
        if(now<s.military.getLong("retreatUntil")){Vec3 away=v.position().subtract(t.position()).normalize().scale(3);area.move(v,area.nearest(v.position().add(away.x,0,away.z)),MilitaryArea.CHASE);return;}
        if(distance>4){
            if(mode(s)!=Mode.GUARD||!area.leaveGuardPost(s.pos,v,t.position()))area.approach(v,t.position(),MilitaryArea.CHASE);
            shield(v,true);
            return;
        }
        if(!v.hasLineOfSight(t)){area.move(v,t.position(),MilitaryArea.CHASE);shield(v,true);return;}
        v.getNavigation().stop();if(now<s.military.getLong("attackAt")){shield(v,true);return;}
        v.stopUsingItem();v.swing(InteractionHand.MAIN_HAND);
        if(v.doHurtTarget(t)){s.military.putLong("lastMeleeHitAt",now);if(weapon.getItem() instanceof SwordItem||weapon.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_SWEEP))weapon.hurtAndBreak(1,v,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));s.setChanged();}
        s.military.putLong("attackAt",now+28);s.military.putLong("retreatUntil",now+10);
    }
    private static void faceThreat(Villager soldier,LivingEntity threat,boolean turnBody){
        var offset=threat.getEyePosition().subtract(soldier.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(offset.z,offset.x))-90F;
        float pitch=(float)-Math.toDegrees(Math.atan2(offset.y,Math.sqrt(offset.horizontalDistanceSqr())));
        var look=soldier.getLookControl();
        look.setLookAt(threat,30,25);
        soldier.setYHeadRot(net.minecraft.util.Mth.approachDegrees(soldier.getYHeadRot(),yaw,30));
        soldier.setXRot(net.minecraft.util.Mth.approachDegrees(soldier.getXRot(),pitch,25));
        if(turnBody){
            float facing=net.minecraft.util.Mth.approachDegrees(soldier.getYRot(),yaw,30);
            soldier.setYRot(facing);
            soldier.setYBodyRot(facing);
        }
    }

    private static void shield(Villager v,boolean enabled){if(enabled&&v.getOffhandItem().canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK)&&!v.isUsingItem())v.startUsingItem(InteractionHand.OFF_HAND);}
    private static int ammo(EmploymentData.Station s){for(int i=0;i<31;i++)if(s.getItem(i).getItem() instanceof ArrowItem)return i;return -1;}
    private static void ranged(ServerLevel l,EmploymentData.Station s,Villager v,LivingEntity t,ItemStack weapon,double distance){
        var area=new MilitaryArea(s);
        boolean retreating=retreat(l,s,v,area);
        MilitaryCombatMovement.aim(v,t);
        if(!retreating){
            MilitaryLoot.finish(v);
            if(distance>36)area.approach(v,t.position(),MilitaryArea.CHASE);
            else v.getNavigation().stop();
        }
        if(!v.hasLineOfSight(t)){
            s.military.remove("drawAt");v.stopUsingItem();
            if(!retreating)area.approach(v,t.position(),MilitaryArea.CHASE);
            return;
        }
        if(weapon.getItem() instanceof BowItem&&l.getGameTime()<MilitaryBow.readyAt(s,v)-20){
            s.military.remove("drawAt");v.stopUsingItem();return;
        }
        boolean crossbow=weapon.getItem() instanceof CrossbowItem;
        if(crossbow&&l.getGameTime()<s.military.getLong("crossbowReadyAt")-CrossbowItem.getChargeDuration(weapon)){
            s.military.remove("drawAt");v.stopUsingItem();return;
        }
        float speed=crossbow?3.15F:1.6F;
        var direction=MilitaryAim.trajectory(l,v,t,speed);
        if(direction==null){
            s.military.remove("drawAt");v.stopUsingItem();
            if(!retreating)area.approach(v,t.position(),MilitaryArea.CHASE);
            return;
        }
        var lookPoint=v.getEyePosition().add(direction.scale(8));
        v.getLookControl().setLookAt(lookPoint.x,lookPoint.y,lookPoint.z,30,25);
        float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.horizontalDistanceSqr())));
        v.setXRot(net.minecraft.util.Mth.approachDegrees(v.getXRot(),pitch,25F));
        var line=v.getEyePosition();
        var end=t.getBoundingBox().getCenter();
        var box=new AABB(line,end).inflate(.5);
        if(l.getEntitiesOfClass(LivingEntity.class,box,entity->entity!=v&&entity!=t&&ally(l,v,entity)).stream()
            .anyMatch(entity->entity.getBoundingBox().inflate(.15).clip(line,end).isPresent()))return;
        if(crossbow&&CrossbowItem.isCharged(weapon)){
            if(l.getGameTime()<s.military.getLong("crossbowReadyAt"))return;
            v.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,lookPoint);
            CrossbowItem.performShooting(l,v,InteractionHand.MAIN_HAND,weapon,speed,.35F);
            CrossbowItem.setCharged(weapon,false);
            s.military.putLong("crossbowReadyAt",l.getGameTime()+60);
            s.military.remove("drawAt");v.stopUsingItem();s.setChanged();return;
        }
        if(!s.military.contains("drawAt")){s.military.putLong("drawAt",l.getGameTime());v.stopUsingItem();v.startUsingItem(InteractionHand.MAIN_HAND);return;}
        int quick=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE,weapon);int load=weapon.getItem() instanceof CrossbowItem?Math.max(5,25-5*quick):20;
        if(l.getGameTime()-s.military.getLong("drawAt")<load
            ||weapon.getItem() instanceof BowItem&&l.getGameTime()<MilitaryBow.readyAt(s,v)
            ||crossbow&&l.getGameTime()<s.military.getLong("crossbowReadyAt"))return;
        int slot=ammo(s);if(slot<0)return;var ammunition=s.getItem(slot);boolean infinity=weapon.getItem() instanceof BowItem&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS,weapon)>0&&ammunition.is(Items.ARROW);
        int shots=weapon.getItem() instanceof CrossbowItem&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT,weapon)>0?3:1;
        int fired=0;
        for(int i=0;i<shots;i++){
            var arrow=((ArrowItem)ammunition.getItem()).createArrow(l,ammunition,v);arrow.setEnchantmentEffectsFromEntity(v,1F);arrow.setBaseDamage(weapon.getItem() instanceof CrossbowItem?2:arrow.getBaseDamage());
            if(weapon.getItem() instanceof CrossbowItem){arrow.setShotFromCrossbow(true);arrow.setPierceLevel((byte)EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING,weapon));}
            if(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS,weapon)>0)arrow.setSecondsOnFire(100);
            arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(direction.x,direction.y,direction.z,speed,.35F);
            if(shots>1)arrow.setDeltaMovement(arrow.getDeltaMovement().yRot((float)Math.toRadians((i-1)*10)));arrow.getPersistentData().putUUID("KCSoldierShot",v.getUUID());
            if(weapon.getItem() instanceof BowItem)arrow.getPersistentData().putBoolean("KCSoldierBow",true);
            if(l.addFreshEntity(arrow))fired++;
        }
        if(fired==0){s.military.remove("drawAt");v.stopUsingItem();return;}
        if(crossbow)s.military.putLong("crossbowReadyAt",l.getGameTime()+60);
        if(!infinity)ammunition.shrink(1);weapon.hurtAndBreak(fired,v,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));s.military.remove("drawAt");v.stopUsingItem();s.setChanged();
    }
    private static boolean retreat(ServerLevel level,EmploymentData.Station station,Villager soldier,MilitaryArea area){
        var threats=level.getEntitiesOfClass(Mob.class,soldier.getBoundingBox().inflate(6),mob->
            mob!=soldier&&mob.isAlive()&&!ally(level,soldier,mob)&&mob.distanceToSqr(soldier)<=36
                &&(mob.getTarget()==soldier||struck(soldier,mob)
                    ||mob instanceof Creeper creeper&&creeper.getSwellDir()>0
                    ||mob.distanceToSqr(soldier)<16&&priority(level,station,soldier,mob,area)!=Integer.MAX_VALUE));
        if(threats.isEmpty())return false;
        var origin=soldier.position();
        var protectedAreas=MilitaryLoot.protectedAreas(level,area);
        // A soldier already defending a protected area may leave it during a retreat.
        var newlyEntered=protectedAreas.stream().filter(box->!box.contains(origin)).toList();
        var candidates=new ArrayList<Vec3>();
        for(int dx=-4;dx<=4;dx+=2)for(int dz=-4;dz<=4;dz+=2)
            if(dx!=0||dz!=0)candidates.add(Vec3.atBottomCenterOf(soldier.blockPosition().offset(dx,0,dz)));
        double current=clearance(origin,threats);
        candidates.sort(Comparator.comparingDouble((Vec3 point)->clearance(point,threats)).reversed());
        for(var point:candidates){
            if(clearance(point,threats)<=current+.5)break;
            if(area.move(soldier,point,MilitaryArea.CHASE,node->{
                var feet=new BlockPos(node);
                return !MilitaryLoot.blocked(newlyEntered,node)&&level.getFluidState(feet).isEmpty()
                    &&!level.getFluidState(feet.below()).is(net.minecraft.tags.FluidTags.LAVA)
                    &&Math.abs(node.y-origin.y)<=1&&clearance(node,threats)>=Math.min(current,4)-.25;
            })){
                MilitaryLoot.protectRoute(soldier,protectedAreas);
                return true;
            }
        }
        soldier.getNavigation().stop();
        return true;
    }

    private static double clearance(Vec3 point,List<? extends LivingEntity> threats){
        return threats.stream().mapToDouble(mob->mob.distanceToSqr(point)).min().orElse(Double.POSITIVE_INFINITY);
    }

    public static void contribute(ServerLevel l,LivingEntity target,UUID soldier){var c=target.getPersistentData().getCompound("KCMilitaryContributors");c.putLong(soldier.toString(),l.getGameTime());target.getPersistentData().put("KCMilitaryContributors",c);target.getPersistentData().putUUID("KCMilitaryLast",soldier);}
    public static void award(ServerLevel l,LivingEntity dead,Entity killer){
        var c=dead.getPersistentData().getCompound("KCMilitaryContributors");
        if(c.isEmpty())return;
        dead.getPersistentData().remove("KCMilitaryContributors");
        long now=l.getGameTime();int reward=l.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)?dead.getExperienceReward():0;
        UUID finalSoldier=killer instanceof LivingEntity v&&isSoldier(v)?v.getUUID():null;
        UUID last=dead.getPersistentData().hasUUID("KCMilitaryLast")?dead.getPersistentData().getUUID("KCMilitaryLast"):null;
        for(String key:c.getAllKeys())try{UUID id=UUID.fromString(key);if(now-c.getLong(key)>MilitaryModule.CONTRIBUTION_TICKS.get()||now<c.getLong(key))continue;
            int permille=id.equals(finalSoldier)?1000:killer instanceof Player&&id.equals(last)?500:finalSoldier!=null||killer instanceof Player?100:0;
            if(permille==0)continue;var data=WorkforceData.get(l);
            if(id.equals(finalSoldier)&&!dead.getPersistentData().getBoolean("KCMilitaryKillRecorded")){
                data.recordSoldierKill(id);
                dead.getPersistentData().putBoolean("KCMilitaryKillRecorded",true);
            }
            long xp=data.progress(id,Profession.SOLDIER.id()).xp()+(long)reward*permille;
            data.setProgress(id,Profession.SOLDIER.id(),level(l,id),xp);if(l.getEntity(id) instanceof LivingEntity v&&isSoldier(v))stats(l,v);
        }catch(IllegalArgumentException ignored){}
        dead.getPersistentData().remove("KCMilitaryContributors");
    }
    public static SoldierZombie zombify(ServerLevel l,Villager v){
        var s=station(v);if(!military(s))return null;var z=MilitaryModule.ZOMBIE.get().create(l);if(z==null)return null;
        v.stopSleeping();z.setUUID(v.getUUID());z.moveTo(v.getX(),v.getY(),v.getZ(),v.getYRot(),v.getXRot());z.setVillagerData(v.getVillagerData());z.setCustomName(v.getCustomName());z.getPersistentData().merge(v.getPersistentData().copy());s.military.putBoolean("zombie",true);var snapshot=v.saveWithoutId(new CompoundTag());snapshot.remove("HandItems");snapshot.remove("ArmorItems");s.military.put("villager",snapshot);s.military.remove("target");
        stats(l,z);z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(v.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));z.setHealth(Math.max(1,Math.min(v.getHealth(),z.getMaxHealth())));bind(s,z);TRANSFERRING.add(v.getUUID());try{for(var slot:GEAR)v.setItemSlot(slot,ItemStack.EMPTY);v.discard();l.addFreshEntity(z);}finally{TRANSFERRING.remove(v.getUUID());}s.setChanged();if(l.getEntity(z.getUUID())==z)notice(l,s,z,"zombified");return z;
    }
    public static void detachZombie(ServerLevel level,EmploymentData.Station station){
        if(!station.military.getBoolean("zombie"))return;
        EmploymentData.get(level).saveDetachedSoldier(station.worker,station.military.getCompound("villager"));
        station.military.remove("zombie");
        station.military.remove("villager");
        station.military.remove("identity");
        station.setChanged();
    }
    public static Villager cure(ServerLevel l,SoldierZombie z){
        var s=station(z);var data=EmploymentData.get(l);
        boolean attached=military(s)&&s.military.getBoolean("zombie");
        var nbt=attached?s.military.getCompound("villager").copy():data.detachedSoldier(z.getUUID());
        if(!attached&&nbt.isEmpty())return null;
        var v=EntityType.VILLAGER.create(l);if(v==null)return null;nbt.remove("UUID");nbt.remove("Passengers");nbt.remove("HandItems");nbt.remove("ArmorItems");v.load(nbt);v.setUUID(z.getUUID());v.moveTo(z.getX(),z.getY(),z.getZ(),z.getYRot(),z.getXRot());v.setNoAi(false);v.setVillagerData(z.getVillagerData().setProfession(VillagerProfession.NONE));
        if(attached){s.military.remove("zombie");s.military.remove("villager");s.setChanged();}
        data.forgetDetachedSoldier(z.getUUID());
        for(var slot:GEAR)z.setItemSlot(slot,ItemStack.EMPTY);
        z.discard();
        if(attached&&s.active&&v.getUUID().equals(s.worker)){stats(l,v);bind(s,v);}
        else{v.getPersistentData().remove("KCSoldier");residentHealth(l,v);}
        v.setHealth(Math.max(1,Math.min(v.getHealth(),v.getMaxHealth())));if(l.addFreshEntity(v))notice(l,s,v,"cured");return v;
    }
    public static void death(ServerLevel l,LivingEntity v){
        var s=station(v);if(!military(s))return;boolean drops=l.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        for(int i=0;i<6;i++){var stack=s.equipment.removeItemNoUpdate(i);if(drops&&!stack.isEmpty()&&!EnchantmentHelper.hasVanishingCurse(stack))v.spawnAtLocation(stack);}
        if(v instanceof Mob mob)for(var slot:GEAR)mob.setItemSlot(slot,ItemStack.EMPTY);
        var message=noticeMessage(l,v,"died");
        s.military.remove("zombie");s.military.remove("villager");s.setChanged();Employment.release(l,v.getUUID());ResidenceData.get(l).unlink(l,v.getUUID());
        sendNotice(l,s,v,message);
    }

    private static net.minecraft.network.chat.Component noticeMessage(ServerLevel level,LivingEntity soldier,String event){
        var residence=ResidenceData.get(level);
        String name=residence.owner(soldier.getUUID())!=null?residence.residentName(soldier.getUUID()):soldier.getDisplayName().getString();
        return net.minecraft.network.chat.Component.translatable("message.kraftcity.soldier."+event,name);
    }

    private static void notice(ServerLevel level,EmploymentData.Station station,LivingEntity soldier,String event){
        sendNotice(level,station,soldier,noticeMessage(level,soldier,event));
    }

    private static void sendNotice(ServerLevel level,EmploymentData.Station station,LivingEntity soldier,
                                   net.minecraft.network.chat.Component message){
        UUID owner=station!=null&&station.military.hasUUID("player")?station.military.getUUID("player"):null;
        for(var player:level.getServer().getPlayerList().getPlayers()){
            if(player.getUUID().equals(owner)||player.level==level&&player.distanceToSqr(soldier)<=64*64)
                player.sendSystemMessage(message);
        }
    }
}
