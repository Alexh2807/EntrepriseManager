package com.gravityyfh.entreprisemanager;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import java.util.logging.Level; // Ajout pour les logs de debug

public class CraftItemListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public CraftItemListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return; // Action non initiée par un joueur
        }
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().log(Level.FINER, "CraftItemEvent ignoré (Joueur en créatif : " + player.getName() + ")");
            return;
        }

        ItemStack itemCraftedStack = event.getRecipe().getResult();
        Material itemType = itemCraftedStack.getType();
        String itemTypeName = itemType.name(); // Obtenir le nom de l'item sous forme de String
        int amountCrafted = 0;

        // Déterminer la quantité craftée
        // Si l'utilisateur clique normalement, cela fabrique la quantité de la recette.
        // Si l'utilisateur fait un shift-click, il fabrique autant que possible.
        // L'événement CraftItemEvent est appelé pour *chaque résultat* de fabrication.
        // Si une recette donne 8 pains et que le joueur shift-click pour en faire 64, l'event est appelé 8 fois.
        // Donc, event.getRecipe().getResult().getAmount() est la quantité pour UNE opération.
        // Pour calculer la quantité totale lors d'un shift-click, c'est plus complexe.
        // La solution la plus simple est de compter la quantité de l'item résultant dans le curseur
        // ou ce qui est ajouté à l'inventaire, mais l'event est PRE-craft.
        // Pour la restriction horaire, il est plus simple de compter CHAQUE action de craft.
        // Si la recette fait 1 item, amountCrafted sera 1.
        // Si la recette fait 8 cookies, amountCrafted sera 8.
        // C'est la quantité pour une seule "pression" sur la table de craft.

        // La variable 'amountCrafted' est la quantité résultante d'UNE SEULE opération de craft.
        // C'est ce que nous allons utiliser pour vérifier les restrictions et enregistrer la productivité.
        amountCrafted = itemCraftedStack.getAmount();


        // Gérer la quantité pour le shift-click :
        // Si le joueur shift-click, event.isShiftClick() sera vrai.
        // L'événement est généralement appelé plusieurs fois pour un shift-click.
        // Chaque appel correspond à une "unité" de la recette.
        // Par exemple, si la recette fait 1 pain et que le joueur en craft 10 avec shift-click,
        // l'event est appelé 10 fois, avec amountCrafted = 1 à chaque fois.
        // Si la recette fait 8 cookies et qu'il en craft 16, l'event est appelé 2 fois, amountCrafted = 8.
        // Donc, la valeur 'amountCrafted' récupérée directement est correcte par événement.

        plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Événement : " + player.getName() + " crafte " + amountCrafted + "x " + itemTypeName);


        // Vérifier les restrictions pour l'item crafté
        // Utiliser la surcharge de verifierEtGererRestrictionAction qui prend un String pour le nom de la cible (ici, le nom de l'item)
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemTypeName, amountCrafted);

        plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Résultat vérification restriction pour " + player.getName() + " craftant " + itemTypeName + " : " + (isBlocked ? "BLOQUÉ" : "AUTORISÉ"));

        if (isBlocked) {
            event.setCancelled(true); // Action bloquée par la restriction (limite non-membre atteinte)
            plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Craft de " + amountCrafted + "x " + itemTypeName + " annulé par restriction pour " + player.getName());
            return;
        }

        // Si l'action n'est pas bloquée, enregistrer l'action productive.
        // La méthode enregistrerActionProductive pour CRAFT_ITEM utilise toujours l'objet Material.
        plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Enregistrement action pour " + player.getName() + " craftant " + amountCrafted + "x " + itemTypeName);
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, amountCrafted);
    }
}