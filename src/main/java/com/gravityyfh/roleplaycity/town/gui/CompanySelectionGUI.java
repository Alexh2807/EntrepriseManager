package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager;
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
import java.util.UUID;

/**
 * GUI pour sélectionner une entreprise lors de l'achat d'un terrain PROFESSIONNEL
 * Affiché uniquement si le joueur possède 2+ entreprises
 */
public class CompanySelectionGUI implements Listener {

    private final RoleplayCity plugin;
    private final CompanyPlotManager companyManager;

    public CompanySelectionGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.companyManager = plugin.getCompanyPlotManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le GUI de sélection d'entreprise pour un achat de terrain
     * @param player Le joueur qui veut acheter
     * @param chunkX Coordonnée X du chunk
     * @param chunkZ Coordonnée Z du chunk
     * @param worldName Nom du monde
     * @param isGroup Si c'est un achat de groupe
     */
    public void open(Player player, int chunkX, int chunkZ, String worldName, boolean isGroup) {
        List<EntrepriseManagerLogic.Entreprise> playerCompanies = getPlayerCompanies(player);

        if (playerCompanies.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous ne possédez aucune entreprise !");
            return;
        }

        if (playerCompanies.size() == 1) {
            // Une seule entreprise, pas besoin de choisir
            player.sendMessage(ChatColor.YELLOW + "Utilisation automatique de votre entreprise : " + playerCompanies.get(0).getNom());
            proceedWithPurchase(player, playerCompanies.get(0).getSiret(), chunkX, chunkZ, worldName);
            return;
        }

        // 2+ entreprises, afficher le GUI de sélection
        int size = Math.min(54, ((playerCompanies.size() + 8) / 9) * 9); // Arrondir au multiple de 9
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.GOLD + "Choisir une Entreprise");

        int slot = 0;
        for (EntrepriseManagerLogic.Entreprise company : playerCompanies) {
            ItemStack item = createCompanyItem(company, chunkX, chunkZ, worldName, isGroup);
            inv.setItem(slot++, item);

            if (slot >= size) break; // Sécurité
        }

        player.openInventory(inv);
    }

    /**
     * Récupère toutes les entreprises d'un joueur (en tant que gérant)
     */
    private List<EntrepriseManagerLogic.Entreprise> getPlayerCompanies(Player player) {
        List<EntrepriseManagerLogic.Entreprise> companies = new ArrayList<>();

        for (EntrepriseManagerLogic.Entreprise entreprise : plugin.getEntrepriseManagerLogic().getEntreprises()) {
            String gerantUuidStr = entreprise.getGerantUUID();
            if (gerantUuidStr != null) {
                try {
                    UUID gerantUuid = UUID.fromString(gerantUuidStr);
                    if (gerantUuid.equals(player.getUniqueId())) {
                        companies.add(entreprise);
                    }
                } catch (IllegalArgumentException e) {
                    // UUID invalide, ignorer
                }
            }
        }

        return companies;
    }

    /**
     * Crée l'item représentant une entreprise dans le GUI
     */
    private ItemStack createCompanyItem(EntrepriseManagerLogic.Entreprise company, int chunkX, int chunkZ, String worldName, boolean isGroup) {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + company.getNom());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + company.getType());
        lore.add(ChatColor.GRAY + "SIRET: " + ChatColor.WHITE + company.getSiret());
        lore.add(ChatColor.GRAY + "Ville: " + ChatColor.WHITE + company.getVille());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Solde: " + ChatColor.GOLD + String.format("%.2f€", company.getSolde()));
        lore.add("");
        lore.add(ChatColor.GREEN + "⬆ Cliquez pour acheter avec cette entreprise");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!event.getView().getTitle().contains("Choisir une Entreprise")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        // Extraire le SIRET du lore (ligne "SIRET: XXXXXX")
        String siret = null;
        for (String line : meta.getLore()) {
            if (ChatColor.stripColor(line).startsWith("SIRET: ")) {
                siret = ChatColor.stripColor(line).substring(7).trim();
                break;
            }
        }

        if (siret == null) {
            player.sendMessage(ChatColor.RED + "Erreur : Impossible d'extraire le SIRET.");
            player.closeInventory();
            return;
        }

        // Extraire les coordonnées du contexte (stockées dans le titre ou passées autrement)
        // Pour l'instant, on va utiliser un cache dans TownCommandHandler
        player.closeInventory();

        // Stocker le SIRET sélectionné dans le cache
        plugin.getTownCommandHandler().setSelectedCompany(player.getUniqueId(), siret);

        player.sendMessage(ChatColor.GREEN + "Entreprise sélectionnée : " + ChatColor.WHITE + ChatColor.stripColor(meta.getDisplayName()));
        player.sendMessage(ChatColor.GRAY + "Veuillez confirmer l'achat...");
    }

    /**
     * Procède à l'achat avec l'entreprise sélectionnée
     */
    private void proceedWithPurchase(Player player, String siret, int chunkX, int chunkZ, String worldName) {
        // Stocker dans le cache et déclencher la commande de confirmation
        plugin.getTownCommandHandler().setSelectedCompany(player.getUniqueId(), siret);

        // Réafficher le message de confirmation d'achat
        player.performCommand("ville:buyplot " + chunkX + " " + chunkZ + " " + worldName);
    }
}
