package br.com.kraftcity.test;

import br.com.kraftcity.commerce.*;
import br.com.kraftcity.employment.*;
import br.com.kraftcity.employment.client.WorkScreen;
import br.com.kraftcity.industry.*;
import br.com.kraftcity.logistics.DiscardChests;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Real commercial hire packets, navigation, whole-duration timers and process restart. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="residenciasaldeoes",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class CommerceVisualCheck {
    private static final Profession[] JOBS={Profession.BAKER,Profession.CONFECTIONER,Profession.GRILL_COOK};
    private static volatile UUID worker;
    private static volatile boolean done,nightReady;
    private static volatile String problem="";
    private static int stage=-1,ticks,readyTicks;private static boolean builderReturned;
    private static final long[] began=new long[3];
    private static final boolean[] visited=new boolean[3];
    private static BlockPos post(int i){return new BlockPos(100+18*i,-57,40);}
    private static EmploymentData.Station station(ServerLevel l,int i){return ((WorkBlockEntity)l.getBlockEntity(post(i))).station();}
    private static int count(EmploymentData.Station s,Item item){int n=0;for(int i=0;i<27;i++)if(s.getItem(i).is(item))n+=s.getItem(i).getCount();return n;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void setup(ServerPlayer player,int i){
        var l=player.getLevel();var p=post(i);l.setDayTime(1000);l.setWeatherParameters(10000,0,false,false);l.setRainLevel(0F);
        player.teleportTo(l,p.getX()+.5,p.getY(),p.getZ()+2.5,180,20);
        l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new AABB(p).inflate(7)).forEach(Entity::discard);
        // Solid terrain throughout the residential influence area; no walkable cavity beneath the test platform.
        for(int x=-10;x<=18;x++)for(int z=-10;z<=10;z++)for(int y=-3;y<=-1;y++)l.setBlock(p.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
        for(int x=-5;x<=13;x++)for(int z=-5;z<=5;z++){l.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<5;y++)l.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        l.setBlock(p.offset(-2,0,-2),ResidenciasDosAldeoes.EMERALD_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH),3);
        var home=p.offset(-3,-1,0);l.setBlock(home.below(),Blocks.STONE.defaultBlockState(),3);ResidenceData.get(l).unlinkAll(l,l.dimension(),home);l.setBlock(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(home)).setSize(11);
        Villager v=EntityType.VILLAGER.create(l);v.setAge(0);v.setNoAi(true);v.setInvulnerable(true);v.setPos(home.getX()+1.5,p.getY(),home.getZ()+.5);l.addFreshEntity(v);
        check(ResidenceData.get(l).link(l,home,v).equals("ok"),"Commercial visual residence");ResidenceData.get(l).renameResident(v.getUUID(),"Comerciante "+JOBS[i].label);worker=v.getUUID();v.setPos(p.getX()+.5,p.getY(),p.getZ()+1.5);
        l.setBlock(p,WorkModule.COMMERCIAL.get().defaultBlockState(),3);var s=station(l,i);s.setItem(31,new ItemStack(Items.EMERALD,30));
        // Upstream materials come from real mature crop / adult animal operations.
        var sourcePos=p.east(8);l.setBlock(sourcePos,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);var src=((WorkBlockEntity)l.getBlockEntity(sourcePos)).station();
        var supplier=EntityType.VILLAGER.create(l);supplier.setAge(0);supplier.setNoAi(true);supplier.setInvulnerable(true);supplier.setPos(home.getX()+1.5,p.getY(),home.getZ()+.5);l.addFreshEntity(supplier);
        check(ResidenceData.get(l).link(l,home,supplier).equals("ok"),"Supplier home");supplier.setPos(sourcePos.getX()+.5,p.getY(),p.getZ()+1.5);
        var employer=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"SupplyEmployer"));employer.setPos(sourcePos.getX()+.5,p.getY(),p.getZ()+1.5);src.setItem(31,new ItemStack(Items.EMERALD,10));
        var sourceJob=i==2?Profession.BUTCHER:Profession.FARMER;check(Employment.hire(employer,src,supplier.getUUID(),sourceJob).equals("ok"),"Supplier hire");
        var chestPos=p.east(6);l.setBlock(chestPos,Blocks.BARREL.defaultBlockState(),3);((BarrelBlockEntity)l.getBlockEntity(chestPos)).clearContent();DiscardChests.invalidate(l);
        if(i==0){
            src.setItem(27,new ItemStack(Items.WOODEN_HOE));src.setItem(0,new ItemStack(Items.WHEAT_SEEDS,25));
            int[][] plots={{-2,1},{-1,1},{0,1},{1,1},{2,1},{2,0}};
            for(var plot:plots){var crop=sourcePos.offset(plot[0],0,plot[1]);l.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);l.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);src.industry.putInt("cursor",((plot[1]+2)*5+plot[0]+2)*5+2);IndustrialWork.tick(l,src);}
            check(count(src,Items.WHEAT)==6,"Real wheat harvest for baker");
        }else if(i==1){
            src.setItem(27,new ItemStack(Items.WOODEN_HOE));src.setItem(0,new ItemStack(Items.SUGAR_CANE,25));var root=sourcePos.east();l.setBlock(root.below(2),Blocks.STONE.defaultBlockState(),3);l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);l.setBlock(root.below().south(),Blocks.WATER.defaultBlockState(),3);
            for(int n=0;n<3;n++)l.setBlock(root.above(n),Blocks.SUGAR_CANE.defaultBlockState(),3);src.industry.putInt("cursor",67);IndustrialWork.tick(l,src);check(count(src,Items.SUGAR_CANE)==27,"Real cane harvest");
        }else{
            src.setItem(27,new ItemStack(Items.WOODEN_AXE));for(int n=0;n<3;n++){var cow=EntityType.COW.create(l);cow.setAge(0);cow.setNoAi(true);cow.setPos(sourcePos.getX()+1.5,sourcePos.getY(),sourcePos.getZ()+.5);cow.getPersistentData().putUUID("KCIndustryStation",src.id);cow.getPersistentData().putLong("KCIndustryBorn",-1000000+n);l.addFreshEntity(cow);}IndustrialWork.tick(l,src);check(count(src,Items.BEEF)==1,"Real butcher source");s.setItem(30,new ItemStack(Items.COAL));
        }
        IndustrialWork.deposit(l,src,supplier);s.commerce.putLong("visual_source",chestPos.asLong());s.commerce.putInt("visual_stage",i);WorkBlock.open(player,s);
    }
    private static Button button(WorkScreen screen,String text){return screen.children().stream().filter(w->w instanceof Button).map(w->(Button)w).filter(b->b.getMessage().getString().contains(text)).findFirst().orElse(null);}
    private static void click(WorkScreen screen,String text){var b=button(screen,text);check(b!=null&&b.active,"Commercial button missing: "+text);b.onPress();}
    private static void select(Minecraft mc){
        var screen=(WorkScreen)mc.screen;String suffix=worker.toString().substring(0,6);
        for(int page=0;page<100&&button(screen,suffix)==null;page++){
            var next=screen.children().stream().filter(w->w instanceof Button).map(w->(Button)w).filter(b->b.getMessage().getString().equals(">")).findFirst().orElseThrow();check(next.active,"Commercial resident not eligible");next.onPress();
        }click(screen,suffix);click(screen,JOBS[stage].label);
    }
    private static void capture(Minecraft mc,String name){Screenshot.grab(new File("../build"),name,mc.getMainRenderTarget(),message->{});}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void actualSupply(WorkActionEvent e){if(!e.action.equals("commercial_supply")||!e.station.commerce.contains("visual_stage"))return;int i=e.station.commerce.getInt("visual_stage");if(i<0||i>=3||!e.station.pos.equals(post(i)))return;check(e.worker.distanceToSqr(Vec3.atCenterOf(e.position))<=9,"Commercial supply occurred without physical arrival");var inventory=DiscardChests.at(e.level,e.position);check(inventory!=null,"Commercial supply accessed absent container");visited[i]=true;e.station.commerce.putLong("visual_actual_supply",e.position.asLong());}
    private static void poll(ServerPlayer player,int i){
        var l=player.getLevel();var s=station(l,i);var v=(Villager)l.getEntity(s.worker);var chest=BlockPos.of(s.commerce.getLong("visual_source"));
        if(v!=null&&v.distanceToSqr(Vec3.atCenterOf(chest))<=9)visited[i]=true;
        if(!done&&l.getGameTime()%200==0)System.out.println("COMMERCE_DEBUG "+JOBS[i]+" elapsed="+s.commerce.getInt("elapsed")+" pos="+(v==null?"unloaded":v.position())+" status="+s.commerce.getString("status"));
        Item product=i==0?Items.BREAD:i==1?Items.SUGAR:Items.COOKED_BEEF;
        if(count(s,product)>0&&!done){
            check(visited[i],"Merchant did not physically reach upstream chest");
            check(l.getGameTime()-began[i]>=CommercialWork.duration(i==0?1:5),"Commercial product completed before real operation duration");
            check(s.getItem(31).getCount()==20&&s.profession==JOBS[i]&&ResidenceData.get(l).owner(s.worker)!=null,"Contract/home conservation");
            var data=WorkforceData.get(l).progress(s.worker,JOBS[i].id());check(data.xp()>=(i==0?1:101),"Commercial XP missing");
            s.commerce.putBoolean("visual_chain_passed",true);s.setChanged();done=true;System.out.println("COMMERCE_"+JOBS[i].name()+"_CHAIN_AND_TIMER_PASSED");
        }
    }
    public static void tick(Minecraft mc,int global){
        if(stage<0){stage=0;ticks=0;done=false;mc.getSingleplayerServer().execute(()->setup(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()),0));}
        check(problem.isEmpty(),problem);if(stage==3&&!BuilderVisualCheck.done()){BuilderVisualCheck.tick(mc,0);return;}if(stage==3&&!builderReturned){builderReturned=true;mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(p.getLevel(),120.5,-57,43.5,180,20);});return;}ticks++;
        if(stage<3){
            int i=stage;
            if(ticks==25){check(mc.screen instanceof WorkScreen,"Commercial hiring screen missing");select(mc);}
            if(ticks==30&&i==0){var m=(WorkMenu)mc.player.containerMenu;check(m.snapshot!=null&&m.snapshot.cost()==10,"Commercial price is not ten");capture(mc,"visual-commerce-hiring.png");}
            if(ticks==35)click((WorkScreen)mc.screen,"Contratar");
            if(ticks==55)mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();var s=station(l,i);check(s.worker!=null&&s.worker.equals(worker)&&s.profession==JOBS[i]&&s.getItem(31).getCount()==20,"Commercial hire packet failed");
                if(i>0)WorkforceData.get(l).setProgress(s.worker,JOBS[i].id(),5,100);
                ((Villager)l.getEntity(s.worker)).setNoAi(false);began[i]=l.getGameTime();
            });
            if(ticks>60&&ticks%10==0)mc.getSingleplayerServer().execute(()->poll(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()),i));
            if(done){
                readyTicks++;
                if(readyTicks==15){check(mc.screen instanceof WorkScreen&&mc.player.containerMenu instanceof WorkMenu m&&m.snapshot!=null&&!m.hiring,"Commercial inventory screen not synchronized");capture(mc,"visual-commerce-"+JOBS[i].path+".png");}
                if(readyTicks==25){mc.player.closeContainer();stage++;ticks=0;readyTicks=0;done=false;
                    if(stage<3){int next=stage;mc.getSingleplayerServer().execute(()->setup(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()),next));}
                }
            }
            check(ticks<2200,"Commercial production/navigation timed out: "+JOBS[i]);
        }else{
            if(ticks%10==0&&!nightReady)mc.getSingleplayerServer().execute(()->{
                var l=mc.getSingleplayerServer().overworld();var s=station(l,0);
                if(CommercialWork.count(s,Items.WHEAT)==0)s.setItem(28,new ItemStack(Items.WHEAT,3));
                if(s.commerce.getInt("elapsed")>=1100){
                    s.commerce.putInt("visual_partial",s.commerce.getInt("elapsed"));s.commerce.putInt("visual_bread_before",count(s,Items.BREAD));s.commerce.putLong("visual_xp_before",WorkforceData.get(l).progress(s.worker,Profession.BAKER.id()).xp());s.setChanged();
                    l.setDayTime(13001);var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(l,60.5,-57,10.5,180,20);nightReady=true;System.out.println("COMMERCE_PARTIAL_OPERATION_SAVED_FOR_RESTART");
                }
            });
            if(nightReady){readyTicks++;if(readyTicks==30)mc.getSingleplayerServer().execute(()->EmploymentVisualCheck.restoreNightFixture(mc.getSingleplayerServer().overworld()));if(readyTicks>=80)MilitaryVisualCheck.tick(mc,readyTicks-80);}
            check(ticks<2400,"Partial commercial operation did not reach checkpoint");
        }
    }
    public static void verifyRestore(ServerLevel l){
        for(int i=0;i<3;i++){
            var s=station(l,i);check(s.active&&s.worker!=null&&s.profession==JOBS[i]&&s.commerce.getBoolean("visual_chain_passed")&&s.getItem(31).getCount()==20,"Commercial reload contract "+i);
            check(ResidenceData.get(l).owner(s.worker)!=null&&WorkforceData.get(l).progress(s.worker,JOBS[i].id()).xp()>=(i==0?1:101),"Commercial history/home reload "+i);
        }
        var baker=station(l,0);check(baker.commerce.getInt("elapsed")==baker.commerce.getInt("visual_partial")&&CommercialWork.count(baker,Items.WHEAT)>=3&&count(baker,Items.BREAD)==baker.commerce.getInt("visual_bread_before"),"Partial recipe changed during offline/night pause");
        check(station(l,2).commerce.getLong("heat")==1400,"Vanilla fuel credit not persistent");
        System.out.println("COMMERCE_THREE_CHAINS_WORLD_RESTART_PASSED");
    }
    public static void restoredTick(Minecraft mc,int tick){
        if(tick==160)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setDayTime(1000));
        if(tick==580)mc.getSingleplayerServer().execute(()->{
            var l=mc.getSingleplayerServer().overworld();var s=station(l,0);
            check(count(s,Items.BREAD)==s.commerce.getInt("visual_bread_before")+1&&CommercialWork.count(s,Items.WHEAT)==0,"Resumed recipe duplicated/lost output after JVM restart");
            check(WorkforceData.get(l).progress(s.worker,Profession.BAKER.id()).xp()==s.commerce.getLong("visual_xp_before")+1,"Reloaded recipe XP duplicated");
            System.out.println("COMMERCE_PARTIAL_RECIPE_RESUMED_ONCE_PASSED");
        });
    }
    private CommerceVisualCheck(){}
}
