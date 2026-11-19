package br.com.kraftcity.test;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.employment.client.WorkScreen;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.io.File;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Actual client packets, menu opening from both actors, placement and process restart. */
public final class EmploymentVisualCheck {
    private static final BlockPos HOME=new BlockPos(60,-58,8),POST=new BlockPos(64,-57,10),COMMERCIAL=new BlockPos(68,-57,10),BED=new BlockPos(59,-57,7);
    private static volatile UUID worker;
    private static com.electronwill.nightconfig.core.CommentedConfig approvedConfig;
    private static volatile boolean hired,shared,portable,pending,rehired;
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static EmploymentData.Station station(ServerLevel level,BlockPos pos){return ((WorkBlockEntity)level.getBlockEntity(pos)).station();}
    private static void open(ServerPlayer player,BlockPos pos){player.teleportTo(player.getLevel(),pos.getX()+.5,pos.getY(),pos.getZ()+1.5,180F,20F);WorkBlock.open(player,station(player.getLevel(),pos));}
    private static WorkScreen screen(Minecraft mc){check(mc.screen instanceof WorkScreen,"Employment screen did not open");return (WorkScreen)mc.screen;}
    private static Button button(WorkScreen screen,String text){return screen.children().stream().filter(c->c instanceof Button).map(c->(Button)c).filter(b->b.getMessage().getString().contains(text)).findFirst().orElse(null);}
    private static void click(WorkScreen screen,String text){Button b=button(screen,text);check(b!=null&&b.active,"Missing/inactive button: "+text);b.onPress();}
    private static void selectWorker(Minecraft mc){
        WorkScreen screen=screen(mc);String suffix=worker.toString().substring(0,6);
        for(int page=0;page<20&&button(screen,suffix)==null;page++){
            Button next=screen.children().stream().filter(c->c instanceof Button).map(c->(Button)c).filter(b->b.getMessage().getString().equals(">")).findFirst().orElse(null);
            check(next!=null&&next.active,"Eligible resident is missing from the UI");next.onPress();
        }
        click(screen,suffix);
    }
    private static void checkCompactFarmer(Minecraft mc,WorkScreen screen,WorkMenu menu){
        screen.init(mc,320,240);
        int left=(320-WorkMenu.FARM_WIDTH)/2,top=(240-WorkMenu.FARM_HEIGHT)/2;
        for(var slot:menu.slots)check(left+slot.x>=0&&left+slot.x+16<=320&&top+slot.y>=0&&top+slot.y+16<=240,
            "Farmer slot outside compact GUI");
        check(button(screen,"Demitir")!=null&&button(screen,"Limites")!=null,"Controls missing in compact GUI");
        screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
    }
    private static void checkFarmerHeader(WorkScreen screen,String ending,int expectedWidth){
        try{
            var title=WorkScreen.class.getDeclaredMethod("farmerTitle",int.class);title.setAccessible(true);
            var bar=WorkScreen.class.getDeclaredMethod("farmerXpWidth");bar.setAccessible(true);
            check(((String)title.invoke(screen,172)).endsWith(ending),"Farmer title mismatch: "+title.invoke(screen,172));
            check((int)bar.invoke(screen)==expectedWidth,"Farmer XP texture width");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void capture(Minecraft mc,String name){Screenshot.grab(new File("../build"),name,mc.getMainRenderTarget(),message->{});}
    public static void tick(Minecraft mc,int ticks){
        var server=mc.getSingleplayerServer();UUID playerId=mc.player.getUUID();
        if(ticks==770)server.execute(()->{
            var level=server.overworld();
            // The approved residential routine releases reservations during daytime.
            // Use a night fixture to test preservation of an existing bed claim.
            level.setDayTime(13_001L);
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new AABB(54,-64,3,74,-42,17)).forEach(Entity::discard);
            for(int x=55;x<=72;x++)for(int z=4;z<=15;z++){
                level.setBlock(new BlockPos(x,-58,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=-57;y<=-51;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            ResidenceData.get(level).unlinkAll(level,level.dimension(),HOME);
            level.setBlock(HOME,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);
            ((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)level.getBlockEntity(HOME)).setSize(11);
            var head=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH).setValue(BedBlock.PART,BedPart.HEAD);
            level.setBlock(BED.south(),head.setValue(BedBlock.PART,BedPart.FOOT),3);level.setBlock(BED,head,3);
            Villager resident=EntityType.VILLAGER.create(level);resident.setNoAi(true);resident.setInvulnerable(true);resident.setAge(0);
            resident.moveTo(61.5,-57,8.5,0F,0F);level.addFreshEntity(resident);worker=resident.getUUID();
            var homes=ResidenceData.get(level);check(homes.link(level,HOME,resident).equals("ok"),"Visual resident link failed");homes.renameResident(worker,"Morador Contratável");homes.reserveBed(worker,level.dimension(),HOME,BED);
            resident.startSleeping(BED);
            level.setBlock(POST,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);level.setBlock(COMMERCIAL,WorkModule.COMMERCIAL.get().defaultBlockState(),3);
            station(level,POST).setItem(31,ItemStack.EMPTY);
            var player=server.getPlayerList().getPlayer(playerId);
            player.getInventory().setItem(1,new ItemStack(Items.EMERALD,20));
            open(player,POST);
        });
        if(ticks==800){selectWorker(mc);click(screen(mc),"Fazendeiro");}
        if(ticks==804){
            var menu=(WorkMenu)mc.player.containerMenu;
            mc.gameMode.handleInventoryMouseClick(menu.containerId,29,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player);
        }
        if(ticks==810){
            check(mc.player.containerMenu.getSlot(0).getItem().getCount()==15,"Payment above 15 or missing client sync");
            capture(mc,"visual-industrial-hiring.png");
        }
        if(ticks==815){
            WorkMenu menu=(WorkMenu)mc.player.containerMenu;int id=menu.containerId;UUID stationId=menu.stationId;
            click(screen(mc),"Contratar");
            WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(id,stationId,worker,Profession.FARMER.ordinal()));
        }
        if(ticks==850)server.execute(()->{
            var level=server.overworld();var s=station(level,POST);
            check(server.getPlayerList().getPlayer(playerId).getInventory().getItem(1).getCount()==10,"First hire did not return five emeralds");
            ResidenceData.get(level).renameResident(worker,"Steve");
            ResidenceData.get(level).applyResidentName((Villager)level.getEntity(worker));
            check(s.worker.equals(worker)&&s.getItem(31).isEmpty()&&level.getBlockState(POST).is(WorkModule.PROFESSIONAL.get(Profession.FARMER).get()),"Real industrial transaction/double-click failed");
            check(ResidenceData.get(level).bedClaim(worker).bed().equals(BED)&&Employment.indicator(level,worker,false),"Hiring lost bed or work indicator");
            s.setItem(0,new ItemStack(Items.APPLE,13));s.setItem(27,new ItemStack(Items.IRON_HOE));s.setItem(28,new ItemStack(Items.BONE_MEAL,3));s.setItem(29,new ItemStack(Items.PUMPKIN_SEEDS,2));s.setItem(30,new ItemStack(Items.WATER_BUCKET));
            WorkforceData.get(level).setProgress(worker,Profession.FARMER.id(),3,57);hired=true;
            System.out.println("EMPLOYMENT_GRAPHICAL_CONTRACT_PASSED");
        });
        if(ticks==870){check(hired&&mc.player.containerMenu instanceof WorkMenu m&&!m.hiring&&m.slots.get(0).getItem().getCount()==13,"Professional menu inventory did not sync");checkFarmerHeader(screen(mc),"Steve (Fazendeiro) - Lv. 3",50);
            checkCompactFarmer(mc,screen(mc),(WorkMenu)mc.player.containerMenu);
            check(!button(screen(mc),"5×5").active&&button(screen(mc),"9×9").active&&!button(screen(mc),"13×13").active,"Farmer area level lock");
            capture(mc,"visual-farmer-inventory.png");}
        if(ticks==875)server.execute(()->{
            var player=server.getPlayerList().getPlayer(playerId);var resident=(Villager)player.getLevel().getEntity(worker);
            var level=player.getLevel();
            ResidenceData.get(level).renameResident(worker,"Alexandria de Campo Verde e Vale do Sol");
            ResidenceData.get(level).applyResidentName(resident);
            WorkforceData.get(level).setProgress(worker,Profession.FARMER.id(),4,99);
            var event=new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,resident);MinecraftForge.EVENT_BUS.post(event);
            check(event.isCanceled()&&player.containerMenu instanceof WorkMenu menu&&menu.inventory==station(player.getLevel(),POST),"Worker did not open the block's exact inventory");shared=true;
            System.out.println("EMPLOYMENT_INVENTORY_SHARED_PASSED");
        });
        if(ticks==887){
            checkFarmerHeader(screen(mc),"… (Fazendeiro) - Lv. 4",174);
            capture(mc,"visual-farmer-long-name.png");
            server.execute(()->br.com.kraftcity.industry.IndustrialRules.progress(server.overworld(),station(server.overworld(),POST)));
        }
        if(ticks==905){check(shared&&mc.player.containerMenu.slots.get(0).getItem().getCount()==13,"Worker inventory did not sync");checkFarmerHeader(screen(mc),"… (Fazendeiro) - Lv. 5",182);
            for(int size=5;size<=13;size+=2)check(button(screen(mc),size+"×"+size).active,"Max-level area stayed locked");
            click(screen(mc),"11×11");
            capture(mc,"visual-worker-inventory.png");}
        if(ticks==912)server.execute(()->{
            var level=server.overworld();
            check(br.com.kraftcity.industry.IndustrialRules.size(level,station(level,POST))==11,"Area change missing on server");
            WorkforceData.get(level).setProgress(worker,Profession.FARMER.id(),3,57);
        });
        if(ticks==914){
            var screen=screen(mc);
            var menu=(WorkMenu)mc.player.containerMenu;
            var dismiss=button(screen,"Demitir");
            check(dismiss!=null&&dismiss.active&&button(screen,"Vincular baú")==null,"Dismissal or automatic discard control missing");
            check(dismiss.x>=screen.width/2&&dismiss.y<screen.height/2,"Dismiss button is not in the upper right");
            capture(mc,"visual-worker-dismiss.png");
            dismiss.onPress();
            WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Dismiss(menu.containerId,menu.stationId,menu.snapshot.epoch()));
        }
        if(ticks==925){
            check(button(screen(mc),"Demitir")==null&&button(screen(mc),"Contratar")!=null,"Dismiss UI remained available at vacant post");
            capture(mc,"visual-worker-dismissed.png");
            server.execute(()->{
                var level=server.overworld();var s=station(level,POST);
                check(s.worker==null&&s.getItem(0).getCount()==13&&ResidenceData.get(level).owner(worker)!=null,"Dismissal lost housing or inventory");
                s.setItem(31,new ItemStack(Items.EMERALD,1));
                check(Employment.hire(server.getPlayerList().getPlayer(playerId),s,worker,Profession.FARMER).equals("ok"),"One-emerald rehire failed");
                System.out.println("EMPLOYMENT_DISMISS_GRAPHICAL_PASSED");
            });
        }
        if(ticks==930)mc.player.closeContainer();
        if(ticks==935)server.execute(()->{
            var level=server.overworld();var player=server.getPlayerList().getPlayer(playerId);var s=station(level,POST);
            level.destroyBlock(POST,true);check(!s.active&&s.worker==null,"Broken workstation still employed worker");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(POST).inflate(2),e->e.getItem().hasTag()&&e.getItem().getTag().hasUUID("KraftCityStation")&&e.getItem().getTag().getUUID("KraftCityStation").equals(s.id));
            check(drops.size()==1,"Break yielded duplicate/missing recovery objects");var token=drops.get(0).getItem().copy();drops.get(0).discard();player.setItemInHand(InteractionHand.MAIN_HAND,token);
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,token,new BlockHitResult(Vec3.atCenterOf(POST.below()),Direction.UP,POST.below(),false));
            check(((BlockItem)token.getItem()).place(context).consumesAction(),"Professional recovery placement failed");
            check(station(level,POST)==s&&s.getItem(0).getCount()==13&&s.getItem(27).is(Items.IRON_HOE),"Recovery lost/copied inventory");
            open(player,POST);portable=true;System.out.println("EMPLOYMENT_PORTABLE_INVENTORY_PASSED");
        });
        if(ticks==945){check(portable&&mc.player.containerMenu.slots.get(0).getItem().getCount()==13,"Recovered inventory did not sync");capture(mc,"visual-replaced-workstation.png");}
        if(ticks==950)mc.player.closeContainer();
        if(ticks==955)server.execute(()->{
            approvedConfig=net.minecraftforge.fml.config.ConfigTracker.INSTANCE.configSets().get(net.minecraftforge.fml.config.ModConfig.Type.SERVER).stream().filter(c->c.getSpec()==EmploymentConfig.SPEC).findFirst().orElseThrow().getConfigData();
            EmploymentConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());EmploymentConfig.COMMERCIAL_ENABLED.set(false);var player=server.getPlayerList().getPlayer(playerId);station(player.getLevel(),COMMERCIAL).setItem(31,new ItemStack(Items.EMERALD,20));open(player,COMMERCIAL);});
        if(ticks==985){
            selectWorker(mc);click(screen(mc),"Padeiro");check(!button(screen(mc),"Contratar").active,"Commercial hire was enabled without a decided price");capture(mc,"visual-commercial-pending.png");
            var menu=(WorkMenu)mc.player.containerMenu;WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(menu.containerId,menu.stationId,worker,Profession.BAKER.ordinal()));
        }
        if(ticks==995)mc.player.closeContainer();
        if(ticks==1000)server.execute(()->{
            var level=server.overworld();var s=station(level,COMMERCIAL);check(s.worker==null&&s.getItem(31).getCount()==20,"Forged commercial request charged emeralds");
            EmploymentConfig.SPEC.setConfig(approvedConfig);approvedConfig=null;
            pending=true;System.out.println("COMMERCIAL_PENDING_NO_CHARGE_PASSED");station(level,POST).setItem(31,new ItemStack(Items.EMERALD,15));open(server.getPlayerList().getPlayer(playerId),POST);
        });
        if(ticks==1020)click(screen(mc),"Contratar");
        if(ticks==1040){
            selectWorker(mc);
            var menu=(WorkMenu)mc.player.containerMenu;
            check(menu.snapshot.candidates().stream().anyMatch(candidate->candidate.id().equals(worker)&&candidate.cost()==1),
                "Server did not synchronize one-emerald rehire price");
            click(screen(mc),"Contratar");
        }
        if(ticks==1060)server.execute(()->{
            var level=server.overworld();var s=station(level,POST);var progress=WorkforceData.get(level).progress(worker,Profession.FARMER.id());
            check(server.getPlayerList().getPlayer(playerId).getInventory().getItem(1).getCount()==24,"Rehire did not return fourteen emeralds");
            check(worker.equals(s.worker)&&progress.level()==3&&progress.xp()==57&&s.getItem(0).getCount()==13&&s.getItem(31).isEmpty(),"Rehire lost progression/items or charged incorrectly");
            check(ResidenceData.get(level).bedClaim(worker)!=null&&ResidenceData.get(level).bedClaim(worker).bed().equals(BED),"Bed claim changed during employment lifecycle");
            rehired=true;System.out.println("EMPLOYMENT_REHIRE_PROGRESS_PASSED");
        });
        if(ticks==1070){check(hired&&shared&&portable&&pending&&rehired,"Employment graphical sequence incomplete");capture(mc,"visual-rehired-progress.png");}
        if(ticks==1080)server.execute(()->{
            var player=server.getPlayerList().getPlayer(playerId);var level=player.getLevel();
            var former=EntityType.VILLAGER.create(level);former.setNoAi(true);former.setAge(0);
            former.moveTo(65.5,-57,8.5,0,0);level.addFreshEntity(former);
            var formerId=former.getUUID();
            check(ResidenceData.get(level).link(level,HOME,former).equals("ok"),"Second resident link failed");
            var vacant=station(level,COMMERCIAL);
            player.teleportTo(level,COMMERCIAL.getX()+.5,COMMERCIAL.getY(),COMMERCIAL.getZ()+1.5,180F,20F);
            check(Employment.hire(player,vacant,formerId,Profession.BAKER).equals("ok"),"Second resident hire failed");
            WorkforceData.get(level).setProgress(formerId,Profession.BAKER.id(),2,31);
            check(Employment.dismiss(player,vacant,Employment.epoch(vacant)),"Could not save an independently dismissed worker");
            vacant.setItem(31,new ItemStack(Items.EMERALD,20));
            vacant.commerce.putUUID("visual_dismissed_worker",formerId);
            vacant.setChanged();
            player.teleportTo(level,60.5,-57,10.5,180F,20F);
            ResidenciasDosAldeoes.ANCHOR.get().use(level.getBlockState(HOME),level,HOME,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(HOME),Direction.UP,HOME,false));
        });
        if(ticks==1110){
            check(mc.screen instanceof br.com.residenciasaldeoes.client.AnchorScreen,"Residential screen did not open");
            try {
                var field=br.com.residenciasaldeoes.client.AnchorScreen.class.getDeclaredField("residents");field.setAccessible(true);
                @SuppressWarnings("unchecked") var entries=(java.util.List<br.com.residenciasaldeoes.network.ModNetwork.ResidentEntry>)field.get(mc.screen);
                check(entries.stream().anyMatch(e->e.id().equals(worker)&&e.work()),"Client residential work indicator is not green");
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            capture(mc,"visual-residential-employed.png");System.out.println("EMPLOYMENT_RESIDENTIAL_INDICATOR_PASSED");
        }
        if(ticks>=1120)IndustryVisualCheck.tick(mc,ticks);
    }
    public static void restoreNightFixture(ServerLevel level){
        var s=station(level,POST);var resident=(Villager)level.getEntity(s.worker);
        ResidenceData.get(level).reserveBed(s.worker,level.dimension(),HOME,BED);resident.startSleeping(BED);
    }
    public static void verifyRestore(ServerLevel level){
        var s=station(level,POST);check(s!=null&&s.active&&s.worker!=null&&s.profession==Profession.FARMER,"Employment lost after process restart");
        var progress=WorkforceData.get(level).progress(s.worker,Profession.FARMER.id());
        check(s.getItem(0).getCount()==13&&s.getItem(27).is(Items.IRON_HOE)&&s.getItem(28).getCount()==3&&s.getItem(29).getCount()==2&&s.getItem(30).is(Items.WATER_BUCKET)&&s.getItem(31).isEmpty()&&progress.level()==3&&progress.xp()==57,"Professional inventory/history lost after restart");
        check(ResidenceData.get(level).owner(s.worker).pos.equals(HOME)&&ResidenceData.get(level).bedClaim(s.worker).bed().equals(BED),"Housing/bed link lost after restart");
        var vacant=station(level,COMMERCIAL);
        check(vacant.worker==null&&vacant.getItem(31).getCount()==20,"Dismissed workstation did not stay vacant");
        var dismissed=vacant.commerce.getUUID("visual_dismissed_worker");
        check(ResidenceData.get(level).owner(dismissed).pos.equals(HOME)
            &&WorkforceData.get(level).worker(dismissed).orElseThrow().workstation().isEmpty()
            &&WorkforceData.get(level).progress(dismissed,Profession.BAKER.id()).equals(new WorkforceData.Progress(2,31))
            &&Employment.cost(level,Profession.Sector.INDUSTRIAL,dismissed)==1,"Dismissed resident history or fee changed after reload");
        System.out.println("EMPLOYMENT_DISMISS_WORLD_RESTART_PASSED");
        System.out.println("EMPLOYMENT_WORLD_RESTART_PASSED");
    }
}
