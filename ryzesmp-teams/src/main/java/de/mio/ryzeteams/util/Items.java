package de.mio.ryzeteams.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Small helper for building GUI display items without italic defaults. */
public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(text(name));
            if (loreLines.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(text(line).color(NamedTextColor.GRAY));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack withLore(Material material, String name, List<String> loreLines) {
        return of(material, name, loreLines.toArray(new String[0]));
    }

    /** A player head for a (possibly offline) player. */
    public static ItemStack head(UUID uuid, String name, String... loreLines) {
        ItemStack item = of(Material.PLAYER_HEAD, name, loreLines);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            item.setItemMeta(skull);
        }
        return item;
    }

    /** A non-italic component (GUI text defaults to italic otherwise). */
    public static Component text(String s) {
        return Component.text(s).decoration(TextDecoration.ITALIC, false);
    }

    public static Material material(String name, Material fallback) {
        try {
            Material m = Material.matchMaterial(name);
            return m != null ? m : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static List<String> lore(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }
}
