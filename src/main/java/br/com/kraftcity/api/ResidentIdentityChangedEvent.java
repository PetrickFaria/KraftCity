package br.com.kraftcity.api;

import java.util.UUID;
import net.minecraftforge.eventbus.api.Event;

/** Successful, duplicate-guarded capture/release identity reconciliation. */
public final class ResidentIdentityChangedEvent extends Event {
    public final UUID previous,current;
    public ResidentIdentityChangedEvent(UUID previous,UUID current){this.previous=previous;this.current=current;}
}
