package br.com.kraftcity.employment;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class WorkMenu extends AbstractContainerMenu {
    public static final int PAYMENT_LIMIT=15;
    public static final int FARM_WIDTH=306, FARM_HEIGHT=235, FARM_GRID_X=73, FARM_PLAYER_Y=157;
    public static final int FARM_AUX_Y=41, FARM_STOCK_Y=69;
    public static int farmAuxX(int slot){return 61+slot*84;}
    public final boolean farmer, fieldLayout;
    public boolean settingsPage;
    private final int ownSlots;
    public final BlockPos pos;
    public final UUID stationId;
    public final boolean hiring;
    public final Profession.Sector sector;
    public final Profession profession;
    private final UUID viaWorker;
    private final Player player;
    public final Container inventory;
    public WorkNetwork.Snapshot snapshot;
    private String status="";
    private int militaryPaymentLimit;
    private long lastSync=Long.MIN_VALUE;
    public WorkMenu(int id,Inventory inv,FriendlyByteBuf b){
        this(id,inv,b.readBlockPos(),b.readUUID(),b.readBoolean(),b.readEnum(Profession.Sector.class),b.readInt(),b.readBoolean()?b.readUUID():null,null);
    }
    public WorkMenu(int id,Inventory inv,EmploymentData.Station s,boolean hiring,UUID worker){
        this(id,inv,s.pos,s.id,hiring,s.sector,s.profession==null?-1:s.profession.ordinal(),worker,s);
    }
    private WorkMenu(int id,Inventory inv,BlockPos pos,UUID identity,boolean hiring,Profession.Sector sector,int profession,UUID worker,Container container){
        super(WorkModule.MENU.get(),id);this.pos=pos;this.stationId=identity;this.hiring=hiring;this.sector=sector;
        this.profession=profession>=0&&profession<Profession.values().length?Profession.values()[profession]:null;
        this.viaWorker=worker;this.player=inv.player;this.inventory=container==null?new SimpleContainer(EmploymentData.SLOTS):container;
        farmer=!hiring&&this.profession==Profession.FARMER;
        fieldLayout=farmer||!hiring&&(this.profession==Profession.BEEKEEPER||this.profession==Profession.LUMBERJACK);
        if(!hiring){
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)
                addSlot(pageSlot(inventory,row*9+col,(fieldLayout?FARM_GRID_X:94)+col*18,(fieldLayout?FARM_STOCK_Y:26)+row*18));
            if(fieldLayout){
                int[] inputs={EmploymentData.TOOL,EmploymentData.INPUT_A,farmer?EmploymentData.EQUIPMENT:EmploymentData.INPUT_B};
                for(int i=0;i<(this.profession==Profession.LUMBERJACK?2:inputs.length);i++){
                    int inputIndex=i;
                    addSlot(new Slot(inventory,inputs[i],farmAuxX(i),FARM_AUX_Y){
                        @Override public boolean isActive(){return !settingsPage;}
                        @Override public boolean mayPlace(ItemStack stack){
                            if(WorkMenu.this.profession==Profession.LUMBERJACK)return inputIndex==0?br.com.kraftcity.industry.LumberjackWork.cutTicks(stack)>0:br.com.kraftcity.industry.LumberjackWork.sapling(stack);
                            if(!farmer)return br.com.kraftcity.industry.ApiaryWork.validInput(index,stack);
                            return switch(index){
                                case 0->stack.getItem() instanceof HoeItem;
                                case 1->stack.is(Items.BONE_MEAL);
                                default->stack.is(Items.WATER_BUCKET);
                            };
                        }
                    });
                }
            }else for(int i=0;i<4;i++)addSlot(new Slot(inventory,EmploymentData.TOOL+i,94+i*36,101));
        }
        if(!fieldLayout)addSlot(new Slot(inventory,EmploymentData.PAYMENT,hiring?187:274,hiring?158:101){
            @Override public boolean mayPlace(ItemStack stack){
                return stack.is(Items.EMERALD)&&getItem().getCount()<getMaxStackSize();
            }
            @Override public int getMaxStackSize(){if(hiring&&sector==Profession.Sector.MILITARY)return militaryPaymentLimit;
                return hiring&&sector==Profession.Sector.INDUSTRIAL?PAYMENT_LIMIT:super.getMaxStackSize();}
        });
        ownSlots=slots.size();
        int playerX=fieldLayout?FARM_GRID_X:94;
        int playerY=fieldLayout?FARM_PLAYER_Y:198+(!hiring&&sector==Profession.Sector.COMMERCIAL?16:0);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)
            addSlot(pageSlot(inv,9+row*9+col,playerX+col*18,playerY+row*18));
        for(int col=0;col<9;col++)addSlot(pageSlot(inv,col,playerX+col*18,playerY+58));
    }
    private Slot pageSlot(Container container,int index,int x,int y){
        return new Slot(container,index,x,y){
            @Override public boolean isActive(){return !settingsPage;}
        };
    }
    public EmploymentData.Station station(){return player.level instanceof ServerLevel l?EmploymentData.get(l).station(stationId):null;}
    @Override public boolean stillValid(Player p){
        if(p.level.isClientSide)return true;
        if(!(p.level instanceof ServerLevel l))return false;var s=station();
        if(s==null||hiring&&s.worker!=null||!s.at(l,pos)||!(l.getBlockEntity(pos) instanceof WorkBlockEntity be)||!stationId.equals(be.identity()))return false;
        if(viaWorker!=null)return viaWorker.equals(s.worker)&&Employment.valid(l,viaWorker)&&l.getEntity(viaWorker)!=null&&l.getEntity(viaWorker).distanceToSqr(p)<64;
        return p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64;
    }
    public void setMilitaryPaymentLimit(int limit){
        if(hiring&&sector==Profession.Sector.MILITARY)militaryPaymentLimit=Math.max(0,limit);
    }
    public void selectMilitaryCandidate(ServerPlayer p,UUID worker){
        if(!hiring||sector!=Profession.Sector.MILITARY||!stillValid(p))return;
        boolean eligible=Employment.candidates(p.getLevel()).stream().anyMatch(v->v.getUUID().equals(worker));
        int price=eligible?Employment.cost(p.getLevel(),sector,worker):0;
        setMilitaryPaymentLimit(price);
        var station=station();
        var payment=station.getItem(EmploymentData.PAYMENT);
        int excess=payment.getCount()-price;
        if(eligible&&payment.is(Items.EMERALD)&&excess>0&&roomFor(p.getInventory(),payment,excess)){
            var refund=station.removeItem(EmploymentData.PAYMENT,excess);
            p.getInventory().add(refund);
            if(!refund.isEmpty()){
                var retained=station.getItem(EmploymentData.PAYMENT);
                if(retained.isEmpty())station.setItem(EmploymentData.PAYMENT,refund);
                else retained.grow(refund.getCount());
            }
            station.setChanged();
        }
    }
    public void hire(ServerPlayer p,UUID worker,int selected){
        if(!hiring||!stillValid(p)||selected<0||selected>=Profession.values().length)return;
        var job=Profession.values()[selected];
        var station=station();
        var payment=station.getItem(EmploymentData.PAYMENT);
        int price=Employment.cost(p.getLevel(),station.sector,worker);
        if((job==Profession.FARMER||job==Profession.BEEKEEPER||job==Profession.LUMBERJACK||job==Profession.SOLDIER)&&price>=0&&payment.is(Items.EMERALD)&&payment.getCount()>price
            &&!roomFor(p.getInventory(),payment,payment.getCount()-price)){
            status="Libere espaço para receber o troco.";
            sync(p);
            return;
        }
        String result=Employment.hire(p,station,worker,job);
        if(result.equals("ok")&&(job==Profession.FARMER||job==Profession.BEEKEEPER||job==Profession.LUMBERJACK||job==Profession.SOLDIER))returnBalance(p,station);
        status=switch(result){case "ok"->"Contratação concluída.";case "pending"->"Preço comercial pendente.";
            case "payment"->"Esmeraldas insuficientes.";case "ineligible"->"Morador indisponível.";default->"Seleção inválida.";};
        if(result.equals("ok")){var s=station();p.closeContainer();WorkBlock.open(p,s);}else sync(p);
    }
    private static boolean roomFor(Inventory inventory,ItemStack stack,int count){
        for(var existing:inventory.items){
            if(existing.isEmpty())count-=stack.getMaxStackSize();
            else if(ItemStack.isSameItemSameTags(existing,stack))
                count-=Math.max(0,stack.getMaxStackSize()-existing.getCount());
            if(count<=0)return true;
        }
        return false;
    }
    private static void returnBalance(ServerPlayer player,EmploymentData.Station station){
        var balance=station.getItem(EmploymentData.PAYMENT).copy();
        if(!balance.is(Items.EMERALD)||!roomFor(player.getInventory(),balance,balance.getCount()))return;
        player.getInventory().add(balance);
        station.setItem(EmploymentData.PAYMENT,balance);
    }
    public void recoverBalance(ServerPlayer p){
        if(!fieldLayout||!stillValid(p)||!p.mayBuild()||p.isSpectator()||!p.getLevel().mayInteract(p,pos))return;
        var station=station();
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false);
        var event=net.minecraftforge.common.ForgeHooks.onRightClickBlock(p,InteractionHand.MAIN_HAND,pos,hit);
        if(event.isCanceled()||event.getUseBlock()==net.minecraftforge.eventbus.api.Event.Result.DENY||!stillValid(p))return;
        returnBalance(p,station);
        sync(p);
    }

    public void sync(ServerPlayer p){WorkNetwork.send(p,this,status);}
    public void openHiring(ServerPlayer p){if(stillValid(p)&&station().worker==null){var s=station();p.closeContainer();WorkBlock.open(p,s,null,true);}}
    @Override public void broadcastChanges(){
        if(player instanceof ServerPlayer p){var s=station();if(!stillValid(p)||s==null||(hiring&&s.worker!=null)||(!hiring&&s.profession==null)){p.closeContainer();return;}
            long now=p.level.getGameTime();if(lastSync==Long.MIN_VALUE||now-lastSync>=10){lastSync=now;if(farmer)br.com.kraftcity.industry.IndustrialWork.restoreFarmerInput(s);sync(p);}}
        super.broadcastChanges();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player p){if(stillValid(p))super.clicked(slot,button,type,p);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack item=slot.getItem(),before=item.copy();int own=ownSlots;
        if(index<own){if(!moveItemStackTo(item,own,slots.size(),true))return ItemStack.EMPTY;}
        else if(!fieldLayout&&item.is(Items.EMERALD)){
            slots.get(own-1).safeInsert(item);
            if(item.getCount()==before.getCount())return ItemStack.EMPTY;
        }
        else if(!hiring){if(!moveItemStackTo(item,0,fieldLayout?own:31,false))return ItemStack.EMPTY;}
        else return ItemStack.EMPTY;
        if(item.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,item);return before;
    }
}
