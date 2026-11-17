package br.com.kraftcity.commerce;

import br.com.kraftcity.employment.EmploymentConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** One-time approval migration, including old worlds explicitly carrying false/-1 defaults. */
public final class CommerceConfigMigration {
    public record Settings(boolean enabled,int cost,int rules){}
    public static Settings migrate(Settings previous){return previous.rules()>=1?previous:new Settings(true,10,1);}
    public static boolean apply(){
        var old=new Settings(EmploymentConfig.COMMERCIAL_ENABLED.get(),EmploymentConfig.COMMERCIAL_COST.get(),EmploymentConfig.COMMERCIAL_RULESET.get());
        var approved=migrate(old);if(approved==old)return false;
        EmploymentConfig.COMMERCIAL_ENABLED.set(approved.enabled());EmploymentConfig.COMMERCIAL_COST.set(approved.cost());EmploymentConfig.COMMERCIAL_RULESET.set(approved.rules());return true;
    }
    public static void loaded(ModConfigEvent.Loading e){
        if(e.getConfig().getSpec()==EmploymentConfig.SPEC&&apply())e.getConfig().save();
    }
    private CommerceConfigMigration(){}
}
