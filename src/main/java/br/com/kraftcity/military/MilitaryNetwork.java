package br.com.kraftcity.military;

import br.com.kraftcity.employment.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.ForgeRegistries;

public final class MilitaryNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("residenciasaldeoes","military"),()->"1","1"::equals,"1"::equals);
    public record Command(int menu,UUID station,int command,String value){
        void encode(FriendlyByteBuf b){b.writeInt(menu);b.writeUUID(station);b.writeInt(command);b.writeUtf(value,160);}
        static Command decode(FriendlyByteBuf b){return new Command(b.readInt(),b.readUUID(),b.readInt(),b.readUtf(160));}
        void handle(Supplier<NetworkEvent.Context> c){var p=c.get().getSender();if(p!=null&&p.containerMenu instanceof SoldierMenu m&&m.containerId==menu&&m.stationId.equals(station)&&m.stillValid(p))apply(p,m,command,value);c.get().setPacketHandled(true);}
    }
    public record Entry(String id,String name,int category,boolean selected){}
    public record State(int menu,UUID station,int mode,boolean night,float hp,float max,int level,long xp,boolean employed,boolean zombie,List<Entry> entries){
        void encode(FriendlyByteBuf b){b.writeInt(menu);b.writeUUID(station);b.writeInt(mode);b.writeBoolean(night);b.writeFloat(hp);b.writeFloat(max);b.writeInt(level);b.writeLong(xp);b.writeBoolean(employed);b.writeBoolean(zombie);b.writeVarInt(entries.size());for(var e:entries){b.writeUtf(e.id,160);b.writeUtf(e.name,160);b.writeInt(e.category);b.writeBoolean(e.selected);}}
        static State decode(FriendlyByteBuf b){int m=b.readInt();var id=b.readUUID();int mode=b.readInt();boolean night=b.readBoolean();float hp=b.readFloat(),max=b.readFloat();int lv=b.readInt();long xp=b.readLong();boolean employed=b.readBoolean(),zombie=b.readBoolean();int count=b.readVarInt();if(count<0||count>32768)throw new IllegalArgumentException("Military catalog too large");var entries=new ArrayList<Entry>();for(int i=0;i<count;i++)entries.add(new Entry(b.readUtf(160),b.readUtf(160),b.readInt(),b.readBoolean()));return new State(m,id,mode,night,hp,max,lv,xp,employed,zombie,List.copyOf(entries));}
        void handle(Supplier<NetworkEvent.Context> c){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->br.com.kraftcity.military.client.SoldierScreen.receive(this));c.get().setPacketHandled(true);}
    }
    private static final Set<String> NEUTRAL=Set.of("enderman","spider","cave_spider","zombified_piglin","piglin","wolf","bee","iron_golem","polar_bear","llama","trader_llama","panda","dolphin","goat");
    public static int category(EntityType<?> type,Entity probe){var key=ForgeRegistries.ENTITY_TYPES.getKey(type);return probe instanceof NeutralMob||key!=null&&NEUTRAL.contains(key.getPath())?1:type.getCategory()==MobCategory.MONSTER?2:0;}
    public static List<Entry> catalog(ServerPlayer p,EmploymentData.Station s){var entries=new ArrayList<Entry>();for(var type:ForgeRegistries.ENTITY_TYPES){var key=ForgeRegistries.ENTITY_TYPES.getKey(type);if(key==null||type==EntityType.PLAYER)continue;try{var probe=type.create(p.getLevel());if(probe instanceof Mob)entries.add(new Entry(key.toString(),type.getDescription().getString(),category(type,probe),Military.selected(s,type)));}catch(RuntimeException ignored){}}entries.sort(Comparator.comparing(Entry::id));return entries;}
    public static void apply(ServerPlayer p,SoldierMenu m,int command,String value){var s=m.station();if(s==null)return;
        switch(command){case 0->{try{var mode=Military.Mode.valueOf(value);s.military.putString("mode",mode.name());if(mode==Military.Mode.FOLLOW)s.military.putUUID("player",p.getUUID());s.military.remove("target");s.military.remove("orderedTarget");s.military.remove("orders");}catch(IllegalArgumentException ignored){}}
            case 1->{if(value.equals("DAY")||value.equals("NIGHT"))s.military.putString("shift",value);}
            case 2->{var key=ResourceLocation.tryParse(value);var type=key==null?null:ForgeRegistries.ENTITY_TYPES.getValue(key);if(type!=null&&type!=EntityType.PLAYER)Military.select(s,type,!Military.selected(s,type));}
            case 3->{}case 4->{if(s.worker==null){p.closeContainer();WorkBlock.open(p,s,null,true);return;}}
            default->{return;}}
        s.setChanged();send(p,m,command==2||command==3);
    }
    public static void send(ServerPlayer p,SoldierMenu m,boolean catalog){var s=m.station();if(s==null)return;var v=s.worker==null?null:p.getLevel().getEntity(s.worker);var progress=s.worker==null?new br.com.kraftcity.workforce.WorkforceData.Progress(1,0):br.com.kraftcity.workforce.WorkforceData.get(p.getLevel()).progress(s.worker,Profession.SOLDIER.id());var living=v instanceof LivingEntity x?x:null;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(m.containerId,s.id,Military.mode(s).ordinal(),Military.shift(s)==Military.Shift.NIGHT,living==null?0:living.getHealth(),living==null?0:living.getMaxHealth(),Military.level(progress.xp()),progress.xp(),s.worker!=null,s.military.getBoolean("zombie"),catalog?catalog(p,s):List.of()));}
    public static void register(){CHANNEL.messageBuilder(Command.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Command::encode).decoder(Command::decode).consumerMainThread(Command::handle).add();CHANNEL.messageBuilder(State.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(State::encode).decoder(State::decode).consumerMainThread(State::handle).add();}
}
