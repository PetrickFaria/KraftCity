package br.com.kraftcity.core;

import net.minecraft.resources.ResourceLocation;

/** Block/UI module availability; profession production remains a separate stage. */
public enum CityModule {
    RESIDENTIAL("residential", true),
    INDUSTRIAL("industrial", true),
    COMMERCIAL("commercial", true),
    MILITARY("military", true),
    BUILDER("builder", true);

    private final ResourceLocation id;
    private final boolean implemented;

    CityModule(String path, boolean implemented) {
        this.id = new ResourceLocation("kraftcity", path);
        this.implemented = implemented;
    }

    public ResourceLocation id() { return id; }
    public boolean implemented() { return implemented; }
}
