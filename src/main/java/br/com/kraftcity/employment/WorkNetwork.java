package br.com.kraftcity.employment;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WorkNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("residenciasaldeoes","employment"),()->"5","5"::equals,"5"::equals);
    public record Candidate(UUID id,String name){}
    public record Hire(int menu,UUID station,UUID worker,int profession){
        void encode(FriendlyByteBuf b){b.writeVarInt(menu);b.writeUUID(station);b.writeUUID(worker);b.writeInt(profession);}
        static Hire decode(FriendlyByteBuf b){return new Hire(b.readVarInt(),b.readUUID(),b.readUUID(),b.readInt());}
        void handle(Supplier<NetworkEvent.Context> ctx){var p=ctx.get().getSender();
            if(p!=null&&p.containerMenu instanceof WorkMenu m&&m.containerId==menu&&m.stationId.equals(station)){
                if(profession==-2)m.openHiring(p);else m.hire(p,worker,profession);
            }
            ctx.get().setPacketHandled(true);
        }
    }
    public record Snapshot(int menu,UUID station,List<Candidate> candidates,int cost,String worker,boolean employed,int level,long xp,int size,boolean preview,int elapsed,int duration,long heat,String operationStatus,String status){
        void encode(FriendlyByteBuf b){b.writeVarInt(menu);b.writeUUID(station);b.writeVarInt(candidates.size());for(var c:candidates){b.writeUUID(c.id);b.writeUtf(c.name,128);}b.writeInt(cost);b.writeUtf(worker,128);b.writeBoolean(employed);b.writeInt(level);b.writeLong(xp);b.writeInt(size);b.writeBoolean(preview);b.writeInt(elapsed);b.writeInt(duration);b.writeLong(heat);b.writeUtf(operationStatus,256);b.writeUtf(status,256);}
        static Snapshot decode(FriendlyByteBuf b){int menu=b.readVarInt();UUID id=b.readUUID();int count=b.readVarInt();if(count<0||count>65536)throw new IllegalArgumentException("Candidate snapshot too large");var list=new ArrayList<Candidate>();for(int i=0;i<count;i++)list.add(new Candidate(b.readUUID(),b.readUtf(128)));return new Snapshot(menu,id,List.copyOf(list),b.readInt(),b.readUtf(128),b.readBoolean(),b.readInt(),b.readLong(),b.readInt(),b.readBoolean(),b.readInt(),b.readInt(),b.readLong(),b.readUtf(256),b.readUtf(256));}
        void handle(Supplier<NetworkEvent.Context> ctx){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->br.com.kraftcity.employment.client.WorkScreen.receive(this));ctx.get().setPacketHandled(true);}
    }
    public static void register(){
        CHANNEL.messageBuilder(Hire.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Hire::encode).decoder(Hire::decode).consumerMainThread(Hire::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::decode).consumerMainThread(Snapshot::handle).add();
    }
    public static void send(ServerPlayer p,WorkMenu menu,String status){
        var s=menu.station();if(s==null)return;var home=br.com.residenciasaldeoes.data.ResidenceData.get(p.getLevel());
        var candidates=menu.hiring?Employment.candidates(p.getLevel()).stream().map(v->new Candidate(v.getUUID(),home.residentName(v.getUUID()))).toList():List.<Candidate>of();
        var progress=s.worker!=null&&s.profession!=null?br.com.kraftcity.workforce.WorkforceData.get(p.getLevel()).progress(s.worker,s.profession.id()):new br.com.kraftcity.workforce.WorkforceData.Progress(0,0);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(menu.containerId,s.id,candidates,EmploymentConfig.cost(s.sector),s.worker==null?"Sem trabalhador":home.residentName(s.worker),s.worker!=null,s.worker!=null?br.com.kraftcity.industry.IndustrialRules.level(p.getLevel(),s):progress.level(),progress.xp(),br.com.kraftcity.industry.IndustrialRules.size(p.getLevel(),s),s.industry.getBoolean("preview"),s.commerce.getInt("elapsed"),s.worker!=null&&s.profession!=null?br.com.kraftcity.commerce.CommercialWork.duration(br.com.kraftcity.industry.IndustrialRules.level(p.getLevel(),s)):1200,s.commerce.getLong("heat"),s.commerce.getString("status"),status));
    }
    private WorkNetwork(){}
}
