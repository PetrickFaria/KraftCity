package br.com.kraftcity.employment;

import br.com.kraftcity.workforce.WorkforceData;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;

public final class LevelPaymentMenu extends AbstractContainerMenu {
    public static final int COST = 10;
    public final Profession profession;
    public final int current;
    public final String resident;
    private final UUID stationId;
    private final UUID worker;
    private final long epoch;
    private final SimpleContainer funds;
    private boolean resolved;

    public LevelPaymentMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, data.readUUID(), data.readUUID(), data.readEnum(Profession.class),
                data.readVarInt(), data.readLong(), data.readUtf(256));
    }

    private LevelPaymentMenu(int id, Inventory inventory, UUID stationId, UUID worker, Profession profession,
                             int current, long epoch, String resident) {
        super(WorkModule.LEVEL_PAYMENT.get(), id);
        this.stationId=stationId;
        this.worker=worker;
        this.profession=profession;
        this.current=current;
        this.epoch=epoch;
        this.resident=resident;
        funds=new SimpleContainer(current==0?5:1);
        for(int i=0;i<funds.getContainerSize();i++){
            final int index=i;
            addSlot(new Slot(funds,i,current==0?20+i*30:80,49){
                @Override public boolean mayPlace(ItemStack stack){
                    return !resolved&&stack.is(Items.EMERALD)&&(index==0||funds.getItem(index-1).getCount()==COST);
                }
                @Override public int getMaxStackSize(){return COST;}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)
            addSlot(new Slot(inventory,9+row*9+col,8+col*18,115+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,173));
    }

    public int completeSlots(){
        int complete=0;
        while(complete<funds.getContainerSize()&&funds.getItem(complete).is(Items.EMERALD)
                &&funds.getItem(complete).getCount()==COST)complete++;
        return complete;
    }

    public int deposited(){
        int total=0;
        for(int i=0;i<funds.getContainerSize();i++)total+=funds.getItem(i).getCount();
        return total;
    }

    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||resolved)return ItemStack.EMPTY;
        var slot=slots.get(index);
        var stack=slot.getItem();
        if(stack.isEmpty())return ItemStack.EMPTY;
        var original=stack.copy();
        int size=funds.getContainerSize();
        boolean moved=false;
        if(index<size)moved=moveItemStackTo(stack,size,slots.size(),true);
        else if(stack.is(Items.EMERALD)){
            for(int i=0;i<size&&!stack.isEmpty();i++){
                var target=slots.get(i);
                if(!target.mayPlace(stack))break;
                int amount=Math.min(COST-target.getItem().getCount(),stack.getCount());
                if(amount<=0)continue;
                if(target.hasItem())target.getItem().grow(amount);
                else target.set(new ItemStack(Items.EMERALD,amount));
                target.setChanged();
                stack.shrink(amount);
                moved=true;
            }
        }
        if(!moved)return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    private EmploymentData.Station station(Player player){
        return player instanceof ServerPlayer p?EmploymentData.get(p.getLevel()).station(stationId):null;
    }

    @Override public boolean stillValid(Player player){
        if(player.level.isClientSide)return true;
        return player instanceof ServerPlayer p&&valid(p,station(p));
    }

    private boolean valid(ServerPlayer p,EmploymentData.Station post){
        var level=p.getLevel();
        if(post==null||!post.at(level,post.pos)||!level.hasChunkAt(post.pos)
                ||!(level.getBlockEntity(post.pos) instanceof WorkBlockEntity block)||!post.id.equals(block.identity())
                ||!p.mayBuild()||p.isSpectator()||!p.isAlive()||!level.mayInteract(p,post.pos)
                ||Employment.epoch(post)!=epoch)return false;
        boolean atPost=p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(post.pos))<64;
        var entity=level.getEntity(worker);
        if(current==0)return atPost&&post.worker==null&&profession.sector==post.sector
                &&(post.profession==null||post.profession==profession)&&EmploymentConfig.cost(post.sector)>=0
                &&entity instanceof net.minecraft.world.entity.npc.Villager villager&&Employment.eligible(level,villager);
        return worker.equals(post.worker)&&profession==post.profession&&Employment.valid(level,worker)
                &&(atPost||entity!=null&&p.distanceToSqr(entity)<64)&&level(post,p)==current&&current<5;
    }

    static int level(EmploymentData.Station post,ServerPlayer player){
        return post.profession==Profession.SOLDIER?br.com.kraftcity.military.Military.level(player.getLevel(),post.worker)
                :br.com.kraftcity.industry.IndustrialRules.level(player.getLevel(),post);
    }

    @Override public boolean clickMenuButton(Player player,int action){
        if(!(player instanceof ServerPlayer p)||resolved||p.containerMenu!=this)return false;
        var post=station(p);
        if(action==1){p.closeContainer();return true;}
        if(action!=0)return false;
        if(!valid(p,post)){p.closeContainer();return false;}
        int paid=completeSlots();
        if(paid==0)return false;
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(post.pos),
                net.minecraft.core.Direction.UP,post.pos,false);
        var permission=net.minecraftforge.common.ForgeHooks.onRightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,post.pos,hit);
        if(permission.isCanceled()||permission.getUseBlock()==net.minecraftforge.eventbus.api.Event.Result.DENY||!valid(p,post)){
            p.closeContainer();
            return false;
        }
        resolved=true;
        if(current==0){
            String result=Employment.hire(p,post,worker,profession,paid);
            if(!result.equals("ok")){p.closeContainer();return false;}
        }else{
            var data=WorkforceData.get(p.getLevel());
            var progress=data.progress(worker,profession.id());
            data.setProgress(worker,profession.id(),current+1,progress.xp());
            if(profession==Profession.SOLDIER&&p.getLevel().getEntity(worker) instanceof net.minecraft.world.entity.LivingEntity living)
                br.com.kraftcity.military.Military.stats(p.getLevel(),living);
        }
        for(int i=0;i<paid;i++)funds.setItem(i,ItemStack.EMPTY);
        p.closeContainer();
        WorkBlock.open(p,post,p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(post.pos))<64?null:worker);
        return true;
    }

    @Override public void removed(Player player){
        if(player instanceof ServerPlayer serverPlayer){
            var remainder=getCarried();
            setCarried(ItemStack.EMPTY);
            refund(serverPlayer,remainder);
            for(int i=0;i<funds.getContainerSize();i++)refund(serverPlayer,funds.removeItemNoUpdate(i));
        }
        super.removed(player);
    }

    private static void refund(ServerPlayer player,ItemStack stack){
        if(stack.isEmpty())return;
        if(player.isAlive())player.getInventory().add(stack);
        if(!stack.isEmpty()){
            var item=player.drop(stack,false);
            if(item!=null)item.setOwner(player.getUUID());
        }
    }

    static void open(ServerPlayer player,EmploymentData.Station post,UUID worker,Profession profession,int current){
        if(post==null||profession==null||worker==null||current>=5)return;
        var name=br.com.residenciasaldeoes.data.ResidenceData.get(player.getLevel()).residentName(worker);
        if(name.length()>128)name=name.substring(0,128);
        String label=name;
        long epoch=Employment.epoch(post);
        var probe=new LevelPaymentMenu(-1,player.getInventory(),post.id,worker,profession,current,epoch,label);
        if(!probe.valid(player,post))return;
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p)->
                new LevelPaymentMenu(id,inventory,post.id,worker,profession,current,epoch,label),Component.literal(
                current==0?"Confirmar Contratação":"Aumentar Nível")),buffer->{
            buffer.writeUUID(post.id);buffer.writeUUID(worker);buffer.writeEnum(profession);
            buffer.writeVarInt(current);buffer.writeLong(epoch);buffer.writeUtf(label,256);
        });
    }
}
