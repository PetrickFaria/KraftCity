package br.com.kraftcity.commerce;

import br.com.kraftcity.employment.EmploymentConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** One-time approval migration, including old worlds explicitly carrying false/-1 defaults. */
public final class CommerceConfigMigration {
    public static boolean apply(){
        if(EmploymentConfig.COMMERCIAL_RULESET.get()>=1)return false;
        EmploymentConfig.COMMERCIAL_ENABLED.set(true);EmploymentConfig.COMMERCIAL_COST.set(10);
        EmploymentConfig.COMMERCIAL_RULESET.set(1);return true;
    }
    public static void loaded(ModConfigEvent.Loading e){
        if(e.getConfig().getSpec()==EmploymentConfig.SPEC&&apply())e.getConfig().save();
    }
    private CommerceConfigMigration(){}
}
