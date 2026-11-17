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
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,row*9+col,94+18*col,26+18*row));
        for(int i=27;i<31;i++)addSlot(new Slot(inventory,i,8+20*(i-27),52));
        addSlot(new Slot(inventory,31,274,101){@Override public boolean mayPlace(ItemStack x){return x.is(Items.EMERALD);}});
        for(int i=0;i<6;i++){final int index=i;addSlot(new Slot(equipment,i,20+36*i,101){@Override public int getMaxStackSize(){return 1;}@Override public boolean mayPlace(ItemStack x){return validGear(index,x);}});}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,94+18*col,214+18*row));for(int col=0;col<9;col++)addSlot(new Slot(inv,col,94+18*col,272));
    }
    public static boolean validGear(int i,ItemStack x){if(i==0)return x.getItem() instanceof SwordItem||x.getItem() instanceof BowItem||x.getItem() instanceof CrossbowItem;if(i==1)return x.getItem() instanceof ShieldItem;return x.getItem() instanceof ArmorItem a&&a.getSlot()==Military.GEAR[i];}
    public EmploymentData.Station station(){return player.level instanceof ServerLevel l?EmploymentData.get(l).station(stationId):null;}
    public static void open(ServerPlayer p,EmploymentData.Station s,UUID via){NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,x)->new SoldierMenu(id,inv,s.id,s.pos,via,s),Component.literal("Soldado")),b->{b.writeUUID(s.id);b.writeBlockPos(s.pos);b.writeBoolean(via!=null);if(via!=null)b.writeUUID(via);});}
    @Override public boolean stillValid(Player p){if(p.level.isClientSide)return true;if(!(p.level instanceof ServerLevel l))return false;var s=station();return s!=null&&s.at(l,pos)&&Military.military(s)&&l.getBlockEntity(pos) instanceof WorkBlockEntity be&&stationId.equals(be.identity())&&(via==null?p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64:via.equals(s.worker)&&l.getEntity(via)!=null&&l.getEntity(via).distanceToSqr(p)<64);}
    @Override public void broadcastChanges(){if(player instanceof ServerPlayer p){if(!stillValid(p)){p.closeContainer();return;}if(p.tickCount%10==0)MilitaryNetwork.send(p,this,false);}super.broadcastChanges();}
    @Override public void clicked(int slot,int button,ClickType type,Player p){if(stillValid(p)){super.clicked(slot,button,type,p);var s=station();if(s!=null&&((ServerLevel)p.level).getEntity(s.worker==null?new UUID(0,0):s.worker) instanceof net.minecraft.world.entity.Mob mob)Military.bind(s,mob);}}
    @Override public ItemStack quickMoveStack(Player p,int index){if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<38){if(!moveItemStackTo(stack,38,slots.size(),true))return ItemStack.EMPTY;}else if(stack.is(Items.EMERALD)){if(!moveItemStackTo(stack,31,32,false))return ItemStack.EMPTY;}else{boolean moved=false;for(int i=0;i<6;i++)if(validGear(i,stack)&&moveItemStackTo(stack,32+i,33+i,false)){moved=true;break;}if(!moved&&!moveItemStackTo(stack,0,31,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
    }
}
