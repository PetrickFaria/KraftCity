package br.com.kraftcity.builder;

import br.com.kraftcity.employment.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.network.NetworkHooks;

public final class BuilderMenu extends AbstractContainerMenu {
    public final UUID stationId,via;public final BlockPos pos;public final Container stock,contract;private final Player player;
    public CompoundTag snapshot=new CompoundTag();public List<CompoundTag> library=List.of();public UUID selectedSchema,selectedJob;public boolean replace;private int lastLibrary=-1;
    public BuilderMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,b.readUUID(),b.readBlockPos(),b.readBoolean()?b.readUUID():null,null);}
    public BuilderMenu(int id,Inventory inv,EmploymentData.Station s,UUID via){this(id,inv,s.id,s.pos,via,s);}
    private BuilderMenu(int id,Inventory inv,UUID station,BlockPos pos,UUID via,EmploymentData.Station s){super(BuilderModule.MENU.get(),id);this.stationId=station;this.pos=pos;this.via=via;player=inv.player;stock=player.level instanceof ServerLevel l?BuilderData.get(l).stock:new SimpleContainer(32);contract=s==null?new SimpleContainer(32):s;
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(stock,row*9+col,258+18*col,26+18*row));
        addSlot(new Slot(stock,27,240,108){@Override public boolean mayPlace(ItemStack x){return x.is(Items.INK_SAC);}});
        addSlot(new Slot(stock,28,240,166){@Override public boolean mayPlace(ItemStack x){return x.is(Items.EMERALD);}});
        addSlot(new Slot(stock,29,204,108){@Override public boolean mayPlace(ItemStack x){return x.is(BuilderModule.BLANK.get());}});
        addSlot(new Slot(stock,30,396,108){@Override public boolean mayPlace(ItemStack x){return false;}});
        addSlot(new Slot(stock,31,204,166){@Override public boolean mayPlace(ItemStack x){return x.is(BuilderModule.PAPER.get())&&x.hasTag()&&x.getTag().getBoolean("Confirmed");}});
        addSlot(new Slot(contract,31,8,272){@Override public boolean mayPlace(ItemStack x){return x.is(Items.EMERALD);}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,258+18*col,214+18*row));for(int col=0;col<9;col++)addSlot(new Slot(inv,col,258+18*col,272));
    }
    public EmploymentData.Station station(){return player.level instanceof ServerLevel l?EmploymentData.get(l).station(stationId):null;}
    public static void open(ServerPlayer p,EmploymentData.Station s,UUID via){NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,x)->new BuilderMenu(id,inv,s,via),Component.literal("Construtores — estoque global")),b->{b.writeUUID(s.id);b.writeBlockPos(s.pos);b.writeBoolean(via!=null);if(via!=null)b.writeUUID(via);});}
    @Override public boolean stillValid(Player p){if(p.level.isClientSide)return true;if(!(p.level instanceof ServerLevel l))return false;var s=station();if(s==null||!s.at(l,pos)||!BuilderWork.builder(s))return false;if(via!=null)return via.equals(s.worker)&&Employment.valid(l,via)&&l.getEntity(via)!=null&&l.getEntity(via).distanceToSqr(p)<64;return l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof WorkBlockEntity be&&s.id.equals(be.identity())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64;}
    public void sync(ServerPlayer p){var d=BuilderData.get(p.getLevel());boolean lib=lastLibrary!=d.library.size();lastLibrary=d.library.size();BuilderNetwork.send(p,this,lib);}
    @Override public void broadcastChanges(){if(player instanceof ServerPlayer p){if(!stillValid(p)){p.closeContainer();return;}if(p.tickCount%10==0)sync(p);}super.broadcastChanges();}
    @Override public void clicked(int slot,int button,ClickType type,Player p){if(stillValid(p))super.clicked(slot,button,type,p);}
    @Override public ItemStack quickMoveStack(Player p,int index){if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;var source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;var stack=source.getItem();var copy=stack.copy();if(index<33){if(!moveItemStackTo(stack,33,slots.size(),true))return ItemStack.EMPTY;}else{int target=stack.is(Items.INK_SAC)?27:stack.is(Items.EMERALD)?28:stack.is(BuilderModule.BLANK.get())?29:stack.is(BuilderModule.PAPER.get())&&stack.hasTag()&&stack.getTag().getBoolean("Confirmed")?31:-1;if(target>=0){if(!moveItemStackTo(stack,target,target+1,false))return ItemStack.EMPTY;}else if(!moveItemStackTo(stack,0,27,false))return ItemStack.EMPTY;}if(stack.isEmpty())source.set(ItemStack.EMPTY);else source.setChanged();source.onTake(p,stack);return copy;}
}
