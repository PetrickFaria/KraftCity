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
    public static int level(ServerLevel l,UUID id){return level(WorkforceData.get(l).progress(id,Profession.SOLDIER.id()).xp());}
    public static double health(int level){return 8+2*Math.min(5,Math.max(1,level));}
    public static void trained(ServerLevel l,EmploymentData.Station s,Villager v,ServerPlayer player){
        if(!s.military.hasUUID("player"))s.military.putUUID("player",player.getUUID());
        s.military.putUUID("identity",v.getUUID());s.military.remove("target");s.military.remove("zombie");v.getPersistentData().putBoolean("KCSoldier",true);
        stats(l,v);v.setHealth(v.getMaxHealth());bind(s,v);s.setChanged();
    }
    public static void stats(ServerLevel l,LivingEntity v){var a=v.getAttribute(Attributes.MAX_HEALTH);if(a!=null)a.setBaseValue(health(level(l,v.getUUID())));if(v.getHealth()>v.getMaxHealth())v.setHealth(v.getMaxHealth());}
    public static boolean onDuty(ServerLevel l,EmploymentData.Station s){long t=Math.floorMod(l.getDayTime(),24000);boolean night=t>=13000&&t<23000;return shift(s)==Shift.NIGHT?night:!night;}
    public static void bind(EmploymentData.Station s,Mob v){for(int i=0;i<6;i++){var gear=s.equipment.getItem(i);if(gear.isDamageableItem()&&gear.getDamageValue()>=gear.getMaxDamage())s.equipment.setItem(i,ItemStack.EMPTY);if(v.getItemBySlot(GEAR[i])!=s.equipment.getItem(i)){
            if(i==0){v.stopUsingItem();s.military.remove("drawAt");}
            v.setItemSlot(GEAR[i],s.equipment.getItem(i));
        }v.setDropChance(GEAR[i],0);}v.setCanPickUpLoot(false);v.setLeftHanded(false);}
    public static void release(ServerLevel l,UUID worker){var s=EmploymentData.get(l).assigned(worker);if(!military(s))return;s.military.remove("target");
        for(var world:l.getServer().getAllLevels())if(world.getEntity(worker) instanceof SoldierZombie z){for(var slot:GEAR)z.setItemSlot(slot,ItemStack.EMPTY);}else if(world.getEntity(worker) instanceof Villager v){v.stopUsingItem();for(var slot:GEAR)v.setItemSlot(slot,ItemStack.EMPTY);v.getPersistentData().remove("KCSoldier");v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);}
        s.setChanged();
    }
    public static boolean selected(EmploymentData.Station s,EntityType<?> type){var key=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);if(key==null)return false;String id=key.toString();var choices=s.military.getCompound("blacklist");return choices.getBoolean(id);}
    public static void select(EmploymentData.Station s,EntityType<?> type,boolean on){var c=s.military.getCompound("blacklist");c.putBoolean(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type).toString(),on);s.military.put("blacklist",c);s.setChanged();}
    public static boolean protectedArea(ServerLevel level,LivingEntity target){
        return protectedArea(level,target,null);
    }

    private static boolean protectedArea(ServerLevel level,LivingEntity target,MilitaryArea area){
        for(var home:ResidenceData.get(level).homes()){
            if(!home.dimension.equals(level.dimension())||!level.hasChunkAt(home.pos)
                ||!(level.getBlockEntity(home.pos) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity))continue;
            int radius=home.size/2;
            var bounds=new AABB(home.pos.offset(-radius,1,-radius),home.pos.offset(radius+1,home.size+1,radius+1));
            if((area==null||area.intersects(bounds))&&distanceTo(bounds,target.position())<=100)return true;
        }
        for(var post:EmploymentData.get(level).stations()){
            if(!post.active||post.worker==null||post.sector==Profession.Sector.MILITARY||!post.dimension.equals(level.dimension())
                ||!level.hasChunkAt(post.pos)||!(level.getBlockEntity(post.pos) instanceof WorkBlockEntity be)||!post.id.equals(be.identity()))continue;
            var bounds=post.sector==Profession.Sector.INDUSTRIAL
                ?br.com.kraftcity.industry.IndustrialWork.area(level,post):new AABB(post.pos);
            if((area==null||area.intersects(bounds))&&distanceTo(bounds,target.position())<=100)return true;
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
        boolean enemy=MilitaryNetwork.category(candidate.getType(),candidate)==2||selected(station,candidate.getType());
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
            return enemy&&player!=null&&area.contains(player.position())&&candidate.distanceToSqr(player)<=100?2:Integer.MAX_VALUE;
        }
        boolean nearResident=false;
        for(var resident:level.getEntitiesOfClass(Villager.class,candidate.getBoundingBox().inflate(10),
            villager->villager!=soldier&&villager.isAlive()&&area.contains(villager.position())&&villager.distanceToSqr(candidate)<=100
                &&(ResidenceData.get(level).owner(villager.getUUID())!=null||Employment.active(villager)))){
            if(struck(resident,candidate))return 0;
            nearResident=true;
        }
        if(struck(soldier,candidate))return 1;
        if(!enemy)return Integer.MAX_VALUE;
        if(nearResident)return 2;
        if(protectedArea(level,candidate,area))return 3;
        return soldier.distanceToSqr(candidate)<=100?4:Integer.MAX_VALUE;
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
        double distance=Double.POSITIVE_INFINITY;
        for(var candidate:l.getEntitiesOfClass(Mob.class,v.getBoundingBox().inflate(Math.min(32,MilitaryModule.DETECTION.get())),mob->mob!=v&&v.hasLineOfSight(mob))){
            int nextRank=priority(l,s,v,candidate,area);
            double nextDistance=v.distanceToSqr(candidate);
            if((nextRank<rank||nextRank!=Integer.MAX_VALUE&&nextRank==rank&&nextDistance<distance)
                &&(nextRank<=1||canEngage(s,v,candidate,area,nextDistance))){
                best=candidate;rank=nextRank;distance=nextDistance;
            }
        }
        return best;
    }

    private static boolean canEngage(EmploymentData.Station station,Villager soldier,LivingEntity target,
                                     MilitaryArea area,double distance){
        if(distance<=4)return true;
        var weapon=station.equipment.getItem(0);
        boolean ranged=weapon.getItem() instanceof BowItem||weapon.getItem() instanceof CrossbowItem;
        if(ranged&&distance<=36&&(ammo(station)>=0||CrossbowItem.isCharged(weapon)))return true;
        return area.canReach(soldier,target.position());
    }

    public static Vec3 limitMovement(Villager soldier,Vec3 movement){
        if(!(soldier.level instanceof ServerLevel level)||!soldier.getPersistentData().getBoolean("KCSoldier"))return movement;
        var station=station(soldier);
        if(!military(station)||!onDuty(level,station))return movement;
        return new MilitaryArea(station).limit(soldier.position(),movement);
    }

    public static AABB areaBounds(BlockPos post){
        return MilitaryArea.boundsAt(post);
    }

    public static boolean insideArea(EmploymentData.Station station,Vec3 point){
        return military(station)&&new MilitaryArea(station).contains(point);
    }

    public static void clearTask(Villager soldier,EmploymentData.Station station){
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
            &&priority(l,s,v,current,area)!=Integer.MAX_VALUE?current:null;
        if(v.tickCount%5==0||threat==null&&s.military.hasUUID("target"))threat=target(l,s,v);
        if(threat!=null){
            if(!s.military.hasUUID("target")||!s.military.getUUID("target").equals(threat.getUUID()))clearTask(v,s);
            s.military.putUUID("target",threat.getUUID());
            combat(l,s,v,threat);
            s.setChanged();
            return;
        }
        if(s.military.hasUUID("target"))clearTask(v,s);
        if(mode(s)==Mode.FOLLOW){
            var owner=linkedPlayer(l,s);
            if(owner!=null&&v.distanceToSqr(owner)>49){
                if(!area.approach(v,owner.position(),MilitaryArea.WALK))navigation.stop();
                return;
            }
            navigation.stop();
            MilitaryLoot.collect(l,s,v,area,false);
        }else if(mode(s)==Mode.GUARD){
            var point=Vec3.atBottomCenterOf(s.pos.above());
            if(v.distanceToSqr(point)>.25)area.move(v,point,MilitaryArea.WALK);
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
                MilitaryLoot.collect(l,s,v,area,false);
            }
        }else if(!MilitaryLoot.collect(l,s,v,area,true)&&navigation.isDone()&&v.tickCount%10==0){
            for(int attempt=0;attempt<6;attempt++){
                var destination=v.blockPosition().offset(v.getRandom().nextInt(25)-12,v.getRandom().nextInt(5)-2,v.getRandom().nextInt(25)-12);
                if(area.move(v,area.nearest(Vec3.atBottomCenterOf(destination)),MilitaryArea.WALK))break;
            }
        }
    }
    private static void heal(ServerLevel level,Villager villager){
        if(villager.isSleeping()&&br.com.residenciasaldeoes.VillagerResidenceEvents.usesResidentialSleepHealing(level,villager))return;
        int interval=Math.max(1,MilitaryModule.HEAL_TICKS.get()/(villager.isSleeping()?2:1));
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
        v.getLookControl().setLookAt(t,30,30);double distance=v.distanceToSqr(t);ItemStack weapon=s.equipment.getItem(0);
        boolean ranged=weapon.getItem() instanceof BowItem||weapon.getItem() instanceof CrossbowItem;
        if(ranged&&(ammo(s)>=0||weapon.getItem() instanceof CrossbowItem&&CrossbowItem.isCharged(weapon))){ranged(l,s,v,t,weapon,distance);return;}
        long now=l.getGameTime();
        if(now<s.military.getLong("retreatUntil")){Vec3 away=v.position().subtract(t.position()).normalize().scale(3);area.move(v,area.nearest(v.position().add(away.x,0,away.z)),MilitaryArea.CHASE);return;}
        if(distance>4){
            if(mode(s)!=Mode.GUARD||!area.leaveGuardPost(s.pos,v,t.position()))area.move(v,t.position(),MilitaryArea.CHASE);
            shield(v,true);
            return;
        }
        if(!v.hasLineOfSight(t)){area.move(v,t.position(),MilitaryArea.CHASE);shield(v,true);return;}
        v.getNavigation().stop();if(now<s.military.getLong("attackAt")){shield(v,true);return;}
        v.stopUsingItem();v.swing(InteractionHand.MAIN_HAND);
        if(v.doHurtTarget(t)){if(weapon.getItem() instanceof SwordItem)weapon.hurtAndBreak(1,v,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));s.setChanged();}
        s.military.putLong("attackAt",now+20);s.military.putLong("retreatUntil",now+10);
    }
    private static void shield(Villager v,boolean enabled){if(enabled&&v.getOffhandItem().canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK)&&!v.isUsingItem())v.startUsingItem(InteractionHand.OFF_HAND);}
    private static int ammo(EmploymentData.Station s){for(int i=0;i<31;i++)if(s.getItem(i).getItem() instanceof ArrowItem)return i;return -1;}
    private static void ranged(ServerLevel l,EmploymentData.Station s,Villager v,LivingEntity t,ItemStack weapon,double distance){
        var offset=t.position().subtract(v.position());
        float desiredYaw=(float)(Math.toDegrees(Math.atan2(offset.z,offset.x))-90);
        float yaw=net.minecraft.util.Mth.approachDegrees(v.getYRot(),desiredYaw,30);
        v.setYRot(yaw);
        v.setYBodyRot(yaw);
        var area=new MilitaryArea(s);
        if(distance<16){var away=v.position().subtract(t.position()).normalize().scale(3);area.move(v,area.nearest(v.position().add(away.x,0,away.z)),MilitaryArea.CHASE);}else if(distance>36)area.move(v,t.position(),MilitaryArea.CHASE);else v.getNavigation().stop();
        if(!v.hasLineOfSight(t)){s.military.remove("drawAt");v.stopUsingItem();area.move(v,t.position(),MilitaryArea.CHASE);return;}
        var line=v.getEyePosition();
        var end=t.getEyePosition();
        var box=new AABB(line,end).inflate(.5);
        if(l.getEntitiesOfClass(LivingEntity.class,box,entity->entity!=v&&entity!=t&&ally(l,v,entity)).stream()
            .anyMatch(entity->entity.getBoundingBox().inflate(.15).clip(line,end).isPresent()))return;
        if(weapon.getItem() instanceof CrossbowItem&&CrossbowItem.isCharged(weapon)){
            v.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,t.getEyePosition());
            CrossbowItem.performShooting(l,v,InteractionHand.MAIN_HAND,weapon,3.15F,1F);CrossbowItem.setCharged(weapon,false);s.military.remove("drawAt");v.stopUsingItem();s.setChanged();return;
        }
        if(!s.military.contains("drawAt")){s.military.putLong("drawAt",l.getGameTime());v.stopUsingItem();v.startUsingItem(InteractionHand.MAIN_HAND);return;}
        int quick=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE,weapon);int load=weapon.getItem() instanceof CrossbowItem?Math.max(5,25-5*quick):20;
        if(l.getGameTime()-s.military.getLong("drawAt")<load)return;
        int slot=ammo(s);if(slot<0)return;var ammunition=s.getItem(slot);boolean infinity=weapon.getItem() instanceof BowItem&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS,weapon)>0&&ammunition.is(Items.ARROW);
        int shots=weapon.getItem() instanceof CrossbowItem&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT,weapon)>0?3:1;
        for(int i=0;i<shots;i++){
            var arrow=((ArrowItem)ammunition.getItem()).createArrow(l,ammunition,v);arrow.setEnchantmentEffectsFromEntity(v,1F);arrow.setBaseDamage(weapon.getItem() instanceof CrossbowItem?2:arrow.getBaseDamage());
            if(weapon.getItem() instanceof CrossbowItem){arrow.setShotFromCrossbow(true);arrow.setPierceLevel((byte)EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING,weapon));}
            if(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS,weapon)>0)arrow.setSecondsOnFire(100);
            arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            double dx=t.getX()-v.getX(),dz=t.getZ()-v.getZ(),dy=t.getY(.5)-arrow.getY()+Math.sqrt(dx*dx+dz*dz)*.2;arrow.shoot(dx,dy,dz,weapon.getItem() instanceof CrossbowItem?3.15F:1.6F,1F);
            if(shots>1)arrow.setDeltaMovement(arrow.getDeltaMovement().yRot((float)Math.toRadians((i-1)*10)));arrow.getPersistentData().putUUID("KCSoldierShot",v.getUUID());l.addFreshEntity(arrow);
        }
        if(!infinity)ammunition.shrink(1);weapon.hurtAndBreak(shots,v,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));s.military.remove("drawAt");v.stopUsingItem();s.setChanged();
    }
    public static void contribute(ServerLevel l,LivingEntity target,UUID soldier){var c=target.getPersistentData().getCompound("KCMilitaryContributors");c.putLong(soldier.toString(),l.getGameTime());target.getPersistentData().put("KCMilitaryContributors",c);target.getPersistentData().putUUID("KCMilitaryLast",soldier);}
    public static void award(ServerLevel l,LivingEntity dead,Entity killer){
        if(!l.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))return;var c=dead.getPersistentData().getCompound("KCMilitaryContributors");long now=l.getGameTime();int reward=dead.getExperienceReward();if(reward<=0)return;
        UUID finalSoldier=killer instanceof LivingEntity v&&isSoldier(v)?v.getUUID():null;
        UUID last=dead.getPersistentData().hasUUID("KCMilitaryLast")?dead.getPersistentData().getUUID("KCMilitaryLast"):null;
        for(String key:c.getAllKeys())try{UUID id=UUID.fromString(key);if(now-c.getLong(key)>MilitaryModule.CONTRIBUTION_TICKS.get()||now<c.getLong(key))continue;
            int permille=id.equals(finalSoldier)?1000:killer instanceof Player&&id.equals(last)?500:finalSoldier!=null||killer instanceof Player?100:0;
            if(permille==0)continue;var data=WorkforceData.get(l);long xp=data.progress(id,Profession.SOLDIER.id()).xp()+(long)reward*permille;data.setProgress(id,Profession.SOLDIER.id(),level(xp),xp);if(l.getEntity(id) instanceof LivingEntity v)stats(l,v);
        }catch(IllegalArgumentException ignored){}
        dead.getPersistentData().remove("KCMilitaryContributors");
    }
    public static SoldierZombie zombify(ServerLevel l,Villager v){
        var s=station(v);if(!military(s))return null;var z=MilitaryModule.ZOMBIE.get().create(l);if(z==null)return null;
        v.stopSleeping();z.setUUID(v.getUUID());z.moveTo(v.getX(),v.getY(),v.getZ(),v.getYRot(),v.getXRot());z.setVillagerData(v.getVillagerData());z.setCustomName(v.getCustomName());z.getPersistentData().merge(v.getPersistentData().copy());s.military.putBoolean("zombie",true);var snapshot=v.saveWithoutId(new CompoundTag());snapshot.remove("HandItems");snapshot.remove("ArmorItems");s.military.put("villager",snapshot);s.military.remove("target");
        stats(l,z);z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(v.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));z.setHealth(z.getMaxHealth());bind(s,z);TRANSFERRING.add(v.getUUID());try{for(var slot:GEAR)v.setItemSlot(slot,ItemStack.EMPTY);v.discard();l.addFreshEntity(z);}finally{TRANSFERRING.remove(v.getUUID());}s.setChanged();return z;
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
        else{v.getPersistentData().remove("KCSoldier");v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);}
        v.setHealth(v.getMaxHealth());l.addFreshEntity(v);return v;
    }
    public static void death(ServerLevel l,LivingEntity v){
        var s=station(v);if(!military(s))return;boolean drops=l.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        for(int i=0;i<6;i++){var stack=s.equipment.removeItemNoUpdate(i);if(drops&&!stack.isEmpty()&&!EnchantmentHelper.hasVanishingCurse(stack))v.spawnAtLocation(stack);}
        if(v instanceof Mob mob)for(var slot:GEAR)mob.setItemSlot(slot,ItemStack.EMPTY);
        s.military.remove("zombie");s.military.remove("villager");s.setChanged();Employment.release(l,v.getUUID());ResidenceData.get(l).unlink(l,v.getUUID());
    }
}
