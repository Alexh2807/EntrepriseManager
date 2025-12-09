package com.gravityyfh.roleplaycity.phone.listener;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.storage.CompanyStorageManager;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.model.PhoneType;
import com.gravityyfh.roleplaycity.phone.model.PlanType;
import com.gravityyfh.roleplaycity.service.ServiceModeManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Listener pour le craft des telephones et forfaits.
 * Gere les restrictions d'entreprise et le mode service.
 */
public class PhoneCraftListener implements Listener {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final EntrepriseManagerLogic entrepriseLogic;

    public PhoneCraftListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.entrepriseLogic = plugin.getEntrepriseManagerLogic();
    }

    /**
     * PrepareItemCraftEvent - Affiche l'item ItemsAdder dans la preview du craft.
     * C'est cette methode qui corrige le bug de l'affichage papier au lieu du telephone.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }

        // Verifier si c'est un telephone
        PhoneType phoneType = phoneManager.getPhoneType(result);
        if (phoneType != null) {
            // Creer l'item telephone avec l'apparence ItemsAdder
            ItemStack itemsAdderPhone = phoneManager.createPhoneItem(phoneType.getId(), 0);
            if (itemsAdderPhone != null) {
                event.getInventory().setResult(itemsAdderPhone);
            }
            return;
        }

        // Verifier si c'est un forfait
        PlanType planType = phoneManager.getPlanType(result);
        if (planType != null) {
            // Creer l'item forfait avec l'apparence ItemsAdder
            ItemStack itemsAdderPlan = phoneManager.createPlanItem(planType.getId());
            if (itemsAdderPlan != null) {
                event.getInventory().setResult(itemsAdderPlan);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        ItemStack resultItem = recipe.getResult();

        // Verifier si c'est un telephone
        if (phoneManager.isPhone(resultItem)) {
            handlePhoneCraft(event, player, resultItem);
            return;
        }

        // Verifier si c'est un forfait
        if (phoneManager.isPlan(resultItem)) {
            handlePlanCraft(event, player, resultItem);
        }
    }

    /**
     * Gere le craft d'un telephone.
     */
    private void handlePhoneCraft(CraftItemEvent event, Player player, ItemStack resultItem) {
        PhoneType phoneType = phoneManager.getPhoneType(resultItem);
        if (phoneType == null) return;

        String phoneId = phoneType.getId();
        int totalCrafted = calculateTotalCrafted(event, resultItem);

        // Verifier les restrictions d'entreprise
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(
            player, "CRAFT_PHONE", phoneId, totalCrafted
        );

        if (isBlocked) {
            event.setCancelled(true);
            return;
        }

        // Enregistrer l'action productive
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_PHONE", phoneId, totalCrafted);

        // Gestion du mode service
        ServiceModeManager serviceManager = plugin.getServiceModeManager();
        boolean isInService = serviceManager != null && serviceManager.isInService(player.getUniqueId());
        String companyName = isInService ? serviceManager.getActiveEnterprise(player.getUniqueId()) : null;

        if (isInService && companyName != null) {
            handleServiceModeCraft(event, player, companyName, resultItem, totalCrafted, "CRAFT_PHONE", phoneId);
        }
    }

    /**
     * Gere le craft d'un forfait.
     */
    private void handlePlanCraft(CraftItemEvent event, Player player, ItemStack resultItem) {
        PlanType planType = phoneManager.getPlanType(resultItem);
        if (planType == null) return;

        String planId = planType.getId();
        int totalCrafted = calculateTotalCrafted(event, resultItem);

        // Verifier les restrictions d'entreprise
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(
            player, "CRAFT_PLAN", planId, totalCrafted
        );

        if (isBlocked) {
            event.setCancelled(true);
            return;
        }

        // Enregistrer l'action productive
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_PLAN", planId, totalCrafted);

        // Gestion du mode service
        ServiceModeManager serviceManager = plugin.getServiceModeManager();
        boolean isInService = serviceManager != null && serviceManager.isInService(player.getUniqueId());
        String companyName = isInService ? serviceManager.getActiveEnterprise(player.getUniqueId()) : null;

        if (isInService && companyName != null) {
            handleServiceModeCraft(event, player, companyName, resultItem, totalCrafted, "CRAFT_PLAN", planId);
        }
    }

    /**
     * Gere le craft en mode service: envoie les items au coffre d'entreprise.
     */
    private void handleServiceModeCraft(CraftItemEvent event, Player player, String companyName,
                                         ItemStack resultItem, int totalCrafted,
                                         String actionType, String itemId) {
        CompanyStorageManager storageManager = plugin.getCompanyStorageManager();
        if (storageManager == null) {
            return;
        }

        // Verifier si ce craft est une activite de l'entreprise
        if (!isActionAllowedForStorage(companyName, actionType, itemId)) {
            return;
        }

        // Creer une copie de l'item avec la bonne quantite
        ItemStack itemToStore = resultItem.clone();
        itemToStore.setAmount(totalCrafted);

        // Ajouter au coffre d'entreprise
        boolean success = storageManager.addItemToStorage(companyName, itemToStore);

        if (success) {
            // Annuler l'event pour empecher l'item d'aller dans l'inventaire
            event.setCancelled(true);

            // Consommer les ingredients manuellement
            consumeIngredients(event, totalCrafted / resultItem.getAmount());

            // Feedback
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            player.sendMessage(ChatColor.GREEN + "+" + totalCrafted + " " + ChatColor.WHITE +
                getItemDisplayName(resultItem) + ChatColor.GREEN + " ajoute(s) au coffre de " +
                ChatColor.GOLD + companyName);
        } else {
            player.sendMessage(ChatColor.RED + "Le coffre de l'entreprise est plein ! L'item va dans votre inventaire.");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.5f);
        }
    }

    /**
     * Verifie si une action est une activite de l'entreprise.
     */
    private boolean isActionAllowedForStorage(String companyName, String actionType, String itemId) {
        com.gravityyfh.roleplaycity.entreprise.model.Entreprise entreprise =
            entrepriseLogic.getEntreprise(companyName);
        if (entreprise == null) return false;

        String type = entreprise.getType();
        String path = "types-entreprise." + type + ".action_restrictions." + actionType + "." + itemId;
        return plugin.getConfig().contains(path);
    }

    /**
     * Calcule le nombre total d'items qui seront craftes.
     */
    private int calculateTotalCrafted(CraftItemEvent event, ItemStack result) {
        int resultAmount = result.getAmount();

        if (!event.isShiftClick()) {
            return resultAmount;
        }

        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        int maxCrafts = Integer.MAX_VALUE;

        for (ItemStack ingredient : matrix) {
            if (ingredient != null && !ingredient.getType().isAir()) {
                int ingredientAmount = ingredient.getAmount();
                maxCrafts = Math.min(maxCrafts, ingredientAmount);
            }
        }

        if (maxCrafts == Integer.MAX_VALUE || maxCrafts <= 0) {
            return resultAmount;
        }

        // Calculer l'espace disponible
        int availableSpace = calculateAvailableSpace(event.getWhoClicked().getInventory(), result);
        int maxBySpace = availableSpace / resultAmount;
        if (maxBySpace <= 0) maxBySpace = 1;

        return Math.min(maxCrafts, maxBySpace) * resultAmount;
    }

    /**
     * Calcule l'espace disponible dans l'inventaire.
     */
    private int calculateAvailableSpace(org.bukkit.inventory.Inventory inventory, ItemStack item) {
        int space = 0;
        int maxStack = item.getMaxStackSize();

        for (int i = 0; i < 36; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                space += maxStack;
            } else if (slot.isSimilar(item)) {
                space += maxStack - slot.getAmount();
            }
        }

        return space;
    }

    /**
     * Consomme les ingredients du craft manuellement.
     */
    private void consumeIngredients(CraftItemEvent event, int craftCount) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        for (int i = 0; i < matrix.length; i++) {
            ItemStack ingredient = matrix[i];
            if (ingredient != null && !ingredient.getType().isAir()) {
                int newAmount = ingredient.getAmount() - craftCount;
                if (newAmount <= 0) {
                    matrix[i] = null;
                } else {
                    ingredient.setAmount(newAmount);
                }
            }
        }

        inventory.setMatrix(matrix);
    }

    /**
     * Recupere le nom d'affichage d'un item.
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().name().toLowerCase().replace("_", " ");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
