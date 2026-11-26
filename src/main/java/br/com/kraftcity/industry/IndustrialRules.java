package br.com.kraftcity.industry;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.workforce.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;

public final class IndustrialRules {
    public static final int FARMER_XP_PER_LEVEL=25;
    public static final int MAX_LEVEL=5;

    public static int farmerXpIntoLevel(int level,long xp){
        if(level>=MAX_LEVEL)return FARMER_XP_PER_LEVEL;
        long earned=xp-(long)(Math.max(1,level)-1)*FARMER_XP_PER_LEVEL;
        return (int)Math.min(FARMER_XP_PER_LEVEL,Math.max(0,earned));
    }

    public static int farmerXpBarWidth(int level,long xp,int width){
        return farmerXpIntoLevel(level,xp)*width/FARMER_XP_PER_LEVEL;
    }
    public static int level(ServerLevel l,EmploymentData.Station s){return s.worker==null||s.profession==null?1:Math.min(5,Math.max(1,WorkforceData.get(l).progress(s.worker,s.profession.id()).level()));}
    public static int maximumSize(int level){return 3+2*Math.min(5,Math.max(1,level));}
    public static int minimumSize(Profession profession){return profession==Profession.LUMBERJACK?15:profession==Profession.BEEKEEPER?7:5;}
    public static int maximumSize(Profession profession,int level){
        return profession==Profession.LUMBERJACK?lumberjackWidth(Math.min(5,Math.max(1,level))-1):maximumSize(level)+(minimumSize(profession)-5);
    }
    public static final int LUMBERJACK_HEIGHT=15;
    public static int lumberjackWidth(int option){return 15+10*option;}
    public static int lumberjackOption(int width){return (width-15)/10;}
    private static boolean validLumberjackWidth(int width,int max){return width>=15&&width<=max&&(width-15)%10==0;}
    public static void setLumberjackWidth(EmploymentData.Station s,int width){
        s.industry.putInt("lumberjack_width",width);
        s.industry.remove("size");
        s.setChanged();
    }
    public static int size(ServerLevel l,EmploymentData.Station s){
        int max=maximumSize(s.profession,level(l,s));
        int selected=s.industry.getInt("size");
        if(s.profession==Profession.LUMBERJACK){
            if(!s.industry.contains("lumberjack_width")){
                int option=selected>=5&&selected<=25&&selected%5==0?selected/5-1:0;
                setLumberjackWidth(s,lumberjackWidth(option));
            }
            int width=s.industry.getInt("lumberjack_width");
            return validLumberjackWidth(width,s.worker==null?lumberjackWidth(4):max)?width:lumberjackWidth(0);
        }
        if(s.profession==Profession.BEEKEEPER){
            // Preserve the old selected width where it still exists, even while the post is vacant.
            int migrated=validSize(selected,15)?Math.max(7,selected):7;
            if(migrated!=selected){s.industry.putInt("size",migrated);s.setChanged();}
            return migrated;
        }
        if(s.profession==Profession.FARMER){
            if(validSize(selected,13))return selected;
            int legacy=s.industry.getInt("zigzag_size");
            selected=validSize(legacy,13)?legacy:max;
            s.industry.putInt("size",selected);
            s.setChanged();
            return selected;
        }
        return level(l,s)==5&&validSize(selected,max)?selected:max;
    }
    private static boolean validSize(int size,int max){return size>=5&&size<=max&&size%2==1;}
    public static boolean selectSize(ServerLevel l,EmploymentData.Station s,int size){
        int max=maximumSize(s.profession,level(l,s));
        if(s.profession==Profession.LUMBERJACK){
            if(!validLumberjackWidth(size,max))return false;
            setLumberjackWidth(s,size);return true;
        }
        if(size<minimumSize(s.profession)||!validSize(size,max)||s.profession!=Profession.FARMER&&s.profession!=Profession.BEEKEEPER&&level(l,s)<5&&size!=max)return false;
        s.industry.putInt("size",size);s.industry.remove("cursor");s.setChanged();return true;
    }
    public static int capacity(int level){return 2*Math.min(5,Math.max(1,level));}
    /** A completed action is one unit; cumulative thresholds are 25/50/75/100. */
    public static void progress(ServerLevel l,EmploymentData.Station s){
        if(s.worker==null||s.profession==null)return;if(s.profession==Profession.FARMER)size(l,s);var data=WorkforceData.get(l);var old=data.progress(s.worker,s.profession.id());
        long xp=old.xp()+1;int step=s.profession==Profession.FARMER||s.profession==Profession.SHEPHERD||s.profession==Profession.BEEKEEPER||s.profession==Profession.LUMBERJACK?FARMER_XP_PER_LEVEL:EmploymentConfig.OTHER_LEVEL_ACTIONS.get();
        int level=Math.min(5,Math.max(old.level(),1+(int)Math.min(4,xp/step)));
        data.setProgress(s.worker,s.profession.id(),level,xp);s.setChanged();
    }
    /** Quarter durability units survive saves. Per-tool fractions travel with the tool item. */
    public static void wear(ServerLevel l,EmploymentData.Station s,ItemStack tool){
        if(!tool.isDamageableItem())return;
        int units=7-level(l,s); // Lv1..5 -> 6,5,4,3,2 quarter units.
        int amount=tool.getOrCreateTag().getInt("KraftCityWearQuarters")+units;
        tool.getOrCreateTag().putInt("KraftCityWearQuarters",amount%4);
        tool.hurtAndBreak(amount/4,(net.minecraft.world.entity.LivingEntity)l.getEntity(s.worker),v->v.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        s.setChanged();
    }
    /** '50% slower' means duration*1.5, '50% faster' means duration*0.5. */
    public static int fishingTicks(int base,int level){return Math.max(1,(base*(7-Math.min(5,Math.max(1,level)))+3)/4);}
    private IndustrialRules(){}
}
