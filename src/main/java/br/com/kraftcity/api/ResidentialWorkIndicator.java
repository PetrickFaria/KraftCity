package br.com.kraftcity.api;

import java.util.UUID;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;

/** Dependency inversion: the residential GUI knows only this query, never employment storage. */
public final class ResidentialWorkIndicator {
    @FunctionalInterface public interface Provider { boolean hasWork(ServerLevel level,UUID resident,boolean legacy); }
    private static volatile Provider provider=(level,resident,legacy)->legacy;
    public static void register(Provider next){provider=Objects.requireNonNull(next);}
    public static boolean resolve(ServerLevel level,UUID resident,boolean legacy){return provider.hasWork(level,resident,legacy);}
    private ResidentialWorkIndicator(){}
}
