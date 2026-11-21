package br.com.kraftcity.military;

import br.com.kraftcity.employment.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.network.NetworkHooks;

public final class SoldierMenu extends AbstractContainerMenu {
    public static final int STORAGE=9, GEAR_START=32, PLAYER_START=38;
    public boolean legacyStorage;
    public final UUID stationId,via;
    public final BlockPos pos;
    public final Container inventory,equipment;
    private final Player player;
    public MilitaryNetwork.State state;
    public List<MilitaryNetwork.Entry> catalog=List.of();
    public SoldierMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,b.readUUID(),b.readBlockPos(),b.readBoolean()?b.readUUID():null,null);}
    public SoldierMenu(int id,Inventory inv,EmploymentData.Station s,UUID via){this(id,inv,s.id,s.pos,via,s);}
    private SoldierMenu(int id,Inventory inv,UUID identity,BlockPos pos,UUID via,EmploymentData.Station s){
        super(MilitaryModule.MENU.get(),id);this.stationId=identity;this.pos=pos;this.via=via;this.player=inv.player;inventory=s==null?new SimpleContainer(32):s;equipment=s==null?new SimpleContainer(6):s.equipment;
        for(int i=0;i<27;i++){
            final boolean legacy=i>=STORAGE;
            addSlot(new Slot(inventory,i,94+18*(i%9),i<9?164:34+18*((i-9)/9)){
                @Override public boolean isActive(){return legacy==legacyStorage;}
                @Override public boolean mayPlace(ItemStack stack){return !legacy;}
            });
        }
        for(int i=27;i<31;i++)addSlot(new Slot(inventory,i,94+18*(i-27),70){
            @Override public boolean isActive(){return legacyStorage;}
            @Override public boolean mayPlace(ItemStack stack){return false;}
        });
        addSlot(new Slot(inventory,31,166,70){@Override public boolean isActive(){return legacyStorage;}@Override public boolean mayPlace(ItemStack x){return false;}});
        for(int i=0;i<6;i++){
            final int gearIndex=i;
            addSlot(new Slot(equipment,i,20+36*i,63){
                @Override public boolean isActive(){return !legacyStorage;}
                @Override public int getMaxStackSize(){return 1;}
                @Override public int getMaxStackSize(ItemStack stack){return 1;}
                @Override public boolean mayPlace(ItemStack x){return validGear(gearIndex,x);}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,94+18*col,201+18*row));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,94+18*col,259));
    }
    public static boolean validGear(int i,ItemStack stack){
        if(stack.isEmpty())return false;
        if(i==0)return stack.getItem() instanceof SwordItem||stack.getItem() instanceof BowItem||stack.getItem() instanceof CrossbowItem
            ||stack.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_SWEEP);
        if(i==1)return stack.canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK);
        return i>=2&&i<Military.GEAR.length&&(stack.getItem() instanceof ArmorItem armor&&armor.getSlot()==Military.GEAR[i]
            ||stack.getEquipmentSlot()==Military.GEAR[i]);
    }
    public EmploymentData.Station station(){return player.level instanceof ServerLevel l?EmploymentData.get(l).station(stationId):null;}
    public static void open(ServerPlayer p,EmploymentData.Station s,UUID via){NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,x)->new SoldierMenu(id,inv,s.id,s.pos,via,s),Component.literal("Soldado")),b->{b.writeUUID(s.id);b.writeBlockPos(s.pos);b.writeBoolean(via!=null);if(via!=null)b.writeUUID(via);});}
    @Override public boolean stillValid(Player p){if(p.level.isClientSide)return true;if(!(p.level instanceof ServerLevel l))return false;var s=station();if(s==null||!s.at(l,pos)||!Military.military(s))return false;if(via!=null)return via.equals(s.worker)&&Employment.valid(l,via)&&l.getEntity(via)!=null&&l.getEntity(via).distanceToSqr(p)<64;return l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof WorkBlockEntity be&&stationId.equals(be.identity())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64;}
    @Override public void broadcastChanges(){if(player instanceof ServerPlayer p){if(!stillValid(p)){p.closeContainer();return;}if(p.tickCount%10==0)MilitaryNetwork.send(p,this,false);}super.broadcastChanges();}
    @Override public void clicked(int index,int button,ClickType type,Player actor){
        if(!stillValid(actor))return;
        if(type==ClickType.PICKUP&&button==1&&getCarried().isEmpty()&&index>=PLAYER_START&&index<slots.size()){
            var source=getSlot(index);
            for(int gear=0;gear<6;gear++){
                if(!validGear(gear,source.getItem()))continue;
                var destination=getSlot(GEAR_START+gear);
                if(!destination.hasItem()&&source.mayPickup(actor)){
                    destination.set(source.remove(1));
                    syncEquipment();
                }
                return;
            }
        }
        super.clicked(index,button,type,actor);
        syncEquipment();
    }

    private void syncEquipment(){
        var station=station();
        if(station!=null&&station.worker!=null&&player.level instanceof ServerLevel level
            &&level.getEntity(station.worker) instanceof net.minecraft.world.entity.Mob mob)Military.bind(station,mob);
    }

    @Override public ItemStack quickMoveStack(Player actor,int index){
        if(!stillValid(actor)||index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=getSlot(index);
        if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();
        var copy=stack.copy();
        if(index<PLAYER_START){
            if(!moveItemStackTo(stack,PLAYER_START,slots.size(),true))return ItemStack.EMPTY;
        }else{
            int gear=-1;
            for(int i=0;i<6;i++)if(validGear(i,stack)){gear=i;break;}
            if(gear>=0){
                var destination=getSlot(GEAR_START+gear);
                if(destination.hasItem()||!destination.mayPlace(stack))return ItemStack.EMPTY;
                destination.set(stack.split(1));
            }else if(!moveItemStackTo(stack,0,STORAGE,false))return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(actor,stack);
        syncEquipment();
        return copy;
    }
}
