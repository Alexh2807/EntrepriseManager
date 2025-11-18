package com.gravityyfh.roleplaycity.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FIX BASSE #15: Builder pattern pour la création d'ItemStacks
 *
 * Réduit la duplication de code pour la création d'items dans les GUIs.
 * Plus de 465 occurrences de lore.add() peuvent bénéficier de cette classe.
 *
 * Avantages:
 * - Syntaxe fluide et lisible
 * - Réduction de la duplication (150+ créations d'items)
 * - Moins de code boilerplate
 * - Méthodes chaînables pour construction progressive
 */
public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;
    private final List<String> lore;

    // === CONSTRUCTEURS ===

    /**
     * Crée un builder à partir d'un matériau
     *
     * @param material Matériau de l'item
     */
    private ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();
        this.lore = new ArrayList<>();
    }

    /**
     * Crée un builder à partir d'un matériau et d'une quantité
     *
     * @param material Matériau de l'item
     * @param amount Quantité
     */
    private ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
        this.itemMeta = itemStack.getItemMeta();
        this.lore = new ArrayList<>();
    }

    /**
     * Crée un builder à partir d'un ItemStack existant
     *
     * @param itemStack ItemStack de base
     */
    private ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.itemMeta = this.itemStack.getItemMeta();
        this.lore = new ArrayList<>();

        // Récupérer la lore existante si présente
        if (itemMeta != null && itemMeta.hasLore()) {
            this.lore.addAll(itemMeta.getLore());
        }
    }

    // === MÉTHODES STATIQUES DE CRÉATION ===

    /**
     * Crée un nouveau builder avec un matériau
     *
     * @param material Matériau de l'item
     * @return Builder pour chaînage
     */
    public static ItemBuilder create(Material material) {
        return new ItemBuilder(material);
    }

    /**
     * Crée un nouveau builder avec un matériau et une quantité
     *
     * @param material Matériau de l'item
     * @param amount Quantité
     * @return Builder pour chaînage
     */
    public static ItemBuilder create(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    /**
     * Crée un builder à partir d'un ItemStack existant
     *
     * @param itemStack ItemStack de base
     * @return Builder pour chaînage
     */
    public static ItemBuilder from(ItemStack itemStack) {
        return new ItemBuilder(itemStack);
    }

    // === NOM ET LORE ===

    /**
     * Définit le nom de l'item (avec couleur)
     *
     * @param name Nom de l'item
     * @return Builder pour chaînage
     */
    public ItemBuilder name(String name) {
        if (itemMeta != null) {
            itemMeta.setDisplayName(name);
        }
        return this;
    }

    /**
     * Définit le nom de l'item avec une couleur
     *
     * @param color Couleur du nom
     * @param name Nom de l'item
     * @return Builder pour chaînage
     */
    public ItemBuilder name(ChatColor color, String name) {
        return name(color + name);
    }

    /**
     * Ajoute une ligne de lore
     *
     * @param line Ligne de lore
     * @return Builder pour chaînage
     */
    public ItemBuilder lore(String line) {
        lore.add(line);
        return this;
    }

    /**
     * Ajoute plusieurs lignes de lore
     *
     * @param lines Lignes de lore
     * @return Builder pour chaînage
     */
    public ItemBuilder lore(String... lines) {
        lore.addAll(Arrays.asList(lines));
        return this;
    }

    /**
     * Ajoute plusieurs lignes de lore avec une couleur
     *
     * @param color Couleur des lignes
     * @param lines Lignes de lore
     * @return Builder pour chaînage
     */
    public ItemBuilder lore(ChatColor color, String... lines) {
        for (String line : lines) {
            lore.add(color + line);
        }
        return this;
    }

    /**
     * Ajoute une liste de lignes de lore
     *
     * @param lines Liste de lignes
     * @return Builder pour chaînage
     */
    public ItemBuilder loreList(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    /**
     * Ajoute une ligne vide dans la lore
     *
     * @return Builder pour chaînage
     */
    public ItemBuilder emptyLore() {
        lore.add("");
        return this;
    }

    /**
     * Efface toute la lore
     *
     * @return Builder pour chaînage
     */
    public ItemBuilder clearLore() {
        lore.clear();
        return this;
    }

    // === QUANTITÉ ===

    /**
     * Définit la quantité de l'item
     *
     * @param amount Quantité
     * @return Builder pour chaînage
     */
    public ItemBuilder amount(int amount) {
        itemStack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    // === ENCHANTEMENTS ET FLAGS ===

    /**
     * Ajoute un enchantement
     *
     * @param enchantment Enchantement
     * @param level Niveau
     * @return Builder pour chaînage
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (itemMeta != null) {
            itemMeta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Ajoute l'effet brillant (enchantement invisible)
     *
     * @return Builder pour chaînage
     */
    public ItemBuilder glow() {
        if (itemMeta != null) {
            itemMeta.addEnchant(Enchantment.DURABILITY, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Ajoute un flag d'item
     *
     * @param flag Flag à ajouter
     * @return Builder pour chaînage
     */
    public ItemBuilder flag(ItemFlag flag) {
        if (itemMeta != null) {
            itemMeta.addItemFlags(flag);
        }
        return this;
    }

    /**
     * Ajoute plusieurs flags d'item
     *
     * @param flags Flags à ajouter
     * @return Builder pour chaînage
     */
    public ItemBuilder flags(ItemFlag... flags) {
        if (itemMeta != null) {
            itemMeta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Cache tous les attributs de l'item
     *
     * @return Builder pour chaînage
     */
    public ItemBuilder hideAll() {
        if (itemMeta != null) {
            itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES,
                                  ItemFlag.HIDE_ENCHANTS,
                                  ItemFlag.HIDE_UNBREAKABLE,
                                  ItemFlag.HIDE_DESTROYS,
                                  ItemFlag.HIDE_PLACED_ON,
                                  ItemFlag.HIDE_POTION_EFFECTS);
        }
        return this;
    }

    // === INDESTRUCTIBLE ===

    /**
     * Rend l'item indestructible
     *
     * @return Builder pour chaînage
     */
    public ItemBuilder unbreakable() {
        if (itemMeta != null) {
            itemMeta.setUnbreakable(true);
        }
        return this;
    }

    // === TÊTE DE JOUEUR ===

    /**
     * Définit le propriétaire d'une tête de joueur
     *
     * @param owner Nom du propriétaire
     * @return Builder pour chaînage
     */
    public ItemBuilder skullOwner(String owner) {
        if (itemMeta instanceof SkullMeta) {
            ((SkullMeta) itemMeta).setOwner(owner);
        }
        return this;
    }

    // === CONSTRUCTION ===

    /**
     * Construit l'ItemStack final
     *
     * @return ItemStack construit
     */
    public ItemStack build() {
        if (itemMeta != null) {
            // Appliquer la lore si non vide
            if (!lore.isEmpty()) {
                itemMeta.setLore(new ArrayList<>(lore));
            }

            // Appliquer les métadonnées
            itemStack.setItemMeta(itemMeta);
        }

        return itemStack;
    }

    // === MÉTHODES UTILITAIRES STATIQUES ===

    /**
     * Crée rapidement un item avec nom et lore
     *
     * @param material Matériau
     * @param name Nom
     * @param lore Lignes de lore
     * @return ItemStack créé
     */
    public static ItemStack quick(Material material, String name, String... lore) {
        return create(material)
            .name(name)
            .lore(lore)
            .build();
    }

    /**
     * Crée un item de remplissage (vitre grise)
     *
     * @return ItemStack de remplissage
     */
    public static ItemStack filler() {
        return create(Material.GRAY_STAINED_GLASS_PANE)
            .name(" ")
            .build();
    }

    /**
     * Crée un item de bordure (vitre noire)
     *
     * @return ItemStack de bordure
     */
    public static ItemStack border() {
        return create(Material.BLACK_STAINED_GLASS_PANE)
            .name(" ")
            .build();
    }

    /**
     * Crée un bouton "Retour"
     *
     * @return ItemStack bouton retour
     */
    public static ItemStack backButton() {
        return create(Material.ARROW)
            .name(ChatColor.RED + "← Retour")
            .lore(ChatColor.GRAY + "Cliquez pour revenir")
            .build();
    }

    /**
     * Crée un bouton "Fermer"
     *
     * @return ItemStack bouton fermer
     */
    public static ItemStack closeButton() {
        return create(Material.BARRIER)
            .name(ChatColor.RED + "✖ Fermer")
            .lore(ChatColor.GRAY + "Cliquez pour fermer")
            .build();
    }

    /**
     * Crée un bouton "Confirmer"
     *
     * @return ItemStack bouton confirmer
     */
    public static ItemStack confirmButton() {
        return create(Material.LIME_DYE)
            .name(ChatColor.GREEN + "✔ Confirmer")
            .lore(ChatColor.GRAY + "Cliquez pour confirmer")
            .build();
    }

    /**
     * Crée un bouton "Annuler"
     *
     * @return ItemStack bouton annuler
     */
    public static ItemStack cancelButton() {
        return create(Material.RED_DYE)
            .name(ChatColor.RED + "✖ Annuler")
            .lore(ChatColor.GRAY + "Cliquez pour annuler")
            .build();
    }
}
