package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.regex.Pattern;

/** Reads only the synchronized hotbar. No item names are used to identify weapons. */
public record TransmissionEquipment(int slot, Kind kind, int range, int ultimateWise, double intelligence) {
    public enum Kind { AOTE, AOTV }
    public static final Comparator<TransmissionEquipment> PREFERENCE = Comparator
            .comparingInt(TransmissionEquipment::range)
            .thenComparingInt(TransmissionEquipment::ultimateWise)
            .thenComparingDouble(TransmissionEquipment::intelligence)
            .thenComparing(TransmissionEquipment::kind)
            .thenComparing(Comparator.comparingInt(TransmissionEquipment::slot).reversed());
    private static final Pattern INTELLIGENCE = Pattern.compile("^Intelligence:\\s*([+-]?[0-9][0-9,]*(?:\\.[0-9]+)?).*");

    private static final Pattern RANGE = Pattern.compile("^Teleport ([0-9]{1,3}) blocks ahead of you\\b.*");

    public static Optional<TransmissionEquipment> select(Inventory inventory) {
        return IntStream.range(0,9).mapToObj(slot -> read(slot,inventory.getItem(slot)))
                .flatMap(Optional::stream).max(PREFERENCE);
    }

    public static Optional<TransmissionEquipment> read(int slot, ItemStack stack) {
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if (data==null) return Optional.empty();
        var lore=stack.get(DataComponents.LORE);
        return read(slot,data.copyTag(),lore==null?List.of():lore.lines().stream().map(Component::getString).toList());
    }

    /** Decode synchronized SkyBlock attributes and the displayed ordinary ability, without a world. */
    public static Optional<TransmissionEquipment> read(int slot,CompoundTag root,List<String> lore) {
        var tag=root.getCompound("ExtraAttributes").orElse(root);
        var kind=switch(tag.getString("id").orElse("")) {
            case "ASPECT_OF_THE_END" -> Kind.AOTE;
            case "ASPECT_OF_THE_VOID" -> Kind.AOTV;
            default -> null;
        };
        if (kind==null) return Optional.empty();
        int tuners=Math.clamp(tag.getInt("tuned_transmission").orElse(0),0,4);
        int wise=tag.getCompound("enchantments").flatMap(nbt->nbt.getInt("ultimate_wise")).orElse(0);
        int range=8+tuners;
        double intelligence=0;
        boolean instantTransmission=false;
        for (String line : lore) {
            String text=line.replaceAll("(?i)\\u00a7[0-9a-fk-orx]","").strip();
            if (text.startsWith("Ability:")) instantTransmission=text.startsWith("Ability: Instant Transmission");
            var stat=INTELLIGENCE.matcher(text);
            if (stat.matches()) {
                double value=Double.parseDouble(stat.group(1).replace(",",""));
                if (Double.isFinite(value)) intelligence=value;
            }
            if (instantTransmission) {
                var distance=RANGE.matcher(text);
                if (distance.matches()) range=Math.max(range,Integer.parseInt(distance.group(1)));
            }
        }
        return Optional.of(new TransmissionEquipment(slot,kind,range,Math.clamp(wise,0,5),intelligence));
    }
}
