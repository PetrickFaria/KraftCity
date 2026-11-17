package br.com.kraftcity.residential;

import br.com.residenciasaldeoes.ResidenciasDosAldeoes;

/** Keeps the approved residential registries and event handlers together. */
public final class ResidentialModule {
    private ResidentialModule() {}

    public static void initialize() {
        new ResidenciasDosAldeoes();
    }
}
