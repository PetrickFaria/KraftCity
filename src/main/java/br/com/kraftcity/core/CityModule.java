package br.com.kraftcity.core;

import net.minecraft.resources.ResourceLocation;

/** Extension identities, not block registrations or enabled gameplay. */
public enum CityModule {
    RESIDENTIAL("residential", true),
    INDUSTRIAL("industrial", false),
    COMMERCIAL("commercial", false),
    MILITARY("military", false),
    BUILDER("builder", false);

    private final ResourceLocation id;
    private final boolean implemented;

    CityModule(String path, boolean implemented) {
        this.id = new ResourceLocation("kraftcity", path);
        this.implemented = implemented;
    }

    public ResourceLocation id() { return id; }
    public boolean implemented() { return implemented; }
}
