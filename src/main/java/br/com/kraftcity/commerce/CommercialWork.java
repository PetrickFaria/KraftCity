package br.com.kraftcity.commerce;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.industry.*;
import br.com.kraftcity.logistics.DiscardChests;
import br.com.kraftcity.workforce.WorkforceData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeHooks;

/** One operation at a time, no item escrow/copy: validate -> simulate -> commit on the server thread. */
public final class CommercialWork {
    private record Sample(long at,int workerAge,UUID worker,long epoch,BlockPos pos){}
    private static final Map<UUID,Sample> SAMPLES=new HashMap<>();
    public static boolean commercial(EmploymentData.Station s){return s.active&&s.worker!=null&&s.profession!=null&&s.sector==Profession.Sector.COMMERCIAL;}
    public static int level(ServerLevel l,EmploymentData.Station s){return Math.min(5,Math.max(1,WorkforceData.get(l).progress(s.worker,s.profession.id()).level()));}
    public static int duration(int level){return switch(Math.min(5,Math.max(1,level))){case 1->1200;case 2->1000;case 3->800;case 4->600;default->300;};}
    public static boolean ownsNavigation(Villager v){
        if(!(v.level instanceof ServerLevel l))return false;var s=EmploymentData.get(l).assigned(v.getUUID());
        long phase=Math.floorMod(l.getDayTime(),24000);
        return s!=null&&commercial(s)&&l.hasChunkAt(s.pos)&&!v.isSleeping()&&!l.isThundering()&&!Employment.fleeing(v)&&(phase<13000||phase>=23000)
            &&!v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
            &&!v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")&&!v.getPersistentData().contains("residenciasaldeoes_night_door_task");
    }
    public static void tick(ServerLevel l,EmploymentData.Station s){
        if(!commercial(s)||!l.hasChunkAt(s.pos)||!(l.getEntity(s.worker) instanceof Villager v)){SAMPLES.remove(s.id);return;}
        long now=l.getGameTime(),epoch=s.commerce.getLong("epoch");var old=SAMPLES.put(s.id,new Sample(now,v.tickCount,s.worker,epoch,s.pos));
        int delta=old!=null&&old.worker.equals(s.worker)&&old.epoch==epoch&&old.pos.equals(s.pos)&&v.tickCount>=old.workerAge?
            (int)Math.min(20,Math.max(0,Math.min(now-old.at,v.tickCount-old.workerAge))):0;
        step(l,s,delta);
    }
    /** Elapsed active ticks are supplied only by the server scheduler; tests exercise the same bounded step. */
    public static void step(ServerLevel l,EmploymentData.Station s,int elapsedTicks){
        if(!l.getServer().isSameThread())throw new IllegalStateException("Commercial work off server thread");
        if(elapsedTicks<0||elapsedTicks>20)throw new IllegalArgumentException("Unbounded operation delta");
        if(!commercial(s)||!l.hasChunkAt(s.pos)||!Employment.valid(l,s.worker)||!(l.getEntity(s.worker) instanceof Villager v))return;
        if(!IndustrialWork.day(l)){
            if(Math.floorMod(l.getDayTime(),24000)<13000&&!l.isThundering()&&!Employment.fleeing(v)&&!v.isSleeping()){
                if(s.profession==Profession.BAKER) BakerWork.deposit(l,s,v);
                else evening(l,s,v);
            }
            pause(s);return;
        }
        if(WorkplaceVisits.leaving(v))return;
        if(!ownsNavigation(v)){state(s,v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")?"Pausa para transporte residencial; progresso preservado.":"Pausa por clima/rotina residencial; progresso preservado.");return;}
        var plans=CommercialRecipes.plans(l,s.profession);
        if(plans.isEmpty()){state(s,"Receita aprovada indisponível.");IndustrialWork.alert(l,s,"recipe","Receita apropriada não encontrada no jogo/datapack.");return;}
        String id=s.commerce.getString("recipe");var plan=id.isEmpty()?null:plans.stream().filter(p->p.id().toString().equals(id)).findFirst().orElse(null);
        if(!id.isEmpty()&&plan==null){state(s,"Receita removida; operação pausada.");return;}
        if(s.profession==Profession.BAKER&&BakerWork.handleLogistics(l,s,v))return;
        if(plan==null){
            plan=plans.stream().filter(p->count(s,p.input())>=p.count()).findFirst().orElse(null);
            if(plan==null&&s.profession!=Profession.GRILL_COOK)plan=plans.get(0);
            if(plan==null){var supply=findSupply(l,s,v,plans);if(supply!=null)plan=supply.plan;}
            if(plan==null){state(s,"Sem carne crua disponível.");IndustrialWork.alert(l,s,"stock","Faltam carnes cruas adequadas no posto ou descarte do Açougueiro.");return;}
            s.commerce.putString("recipe",plan.id().toString());s.commerce.putInt("elapsed",0);s.setChanged();
        }
        if(count(s,plan.input())<plan.count()){
            if(s.profession==Profession.BAKER)return;
            var supply=findSupply(l,s,v,List.of(plan));
            if(supply==null){state(s,"Estoque insuficiente; progresso preservado.");IndustrialWork.alert(l,s,"stock","Faltam insumos necessários no posto e nos descartes industriais carregados.");return;}
            state(s,"Buscando insumos no descarte industrial.");
            WorkplaceVisits.allowSupplyVisit(v);
            if(!reach(v,supply.chest.pos()))return;
            if(!IndustrialWork.allowed(l,s,v,supply.chest.pos(),null,"commercial_supply")||!stillWorker(l,s,v)||v.distanceToSqr(Vec3.atCenterOf(supply.chest.pos()))>9)return;
            take(l,s,supply,plan.count()-count(s,plan.input()));return;
        }
        if(!(s.profession==Profession.BAKER?BakerWork.atPost(l,s,v):reach(v,s.pos))){state(s,"Indo ao posto de trabalho.");return;}
        if(s.profession==Profession.GRILL_COOK&&!fuelAvailable(s,plan)){
            state(s,EmploymentConfig.COOKING_FUEL.get()==EmploymentConfig.CookingFuel.PENDING?"Combustível pendente; produção pausada.":"Falta combustível vanilla.");
            IndustrialWork.alert(l,s,"fuel",s.commerce.getString("status"));return;
        }
        if(!IndustrialWork.allowed(l,s,v,s.pos,null,"commercial_produce")||!stillWorker(l,s,v)
                ||s.profession==Profession.BAKER&&(!IndustrialWork.day(l)||!ownsNavigation(v)||!BakerWork.atPost(l,s,v))){state(s,"Proteção/vínculo recusou a operação.");return;}
        int elapsed=Math.min(duration(level(l,s)),s.commerce.getInt("elapsed")+elapsedTicks);
        s.commerce.putInt("elapsed",elapsed);state(s,"Produzindo; insumos consumidos ao concluir.");s.setChanged();
        if(elapsed<duration(level(l,s)))return;
        if(!complete(l,s,plan)){state(s,"Saída cheia ou insumo alterado; operação pronta aguardando.");IndustrialWork.alert(l,s,"space","Libere espaço para o resultado; insumos e progresso foram preservados.");return;}
        if(s.profession==Profession.BAKER)BakerWork.recordBread(l,v);
        var data=WorkforceData.get(l);var old=data.progress(s.worker,s.profession.id());long xp=old.xp()+1;
        data.setProgress(s.worker,s.profession.id(),Math.min(5,Math.max(old.level(),1+(int)Math.min(4,xp/25))),xp);
        s.commerce.remove("recipe");s.commerce.putInt("elapsed",0);state(s,"Receita concluída.");s.setChanged();
    }
    static boolean stillWorker(ServerLevel l,EmploymentData.Station s,Villager v){return commercial(s)&&v.getUUID().equals(s.worker)&&l.getEntity(s.worker)==v&&Employment.valid(l,s.worker);}
    public static int count(EmploymentData.Station s,Item input){int n=s.getItem(28).is(input)?s.getItem(28).getCount():0;for(int i=0;i<27;i++)if(s.getItem(i).is(input))n+=s.getItem(i).getCount();return n;}
    private static boolean reach(Villager v,BlockPos p){
        if(v.distanceToSqr(Vec3.atCenterOf(p))<=9){v.getNavigation().stop();return true;}
        v.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,.75);return false;
    }
    static void state(EmploymentData.Station s,String value){if(!s.commerce.getString("status").equals(value)){s.commerce.putString("status",value);s.setChanged();}}
    private static void pause(EmploymentData.Station s){state(s,"Pausa noturna/retorno residencial; progresso preservado.");}
    private record Supply(EmploymentData.Station source,DiscardChests.Access chest,CommercialRecipes.Plan plan){}
    private static Supply findSupply(ServerLevel l,EmploymentData.Station s,Villager v,List<CommercialRecipes.Plan> plans){
        var candidates=new ArrayList<Supply>();Profession source=s.profession==Profession.GRILL_COOK?Profession.BUTCHER:Profession.FARMER;
        for(var industrial:EmploymentData.get(l).stations()){
            if(!industrial.active||industrial.profession!=source||!industrial.dimension.equals(s.dimension)||!l.hasChunkAt(industrial.pos))continue;
            if(!(l.getBlockEntity(industrial.pos) instanceof WorkBlockEntity be)||!industrial.id.equals(be.identity()))continue;
            for(var chest:DiscardChests.around(l,industrial,IndustrialWork.area(l,industrial)))if(DiscardChests.canOpen(l,chest.pos(),IndustrialWork.actor(l,s,v)))for(var plan:plans)
                if(containerCount(chest.inventory(),plan.input())>0&&count(s,plan.input())<plan.count())candidates.add(new Supply(industrial,chest,plan));
        }
        candidates.sort(Comparator.<Supply>comparingDouble(c->c.chest.pos().distSqr(s.pos)).thenComparingInt(c->c.chest.pos().getX()).thenComparingInt(c->c.chest.pos().getY()).thenComparingInt(c->c.chest.pos().getZ()).thenComparing(c->c.plan.id().toString()));
        return candidates.isEmpty()?null:candidates.get(0);
    }
    private static int containerCount(Container c,Item input){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(input))n+=c.getItem(i).getCount();return n;}
    private static void take(ServerLevel l,EmploymentData.Station s,Supply supply,int needed){
        // Re-resolve after navigation: a broken/replaced/unloaded chest cannot be accessed through a stale object.
        var source=DiscardChests.at(l,supply.chest.pos());if(source==null)return;
        for(int slot=0;slot<source.getContainerSize()&&needed>0;slot++){
            var item=source.getItem(slot);if(!item.is(supply.plan.input()))continue;int n=Math.min(needed,item.getCount());
            while(n>0){var taken=item.copy();taken.setCount(n);
                if(IndustrialWork.insert(s,taken,false)){
                    var removed=source.removeItem(slot,n);if(removed.getCount()!=n)throw new IllegalStateException("Nonstandard container removal violated transaction");
                    IndustrialWork.insert(s,removed,true);source.setChanged();needed-=n;break;
                }n--;
            }
        }
    }
    private static boolean fuelAvailable(EmploymentData.Station s,CommercialRecipes.Plan plan){
        if(EmploymentConfig.COOKING_FUEL.get()!=EmploymentConfig.CookingFuel.VANILLA_FUEL)return false;
        long heat=s.commerce.getLong("heat");
        for(int i=0;i<31&&heat<plan.heat();i++)if(i!=27&&i!=28&&i!=29)heat+=(long)ForgeHooks.getBurnTime(s.getItem(i),RecipeType.SMELTING)*s.getItem(i).getCount();
        return heat>=plan.heat();
    }
    private static int fuelSlot(Container s){
        if(ForgeHooks.getBurnTime(s.getItem(30),RecipeType.SMELTING)>0)return 30;
        for(int i=0;i<27;i++)if(ForgeHooks.getBurnTime(s.getItem(i),RecipeType.SMELTING)>0)return i;return -1;
    }
    private static boolean complete(ServerLevel l,EmploymentData.Station s,CommercialRecipes.Plan plan){
        if(count(s,plan.input())<plan.count())return false;
        var copy=new SimpleContainer(32);for(int i=0;i<32;i++)copy.setItem(i,s.getItem(i).copy());
        int needed=plan.count();if(copy.getItem(28).is(plan.input())){int n=Math.min(needed,copy.getItem(28).getCount());copy.removeItem(28,n);needed-=n;}
        for(int i=0;i<27&&needed>0;i++)if(copy.getItem(i).is(plan.input())){int n=Math.min(needed,copy.getItem(i).getCount());copy.removeItem(i,n);needed-=n;}
        if(needed!=0)return false;
        long heat=s.commerce.getLong("heat");var remainders=new ArrayList<ItemStack>();
        if(plan.heat()>0){
            if(!fuelAvailable(s,plan))return false;
            while(heat<plan.heat()){
                int slot=fuelSlot(copy);if(slot<0)return false;var fuel=copy.getItem(slot);heat+=ForgeHooks.getBurnTime(fuel,RecipeType.SMELTING);
                var remainder=fuel.getCraftingRemainingItem();if(!remainder.isEmpty())remainders.add(remainder);copy.removeItem(slot,1);
            }heat-=plan.heat();
        }
        var common=new SimpleContainer(27);for(int i=0;i<27;i++)common.setItem(i,copy.getItem(i));
        if(!common.addItem(plan.output()).isEmpty())return false;
        for(var remainder:remainders)if(!common.addItem(remainder).isEmpty())return false;
        for(int i=0;i<27;i++)copy.setItem(i,common.getItem(i));
        for(int i=0;i<32;i++)s.setItem(i,copy.getItem(i));
        s.commerce.putLong("heat",heat);s.setChanged();return true;
    }
    private static void evening(ServerLevel l,EmploymentData.Station s,Villager v){
        long day=Math.floorDiv(l.getDayTime(),24000)+1;
        if(s.commerce.getLong("deposited_day")!=day){
            var offers=DiscardChests.offers(s,stack->CommercialRecipes.product(s.profession,stack));
            var candidates=DiscardChests.destinations(l,s,IndustrialWork.area(l,s));
            if(candidates.isEmpty()&&DiscardChests.hasOffers(offers))
                IndustrialWork.alert(l,s,"discard","Falta baú de descarte válido a até cinco blocos do limite da área.");
            var tried=new HashSet<Long>();
            if(s.commerce.getLong("discard_tried_day")==day){
                for(long at:s.commerce.getLongArray("discard_tried"))tried.add(at);
            }else{
                s.commerce.putLong("discard_tried_day",day);
                s.commerce.remove("discard_tried");
            }
            for(var pos:candidates){
                if(!DiscardChests.hasOffers(offers))break;
                if(tried.contains(pos.asLong())||!DiscardChests.accepts(l,s,v,pos,offers))continue;
                int travel=br.com.kraftcity.industry.FarmerLogistics.approachDiscard(l,v,pos);
                if(travel==0){state(s,"Levando produtos ao descarte.");return;}
                if(travel>0)DiscardChests.deposit(l,s,v,pos,offers,"commercial_deposit");
                tried.add(pos.asLong());
                s.commerce.putLongArray("discard_tried",tried.stream().mapToLong(Long::longValue).toArray());
            }
            s.commerce.remove("discard_tried");
            s.commerce.putLong("deposited_day",day);s.setChanged();
        }

    }
    public static void cancel(EmploymentData.Station s){s.commerce.remove("recipe");s.commerce.putInt("elapsed",0);s.setChanged();}
    public static void clearSamples(){SAMPLES.clear();}
    private CommercialWork(){}
}
