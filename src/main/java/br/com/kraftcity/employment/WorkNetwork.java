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
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("residenciasaldeoes","employment"),()->"16","16"::equals,"16"::equals);
    public record Candidate(UUID id,String name,int cost){}
    public record Hire(int menu,UUID station,UUID worker,int profession){
        void encode(FriendlyByteBuf b){b.writeVarInt(menu);b.writeUUID(station);b.writeUUID(worker);b.writeInt(profession);}
        static Hire decode(FriendlyByteBuf b){return new Hire(b.readVarInt(),b.readUUID(),b.readUUID(),b.readInt());}
        void handle(Supplier<NetworkEvent.Context> ctx){var p=ctx.get().getSender();
            if(p!=null&&p.containerMenu instanceof WorkMenu m&&m.containerId==menu&&m.stationId.equals(station)){
                if(profession==-2)m.openHiring(p);else if(profession==-3)m.recoverBalance(p);else if(profession==-4)m.selectMilitaryCandidate(p,worker);else m.hire(p,worker,profession);
            }
            ctx.get().setPacketHandled(true);
        }
    }
    public record Dismiss(int menu,UUID station,long epoch){
        void encode(FriendlyByteBuf b){b.writeVarInt(menu);b.writeUUID(station);b.writeLong(epoch);}
        static Dismiss decode(FriendlyByteBuf b){return new Dismiss(b.readVarInt(),b.readUUID(),b.readLong());}
        void handle(Supplier<NetworkEvent.Context> ctx){
            var player=ctx.get().getSender();
            if(player!=null&&player.containerMenu.containerId==menu&&player.containerMenu.stillValid(player)){
                var current=player.containerMenu;
                EmploymentData.Station target=null;
                if(current instanceof WorkMenu work&&!work.hiring&&work.stationId.equals(station))target=work.station();
                if(current instanceof br.com.kraftcity.builder.BuilderMenu builder&&builder.stationId.equals(station))target=builder.station();
                if(current instanceof br.com.kraftcity.military.SoldierMenu soldier&&soldier.stationId.equals(station))target=soldier.station();
                if(target!=null&&Employment.dismiss(player,target,epoch)){
                    player.closeContainer();
                    if(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target.pos))<64)WorkBlock.open(player,target);
                }
            }
            ctx.get().setPacketHandled(true);
        }
    }
    public record Snapshot(int menu,UUID station,List<Candidate> candidates,int cost,String worker,boolean employed,long epoch,int level,long xp,int size,boolean preview,boolean paymentBalance,int elapsed,int duration,long heat,String operationStatus,String status,boolean discardLinked){
        void encode(FriendlyByteBuf b){b.writeVarInt(menu);b.writeUUID(station);b.writeVarInt(candidates.size());for(var c:candidates){b.writeUUID(c.id);b.writeUtf(c.name,128);b.writeInt(c.cost);}b.writeInt(cost);b.writeUtf(worker,128);b.writeBoolean(employed);b.writeLong(epoch);b.writeInt(level);b.writeLong(xp);b.writeInt(size);b.writeBoolean(preview);b.writeBoolean(paymentBalance);b.writeInt(elapsed);b.writeInt(duration);b.writeLong(heat);b.writeUtf(operationStatus,256);b.writeUtf(status,256);b.writeBoolean(discardLinked);}
        static Snapshot decode(FriendlyByteBuf b){int menu=b.readVarInt();UUID id=b.readUUID();int count=b.readVarInt();if(count<0||count>65536)throw new IllegalArgumentException("Candidate snapshot too large");var list=new ArrayList<Candidate>();for(int i=0;i<count;i++)list.add(new Candidate(b.readUUID(),b.readUtf(128),b.readInt()));return new Snapshot(menu,id,List.copyOf(list),b.readInt(),b.readUtf(128),b.readBoolean(),b.readLong(),b.readInt(),b.readLong(),b.readInt(),b.readBoolean(),b.readBoolean(),b.readInt(),b.readInt(),b.readLong(),b.readUtf(256),b.readUtf(256),b.readBoolean());}
        void handle(Supplier<NetworkEvent.Context> ctx){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->br.com.kraftcity.employment.client.WorkScreen.receive(this));ctx.get().setPacketHandled(true);}
    }
    public record Activity(int entity,UUID worker,Profession profession,boolean working,net.minecraft.world.item.ItemStack stowed){
        void encode(FriendlyByteBuf b){b.writeVarInt(entity);b.writeUUID(worker);b.writeEnum(profession);b.writeBoolean(working);b.writeItem(stowed);}
        static Activity decode(FriendlyByteBuf b){return new Activity(b.readVarInt(),b.readUUID(),b.readEnum(Profession.class),b.readBoolean(),b.readItem());}
        void handle(Supplier<NetworkEvent.Context> c){
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->br.com.kraftcity.employment.client.WorkerPose.receive(entity,worker,profession,working,stowed));
            c.get().setPacketHandled(true);
        }
    }
    public static void register(){
        CHANNEL.messageBuilder(Activity.class,5,NetworkDirection.PLAY_TO_CLIENT).encoder(Activity::encode).decoder(Activity::decode).consumerMainThread(Activity::handle).add();
        CHANNEL.messageBuilder(Dismiss.class,4,NetworkDirection.PLAY_TO_SERVER).encoder(Dismiss::encode).decoder(Dismiss::decode).consumerMainThread(Dismiss::handle).add();
        CHANNEL.messageBuilder(Hire.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Hire::encode).decoder(Hire::decode).consumerMainThread(Hire::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::decode).consumerMainThread(Snapshot::handle).add();
    }
    public static void send(ServerPlayer p,WorkMenu menu,String status){
        var s=menu.station();if(s==null)return;var home=br.com.residenciasaldeoes.data.ResidenceData.get(p.getLevel());
        var candidates=menu.hiring?Employment.candidates(p.getLevel()).stream().map(v->new Candidate(v.getUUID(),home.residentName(v.getUUID()),Employment.cost(p.getLevel(),s.sector,v.getUUID()))).toList():List.<Candidate>of();
        var progress=s.worker!=null&&s.profession!=null?br.com.kraftcity.workforce.WorkforceData.get(p.getLevel()).progress(s.worker,s.profession.id()):new br.com.kraftcity.workforce.WorkforceData.Progress(0,0);
        String operationStatus=s.commerce.getString("status");
        if(s.profession==Profession.BAKER && s.worker!=null && p.getLevel().getEntity(s.worker) instanceof net.minecraft.world.entity.npc.Villager baker){
            int limit=br.com.kraftcity.commerce.BakerWork.wheatLimit(br.com.kraftcity.commerce.CommercialWork.level(p.getLevel(),s));
            int collected=br.com.kraftcity.commerce.BakerWork.collected(p.getLevel(),baker);
            operationStatus="Trigo hoje: "+collected+"/"+limit+" (restam "+Math.max(0,limit-collected)+") · "+operationStatus;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(menu.containerId,s.id,candidates,EmploymentConfig.cost(s.sector),s.worker==null?"Sem trabalhador":menu.fieldLayout?workerName(p,s,home):home.residentName(s.worker),s.worker!=null,Employment.epoch(s),s.worker!=null?br.com.kraftcity.industry.IndustrialRules.level(p.getLevel(),s):progress.level(),progress.xp(),br.com.kraftcity.industry.IndustrialRules.size(p.getLevel(),s),s.industry.getBoolean("preview"),menu.fieldLayout&&s.getItem(EmploymentData.PAYMENT).is(net.minecraft.world.item.Items.EMERALD),s.commerce.getInt("elapsed"),s.worker!=null&&s.profession!=null?br.com.kraftcity.commerce.CommercialWork.duration(br.com.kraftcity.industry.IndustrialRules.level(p.getLevel(),s)):1200,s.commerce.getLong("heat"),operationStatus,status,(menu.fieldLayout||s.profession==Profession.BAKER)&&br.com.kraftcity.logistics.DiscardBinding.linked(p.getLevel(),s)));
    }
    private static String workerName(ServerPlayer player,EmploymentData.Station station,br.com.residenciasaldeoes.data.ResidenceData home){
        var entity=player.getLevel().getEntity(station.worker);
        var custom=entity==null?null:entity.getCustomName();
        String name=custom==null?home.residentName(station.worker):custom.getString();
        return name.length()<=128?name:name.substring(0,Character.isHighSurrogate(name.charAt(127))?127:128);
    }
    private WorkNetwork(){}
}
