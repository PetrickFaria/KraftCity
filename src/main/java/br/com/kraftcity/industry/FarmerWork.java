package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.EmploymentData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.GameRules;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FarmerWork {
    public static final int HEIGHT=3;
    private FarmerWork(){}

    public static List<BlockPos> zigzag(BlockPos post,int size,int corner){
        var path=new ArrayList<BlockPos>(size*size);
        for(int row=0;row<size;row++){
            for(int column=0;column<size;column++){
                int x=(row%2==0?column:size-1-column)-size/2;
                path.add(rotated(post,x,row-size/2,corner));
            }
        }
        return path;
    }
    private static BlockPos rotated(BlockPos post,int x,int z,int corner){
        return switch(corner){
            case 1 -> post.offset(-z,1,x);
            case 2 -> post.offset(-x,1,-z);
            case 3 -> post.offset(z,1,-x);
            default -> post.offset(x,1,z);
        };
    }
    public static int farthestCorner(BlockPos post,int size,Vec3 worker){
        int best=0;
        double distance=-1;
        for(int corner=0;corner<4;corner++){
            double next=worker.distanceToSqr(Vec3.atCenterOf(rotated(post,-size/2,-size/2,corner)));
            if(next>distance){best=corner;distance=next;}
        }
        return best;
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
    private static Item seed(BlockState crop){
        if(crop.is(Blocks.WHEAT))return Items.WHEAT_SEEDS;
        if(crop.is(Blocks.BEETROOTS))return Items.BEETROOT_SEEDS;
        if(crop.is(Blocks.POTATOES))return Items.POTATO;
        if(crop.is(Blocks.CARROTS))return Items.CARROT;
        return Items.AIR;
    }

    static void tick(ServerLevel l,EmploymentData.Station s,Villager v){
        var tool=s.getItem(27);
        if(!(tool.getItem() instanceof HoeItem)){IndustrialWork.alert(l,s,"tool","Falta enxada/arador utilizável.");return;}
        int size=IndustrialRules.size(l,s);
        if(s.industry.getInt("zigzag_size")!=size){
            s.industry.putInt("zigzag_size",size);
            s.industry.putInt("zigzag_corner",farthestCorner(s.pos,size,v.position()));
            s.industry.putInt("zigzag_harvest",0);
            s.industry.putInt("zigzag_plant",0);
            s.setChanged();
        }
        var path=zigzag(s.pos,size,s.industry.getInt("zigzag_corner"));
        if(harvest(l,s,v,size,path,tool))return;
        if(clearFruitSpace(l,s,v,size,path))return;
        if(sow(l,s,v,size,path,tool,false)||sow(l,s,v,size,path,tool,true))return;
        if(s.getItem(28).is(Items.BONE_MEAL)){
            for(var base:path)for(int y=0;y<HEIGHT;y++){
                var pos=base.above(y);
                if(!l.hasChunkAt(pos))continue;
                var state=l.getBlockState(pos);
                if(!(state.getBlock() instanceof CropBlock crop)||crop.isMaxAge(state)||seed(state)==Items.AIR)continue;
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
            boolean crop=seed(state)!=Items.AIR&&state.getBlock() instanceof CropBlock c&&c.isMaxAge(state);
            boolean cane=state.is(Blocks.SUGAR_CANE)&&inside(s,size,pos.below())&&inside(s,size,pos.above())
                &&l.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)&&!l.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE)
                &&l.getBlockState(pos.above()).is(Blocks.SUGAR_CANE)&&!l.getBlockState(pos.above(2)).is(Blocks.SUGAR_CANE);
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
        if(!replanted.canSurvive(l,pos)||!IndustrialWork.placeAllowed(l,s,v,pos,replanted)||!same(l,s,v,pos,state,tool))return;
        var drops=new ArrayList<>(Block.getDrops(state,l,pos,l.getBlockEntity(pos),v,tool));
        Item seed=seed(state);
        var naturalSeed=drops.stream().filter(x->x.is(seed)&&!x.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        boolean fromDrop=!naturalSeed.isEmpty();
        if(fromDrop)naturalSeed.shrink(1);
        int input=fromDrop?-1:IndustrialWork.find(s,x->x.is(seed),0,27);
        Item produce=state.is(Blocks.WHEAT)?Items.WHEAT:state.is(Blocks.BEETROOTS)?Items.BEETROOT:seed;
        drops.removeIf(x->x.is(produce)||x.isEmpty());
        drops.add(new ItemStack(produce,1+l.random.nextInt(IndustrialTools.tier(tool)+1)));
        boolean replant=fromDrop||input>=0;
        if(!IndustrialWork.exchange(s,input,1,drops,false)){
            IndustrialWork.alert(l,s,"space","Inventário cheio; colheita preservada no terreno.");return;
        }
        if(!l.setBlock(pos,replant?replanted:Blocks.AIR.defaultBlockState(),replant?Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE:Block.UPDATE_ALL))return;
        IndustrialWork.exchange(s,input,1,drops,true);
        if(!replant){
            var pending=s.industry.getCompound("farmer_replant");
            pending.putString(Long.toString(pos.asLong()),Registry.BLOCK.getKey(crop).toString());
            s.industry.put("farmer_replant",pending);
            IndustrialWork.alert(l,s,"seed","Falta semente para replantar a mesma cultura.");
        }
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
            ||!same(l,s,v,pos,state,tool)||cane&&(l.getBlockState(pos.above())!=upper||!l.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)))return;
        var drops=cane?List.of(new ItemStack(Items.SUGAR_CANE,2)):Block.getDrops(state,l,pos,null,v,tool);
        if(!IndustrialWork.outputs(s,drops,false)){
            IndustrialWork.alert(l,s,"space","Inventário cheio; colheita preservada no terreno.");return;
        }
        if(cane&&!l.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2))return;
        if(!l.setBlock(pos,Blocks.AIR.defaultBlockState(),3)){
            if(cane)l.setBlock(pos.above(),upper,3);
            return;
        }
        if(cane){
            if(l.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)){
                dropCane(l,pos);
                dropCane(l,pos.above());
            }
            FarmerLogistics.rescan(v);
        }else IndustrialWork.outputs(s,drops,true);
        v.swing(InteractionHand.MAIN_HAND,true);
        IndustrialRules.wear(l,s,tool);
        IndustrialRules.progress(l,s);
    }

    private static void dropCane(ServerLevel l,BlockPos pos){
        var drop=new ItemEntity(l,pos.getX()+.5,pos.getY()+.4,pos.getZ()+.5,new ItemStack(Items.SUGAR_CANE));
        drop.setDeltaMovement(0,.075,0);
        drop.setDefaultPickUpDelay();
        l.addFreshEntity(drop);
    }

    private static boolean fruitSoil(ServerLevel l,BlockPos pos){
        var soil=l.getBlockState(pos.below());
        return soil.is(Blocks.FARMLAND)||soil.is(net.minecraft.tags.BlockTags.DIRT);
    }
    private static BlockPos fruitSpace(ServerLevel l,EmploymentData.Station s,int size,BlockPos stem){
        for(var direction:Direction.Plane.HORIZONTAL){
            var pos=stem.relative(direction);
            if(inside(s,size,pos)&&l.hasChunkAt(pos)&&l.getBlockState(pos).isAir()&&fruitSoil(l,pos))return pos;
        }
        return null;
    }
    private static boolean clearFruitSpace(ServerLevel l,EmploymentData.Station s,Villager v,int size,List<BlockPos> path){
        for(var base:path)for(int y=0;y<HEIGHT;y++){
            var stem=base.above(y);
            if(!l.hasChunkAt(stem))continue;
            var state=l.getBlockState(stem);
            if(!(state.getBlock() instanceof StemBlock)||state.getValue(StemBlock.AGE)!=7||fruitSpace(l,s,size,stem)!=null)continue;
            for(var direction:Direction.Plane.HORIZONTAL){
                var pos=stem.relative(direction);
                if(!inside(s,size,pos)||!l.hasChunkAt(pos)||!fruitSoil(l,pos))continue;
                var obstacle=l.getBlockState(pos);
                if(!obstacle.is(Blocks.GRASS)&&!obstacle.is(Blocks.FERN)&&!obstacle.is(Blocks.DEAD_BUSH))continue;
                int travel=FarmerLogistics.approachCell(l,v,pos,false);
                if(travel<0)continue;
                if(travel==0)return true;
                if(!IndustrialWork.breakAllowed(l,s,v,pos)||l.getBlockState(pos)!=obstacle||l.getBlockState(stem)!=state)continue;
                if(!l.destroyBlock(pos,true,v))continue;
                if(l.getBlockState(pos.below()).is(Blocks.FARMLAND)
                    &&IndustrialWork.allowed(l,s,v,pos.below(),null,"farmer_fruit_space")){
                    FarmerLogistics.jumpOn(v,pos.below());
                }
                return true;
            }
            IndustrialWork.alert(l,s,"fruit_space","Caule maduro sem espaço lateral livre; cultivos vizinhos preservados.");
        }
        return false;
    }
    private static boolean reservedFruitSpace(ServerLevel l,EmploymentData.Station s,int size,BlockPos pos){
        for(var direction:Direction.Plane.HORIZONTAL){
            var stem=pos.relative(direction);
            if(inside(s,size,stem)&&l.hasChunkAt(stem)&&l.getBlockState(stem).getBlock() instanceof StemBlock
                &&pos.equals(fruitSpace(l,s,size,stem)))return true;
        }
        return false;
    }
    private static boolean sow(ServerLevel l,EmploymentData.Station s,Villager v,int size,List<BlockPos> path,ItemStack tool,boolean till){
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
                int slot=sowingSlot(l,s,size,pos,till);
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
    private static int sowingSlot(ServerLevel l,EmploymentData.Station s,int size,BlockPos pos,boolean till){
        var key=Long.toString(pos.asLong());
        var pending=s.industry.getCompound("farmer_replant").getString(key);
        var soil=l.getBlockState(pos.below());
        int slot=-1,best=0;
        for(int i=0;i<27;i++){
            var candidate=plant(s.getItem(i));
            if(candidate==null||!pending.isEmpty()&&!Registry.BLOCK.getKey(candidate.getBlock()).toString().equals(pending))continue;
            int priority;
            if(candidate.is(Blocks.SUGAR_CANE)){
                if(till||soil.is(Blocks.SUGAR_CANE)||!candidate.canSurvive(l,pos))continue;
                priority=3;
            }else{
                if(till?!tillable(soil):!soil.is(Blocks.FARMLAND))continue;
                boolean stem=candidate.getBlock() instanceof StemBlock;
                if(stem&&fruitSpace(l,s,size,pos)==null)continue;
                priority=stem?2:1;
            }
            if(priority>best){slot=i;best=priority;}
        }
        return slot;
    }

    private static boolean tillable(BlockState soil){
        return soil.is(Blocks.DIRT)||soil.is(Blocks.GRASS_BLOCK)||soil.is(Blocks.DIRT_PATH);
    }
    private static void sowAt(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos,int slot,ItemStack tool){
        var seed=s.getItem(slot);
        var crop=plant(seed);
        var ground=pos.below();
        var soil=l.getBlockState(ground);
        if(crop==null||!IndustrialWork.placeAllowed(l,s,v,pos,crop)
            ||s.getItem(slot)!=seed||!same(l,s,v,pos,Blocks.AIR.defaultBlockState(),tool))return;
        boolean till=!crop.is(Blocks.SUGAR_CANE)&&tillable(soil);
        if(till){
            var farmland=Blocks.FARMLAND.defaultBlockState();
            if(!IndustrialWork.breakAllowed(l,s,v,ground)||!IndustrialWork.placeAllowed(l,s,v,ground,farmland)
                ||s.getItem(slot)!=seed||l.getBlockState(ground)!=soil||!same(l,s,v,pos,Blocks.AIR.defaultBlockState(),tool)
                ||!l.setBlock(ground,farmland,3))return;
        }
        if(crop.canSurvive(l,pos)&&l.setBlock(pos,crop,3)){
            s.removeItem(slot,1);
            v.swing(InteractionHand.MAIN_HAND,true);
            s.industry.getCompound("farmer_replant").remove(Long.toString(pos.asLong()));
            s.setChanged();
            if(till)IndustrialRules.wear(l,s,tool);
        }else if(till){
            l.setBlock(ground,soil,3);
        }
    }
}
