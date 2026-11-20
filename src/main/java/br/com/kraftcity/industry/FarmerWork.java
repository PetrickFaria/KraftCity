package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.EmploymentData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmerWork {
    public static final int HEIGHT=3;
    private static final int HARVEST_UPDATE=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE;
    private FarmerWork(){}

    public static List<BlockPos> zigzag(BlockPos post,int size){
        var path=new ArrayList<BlockPos>(size*size);
        for(int row=0;row<size;row++){
            for(int column=0;column<size;column++){
                int x=(row%2==0?column:size-1-column)-size/2;
                path.add(post.offset(x,1,row-size/2));
            }
        }
        return path;
    }

    static boolean inside(EmploymentData.Station s,int size,BlockPos pos){
        int height=pos.getY()-s.pos.getY();
        return height>=1&&height<=HEIGHT&&Math.abs(pos.getX()-s.pos.getX())<=size/2&&Math.abs(pos.getZ()-s.pos.getZ())<=size/2;
    }
    static BlockState plant(ItemStack seed){
        if(seed.is(Items.SUGAR_CANE))return Blocks.SUGAR_CANE.defaultBlockState();
        Block block=seed.getItem() instanceof BlockItem item?item.getBlock():Blocks.AIR;
        return block instanceof CropBlock||block instanceof StemBlock||block==Blocks.SUGAR_CANE?block.defaultBlockState():null;
    }
    private static Item seed(ServerLevel level,BlockPos pos,BlockState state){
        var stack=state.getBlock().getCloneItemStack(level,pos,state);
        var planted=plant(stack);
        return planted!=null&&planted.getBlock()==state.getBlock()?stack.getItem():Items.AIR;
    }

    static void tick(ServerLevel l,EmploymentData.Station s,Villager v){
        var tool=s.getItem(27);
        if(!(tool.getItem() instanceof HoeItem)){
            IndustrialWork.alert(l,s,"tool","Falta enxada/arador utilizável.");
            FarmerLogistics.stayInField(l,s,v);
            return;
        }
        int size=IndustrialRules.size(l,s);
        if(s.industry.getInt("zigzag_size")!=size||s.industry.contains("zigzag_corner")){
            s.industry.putInt("zigzag_size",size);
            s.industry.remove("zigzag_corner");
            s.industry.putInt("zigzag_harvest",0);
            s.industry.putInt("zigzag_plant",0);
            s.setChanged();
        }
        var path=zigzag(s.pos,size);
        if(plantCane(l,s,v,size,path,tool))return;
        if(harvest(l,s,v,size,path,tool))return;
        if(clearFruitSpace(l,s,v,size,path))return;
        if(sow(l,s,v,size,path,tool))return;
        if(s.getItem(28).is(Items.BONE_MEAL)){
            for(var base:path)for(int y=0;y<HEIGHT;y++){
                var pos=base.above(y);
                if(!l.hasChunkAt(pos))continue;
                var state=l.getBlockState(pos);
                if(!(state.getBlock() instanceof CropBlock crop)||crop.isMaxAge(state)||seed(l,pos,state)==Items.AIR)continue;
                int travel=FarmerLogistics.approachCell(l,v,pos,false);
                if(travel<0)continue;
                if(travel>0)harvestCrop(l,s,v,pos,state,crop,tool);
                return;
            }
        }
        FarmerLogistics.stayInField(l,s,v);
    }

    private static boolean harvest(ServerLevel l,EmploymentData.Station s,Villager v,int size,List<BlockPos> path,ItemStack tool){
        int total=path.size()*HEIGHT;
        int cursor=s.industry.getInt("zigzag_harvest");
        for(int scan=0;scan<total;scan++){
            int index=Math.floorMod(cursor+scan,total);
            var pos=path.get(index/HEIGHT).above(index%HEIGHT);
            if(!l.hasChunkAt(pos))continue;
            var state=l.getBlockState(pos);
            boolean crop=state.getBlock() instanceof CropBlock c&&c.isMaxAge(state)&&seed(l,pos,state)!=Items.AIR;
            boolean cane=state.is(Blocks.SUGAR_CANE)&&ripeCane(l,s,size,pos);
            boolean fruit=stemForFruit(l,s,size,pos,state);
            if(!crop&&!cane&&!fruit)continue;
            int travel=FarmerLogistics.approachCell(l,v,cane?pos.below():pos,cane||fruit);
            if(travel<0)continue;
            s.industry.putInt("zigzag_harvest",travel==0?index:(index+1)%total);
            s.setChanged();
            if(travel>0){
                if(crop)harvestCrop(l,s,v,pos,state,(CropBlock)state.getBlock(),tool);
                else harvestSpecial(l,s,v,pos,state,cane,tool);
            }
            return true;
        }
        return false;
    }

    private static boolean ripeCane(ServerLevel level,EmploymentData.Station station,int size,BlockPos middle){
        var root=middle.below();
        return inside(station,size,root)&&inside(station,size,middle.above())
            &&level.getBlockState(root).is(Blocks.SUGAR_CANE)&&!level.getBlockState(root.below()).is(Blocks.SUGAR_CANE)
            &&level.getBlockState(root).canSurvive(level,root)
            &&level.getBlockState(middle.above()).is(Blocks.SUGAR_CANE)&&!level.getBlockState(middle.above(2)).is(Blocks.SUGAR_CANE);
    }

    private static boolean same(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos,BlockState state,ItemStack tool){
        return s.getItem(27)==tool&&v.getUUID().equals(s.worker)&&Employment.valid(l,v.getUUID())
            &&l.hasChunkAt(pos)&&l.getBlockState(pos)==state;
    }
    private static void harvestCrop(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos,BlockState state,CropBlock crop,ItemStack tool){
        if(!crop.isMaxAge(state)){
            var meal=s.getItem(28);
            if(IndustrialWork.allowed(l,s,v,pos,null,"bonemeal")&&same(l,s,v,pos,state,tool)
                &&s.getItem(28)==meal&&meal.is(Items.BONE_MEAL)&&crop.isValidBonemealTarget(l,pos,state,false)){
                if(crop.isBonemealSuccess(l,l.random,pos,state))crop.performBonemeal(l,l.random,pos,state);
                s.removeItem(28,1);
            }
            return;
        }
        if(!IndustrialWork.breakAllowed(l,s,v,pos)||!same(l,s,v,pos,state,tool))return;
        var replanted=crop.getStateForAge(0);
        if(!replanted.canSurvive(l,pos)||!IndustrialWork.placeAllowed(l,s,v,pos,replanted)||!same(l,s,v,pos,state,tool)
            ||!replanted.canSurvive(l,pos))return;
        var drops=new ArrayList<>(Block.getDrops(state,l,pos,l.getBlockEntity(pos),v,tool));
        Item seed=seed(l,pos,state);
        var naturalSeed=drops.stream().filter(x->x.is(seed)&&!x.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        boolean fromDrop=!naturalSeed.isEmpty();
        if(fromDrop)naturalSeed.shrink(1);
        int input=fromDrop?-1:IndustrialWork.find(s,x->x.is(seed),0,27);
        Item produce=state.is(Blocks.WHEAT)?Items.WHEAT:state.is(Blocks.BEETROOTS)?Items.BEETROOT:
            state.is(Blocks.CARROTS)||state.is(Blocks.POTATOES)?seed:Items.AIR;
        drops.removeIf(x->x.isEmpty()||produce!=Items.AIR&&x.is(produce));
        if(produce!=Items.AIR)drops.add(new ItemStack(produce,1+l.random.nextInt(IndustrialTools.tier(tool)+1)));
        boolean replant=fromDrop||input>=0;
        if(!IndustrialWork.exchange(s,input,1,drops,false)){
            IndustrialWork.alert(l,s,"space","Inventário cheio; colheita preservada no terreno.");return;
        }
        if(!l.setBlock(pos,replant?replanted:Blocks.AIR.defaultBlockState(),HARVEST_UPDATE))return;
        if(input>=0)s.removeItem(input,1);
        for(var stack:drops)dropHarvest(l,pos,stack,false);
        FarmerLogistics.rescan(v);
        if(!replant){
            var pending=s.industry.getCompound("farmer_replant");
            pending.putString(Long.toString(pos.asLong()),Registry.BLOCK.getKey(crop).toString());
            s.industry.put("farmer_replant",pending);
            IndustrialWork.alert(l,s,"seed","Falta semente para replantar a mesma cultura.");
        }
        traceHarvest(v,pos,state,l.getBlockState(pos));
        v.swing(InteractionHand.MAIN_HAND,true);
        IndustrialRules.wear(l,s,tool);
        IndustrialRules.progress(l,s);
    }

    private static boolean stemForFruit(ServerLevel l,EmploymentData.Station s,int size,BlockPos pos,BlockState fruit){
        var stem=fruit.is(Blocks.MELON)?Blocks.ATTACHED_MELON_STEM:fruit.is(Blocks.PUMPKIN)?Blocks.ATTACHED_PUMPKIN_STEM:null;
        if(stem==null)return false;
        for(var direction:Direction.Plane.HORIZONTAL){
            var at=pos.relative(direction);
            if(!inside(s,size,at)||!l.hasChunkAt(at))continue;
            var state=l.getBlockState(at);
            if(state.is(stem)&&state.getValue(AttachedStemBlock.FACING)==direction.getOpposite())return true;
        }
        return false;
    }
    private static void harvestSpecial(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos,BlockState state,boolean cane,ItemStack tool){
        var upper=l.getBlockState(pos.above());
        if(!IndustrialWork.breakAllowed(l,s,v,pos)||cane&&!IndustrialWork.breakAllowed(l,s,v,pos.above())
            ||!same(l,s,v,pos,state,tool)||cane&&(l.getBlockState(pos.above())!=upper||!ripeCane(l,s,IndustrialRules.size(l,s),pos))
            ||!cane&&!stemForFruit(l,s,IndustrialRules.size(l,s),pos,state))return;
        var drops=cane?List.of(new ItemStack(Items.SUGAR_CANE,2)):Block.getDrops(state,l,pos,null,v,tool);
        if(!IndustrialWork.outputs(s,drops,false)){
            IndustrialWork.alert(l,s,"space","Inventário cheio; colheita preservada no terreno.");return;
        }
        if(cane&&!l.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),HARVEST_UPDATE))return;
        if(!l.setBlock(pos,Blocks.AIR.defaultBlockState(),HARVEST_UPDATE)){
            if(cane)l.setBlock(pos.above(),upper,HARVEST_UPDATE);
            return;
        }
        if(cane){
            traceHarvest(v,pos.above(),upper,l.getBlockState(pos.above()));
            if(l.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)){
                dropHarvest(l,pos,new ItemStack(Items.SUGAR_CANE),true);
                dropHarvest(l,pos.above(),new ItemStack(Items.SUGAR_CANE),true);
            }
            FarmerLogistics.rescan(v);
        }else{
            var attached=state.is(Blocks.MELON)?Blocks.ATTACHED_MELON_STEM:Blocks.ATTACHED_PUMPKIN_STEM;
            var mature=(state.is(Blocks.MELON)?Blocks.MELON_STEM:Blocks.PUMPKIN_STEM).defaultBlockState().setValue(StemBlock.AGE,7);
            for(var side:Direction.Plane.HORIZONTAL){
                var stem=pos.relative(side);
                if(!l.hasChunkAt(stem)||!inside(s,IndustrialRules.size(l,s),stem))continue;
                var before=l.getBlockState(stem);
                if(before.is(attached)&&before.getValue(AttachedStemBlock.FACING)==side.getOpposite()){
                    l.setBlock(stem,mature,HARVEST_UPDATE);
                    traceHarvest(v,stem,before,mature);
                }
            }
            for(var stack:drops)dropHarvest(l,pos,stack,false);
            FarmerLogistics.rescan(v);
        }
        traceHarvest(v,pos,state,l.getBlockState(pos));
        v.swing(InteractionHand.MAIN_HAND,true);
        IndustrialRules.wear(l,s,tool);
        IndustrialRules.progress(l,s);
    }

    private static void dropHarvest(ServerLevel level,BlockPos pos,ItemStack stack,boolean raised){
        if(stack.isEmpty())return;
        var drop=new ItemEntity(level,pos.getX()+.5,pos.getY()+(raised?.4:.2),pos.getZ()+.5,stack);
        drop.setDeltaMovement(0,raised?.075:0,0);
        if(raised)drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    private static BlockPos fruitSpace(ServerLevel l,EmploymentData.Station s,int size,BlockPos stem){
        BlockPos farmland=null;
        for(var direction:Direction.Plane.HORIZONTAL){
            var pos=stem.relative(direction);
            if(!inside(s,size,pos)||!l.hasChunkAt(pos)||!l.getBlockState(pos).isAir())continue;
            var soil=l.getBlockState(pos.below());
            if(soil.is(BlockTags.DIRT))return pos;
            if(farmland==null&&soil.is(Blocks.FARMLAND))farmland=pos;
        }
        return farmland;
    }
    private static boolean clearFruitSpace(ServerLevel l,EmploymentData.Station s,Villager v,int size,List<BlockPos> path){
        for(var base:path)for(int y=0;y<HEIGHT;y++){
            var stem=base.above(y);
            if(!l.hasChunkAt(stem))continue;
            var state=l.getBlockState(stem);
            if(!(state.getBlock() instanceof StemBlock)||state.getValue(StemBlock.AGE)!=7)continue;
            BlockPos candidate=null;
            boolean ready=false;
            for(var direction:Direction.Plane.HORIZONTAL){
                var pos=stem.relative(direction);
                if(!inside(s,size,pos)||!l.hasChunkAt(pos))continue;
                var soil=l.getBlockState(pos.below());
                var obstacle=l.getBlockState(pos);
                if(obstacle.isAir()&&soil.is(BlockTags.DIRT)){
                    ready=true;
                    break;
                }
                if(!soil.is(Blocks.FARMLAND))continue;
                if(obstacle.isAir()||candidate==null&&(obstacle.is(Blocks.GRASS)||obstacle.is(Blocks.FERN)||obstacle.is(Blocks.DEAD_BUSH))){
                    if(candidate==null||!l.getBlockState(candidate).isAir())candidate=pos;
                }
            }
            if(ready)continue;
            if(candidate!=null){
                int travel=FarmerLogistics.approachCell(l,v,candidate,false);
                if(travel==0)return true;
                if(travel>0&&v.isOnGround()&&IndustrialWork.allowed(l,s,v,candidate.below(),null,"farmer_fruit_space")){
                    var obstacle=l.getBlockState(candidate);
                    if(!obstacle.isAir()){
                        if(!IndustrialWork.breakAllowed(l,s,v,candidate)||l.getBlockState(candidate)!=obstacle
                            ||!l.destroyBlock(candidate,true,v))continue;
                    }
                    if(l.getBlockState(stem)==state&&l.getBlockState(candidate).isAir()
                        &&l.getBlockState(candidate.below()).is(Blocks.FARMLAND)
                        &&FarmerLogistics.jumpOn(v,candidate.below(),stem))return true;
                }
            }
            IndustrialWork.alert(l,s,"fruit_space","Caule maduro sem espaço lateral livre; cultivos vizinhos preservados.");
        }
        return false;
    }

    private static boolean plantCane(ServerLevel l,EmploymentData.Station s,Villager v,int size,List<BlockPos> path,ItemStack tool){
        int slot=IndustrialWork.find(s,stack->stack.is(Items.SUGAR_CANE),0,27);
        var cane=Blocks.SUGAR_CANE.defaultBlockState();
        if(slot>=0)for(var base:path)for(int y=0;y<HEIGHT;y++){
            var pos=base.above(y);
            if(!l.hasChunkAt(pos)||!l.getBlockState(pos).isAir()
                ||l.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)||!cane.canSurvive(l,pos))continue;
            if(!hasWater(l,s,size,pos))continue;
            int travel=FarmerLogistics.approachCell(l,v,pos,false);
            if(travel<0)continue;
            if(travel>0&&sowAt(l,s,v,pos,slot,tool)){
                s.industry.putBoolean("farmer_cane_planted",true);
                s.setChanged();
            }
            return true;
        }
        if(s.industry.getBoolean("farmer_cane_planted")){
            s.industry.remove("farmer_cane_planted");
            s.industry.putInt("zigzag_plant",0);
            s.industry.putInt("zigzag_harvest",0);
            s.setChanged();
        }
        return false;
    }

    private static boolean hasWater(ServerLevel l,EmploymentData.Station s,int size,BlockPos crop){
        for(var side:Direction.Plane.HORIZONTAL){
            var adjacent=crop.relative(side);
            var water=adjacent.below();
            if(inside(s,size,adjacent)&&l.hasChunkAt(water)&&l.getBlockState(water).is(Blocks.WATER))return true;
        }
        return false;
    }

    private static void traceHarvest(Villager v,BlockPos pos,BlockState before,BlockState after){
        com.mojang.logging.LogUtils.getLogger().debug(
            "Farmer {} at {} harvest changed {}: {} -> {}",v.getUUID(),v.position(),pos,before,after);
    }

    private static boolean reservedFruitSpace(ServerLevel l,EmploymentData.Station s,int size,BlockPos pos){
        for(var direction:Direction.Plane.HORIZONTAL){
            var stem=pos.relative(direction);
            if(inside(s,size,stem)&&l.hasChunkAt(stem)&&l.getBlockState(stem).getBlock() instanceof StemBlock
                &&pos.equals(fruitSpace(l,s,size,stem)))return true;
        }
        return false;
    }
    private static boolean sow(ServerLevel l,EmploymentData.Station s,Villager v,int size,List<BlockPos> path,ItemStack tool){
        if(IndustrialWork.find(s,x->plant(x)!=null,0,27)<0)return false;
        var pending=s.industry.getCompound("farmer_replant");
        int total=path.size()*HEIGHT;
        int cursor=s.industry.getInt("zigzag_plant");
        // An empty plot with a saved species is maintained before opening a new one.
        for(boolean repair:new boolean[]{true,false}){
            if(repair&&pending.isEmpty())continue;
            for(int scan=0;scan<total;scan++){
                int index=Math.floorMod(cursor+scan,total);
                var pos=path.get(index/HEIGHT).above(index%HEIGHT);
                var key=Long.toString(pos.asLong());
                if(pending.contains(key)!=repair||!l.hasChunkAt(pos))continue;
                var state=l.getBlockState(pos);
                if(!state.isAir()){
                    if(repair){pending.remove(key);s.setChanged();}
                    continue;
                }
                if(reservedFruitSpace(l,s,size,pos))continue;
                int slot=sowingSlot(l,s,size,pos);
                if(slot<0)continue;
                int travel=FarmerLogistics.approachCell(l,v,pos,false);
                if(travel<0)continue;
                s.industry.putInt("zigzag_plant",travel==0?index:(index+1)%total);
                s.setChanged();
                if(travel>0)sowAt(l,s,v,pos,slot,tool);
                return true;
            }
        }
        return false;
    }
    private static int sowingSlot(ServerLevel l,EmploymentData.Station s,int size,BlockPos pos){
        var key=Long.toString(pos.asLong());
        var pending=s.industry.getCompound("farmer_replant").getString(key);
        var soil=l.getBlockState(pos.below());
        if(!soil.is(Blocks.FARMLAND)&&!tillable(soil))return -1;
        int slot=-1,best=0;
        for(int i=0;i<27;i++){
            var candidate=plant(s.getItem(i));
            if(candidate==null||!pending.isEmpty()&&!Registry.BLOCK.getKey(candidate.getBlock()).toString().equals(pending))continue;
            if(candidate.is(Blocks.SUGAR_CANE))continue;
            boolean stem=candidate.getBlock() instanceof StemBlock;
            int border=Math.max(Math.abs(pos.getX()-s.pos.getX()),Math.abs(pos.getZ()-s.pos.getZ()));
            if(stem&&(border!=size/2||fruitSpace(l,s,size,pos)==null))continue;
            int priority=stem?2:1;
            if(priority>best){slot=i;best=priority;}
        }
        return slot;
    }

    private static boolean tillable(BlockState soil){
        return soil.is(Blocks.DIRT)||soil.is(Blocks.GRASS_BLOCK)||soil.is(Blocks.DIRT_PATH);
    }
    private static boolean sowAt(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos,int slot,ItemStack tool){
        var seed=s.getItem(slot);
        var crop=plant(seed);
        var ground=pos.below();
        var soil=l.getBlockState(ground);
        if(crop==null||!IndustrialWork.placeAllowed(l,s,v,pos,crop)
            ||s.getItem(slot)!=seed||!same(l,s,v,pos,Blocks.AIR.defaultBlockState(),tool))return false;
        boolean till=!crop.is(Blocks.SUGAR_CANE)&&tillable(soil);
        if(till){
            var farmland=Blocks.FARMLAND.defaultBlockState();
            if(!IndustrialWork.breakAllowed(l,s,v,ground)||!IndustrialWork.placeAllowed(l,s,v,ground,farmland)
                ||s.getItem(slot)!=seed||l.getBlockState(ground)!=soil||!same(l,s,v,pos,Blocks.AIR.defaultBlockState(),tool)
                ||!l.setBlock(ground,farmland,3))return false;
        }
        if(crop.canSurvive(l,pos)&&l.setBlock(pos,crop,3)){
            s.removeItem(slot,1);
            v.swing(InteractionHand.MAIN_HAND,true);
            s.industry.getCompound("farmer_replant").remove(Long.toString(pos.asLong()));
            s.setChanged();
            if(till)IndustrialRules.wear(l,s,tool);
            return true;
        }else if(till){
            l.setBlock(ground,soil,3);
        }
        return false;
    }
}
