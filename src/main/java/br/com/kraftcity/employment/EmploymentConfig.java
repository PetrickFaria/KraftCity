package br.com.kraftcity.employment;

import net.minecraftforge.common.ForgeConfigSpec;

public final class EmploymentConfig {
    public static final int INDUSTRIAL_COST=10;
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue COMMERCIAL_ENABLED;
    public static final ForgeConfigSpec.IntValue COMMERCIAL_COST, ACTION_TICKS, FISH_TICKS, BREED_TICKS, OTHER_LEVEL_ACTIONS, CARE_TICKS;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("Preço comercial definitivo PENDENTE. Só habilite após decisão explícita do administrador.").push("contratacao");
        COMMERCIAL_ENABLED=b.define("habilitarContratacaoComercial",false);
        COMMERCIAL_COST=b.comment("-1 = preço não definido, cobrança bloqueada. Não é contratação gratuita.")
                .defineInRange("custoComercialEsmeraldas",-1,-1,64);
        b.pop();b.push("industria");
        ACTION_TICKS=b.comment("Intervalo operacional provisorio, nao altera tempos vanilla de crescimento.").defineInRange("intervaloAcoesTicks",20,1,1200);
        FISH_TICKS=b.comment("Tempo-base configuravel Lv3; Lv1..5 = 1.5/1.25/1/0.75/0.5 vezes este tempo.").defineInRange("pescaBaseTicks",600,20,24000);
        BREED_TICKS=b.comment("Intervalo minimo para tentativas de reproducao; cooldown dos animais tambem respeitado.").defineInRange("reproducaoTicks",6000,20,24000);
        OTHER_LEVEL_ACTIONS=b.comment("Provisorio: acoes por nivel para Acougueiro/Pescador/Criador/Apicultor; Fazendeiro/Pastoreiro usam 25 fixos.").defineInRange("acoesPorNivelOutrasProfissoes",25,1,10000);
        CARE_TICKS=b.comment("Provisorio: cuidado/treino de lobo logico com um osso, sem ganho passivo de XP.").defineInRange("cuidadoLobosTicks",1200,20,24000);
        b.pop(); SPEC=b.build();
    }
    public static int cost(Profession.Sector sector) {
        return sector==Profession.Sector.INDUSTRIAL?INDUSTRIAL_COST:
                COMMERCIAL_ENABLED.get()&&COMMERCIAL_COST.get()>=0?COMMERCIAL_COST.get():-1;
    }
    private EmploymentConfig() {}
}
