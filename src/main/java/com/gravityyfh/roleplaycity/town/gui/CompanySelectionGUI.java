package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager;
import com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager;
import com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.OperationType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI pour sélectionner une entreprise lors de l'achat ou location d'un terrain PROFESSIONNEL
 * Affiché TOUJOURS, même avec 1 seule entreprise (selon configuration utilisateur)
 */
public class CompanySelectionGUI implements Listener {

    private final RoleplayCity plugin;
    private final CompanyPlotManager companyManager;
    private EnterpriseContextManager enterpriseContextManager;

    // Contexte de sélection par joueur (pour stocker les coordonnées et le type d'opération)
    private final Map<UUID, SelectionContext> playerContexts = new HashMap<>();

    public CompanySelectionGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.companyManager = plugin.getCompanyPlotManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Injecte le EnterpriseContextManager (appelé depuis RoleplayCity.onEnable)
     */
    public void setEnterpriseContextManager(EnterpriseContextManager manager) {
        this.enterpriseContextManager = manager;
    }

    /**
     * Contexte de sélection stockant les informations de l'opération
     */
    private static class SelectionContext {
        final int chunkX;
        final int chunkZ;
        final String worldName;
        final boolean isGroup;
        final OperationType operationType;

        SelectionContext(int chunkX, int chunkZ, String worldName, boolean isGroup, OperationType operationType) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.worldName = worldName;
            this.isGroup = isGroup;
            this.operationType = operationType;
        }
    }

    /**
     * Ouvre le GUI de sélection d'entreprise pour un achat ou location de terrain
     * @param player Le joueur qui veut acheter/louer
     * @param chunkX Coordonnée X du chunk
     * @param chunkZ Coordonnée Z du chunk
     * @param worldName Nom du monde
     * @param isGroup Si c'est un achat de groupe
     * @param operationType Type d'opération (PURCHASE ou RENTAL)
     */
    public void open(Player player, int chunkX, int chunkZ, String worldName, boolean isGroup, OperationType operationType) {
        // Utiliser EnterpriseContextManager si disponible, sinon fallback
        List<EntrepriseManagerLogic.Entreprise> playerCompanies;
        if (enterpriseContextManager != null) {
            playerCompanies = enterpriseContextManager.getPlayerEnterprises(player);
        } else {
            playerCompanies = getPlayerCompanies(player);
        }

        if (playerCompanies.isEmpty()) {
            player.sendMessage(ChatColor.RED + "✗ Vous ne possédez aucune entreprise !");
            return;
        }

        // Stocker le contexte pour récupération lors du clic
        playerContexts.put(player.getUniqueId(), new SelectionContext(chunkX, chunkZ, worldName, isGroup, operationType));

        // TOUJOURS afficher le GUI, même avec 1 entreprise (selon configuration utilisateur)
        String actionName = (operationType == OperationType.PURCHASE) ? "Acheter" : "Louer";
        int size = Math.min(54, ((playerCompanies.size() + 8) / 9) * 9); // Arrondir au multiple de 9
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.GOLD + actionName + " avec quelle entreprise?");

        int slot = 0;
        for (EntrepriseManagerLogic.Entreprise company : playerCompanies) {
            ItemStack item = createCompanyItem(company, operationType);
            inv.setItem(slot++, item);

            if (slot >= size) break; // Sécurité
        }

        player.openInventory(inv);
    }

    /**
     * Version legacy pour compatibilité (PURCHASE par défaut)
     * @deprecated Utilisez open() avec OperationType
     */
    @Deprecated
    public void open(Player player, int chunkX, int chunkZ, String worldName, boolean isGroup) {
        open(player, chunkX, chunkZ, worldName, isGroup, OperationType.PURCHASE);
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
    private ItemStack createCompanyItem(EntrepriseManagerLogic.Entreprise company, OperationType operationType) {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + company.getNom());

        String actionVerb = (operationType == OperationType.PURCHASE) ? "acheter" : "louer";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + company.getType());
        lore.add(ChatColor.GRAY + "SIRET: " + ChatColor.WHITE + company.getSiret());
        lore.add(ChatColor.GRAY + "Ville: " + ChatColor.WHITE + company.getVille());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Solde: " + ChatColor.GOLD + String.format("%.2f€", company.getSolde()));
        lore.add("");
        lore.add(ChatColor.GREEN + "⬆ Cliquez pour " + actionVerb + " avec cette entreprise");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.contains("avec quelle entreprise")) return;

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
            player.sendMessage(ChatColor.RED + "✗ Erreur : Impossible d'extraire le SIRET.");
            player.closeInventory();
            return;
        }

        // Récupérer le contexte du joueur
        SelectionContext context = playerContexts.remove(player.getUniqueId());
        if (context == null) {
            // Contexte déjà traité (double-clic ou événement multiple), ignorer silencieusement
            player.closeInventory();
            return;
        }

        player.closeInventory();

        // Stocker le SIRET dans les DEUX systèmes pour compatibilité
        if (enterpriseContextManager != null) {
            enterpriseContextManager.setSelectedEnterprise(player.getUniqueId(), siret, context.operationType);
        }
        // IMPORTANT: Aussi stocker dans l'ancien cache pour compatibilité avec le système de vérification
        plugin.getTownCommandHandler().setSelectedCompany(player.getUniqueId(), siret);

        String companyName = ChatColor.stripColor(meta.getDisplayName());
        player.sendMessage(ChatColor.GREEN + "✓ Entreprise sélectionnée : " + ChatColor.WHITE + companyName);

        // Procéder avec l'opération
        proceedWithOperation(player, context);
    }

    /**
     * Procède avec l'opération sélectionnée (achat ou location)
     */
    private void proceedWithOperation(Player player, SelectionContext context) {
        if (context.operationType == OperationType.PURCHASE) {
            player.sendMessage(ChatColor.GRAY + "Veuillez confirmer l'achat...");
            player.performCommand("ville buyplot " + context.chunkX + " " + context.chunkZ + " " + context.worldName);
        } else if (context.operationType == OperationType.RENTAL) {
            player.sendMessage(ChatColor.GRAY + "Veuillez confirmer la location...");
            player.performCommand("ville rentplot " + context.chunkX + " " + context.chunkZ + " " + context.worldName);
        }
    }

    /**
     * Nettoie le contexte d'un joueur (utile lors de déconnexion)
     */
    public void clearPlayerContext(UUID playerUuid) {
        playerContexts.remove(playerUuid);
    }
}
