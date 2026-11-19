package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.EmploymentData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FarmerWork {
    private FarmerWork(){}

    public static List<BlockPos> spiral(BlockPos post,int size,int corner){
        var path=new ArrayList<BlockPos>(size*size);
        for(int low=-size/2,high=size/2;low<=high;low++,high--){
            for(int x=low;x<=high;x++)path.add(rotated(post,x,low,corner));
            for(int z=low+1;z<=high;z++)path.add(rotated(post,high,z,corner));
            if(low==high)break;
            for(int x=high-1;x>=low;x--)path.add(rotated(post,x,high,corner));
            for(int z=high-1;z>low;z--)path.add(rotated(post,low,z,corner));
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
        return height>=1&&height<=5&&Math.abs(pos.getX()-s.pos.getX())<=size/2&&Math.abs(pos.getZ()-s.pos.getZ())<=size/2;
    }
    static BlockState plant(ItemStack seed){
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
        int size=IndustrialRules.size(l,s),total=size*size*5;
        int cursor=s.industry.getInt("cursor");
        for(int scan=0;scan<32;scan++){
            int index=Math.floorMod(cursor+scan,total),column=index/5;
            var pos=s.pos.offset(column%size-size/2,index%5+1,column/size-size/2);
            if(!l.hasChunkAt(pos))continue;
            var state=l.getBlockState(pos);
            boolean crop=seed(state)!=Items.AIR&&state.getBlock() instanceof CropBlock c
                &&(c.isMaxAge(state)||s.getItem(28).is(Items.BONE_MEAL));
            boolean cane=state.is(Blocks.SUGAR_CANE)&&inside(s,size,pos.below())&&inside(s,size,pos.above())
                &&l.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)&&!l.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE)
                &&l.getBlockState(pos.above()).is(Blocks.SUGAR_CANE)&&!l.getBlockState(pos.above(2)).is(Blocks.SUGAR_CANE);
            boolean fruit=stemForFruit(l,s,size,pos,state);
            if(!crop&&!cane&&!fruit)continue;
            int travel=FarmerLogistics.approach(l,s,v,pos);
            if(travel==0){cursor(s,index);return;}
            if(travel<0)continue;
            if(crop)harvestCrop(l,s,v,pos,state,(CropBlock)state.getBlock(),tool);
            else harvestSpecial(l,s,v,pos,state,cane,tool);
            cursor(s,(index+1)%total);
            return;
        }
        cursor(s,Math.floorMod(cursor+32,total));
        sow(l,s,v,size,tool);
    }

    private static void cursor(EmploymentData.Station s,int index){
        s.industry.putInt("cursor",index);s.setChanged();
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
        if(!l.setBlock(pos,replant?replanted:Blocks.AIR.defaultBlockState(),3))return;
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
        IndustrialWork.outputs(s,drops,true);
        v.swing(InteractionHand.MAIN_HAND,true);
        IndustrialRules.wear(l,s,tool);
        IndustrialRules.progress(l,s);
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
    private static boolean reservedFruitSpace(ServerLevel l,EmploymentData.Station s,int size,BlockPos pos){
        for(var direction:Direction.Plane.HORIZONTAL){
            var stem=pos.relative(direction);
            if(inside(s,size,stem)&&l.hasChunkAt(stem)&&l.getBlockState(stem).getBlock() instanceof StemBlock
                &&pos.equals(fruitSpace(l,s,size,stem)))return true;
        }
        return false;
    }
    private static void sow(ServerLevel l,EmploymentData.Station s,Villager v,int size,ItemStack tool){
        if(IndustrialWork.find(s,x->plant(x)!=null,0,27)<0)return;
        if(s.industry.getInt("spiral_size")!=size){
            s.industry.putInt("spiral_size",size);
            s.industry.putInt("spiral_corner",farthestCorner(s.pos,size,v.position()));
            s.industry.putInt("spiral_cursor",0);
            s.setChanged();
        }
        if(replantPending(l,s,v,size,tool))return;
        var path=spiral(s.pos,size,s.industry.getInt("spiral_corner"));
        int cursor=s.industry.getInt("spiral_cursor"),total=path.size()*5;
        for(int scan=0;scan<Math.min(total,32);scan++){
            int index=Math.floorMod(cursor+scan,total);
            var pos=path.get(index/5).above(index%5);
            if(!l.hasChunkAt(pos)||!l.getBlockState(pos).isAir()||reservedFruitSpace(l,s,size,pos))continue;
            int slot=sowingSlot(l,s,size,pos);
            if(slot<0)continue;
            int travel=FarmerLogistics.approach(l,s,v,pos);
            if(travel<0)continue;
            s.industry.putInt("spiral_cursor",travel==0?index:(index+1)%total);
            s.setChanged();
            if(travel>0)sowAt(l,s,v,pos,slot,tool);
            return;
        }
        s.industry.putInt("spiral_cursor",Math.floorMod(cursor+32,total));
        s.setChanged();
    }
    private static boolean replantPending(ServerLevel l,EmploymentData.Station s,Villager v,int size,ItemStack tool){
        var pending=s.industry.getCompound("farmer_replant");
        for(var key:new java.util.TreeSet<>(pending.getAllKeys())){
            var pos=BlockPos.of(Long.parseLong(key));
            if(!inside(s,size,pos)||!l.hasChunkAt(pos))continue;
            if(!l.getBlockState(pos).isAir()){
                pending.remove(key);
                s.setChanged();
                continue;
            }
            int slot=sowingSlot(l,s,size,pos);
            if(slot<0)continue;
            int travel=FarmerLogistics.approach(l,s,v,pos);
            if(travel<0)continue;
            if(travel>0)sowAt(l,s,v,pos,slot,tool);
            return true;
        }
        return false;
    }
    private static int sowingSlot(ServerLevel l,EmploymentData.Station s,int size,BlockPos pos){
        var key=Long.toString(pos.asLong());
        var pending=s.industry.getCompound("farmer_replant").getString(key);
        var soil=l.getBlockState(pos.below());
        int slot=-1,best=0;
        for(int i=0;i<27;i++){
            var candidate=plant(s.getItem(i));
            if(candidate==null||!pending.isEmpty()&&!Registry.BLOCK.getKey(candidate.getBlock()).toString().equals(pending))continue;
            int priority;
            if(candidate.is(Blocks.SUGAR_CANE)){
                if(soil.is(Blocks.SUGAR_CANE)||!candidate.canSurvive(l,pos))continue;
                priority=3;
            }else{
                if(!soil.is(Blocks.FARMLAND)&&!tillable(soil))continue;
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
