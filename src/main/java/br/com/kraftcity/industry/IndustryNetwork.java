package br.com.kraftcity.industry;

import br.com.kraftcity.employment.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;

public final class IndustryNetwork {
    public record Options(int menu,UUID station,int size,int command){
        public void encode(FriendlyByteBuf b){b.writeInt(menu);b.writeUUID(station);b.writeInt(size);b.writeInt(command);}
        public static Options decode(FriendlyByteBuf b){return new Options(b.readInt(),b.readUUID(),b.readInt(),b.readInt());}
        public void handle(Supplier<NetworkEvent.Context> c){var p=c.get().getSender();
            if(p!=null&&p.containerMenu instanceof WorkMenu m&&m.containerId==menu&&m.stationId.equals(station)&&!m.hiring&&m.stillValid(p)){
                var s=m.station();if(s!=null&&s.profession!=null){
                    if(command==0&&IndustrialRules.selectSize(p.getLevel(),s,size)){
                        if(FarmerLogistics.fieldWorker(s)&&s.worker!=null
                            &&p.getLevel().getEntity(s.worker) instanceof net.minecraft.world.entity.npc.Villager worker)FarmerLogistics.areaChanged(worker);
                        if(s.profession==Profession.LUMBERJACK&&s.worker!=null
                            &&p.getLevel().getEntity(s.worker) instanceof net.minecraft.world.entity.npc.Villager worker)LumberjackWork.pause(worker);
                        sendBounds(p);
                    }
                    else if(command==1){s.industry.putBoolean("preview",!s.industry.getBoolean("preview"));s.setChanged();sendBounds(p);}
                    else if(command==4&&s.sector==Profession.Sector.COMMERCIAL){br.com.kraftcity.commerce.CommercialWork.cancel(s);}
                    else if(command==3&&s.profession==Profession.BUTCHER){p.getPersistentData().putUUID("KCRegisterAnimal",station);p.closeContainer();p.sendSystemMessage(Component.literal("Clique nos animais que você colocou na área. Agache e clique no posto para encerrar."));}
                    else if(command==5&&(m.fieldLayout||s.profession==Profession.BAKER)){
                        br.com.kraftcity.logistics.DiscardBinding.begin(p,s);
                        if(p.containerMenu!=m){c.get().setPacketHandled(true);return;}
                    }
                    else if(command==6&&(m.fieldLayout||s.profession==Profession.BAKER))br.com.kraftcity.logistics.DiscardBinding.unlink(p,s);
                    m.sync(p);
                }
            }c.get().setPacketHandled(true);
        }
    }
    public record Bound(ResourceLocation dimension,BlockPos pos,int size,boolean farmer,boolean apiary,boolean lumberjack){}
    public record Bounds(List<Bound> entries){
        public void encode(FriendlyByteBuf b){b.writeVarInt(entries.size());for(var e:entries){b.writeResourceLocation(e.dimension);b.writeBlockPos(e.pos);b.writeVarInt(e.size);b.writeBoolean(e.farmer);b.writeBoolean(e.apiary);b.writeBoolean(e.lumberjack);}}
        public static Bounds decode(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>4096)throw new IllegalArgumentException("bounds");var out=new ArrayList<Bound>();for(int i=0;i<n;i++)out.add(new Bound(b.readResourceLocation(),b.readBlockPos(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean()));return new Bounds(List.copyOf(out));}
        public void handle(Supplier<NetworkEvent.Context> c){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->br.com.kraftcity.industry.client.WorkPreview.set(entries));c.get().setPacketHandled(true);}
    }
    public static void register(){
        WorkNetwork.CHANNEL.messageBuilder(Options.class,2,NetworkDirection.PLAY_TO_SERVER).encoder(Options::encode).decoder(Options::decode).consumerMainThread(Options::handle).add();
        WorkNetwork.CHANNEL.messageBuilder(Bounds.class,3,NetworkDirection.PLAY_TO_CLIENT).encoder(Bounds::encode).decoder(Bounds::decode).consumerMainThread(Bounds::handle).add();
    }
    public static void sendBounds(ServerPlayer p){
        var list=new ArrayList<Bound>();for(var s:EmploymentData.get(p.getLevel()).stations())if(s.active&&s.profession!=null&&s.dimension.equals(p.level.dimension())&&s.industry.getBoolean("preview")&&p.distanceToSqr(s.pos.getX(),s.pos.getY(),s.pos.getZ())<65536&&list.size()<4096)
            list.add(new Bound(s.dimension.location(),s.pos,IndustrialRules.size(p.getLevel(),s),s.profession==Profession.FARMER,s.profession==Profession.BEEKEEPER,s.profession==Profession.LUMBERJACK));
        WorkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Bounds(List.copyOf(list)));
    }
    private IndustryNetwork(){}
}
