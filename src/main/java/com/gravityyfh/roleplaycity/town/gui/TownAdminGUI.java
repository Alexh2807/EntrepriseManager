package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownLevel;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TownAdminGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private TownClaimsGUI claimsGUI;
    private TownMainGUI mainGUI;
    private TownUpgradeGUI upgradeGUI;

    private static final String ADMIN_TITLE = ChatColor.DARK_RED + "Administration Ville";
    private static final String DELETE_CONFIRM_TITLE = ChatColor.DARK_RED + "⚠ Confirmer Suppression";

    public TownAdminGUI(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
    }

    public void setClaimsGUI(TownClaimsGUI claimsGUI) {
        this.claimsGUI = claimsGUI;
    }

    public void setMainGUI(TownMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    public void setUpgradeGUI(TownUpgradeGUI upgradeGUI) {
        this.upgradeGUI = upgradeGUI;
    }

    public void openAdminMenu(Player player, Town town) {
        Inventory inv = Bukkit.createInventory(null, 27, ADMIN_TITLE);

        // Admin override = accès maire
        boolean isAdminOverride = townManager.isAdminOverride(player, town.getName());
        TownRole role = isAdminOverride ? TownRole.MAIRE : town.getMemberRole(player.getUniqueId());
        boolean isMayor = isAdminOverride || (role == TownRole.MAIRE);
        boolean isAssistant = isAdminOverride || (role == TownRole.ADJOINT);
        boolean isArchitect = isAdminOverride || (role == TownRole.ARCHITECTE);

        // 1. Améliorer la Ville (Maire uniquement)
        if (isMayor) {
            TownLevel currentLevel = town.getLevel();
            TownLevel nextLevel = currentLevel.getNextLevel();

            ItemStack upgradeItem = new ItemStack(nextLevel != null ? Material.NETHER_STAR : Material.BEACON);
            ItemMeta upgradeMeta = upgradeItem.getItemMeta();
            upgradeMeta.setDisplayName(ChatColor.GOLD + "⭐ Améliorer la Ville");
            List<String> upgradeLore = new ArrayList<>();
            upgradeLore.add(ChatColor.GRAY + "Niveau actuel: " + ChatColor.AQUA + currentLevel.getDisplayName());
            if (nextLevel != null) {
                upgradeLore.add(ChatColor.GRAY + "Prochain: " + ChatColor.GREEN + nextLevel.getDisplayName());
                upgradeLore.add("");
                upgradeLore.add(ChatColor.YELLOW + "▶ Cliquez pour évoluer");
            } else {
                upgradeLore.add(ChatColor.GREEN + "✓ Niveau maximum !");
            }
            upgradeMeta.setLore(upgradeLore);
            upgradeItem.setItemMeta(upgradeMeta);
            inv.setItem(10, upgradeItem);
        }

        // 2. Définir le Spawn (Maire/Adjoint)
        if (isMayor || isAssistant) {
            ItemStack spawnItem = new ItemStack(Material.RESPAWN_ANCHOR);
            ItemMeta spawnMeta = spawnItem.getItemMeta();
            spawnMeta.setDisplayName(ChatColor.GREEN + "🏠 Définir le Spawn");
            List<String> spawnLore = new ArrayList<>();
            if (town.hasSpawnLocation()) {
                spawnLore.add(ChatColor.GREEN + "✓ Spawn configuré");
                spawnLore.add("");
                spawnLore.add(ChatColor.GRAY + "Les joueurs peuvent se");
                spawnLore.add(ChatColor.GRAY + "téléporter à votre ville");
            } else {
                spawnLore.add(ChatColor.GRAY + "Définir le point de spawn");
                spawnLore.add(ChatColor.GRAY + "pour la téléportation");
            }
            spawnLore.add("");
            spawnLore.add(ChatColor.YELLOW + "▶ Cliquez pour définir ici");
            spawnMeta.setLore(spawnLore);
            spawnItem.setItemMeta(spawnMeta);
            inv.setItem(12, spawnItem);
        }

        // 3. Gestion de la Ville (Renommer/Desc/Supprimer) (Maire/Adjoint)
        if (isMayor || isAssistant) {
            ItemStack manageItem = new ItemStack(Material.COMMAND_BLOCK);
            ItemMeta manageMeta = manageItem.getItemMeta();
            manageMeta.setDisplayName(ChatColor.RED + "⚙ Paramètres Ville");
            List<String> manageLore = new ArrayList<>();
            manageLore.add(ChatColor.GRAY + "▪ Modifier le nom");
            manageLore.add(ChatColor.GRAY + "▪ Changer la description");
            if (isMayor) {
                manageLore.add(ChatColor.RED + "▪ Supprimer la ville");
            }
            manageLore.add("");
            manageLore.add(ChatColor.YELLOW + "▶ Cliquez pour gérer");
            manageMeta.setLore(manageLore);
            manageItem.setItemMeta(manageMeta);
            inv.setItem(14, manageItem);
        }

        // 4. Claims et Terrains (Maire/Adjoint/Architecte)
        if (isMayor || isAssistant || isArchitect) {
            ItemStack claimsItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta claimsMeta = claimsItem.getItemMeta();
            claimsMeta.setDisplayName(ChatColor.GREEN + "🗺 Claims et Terrains");
            List<String> claimsLore = new ArrayList<>();
            claimsLore.add(ChatColor.GRAY + "▪ Gérer les parcelles");
            claimsLore.add(ChatColor.GRAY + "▪ Claim / Unclaim");
            claimsLore.add(ChatColor.GRAY + "▪ Vendre / Louer");
            claimsLore.add("");
            claimsLore.add(ChatColor.YELLOW + "▶ Cliquez pour accéder");
            claimsMeta.setLore(claimsLore);
            claimsItem.setItemMeta(claimsMeta);
            inv.setItem(16, claimsItem);
        }

        // Retour (haut gauche)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(0, backItem);

        // Fermer (haut droite)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(8, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ADMIN_TITLE))
            return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        String townName = townManager.getEffectiveTown(player);
        if (townName == null)
            return;
        Town town = townManager.getTown(townName);
        if (town == null)
            return;

        if (displayName.contains("Améliorer la Ville")) {
            player.closeInventory();
            if (upgradeGUI != null) {
                upgradeGUI.openUpgradeMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Menu d'amélioration indisponible.");
            }
        } else if (displayName.contains("Définir le Spawn")) {
            player.closeInventory();
            // Vérifier que le joueur est sur un terrain de la ville
            if (!plugin.getClaimManager().isSpawnLocationValid(town, player.getLocation())) {
                player.sendMessage(ChatColor.RED + "✖ Vous devez être sur un terrain de votre ville pour définir le spawn !");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            town.setSpawnLocation(player.getLocation());
            townManager.saveTownsNow();
            player.sendMessage(ChatColor.GREEN + "✓ Le point de spawn de la ville a été défini ici !");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        } else if (displayName.contains("Paramètres Ville")) {
            player.closeInventory();
            openSettingsChoiceMenu(player, town);
        } else if (displayName.contains("Claims et Terrains")) {
            player.closeInventory();
            if (claimsGUI != null) {
                claimsGUI.openClaimsMenu(player);
            }
        } else if (displayName.contains("Retour")) {
            player.closeInventory();
            if (mainGUI != null) {
                mainGUI.openMainMenu(player);
            }
        } else if (displayName.contains("Fermer")) {
            player.closeInventory();
        }
    }

    private void openSettingsChoiceMenu(Player player, Town town) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.RED + "Paramètres Ville");

        // Renommer
        ItemStack renameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameItem.getItemMeta();
        renameMeta.setDisplayName(ChatColor.YELLOW + "Renommer la Ville");
        renameItem.setItemMeta(renameMeta);
        inv.setItem(11, renameItem);

        // Description
        ItemStack descItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta descMeta = descItem.getItemMeta();
        descMeta.setDisplayName(ChatColor.YELLOW + "Changer Description");
        descItem.setItemMeta(descMeta);
        inv.setItem(13, descItem);

        // Supprimer (Maire seulement)
        if (town.isMayor(player.getUniqueId())) {
            ItemStack deleteItem = new ItemStack(Material.TNT);
            ItemMeta deleteMeta = deleteItem.getItemMeta();
            deleteMeta.setDisplayName(ChatColor.RED + "SUPPRIMER LA VILLE");
            deleteMeta.setLore(List.of(ChatColor.DARK_RED + "Action Irréversible !"));
            deleteItem.setItemMeta(deleteMeta);
            inv.setItem(15, deleteItem);
        }

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onSettingsClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.RED + "Paramètres Ville"))
            return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        String townName = townManager.getEffectiveTown(player);
        if (townName == null)
            return;
        Town town = townManager.getTown(townName);

        if (displayName.contains("Renommer")) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Entrez le nouveau nom de la ville dans le chat (ou 'annuler') :");
            plugin.getChatListener().waitForInput(player.getUniqueId(), (newName) -> {
                if (townManager.renameTown(town.getName(), newName, 0)) { // 0 cost for now or fetch from config
                    player.sendMessage(ChatColor.GREEN + "Ville renommée en " + newName + " !");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de renommer la ville (Nom invalide ou déjà pris).");
                }
            });
        } else if (displayName.contains("Description")) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Entrez la nouvelle description dans le chat (ou 'annuler') :");
            plugin.getChatListener().waitForInput(player.getUniqueId(), (newDesc) -> {
                town.setDescription(newDesc);
                townManager.saveTownsNow();
                player.sendMessage(ChatColor.GREEN + "Description mise à jour !");
            });
        } else if (displayName.contains("SUPPRIMER")) {
            openDeleteConfirmationMenu(player, town);
        } else if (displayName.contains("Retour")) {
            openAdminMenu(player, town);
        }
    }

    /**
     * Ouvre le menu de confirmation pour supprimer la ville
     */
    private void openDeleteConfirmationMenu(Player player, Town town) {
        Inventory inv = Bukkit.createInventory(null, 27, DELETE_CONFIRM_TITLE);

        // Bouton de confirmation (rouge - TNT)
        ItemStack confirmItem = new ItemStack(Material.TNT);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "✖ CONFIRMER LA SUPPRESSION");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        confirmLore.add(ChatColor.RED + "⚠ ATTENTION ⚠");
        confirmLore.add(ChatColor.GRAY + "Cette action est " + ChatColor.RED + "IRRÉVERSIBLE" + ChatColor.GRAY + " !");
        confirmLore.add("");
        confirmLore.add(ChatColor.GRAY + "Vous allez supprimer:");
        confirmLore.add(ChatColor.WHITE + "• Ville: " + ChatColor.GOLD + town.getName());
        confirmLore.add(ChatColor.WHITE + "• Membres: " + ChatColor.YELLOW + town.getMembers().size());
        confirmLore.add(ChatColor.WHITE + "• Claims: " + ChatColor.YELLOW + town.getRealChunkCount());
        confirmLore.add("");
        confirmLore.add(ChatColor.DARK_RED + "▶ Cliquez pour supprimer définitivement");
        confirmMeta.setLore(confirmLore);
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(11, confirmItem);

        // Bouton d'annulation (vert - Emeraude)
        ItemStack cancelItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ ANNULER");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("");
        cancelLore.add(ChatColor.GRAY + "Retourner au menu précédent");
        cancelLore.add(ChatColor.GRAY + "sans supprimer la ville.");
        cancelLore.add("");
        cancelLore.add(ChatColor.GREEN + "▶ Cliquez pour annuler");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(15, cancelItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onDeleteConfirmClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(DELETE_CONFIRM_TITLE))
            return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;

        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            player.closeInventory();
            return;
        }
        Town town = townManager.getTown(townName);
        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        if (town == null || (!isAdminOverride && !town.isMayor(player.getUniqueId()))) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "❌ Seul le maire peut supprimer la ville !");
            return;
        }

        Material type = clicked.getType();

        if (type == Material.TNT) {
            // Confirmer la suppression
            player.closeInventory();

            String deletedTownName = town.getName();
            boolean success = townManager.deleteTown(deletedTownName);

            if (success) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "═══════════════════════════════════");
                player.sendMessage(ChatColor.DARK_RED + "  ⚠ VILLE SUPPRIMÉE ⚠");
                player.sendMessage(ChatColor.RED + "═══════════════════════════════════");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "  La ville " + ChatColor.GOLD + deletedTownName + ChatColor.GRAY + " a été supprimée.");
                player.sendMessage(ChatColor.GRAY + "  Tous les claims ont été libérés.");
                player.sendMessage(ChatColor.GRAY + "  Tous les membres ont été retirés.");
                player.sendMessage("");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1f);
            } else {
                player.sendMessage(ChatColor.RED + "❌ Erreur lors de la suppression de la ville.");
            }
        } else if (type == Material.EMERALD_BLOCK) {
            // Annuler - retour au menu des paramètres
            openSettingsChoiceMenu(player, town);
        }
    }
}
