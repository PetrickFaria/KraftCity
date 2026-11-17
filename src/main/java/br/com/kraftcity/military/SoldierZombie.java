package br.com.kraftcity.military;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.level.Level;

/** Vanilla hostile AI and weakness/apple countdown. Equipment has exactly one ledger owner. */
public final class SoldierZombie extends ZombieVillager {
    public SoldierZombie(EntityType<? extends ZombieVillager> type,Level level){super(type,level);setPersistenceRequired();setCanPickUpLoot(false);}
}
