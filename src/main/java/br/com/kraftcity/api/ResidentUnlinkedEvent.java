package br.com.kraftcity.api;

import java.util.UUID;
import net.minecraftforge.eventbus.api.Event;

/** Fired by the residential subsystem after an explicit link removal, including unloaded residents. */
public final class ResidentUnlinkedEvent extends Event {
    public final UUID resident;
    public ResidentUnlinkedEvent(UUID resident) { this.resident=resident; }
}
