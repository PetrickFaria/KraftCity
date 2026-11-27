package br.com.kraftcity.industry;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkBlockEntity;
import br.com.kraftcity.military.MilitaryDropOrigin;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

final class ProfessionalLoot {
    static boolean matches(Profession profession, ItemStack stack) {
        return stack.is(TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("kraftcity", "pickup/" + profession.path)))
                || switch (profession) {
            case FARMER -> IndustrialWork.food(stack) || FarmerWork.plant(stack) != null
                    || stack.is(Items.MELON) || stack.is(Items.MELON_SLICE) || stack.is(Items.PUMPKIN)
                    || tagged(stack, "forge:crops") || tagged(stack, "forge:seeds")
                    || stack.is(Items.BONE_MEAL) || stack.getItem() instanceof net.minecraft.world.item.HoeItem;
            case LUMBERJACK -> stack.is(ItemTags.LOGS) || stack.is(ItemTags.LEAVES)
                    || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.PLANKS)
                    || stack.is(Items.STICK) || stack.is(Items.APPLE) || LumberjackWork.cutTicks(stack) > 0;
            case BEEKEEPER -> stack.is(ItemTags.FLOWERS) || stack.is(Items.HONEYCOMB) || stack.is(Items.HONEY_BOTTLE)
                    || stack.is(Items.SHEARS) || stack.is(Items.GLASS_BOTTLE);
            case FISHERMAN -> stack.is(ItemTags.FISHES) || stack.is(Items.INK_SAC) || stack.is(Items.FISHING_ROD);
            case SHEPHERD -> stack.is(ItemTags.WOOL) || stack.is(Items.WHEAT) || stack.is(Items.SHEARS);
            case BUTCHER -> stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.CHICKEN)
                    || stack.is(Items.MUTTON) || stack.is(Items.RABBIT) || stack.is(Items.LEATHER)
                    || stack.is(Items.RABBIT_HIDE) || stack.is(Items.FEATHER) || tagged(stack, "forge:raw_meats");
            case BREEDER -> IndustrialWork.food(stack) || stack.is(Items.BONE)
                    || stack.getFoodProperties(null) != null && stack.getFoodProperties(null).isMeat();
            default -> false;
        };
    }

    private static boolean tagged(ItemStack stack, String name) {
        return stack.is(TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(name)));
    }

    static boolean active(ServerLevel level, EmploymentData.Station station) {
        if (station == null || !station.active || station.worker == null || !station.dimension.equals(level.dimension())
                || !level.hasChunkAt(station.pos) || !(level.getBlockEntity(station.pos) instanceof WorkBlockEntity post)
                || !station.id.equals(post.identity())) return false;
        var worker = level.getEntity(station.worker);
        return worker == null || worker instanceof Villager villager && villager.isAlive();
    }

    record Reservation(EmploymentData.Station station, AABB area) {}

    static List<Reservation> reservations(ServerLevel level, AABB search) {
        return EmploymentData.get(level).stations().stream()
                .filter(station -> station.profession != null && station.sector == Profession.Sector.INDUSTRIAL
                        && station.profession != Profession.COLLECTOR && active(level, station))
                .map(station -> {
                    var area = IndustrialWork.area(level, station);
                    return new Reservation(station, FarmerLogistics.fieldWorker(station) ? area.expandTowards(0, -1, 0) : area);
                })
                .filter(reservation -> reservation.area.intersects(search)).toList();
    }

    static boolean eligible(ServerLevel level, EmploymentData.Station station, ItemEntity item, List<Reservation> reservations) {
        if (MilitaryDropOrigin.reserved(level, item)) return false;
        var source = FarmerLogistics.harvestStation(item);
        if (station.id.equals(source)) return true;
        if (station.profession != Profession.COLLECTOR) return matches(station.profession, item.getItem());
        for (var reservation : reservations) {
            var other = reservation.station;
            if (reservation.area.contains(item.position())
                    && (other.id.equals(source) || matches(other.profession, item.getItem()))) return false;
        }
        return true;
    }
}
