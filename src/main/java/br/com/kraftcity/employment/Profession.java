package br.com.kraftcity.employment;

import java.util.Arrays;
import net.minecraft.resources.ResourceLocation;

/** Stable operational identities shared by employment and profession implementations. */
public enum Profession {
    FARMER("farmer", "Fazendeiro", Sector.INDUSTRIAL),
    BUTCHER("butcher", "Açougueiro", Sector.INDUSTRIAL),
    SHEPHERD("shepherd", "Pastoreiro", Sector.INDUSTRIAL),
    FISHERMAN("fisherman", "Pescador", Sector.INDUSTRIAL),
    BREEDER("breeder", "Criador", Sector.INDUSTRIAL),
    BEEKEEPER("beekeeper", "Apicultor", Sector.INDUSTRIAL),
    BAKER("baker", "Padeiro", Sector.COMMERCIAL),
    CONFECTIONER("confectioner", "Confeiteiro", Sector.COMMERCIAL),
    GRILL_COOK("grill_cook", "Churrasqueiro", Sector.COMMERCIAL),
    SOLDIER("soldier", "Soldado", Sector.MILITARY),
    BUILDER("builder", "Construtor", Sector.BUILDER);

    public enum Sector { INDUSTRIAL, COMMERCIAL, MILITARY, BUILDER }
    public final String path, label;
    public final Sector sector;
    Profession(String path, String label, Sector sector) { this.path=path; this.label=label; this.sector=sector; }
    public ResourceLocation id() { return new ResourceLocation("kraftcity", path); }
    public static Profession byId(String id) { return Arrays.stream(values()).filter(p->p.id().toString().equals(id)).findFirst().orElse(null); }
}
