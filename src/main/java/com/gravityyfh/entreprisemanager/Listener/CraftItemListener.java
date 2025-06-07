package com.gravityyfh.entreprisemanager.Listener;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class CraftItemListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public CraftItemListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        ItemStack resultItem = recipe.getResult();
        Material itemType = resultItem.getType();
        String itemTypeName = itemType.name();
        int amountPerCraft = resultItem.getAmount();

        int totalAmountToCraft;

        // --- LOGIQUE CORRIGÉE POUR LE SHIFT-CLICK ---
        if (event.isShiftClick()) {
            // Si le joueur shift-click, nous devons calculer la quantité totale qui sera fabriquée
            // pour la vérifier par rapport à la limite horaire.
            int maxCraftsPossible = calculateMaxCraftable(player, recipe, event.getInventory());
            totalAmountToCraft = maxCraftsPossible * amountPerCraft;
        } else {
            // Pour un clic normal, la quantité est simplement celle d'une seule fabrication.
            totalAmountToCraft = amountPerCraft;
        }
        // --- FIN DE LA LOGIQUE CORRIGÉE ---

        if (totalAmountToCraft <= 0) {
            // Si le calcul aboutit à 0, le craft va échouer de toute façon (pas assez d'ingrédients),
            // ou bien il s'agit d'un clic simple sur un résultat de 0 (impossible). On ignore.
            return;
        }

        plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Tentative de craft par " + player.getName() + " pour une quantité totale de " + totalAmountToCraft + "x " + itemTypeName + (event.isShiftClick() ? " (via Shift-Click)" : ""));

        // On vérifie la restriction en utilisant la quantité TOTALE qui sera fabriquée.
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemTypeName, totalAmountToCraft);

        plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Résultat vérification restriction pour " + player.getName() + " : " + (isBlocked ? "BLOQUÉ" : "AUTORISÉ"));

        if (isBlocked) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Craft de " + totalAmountToCraft + "x " + itemTypeName + " annulé par restriction pour " + player.getName());
            return;
        }

        // L'action est autorisée. On laisse l'enregistrement de productivité se faire.
        // Pour un shift-click, cet événement sera appelé plusieurs fois.
        // La restriction ayant été validée en amont sur le total, on peut laisser
        // la logique d'enregistrement de revenu traiter chaque événement individuellement.
        plugin.getLogger().log(Level.FINER, "[DEBUG Craft] Enregistrement action pour " + player.getName() + " craftant (quantité par event) " + amountPerCraft + "x " + itemTypeName);
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, amountPerCraft);
    }

    /**
     * Calcule le nombre maximal de fois qu'une recette peut être fabriquée.
     * C'est la partie la plus complexe.
     *
     * @param player L'auteur du craft.
     * @param recipe La recette utilisée.
     * @param craftingInventory L'inventaire de la table de craft.
     * @return Le nombre de fois que la recette peut être complétée.
     */
    private int calculateMaxCraftable(Player player, Recipe recipe, CraftingInventory craftingInventory) {
        // La méthode la plus simple pour le shift-click est de vérifier la quantité d'ingrédients
        // DANS LA GRILLE DE CRAFT et de trouver le plus petit stack.
        // Cela détermine le nombre de crafts possibles EN UN SEUL CLIC.
        int maxCrafts = Integer.MAX_VALUE;

        for (ItemStack item : craftingInventory.getMatrix()) {
            if (item != null && item.getType() != Material.AIR) {
                maxCrafts = Math.min(maxCrafts, item.getAmount());
            }
        }

        if (maxCrafts == Integer.MAX_VALUE) return 0; // Grille vide

        // Maintenant, on vérifie combien de sets d'ingrédients le joueur possède DANS SON INVENTAIRE
        // pour remplacer ceux de la grille de craft.
        HashMap<Material, Integer> ingredients = new HashMap<>();
        for (ItemStack item : craftingInventory.getMatrix()) {
            if (item != null && item.getType() != Material.AIR) {
                ingredients.merge(item.getType(), 1, Integer::sum);
            }
        }

        if (ingredients.isEmpty()) return 0;

        int craftableFromInventory = Integer.MAX_VALUE;
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            int playerAmount = 0;
            // On compte UNIQUEMENT les items dans l'inventaire principal, pas l'armure ou la main secondaire.
            for (ItemStack invItem : player.getInventory().getStorageContents()) {
                if (invItem != null && invItem.getType() == entry.getKey()) {
                    playerAmount += invItem.getAmount();
                }
            }
            craftableFromInventory = Math.min(craftableFromInventory, playerAmount / entry.getValue());
        }

        return maxCrafts + craftableFromInventory;
    }
}