package br.com.kraftcity.test;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.industry.*;
import br.com.kraftcity.employment.client.WorkScreen;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.io.File;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** Six production scenes use the same public server jobs and synchronized menus as real play. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="residenciasaldeoes",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class IndustryVisualCheck {
    private static String pendingCapture,completedCapture;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void captureFrame(net.minecraftforge.event.TickEvent.RenderTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||pendingCapture==null)return;
        var mc=Minecraft.getInstance();
        Screenshot.grab(new File("../build"),pendingCapture,mc.getMainRenderTarget(),message->{});
        completedCapture=pendingCapture;
        pendingCapture=null;
    }

    private static boolean captureReady(String filename){
        if(filename.equals(completedCapture))return true;
        pendingCapture=filename;
        return false;
    }
    private static boolean farmerSceneDone,blueBoundsCleared;
    private static int bindingDelay;
    private static volatile boolean chestSelected;
    private static List<ItemStack> stockBeforeCapture;
    private static final net.minecraft.resources.ResourceLocation ARCANE = new net.minecraft.resources.ResourceLocation("bausarcanos","bau_ender");
    private static final Map<Profession,UUID> ids=new EnumMap<>(Profession.class);
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    // Keep the demo workstations away from the generated GameTest grid.
    // Use an isolated strip so its leftover containers cannot receive demo output.
    private static BlockPos post(int i){return new BlockPos(100+i*18,-57,-120);}
    private static EmploymentData.Station station(ServerLevel l,int i){return ((WorkBlockEntity)l.getBlockEntity(post(i))).station();}
    private static <T extends Animal>T animal(ServerLevel l,EmploymentData.Station s,EntityType<T> type){var a=type.create(l);a.setPos(s.pos.getX()+1.5,s.pos.getY(),s.pos.getZ()+.5);a.setNoAi(true);a.setAge(0);a.getPersistentData().putUUID("KCIndustryStation",s.id);l.addFreshEntity(a);return a;}
    private static int count(EmploymentData.Station s,Item item){int n=0;for(int i=0;i<27;i++)if(s.getItem(i).is(item))n+=s.getItem(i).getCount();return n;}
    private static void setup(ServerPlayer p,int i){
        var l=p.getLevel();
        if(i==0){
            var original=new BlockPos(1,-58,8);var anchor=(br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(original);
            for(int slot=0;slot<10;slot++)check(anchor.getItem(slot).getCount()==slot+1,"Original persistence fixture changed before industrial scenes");
            // Alpha/courier scenes are complete. Retire only their actors before another workday.
            // Keep every stored stack untouched for the final fresh-JVM conservation assertion.
            ResidenceData.get(l).unlinkResidents(l,l.dimension(),original);
        }
        l.setDayTime(1000);l.setWeatherParameters(10000,0,false,false);var pos=post(i);p.teleportTo(l,pos.getX()+.5,pos.getY(),pos.getZ()+2.5,180F,20F);
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){l.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<5;y++)l.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(pos).inflate(7)).forEach(Entity::discard);
        var profession=Profession.values()[i];var home=pos.offset(-3,-1,0);l.setBlock(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(home)).setSize(11);
        var v=EntityType.VILLAGER.create(l);v.setNoAi(true);v.setInvulnerable(true);v.setAge(0);v.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);l.addFreshEntity(v);
        check(ResidenceData.get(l).link(l,home,v).equals("ok"),"Industry visual home");ResidenceData.get(l).renameResident(v.getUUID(),profession.label+" Industrial");
        l.setBlock(pos,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);var s=station(l,i);s.setItem(31,new ItemStack(Items.EMERALD,10));check(Employment.hire(p,s,v.getUUID(),profession).equals("ok"),"Industry visual hire");ids.put(profession,s.id);
        var target=pos.east();
        switch(profession){
            case FARMER->{
                var arcane=arcaneBlock();
                l.setBlock(pos.south().east(),(arcane==null?Blocks.CHEST:arcane).defaultBlockState(),3);
                if(arcane!=null)l.setBlock(pos.south().east(2),arcane.defaultBlockState(),3);s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,10));s.setItem(27,new ItemStack(IndustrialTools.HOE.get()));s.setItem(30,new ItemStack(Items.WATER_BUCKET));l.setBlock(target,Blocks.FARMLAND.defaultBlockState(),3);l.setBlock(target.above(),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);s.industry.putInt("cursor",67);}
            case BUTCHER->{s.setItem(27,new ItemStack(IndustrialTools.AXE.get()));var old=animal(l,s,EntityType.COW);old.getPersistentData().putLong("KCIndustryBorn",-1000000);animal(l,s,EntityType.COW);animal(l,s,EntityType.COW);}
            case SHEPHERD->{s.setItem(27,new ItemStack(IndustrialTools.SHEARS.get(IndustrialTools.STONE).get()));animal(l,s,EntityType.SHEEP);}
            case FISHERMAN->{s.setItem(27,new ItemStack(IndustrialTools.RODS.get(IndustrialTools.STONE).get()));var water=pos.offset(0,-2,0);for(BlockPos at:BlockPos.betweenClosed(water.offset(-1,-1,-1),water.offset(1,1,1)))l.setBlock(at,Blocks.WATER.defaultBlockState(),3);s.industry.putLong("water",water.asLong());s.industry.putInt("fish_elapsed",IndustrialRules.fishingTicks(EmploymentConfig.FISH_TICKS.get(),1));}
            case BREEDER->{WorkforceData.get(l).setProgress(v.getUUID(),profession.id(),2,25);s.setItem(28,new ItemStack(Items.BONE,8));s.setItem(29,new ItemStack(Items.BEEF,2));for(var w:List.of(animal(l,s,EntityType.WOLF),animal(l,s,EntityType.WOLF))){w.setTame(true);w.getPersistentData().putUUID("KraftCityCaretaker",v.getUUID());}}
            case BEEKEEPER->{s.setItem(0,new ItemStack(Items.GLASS_BOTTLE,2));s.setItem(1,new ItemStack(IndustrialTools.SHEARS.get(3).get()));l.setBlock(target.below(),Blocks.CAMPFIRE.defaultBlockState(),3);l.setBlock(target,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,5),3);s.industry.putInt("cursor",67);}
            default->throw new IllegalStateException();
        }
        // Cursor at (x=+1,z=0,y=0) for the initial 5x5 area.
        if(profession==Profession.FARMER)v.setPos(target.getX()+.5,target.getY()+1,target.getZ()+.5);
        if(profession==Profession.BEEKEEPER)s.industry.putInt("cursor",67);
        IndustrialWork.tick(l,s);
        if(profession==Profession.FARMER){
            for(int action=0;action<3;action++)IndustrialWork.tick(l,s);
            WorkforceData.get(l).setProgress(v.getUUID(),profession.id(),2,25);
        }
        boolean worked=switch(profession){
            case FARMER->count(s,Items.WHEAT)>0;
            case BUTCHER->count(s,Items.BEEF)>0;
            case SHEPHERD->count(s,Items.WHITE_WOOL)>0;
            case FISHERMAN->count(s,Items.COD)+count(s,Items.SALMON)+count(s,Items.TROPICAL_FISH)+count(s,Items.PUFFERFISH)>0;
            case BREEDER->l.getEntitiesOfClass(Wolf.class,IndustrialWork.area(l,s),Wolf::isBaby).size()==1;
            case BEEKEEPER->count(s,Items.HONEY_BOTTLE)==1;
            default->false;
        };
        check(worked,"Industry profession produced no real result: "+profession);
        s.industry.putBoolean("preview",true);s.setChanged();IndustryNetwork.sendBounds(p);WorkBlock.open(p,s);
        System.out.println("INDUSTRY_"+profession.name()+"_FUNCTIONAL_PASSED");
    }
    private static java.util.List<IndustryNetwork.Bound> bounds(){
        try{var f=br.com.kraftcity.industry.client.WorkPreview.class.getDeclaredField("entries");f.setAccessible(true);
            @SuppressWarnings("unchecked") var result=(java.util.List<IndustryNetwork.Bound>)f.get(null);return result;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void toggle(Minecraft mc){
        var screen=(WorkScreen)mc.screen;var b=screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button).map(c->(net.minecraft.client.gui.components.Button)c).filter(c->c.getMessage().getString().equals("Limites")).findFirst().orElseThrow();b.onPress();
    }
    private static Block arcaneBlock(){
        var registry=net.minecraftforge.registries.ForgeRegistries.BLOCKS;
        return registry.containsKey(ARCANE)?registry.getValue(ARCANE):null;
    }

    private static void testArcaneTransfer(ServerLevel level,ServerPlayer player,EmploymentData.Station station,BlockPos target){
        var worker=(Villager)level.getEntity(station.worker);
        final int slot=EmploymentData.COMMON-1;
        station.setItem(slot,new ItemStack(Items.COBBLESTONE,4));
        int[] offer=new int[EmploymentData.COMMON];offer[slot]=4;
        var tile=level.getBlockEntity(target);
        var other=level.getBlockEntity(target.east());
        try{
            var type=tile.getClass();
            var channel=type.getField("channel");
            check(channel.getInt(tile)==channel.getInt(other),"Arcane fixtures do not share a channel");
            var storageType=Class.forName("br.com.petrick.bausarcanos.EnderStorage",false,type.getClassLoader());
            var storage=storageType.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,level.getServer());
            var inventory=storageType.getMethod("inventory",UUID.class,int.class);
            var owner=(net.minecraft.world.Container)inventory.invoke(storage,player.getUUID(),channel.getInt(tile));
            var stranger=(net.minecraft.world.Container)inventory.invoke(storage,UUID.randomUUID(),channel.getInt(tile));
            check(owner.isEmpty()&&stranger.isEmpty(),"Unexpected stock in Arcane test channels");
            check(tile.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new)
                .getSlots()==0,"Arcane version no longer exposes its zero-slot automation capability");
            br.com.kraftcity.logistics.DiscardChests.deposit(level,station,worker,target,offer,"deposit");
            check(station.getItem(slot).isEmpty()&&owner.countItem(Items.COBBLESTONE)==4&&stranger.isEmpty(),
                "Arcane discard did not reach the authorized personal channel exactly once");
            var lid=type.getField("openers");
            check(lid.getInt(tile)==1,"Arcane lid did not open for the transfer");
            var aliasMenu=((net.minecraft.world.MenuProvider)other).createMenu(0,player.getInventory(),player);
            check(aliasMenu.slots.stream().anyMatch(entry->entry.getItem().is(Items.COBBLESTONE)&&entry.getItem().getCount()==4),
                "Second Arcane chest does not show the deposited items");
            aliasMenu.removed(player);
            System.out.println("FARMER_ARCANE_OWNER_CHANNEL_TRANSFER_PASSED");
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }

    private static void press(Minecraft mc,String label){
        var button=mc.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
            .map(net.minecraft.client.gui.components.Button.class::cast)
            .filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();
        button.onPress();
    }

    private static boolean bindingFlow(Minecraft mc,int phase){
        var level=mc.getSingleplayerServer().overworld();
        var playerId=mc.player.getUUID();
        var target=post(0).south().east();
        if(phase==6){
            if(!(mc.screen instanceof WorkScreen)
                ||!(mc.player.containerMenu instanceof WorkMenu menu)||menu.snapshot==null)return false;
            if(!captureReady("visual-farmer-discard-unbound.png"))return false;
            press(mc,"Vincular Baú");
        }
        if(phase==11){
            if(mc.screen!=null)return false;
            mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target),
                    net.minecraft.core.Direction.UP,target,false));
        }
        if(phase==22){
            if(!chestSelected){
                level.getServer().execute(()->{
                    var s=station(level,0);
                    if(!chestSelected&&br.com.kraftcity.logistics.DiscardBinding.linked(level,s)){
                        var player=level.getServer().getPlayerList().getPlayer(playerId);
                        if(arcaneBlock()!=null)testArcaneTransfer(level,player,s,target);
                        chestSelected=true;
                        WorkBlock.open(player,s);
                    }
                });
                return false;
            }
            if(!(mc.screen instanceof WorkScreen)
                ||!(mc.player.containerMenu instanceof WorkMenu menu)||menu.snapshot==null)return false;
            check(menu.snapshot.discardLinked(),"Bound chest was missing from the menu");
            if(!captureReady("visual-farmer-discard-linked.png"))return false;
            press(mc,"Vinculado");
            check(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen,"No unlink confirmation");
        }
        if(phase==24){
            if(!captureReady("visual-farmer-discard-confirm.png"))return false;
            press(mc,"Cancelar");
            check(((WorkMenu)mc.player.containerMenu).snapshot.discardLinked(),"Cancel changed the link");
        }
        if(phase==26){
            press(mc,"Vinculado");
            press(mc,"Desvincular");
        }
        if(phase==32){
            if(((WorkMenu)mc.player.containerMenu).snapshot.discardLinked())return false;
            System.out.println("FARMER_DISCARD_BINDING_GUI_PASSED");
        }
        if(phase==33)level.getServer().execute(()->{
            var station=station(level,0);
            var original=new ArrayList<ItemStack>();
            for(int i=0;i<EmploymentData.COMMON;i++){
                original.add(station.getItem(i).copy());
                station.setItem(i,new ItemStack(Items.COBBLESTONE,64));
            }
            stockBeforeCapture=List.copyOf(original);
        });
        if(phase==34){
            var menu=(WorkMenu)mc.player.containerMenu;
            if(menu.slots.subList(0,EmploymentData.COMMON).stream().anyMatch(slot->slot.getItem().getCount()!=64))return false;
            if(!captureReady("visual-farmer-discard-full.png"))return false;
            level.getServer().execute(()->{
                var station=station(level,0);
                for(int i=0;i<stockBeforeCapture.size();i++)station.setItem(i,stockBeforeCapture.get(i));
            });
        }
        return true;
    }

    public static void tick(Minecraft mc,int ticks){
        int elapsed=ticks-1120-bindingDelay;if(elapsed<0)return;int i=elapsed/70,phase=elapsed%70;
        if(i<6){
            if(i==0&&!bindingFlow(mc,phase)){
                check(++bindingDelay<160,"Discard binding UI timed out during phase "+phase);
                return;
            }
            if(phase==0)mc.getSingleplayerServer().execute(()->setup(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()),i));
            if(phase==45){check(mc.screen instanceof WorkScreen,"Industry menu missing");check(mc.player.containerMenu instanceof WorkMenu m&&m.slots.size()==(i==0?66:68)&&m.snapshot!=null,"Industry menu not synchronized");}
            if(i==0&&phase==35){
                var menu=(WorkMenu)mc.player.containerMenu;
                check(menu.snapshot.level()==2&&menu.snapshot.size()==5,"Lv2 changed the active area");
                var buttons=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button)
                    .map(w->(net.minecraft.client.gui.components.Button)w).toList();
                check(buttons.stream().anyMatch(button->button.getMessage().getString().equals("9×9")&&!button.active),"Lv2 unlocked 9×9");
                var seven=buttons.stream().filter(button->button.getMessage().getString().equals("7×7")).findFirst().orElseThrow();
                check(seven.active,"Lv2 did not unlock 7×7");
                seven.onPress();
            }
            if(phase==50)Screenshot.grab(new File("../build"),"visual-industry-"+Profession.values()[i].path+".png",mc.getMainRenderTarget(),message->{});
            if(i==0&&phase==51){
                check(((WorkMenu)mc.player.containerMenu).snapshot.size()==7
                    &&bounds().stream().anyMatch(b->b.pos().equals(post(0))&&b.size()==7&&b.farmer()),"Selected 7×7 area did not synchronize");
                System.out.println("FARMER_MANUAL_AREA_SELECTION_PASSED");toggle(mc);
            }
            if(i==0&&phase>51&&phase<62&&!blueBoundsCleared&&bounds().stream().noneMatch(b->b.pos().equals(post(0)))){
                blueBoundsCleared=true;
                toggle(mc);
            }
            if(i==0&&phase==62)check(blueBoundsCleared,"Blue toggle off left ghost");
            if(phase==63)mc.player.closeContainer();
            if(i==0&&phase==64)Screenshot.grab(new File("../build"),"visual-farmer-hoe.png",mc.getMainRenderTarget(),message->{});
            if(i==0&&phase==65)mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var pos=post(0);
                player.teleportTo(player.getLevel(),pos.getX()+7.5,pos.getY()+4,pos.getZ()+7.5,135F,35F);
            });
            if(i==0&&phase==69){check(bounds().stream().anyMatch(b->b.pos().equals(post(0))),"Blue toggle on missing");Screenshot.grab(new File("../build"),"visual-industry-blue-limits.png",mc.getMainRenderTarget(),message->{});System.out.println("INDUSTRY_BLUE_LIMITS_PASSED");}
        }
        if(elapsed==425)mc.getSingleplayerServer().execute(()->{
            var l=mc.getSingleplayerServer().overworld();
            var original=(br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(new BlockPos(1,-58,8));
            for(int slot=0;slot<10;slot++)check(original.getItem(slot).getCount()==slot+1,"Original stacks changed during industrial scenes");
            if(arcaneBlock()!=null){
                var s=station(l,0);
                var player=l.getServer().getPlayerList().getPlayer(mc.player.getUUID());
                var target=post(0).south().east();
                var tile=l.getBlockEntity(target);
                try{
                    check(tile.getClass().getField("openers").getInt(tile)==0,"Arcane lid was left open");
                }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
                br.com.kraftcity.logistics.DiscardBinding.begin(player,s);
                net.minecraftforge.common.ForgeHooks.onRightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,target,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target),
                        net.minecraft.core.Direction.UP,target,false));
                check(br.com.kraftcity.logistics.DiscardBinding.linked(l,s),"Arcane rebind after unlink failed");
                s.industry.putUUID("visual_arcane_owner",player.getUUID());
                s.setChanged();
            }
            l.setDayTime(13001);EmploymentVisualCheck.restoreNightFixture(l);
            for(var id:ids.values()){var s=EmploymentData.get(l).station(id);s.industry.putString("visual_test","six_jobs_passed");s.setChanged();}
        });
        if(elapsed>=450){
            if(!farmerSceneDone){farmerSceneDone=FarmerVisualCheck.tick(mc);return;}
            CommerceVisualCheck.tick(mc,ticks);
        }
    }
    public static void verifyRestore(ServerLevel l){
        for(int i=0;i<6;i++){
            var s=station(l,i);check(s!=null&&s.active&&s.worker!=null&&s.profession==Profession.values()[i]&&s.industry.getString("visual_test").equals("six_jobs_passed"),"Industry post state lost #"+i);
            var progress=WorkforceData.get(l).progress(s.worker,s.profession.id());check(progress.xp()>=1,"Industry progression lost #"+i);
            if(i==0)check(count(s,Items.WHEAT)>0&&s.getItem(30).is(Items.WATER_BUCKET)
                &&IndustrialRules.size(l,s)==7,"Farmer production, water or selected area lost");
            if(i==1)check(count(s,Items.BEEF)>0,"Butcher output lost");
            if(i==2)check(count(s,Items.WHITE_WOOL)>0&&s.getItem(27).is(IndustrialTools.SHEARS.get(IndustrialTools.STONE).get())
                &&s.getItem(27).getDamageValue()>0,"Shepherd output or worn stone shears lost");
            if(i==3)check(count(s,Items.COD)+count(s,Items.SALMON)+count(s,Items.TROPICAL_FISH)+count(s,Items.PUFFERFISH)>0
                &&s.getItem(27).is(IndustrialTools.RODS.get(IndustrialTools.STONE).get())&&s.getItem(27).getDamageValue()>0,"Fish output or worn stone rod lost");
            if(i==5)check(count(s,Items.HONEY_BOTTLE)>0,"Honey output lost");
        }
        if(arcaneBlock()!=null){
            var s=station(l,0);
            check(br.com.kraftcity.logistics.DiscardBinding.linked(l,s),"Arcane link lost after reopening");
            var player=l.getServer().getPlayerList().getPlayer(s.industry.getUUID("visual_arcane_owner"));
            var tile=l.getBlockEntity(post(0).south().east(2));
            var menu=((net.minecraft.world.MenuProvider)tile).createMenu(0,player.getInventory(),player);
            check(menu.slots.stream().anyMatch(slot->slot.getItem().is(Items.COBBLESTONE)&&slot.getItem().getCount()==4),
                "Reopened Arcane alias lost its original stock");
            menu.removed(player);
            System.out.println("FARMER_ARCANE_RESTART_PASSED");
        }
        FarmerVisualCheck.verifyRestore(l);
        System.out.println("INDUSTRY_SIX_JOBS_WORLD_RESTART_PASSED");
    }
    private IndustryVisualCheck(){}
}
