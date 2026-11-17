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
    public static void bind(EmploymentData.Station s,Mob v){for(int i=0;i<6;i++){if(v.getItemBySlot(GEAR[i])!=s.equipment.getItem(i))v.setItemSlot(GEAR[i],s.equipment.getItem(i));v.setDropChance(GEAR[i],0);}v.setCanPickUpLoot(false);}
    public static void release(ServerLevel l,UUID worker){var s=EmploymentData.get(l).assigned(worker);if(!military(s))return;s.military.remove("target");
        for(var world:l.getServer().getAllLevels())if(world.getEntity(worker) instanceof SoldierZombie z){for(var slot:GEAR)z.setItemSlot(slot,ItemStack.EMPTY);}else if(world.getEntity(worker) instanceof Villager v){v.stopUsingItem();for(var slot:GEAR)v.setItemSlot(slot,ItemStack.EMPTY);v.getPersistentData().remove("KCSoldier");v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);}
        s.setChanged();
    }
    public static boolean selected(EmploymentData.Station s,EntityType<?> type){var key=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);if(key==null)return false;String id=key.toString();var choices=s.military.getCompound("blacklist");return choices.contains(id)?choices.getBoolean(id):key.getNamespace().equals("minecraft")&&type.getCategory()==MobCategory.MONSTER&&type!=EntityType.CREEPER&&MilitaryNetwork.category(type,null)==2;}
    public static void select(EmploymentData.Station s,EntityType<?> type,boolean on){var c=s.military.getCompound("blacklist");c.putBoolean(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type).toString(),on);s.military.put("blacklist",c);s.setChanged();}
    public static boolean protectedArea(ServerLevel l,LivingEntity target){
        var homes=ResidenceData.get(l);for(var home:homes.homes())if(home.dimension.equals(l.dimension())&&l.hasChunkAt(home.pos)&&l.getBlockEntity(home.pos) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity){
            double half=home.size/2;if(new AABB(home.pos).inflate(half,5,half).inflate(5).contains(target.position()))return true;
            for(UUID id:home.residents)if(l.getEntity(id) instanceof Villager resident&&resident.isAlive()&&resident.distanceToSqr(target)<=100)return true;
        }
        for(var post:EmploymentData.get(l).stations())if(post.active&&post.dimension.equals(l.dimension())&&post.sector!=Profession.Sector.MILITARY&&l.hasChunkAt(post.pos)&&l.getBlockEntity(post.pos) instanceof WorkBlockEntity be&&post.id.equals(be.identity())){
            double half=br.com.kraftcity.industry.IndustrialRules.size(l,post)/2;if(new AABB(post.pos).inflate(half,5,half).inflate(5).contains(target.position()))return true;
        }return false;
    }
    public static boolean authorized(ServerLevel l,EmploymentData.Station s,Villager v,LivingEntity target){
        if(!target.isAlive()||target==v||target instanceof Player||ResidenceData.get(l).owner(target.getUUID())!=null||!selected(s,target.getType()))return false;
        if(!onDuty(l,s)||v.isSleeping()||!Employment.valid(l,v.getUUID()))return false;
        if(mode(s)==Mode.FOLLOW){var p=s.military.hasUUID("player")?l.getPlayerByUUID(s.military.getUUID("player")):null;var orders=s.military.getCompound("orders");String key=target.getUUID().toString();boolean ordered=orders.contains(key)&&l.getGameTime()-orders.getLong(key)>=0&&l.getGameTime()-orders.getLong(key)<=MilitaryModule.CONTRIBUTION_TICKS.get();return p!=null&&target.distanceToSqr(p)<=100&&ordered;}
        if(target.distanceToSqr(Vec3.atCenterOf(s.pos))>Math.pow(MilitaryModule.DETECTION.get(),2))return false;
        return protectedArea(l,target)||(v.getLastHurtByMob()==target&&v.tickCount-v.getLastHurtByMobTimestamp()<100);
    }
    public static LivingEntity target(ServerLevel l,EmploymentData.Station s,Villager v){
        LivingEntity current=s.military.hasUUID("target")&&l.getEntity(s.military.getUUID("target")) instanceof LivingEntity t?t:null;
        var choices=l.getEntitiesOfClass(LivingEntity.class,new AABB(s.pos).inflate(MilitaryModule.DETECTION.get()),t->authorized(l,s,v,t)&&v.hasLineOfSight(t));
        if(mode(s)==Mode.FOLLOW)choices=l.getEntitiesOfClass(LivingEntity.class,v.getBoundingBox().inflate(20),t->authorized(l,s,v,t)&&v.hasLineOfSight(t));
        // Keep focus unless a protected resident/area needs rescue over self-defense.
        if(current!=null&&authorized(l,s,v,current)&&(protectedArea(l,current)||choices.stream().noneMatch(t->protectedArea(l,t))))return current;
        return choices.stream().min(Comparator.<LivingEntity>comparingInt(t->protectedArea(l,t)?0:1).thenComparingDouble(v::distanceToSqr).thenComparing(t->t.getUUID().toString())).orElse(null);
    }
    public static void tick(ServerLevel l,EmploymentData.Station s,Villager v){
        if(!Employment.valid(l,v.getUUID())||!l.hasChunkAt(s.pos))return;bind(s,v);stats(l,v);
        if(!onDuty(l,s)){s.military.remove("target");v.stopUsingItem();sleep(l,s,v);heal(l,s,v);return;}
        if(v.isSleeping()){v.stopSleeping();v.getNavigation().stop();}heal(l,s,v);
        var t=target(l,s,v);
        if(t!=null){s.military.putUUID("target",t.getUUID());combat(l,s,v,t);s.setChanged();return;}
        s.military.remove("target");v.stopUsingItem();
        if(mode(s)==Mode.FOLLOW){var p=s.military.hasUUID("player")?l.getPlayerByUUID(s.military.getUUID("player")):null;if(p!=null&&v.distanceToSqr(p)>9)v.getNavigation().moveTo(p,1.1);else v.getNavigation().stop();}
        else if(mode(s)==Mode.GUARD){Vec3 p=Vec3.atBottomCenterOf(s.pos.above());if(v.distanceToSqr(p)>.16)v.getNavigation().moveTo(p.x,p.y,p.z,1.1);else{v.getNavigation().stop();v.setDeltaMovement(Vec3.ZERO);float yaw=l.getBlockState(s.pos).getValue(WorkBlock.FACING).toYRot();v.setYRot(yaw);v.setYHeadRot(yaw);v.setYBodyRot(yaw);}}
        else if(v.distanceToSqr(Vec3.atCenterOf(s.pos))>225)v.getNavigation().moveTo(s.pos.getX()+.5,s.pos.getY()+1,s.pos.getZ()+.5,1.1);
        else if(v.tickCount%80==0||v.getNavigation().isDone()){int dx=v.getRandom().nextInt(21)-10,dz=v.getRandom().nextInt(21)-10;var p=s.pos.offset(dx,0,dz);if(dx*dx+dz*dz<=225&&l.hasChunkAt(p))v.getNavigation().moveTo(p.getX()+.5,p.getY()+1,p.getZ()+.5,.65);}
        s.setChanged();
    }
    private static void heal(ServerLevel l,EmploymentData.Station s,Villager v){int interval=Math.max(1,MilitaryModule.HEAL_TICKS.get()/(v.isSleeping()?2:1));if(v.tickCount%interval==0&&v.getHealth()<v.getMaxHealth())v.heal(1);}
    private static void sleep(ServerLevel l,EmploymentData.Station s,Villager v){
        if(v.isSleeping())return;var data=ResidenceData.get(l);var home=data.owner(v.getUUID());if(home==null||!l.hasChunkAt(home.pos))return;
        BlockPos bed=data.bedClaim(v.getUUID())==null?null:data.bedClaim(v.getUUID()).bed();
        if(bed==null||!(l.getBlockState(bed).getBlock() instanceof BedBlock)){
            bed=null;int r=home.size/2;for(var p:BlockPos.betweenClosed(home.pos.offset(-r,-2,-r),home.pos.offset(r,4,r)))if(l.hasChunkAt(p)&&l.getBlockState(p).getBlock() instanceof BedBlock&&l.getBlockState(p).getValue(BedBlock.PART)==BedPart.HEAD&&(data.bedOwner(l.dimension(),p)==null||data.ownsBed(l.dimension(),p,v.getUUID()))&&data.reserveBed(v.getUUID(),l.dimension(),home.pos,p)){bed=p.immutable();break;}
        }
        if(bed==null)return;if(v.distanceToSqr(Vec3.atCenterOf(bed))<=4){v.getNavigation().stop();v.startSleeping(bed);}else v.getNavigation().moveTo(bed.getX()+.5,bed.getY(),bed.getZ()+.5,.7);
    }
    public static void combat(ServerLevel l,EmploymentData.Station s,Villager v,LivingEntity t){
        v.getLookControl().setLookAt(t,30,30);double distance=v.distanceToSqr(t);ItemStack weapon=s.equipment.getItem(0);
        boolean ranged=weapon.getItem() instanceof BowItem||weapon.getItem() instanceof CrossbowItem;
        if(ranged&&ammo(s)>=0){ranged(l,s,v,t,weapon,distance);return;}
        long now=l.getGameTime();
        if(now<s.military.getLong("retreatUntil")){Vec3 away=v.position().subtract(t.position()).normalize().scale(3);v.getNavigation().moveTo(v.getX()+away.x,v.getY(),v.getZ()+away.z,1.1);return;}
        if(distance>4){v.getNavigation().moveTo(t,1.25);shield(v,true);return;}
        if(!v.hasLineOfSight(t)){v.getNavigation().moveTo(t,1.1);shield(v,true);return;}
        v.getNavigation().stop();if(now<s.military.getLong("attackAt")){shield(v,true);return;}
        v.stopUsingItem();v.swing(InteractionHand.MAIN_HAND);
        if(v.doHurtTarget(t)){if(weapon.getItem() instanceof SwordItem)weapon.hurtAndBreak(1,v,e->e.broadcastBreakEvent(EquipmentSlot.MAINHAND));s.setChanged();}
        s.military.putLong("attackAt",now+20);s.military.putLong("retreatUntil",now+10);
    }
    private static void shield(Villager v,boolean enabled){if(enabled&&v.getOffhandItem().getItem() instanceof ShieldItem&&!v.isUsingItem())v.startUsingItem(InteractionHand.OFF_HAND);}
    private static int ammo(EmploymentData.Station s){for(int i=0;i<31;i++)if(s.getItem(i).getItem() instanceof ArrowItem)return i;return -1;}
    private static void ranged(ServerLevel l,EmploymentData.Station s,Villager v,LivingEntity t,ItemStack weapon,double distance){
        if(distance<16){var away=v.position().subtract(t.position()).normalize().scale(3);v.getNavigation().moveTo(v.getX()+away.x,v.getY(),v.getZ()+away.z,1.1);}else if(distance>36)v.getNavigation().moveTo(t,1.1);else v.getNavigation().stop();
        if(!v.hasLineOfSight(t)){s.military.remove("drawAt");v.stopUsingItem();v.getNavigation().moveTo(t,1.1);return;}
        if(!s.military.contains("drawAt")){s.military.putLong("drawAt",l.getGameTime());v.startUsingItem(InteractionHand.MAIN_HAND);return;}
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
        var c=dead.getPersistentData().getCompound("KCMilitaryContributors");long now=l.getGameTime();int reward=dead.getExperienceReward();if(reward<=0)return;
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
    public static Villager cure(ServerLevel l,SoldierZombie z){
        var s=station(z);if(!military(s)||!s.military.getBoolean("zombie"))return null;
        var v=EntityType.VILLAGER.create(l);if(v==null)return null;var nbt=s.military.getCompound("villager").copy();nbt.remove("UUID");nbt.remove("Passengers");nbt.remove("HandItems");nbt.remove("ArmorItems");v.load(nbt);v.setUUID(z.getUUID());v.moveTo(z.getX(),z.getY(),z.getZ(),z.getYRot(),z.getXRot());v.setNoAi(false);v.setVillagerData(z.getVillagerData().setProfession(VillagerProfession.NONE));
        s.military.remove("zombie");s.military.remove("villager");for(var slot:GEAR)z.setItemSlot(slot,ItemStack.EMPTY);z.discard();stats(l,v);v.setHealth(v.getMaxHealth());l.addFreshEntity(v);if(s.active&&v.getUUID().equals(s.worker))bind(s,v);else{v.getPersistentData().remove("KCSoldier");v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);v.setHealth(20);}s.setChanged();return v;
    }
    public static void death(ServerLevel l,LivingEntity v){
        var s=station(v);if(!military(s))return;boolean drops=l.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        for(int i=0;i<6;i++){var stack=s.equipment.removeItemNoUpdate(i);if(drops&&!stack.isEmpty()&&!EnchantmentHelper.hasVanishingCurse(stack))v.spawnAtLocation(stack);}
        if(v instanceof Mob mob)for(var slot:GEAR)mob.setItemSlot(slot,ItemStack.EMPTY);
        s.military.remove("zombie");s.military.remove("villager");s.setChanged();Employment.release(l,v.getUUID());ResidenceData.get(l).unlink(l,v.getUUID());
    }
}
