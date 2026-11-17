package br.com.kraftcity.employment;

import net.minecraftforge.common.ForgeConfigSpec;

public final class EmploymentConfig {
    public static final int INDUSTRIAL_COST=10;
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue COMMERCIAL_ENABLED;
    public static final ForgeConfigSpec.IntValue COMMERCIAL_COST;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Preço comercial definitivo PENDENTE. Só habilite após decisão explícita do administrador.").push("contratacao");
        COMMERCIAL_ENABLED=b.define("habilitarContratacaoComercial",false);
        COMMERCIAL_COST=b.comment("-1 = preço não definido, cobrança bloqueada. Não é contratação gratuita.")
                .defineInRange("custoComercialEsmeraldas",-1,-1,64);
        b.pop(); SPEC=b.build();
    }
    public static int cost(Profession.Sector sector) {
        return sector==Profession.Sector.INDUSTRIAL?INDUSTRIAL_COST:
                COMMERCIAL_ENABLED.get()&&COMMERCIAL_COST.get()>=0?COMMERCIAL_COST.get():-1;
    }
    private EmploymentConfig() {}
}
