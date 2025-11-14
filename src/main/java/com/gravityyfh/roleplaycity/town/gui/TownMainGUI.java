package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.gui.NavigationManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TownMainGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private TownClaimsGUI claimsGUI;
    private TownBankGUI bankGUI;
    private TownPlotManagementGUI plotManagementGUI;
    private TownPoliceGUI policeGUI;
    private TownJusticeGUI justiceGUI;
    private TownCitizenFinesGUI citizenFinesGUI;
    private TownMembersGUI membersGUI;
    private TownUpgradeGUI upgradeGUI;
    private MyPropertyGUI myPropertyGUI;
    private MyCompaniesGUI myCompaniesGUI;
    private DebtManagementGUI debtManagementGUI;

    private static final String MENU_TITLE = "Menu Principal";
    private static final String CREATE_TOWN_TITLE = "Creer une Ville";
    private static final String JOIN_TOWN_TITLE = "Rejoindre une Ville";

    public TownMainGUI(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
    }

    public void setClaimsGUI(TownClaimsGUI claimsGUI) {
        this.claimsGUI = claimsGUI;
    }

    public void setBankGUI(TownBankGUI bankGUI) {
        this.bankGUI = bankGUI;
    }

    public void setPlotManagementGUI(TownPlotManagementGUI plotManagementGUI) {
        this.plotManagementGUI = plotManagementGUI;
    }

    public void setPoliceGUI(TownPoliceGUI policeGUI) {
        this.policeGUI = policeGUI;
    }

    public void setJusticeGUI(TownJusticeGUI justiceGUI) {
        this.justiceGUI = justiceGUI;
    }

    public void setCitizenFinesGUI(TownCitizenFinesGUI citizenFinesGUI) {
        this.citizenFinesGUI = citizenFinesGUI;
    }

    public void setMembersGUI(TownMembersGUI membersGUI) {
        this.membersGUI = membersGUI;
    }

    public void setUpgradeGUI(TownUpgradeGUI upgradeGUI) {
        this.upgradeGUI = upgradeGUI;
    }

    public void setMyPropertyGUI(MyPropertyGUI myPropertyGUI) {
        this.myPropertyGUI = myPropertyGUI;
    }

    public void setMyCompaniesGUI(MyCompaniesGUI myCompaniesGUI) {
        this.myCompaniesGUI = myCompaniesGUI;
    }

    public void setDebtManagementGUI(DebtManagementGUI debtManagementGUI) {
        this.debtManagementGUI = debtManagementGUI;
    }

    /**
     * Vérifie si le joueur possède ou loue des terrains dans la ville
     * ⚠️ NOUVEAU SYSTÈME : Vérifie plots individuels ET PlotGroups
     */
    private boolean hasOwnedOrRentedPlots(Player player, Town town) {
        UUID playerUuid = player.getUniqueId();

        // Vérifier plots individuels
        for (Plot plot : town.getPlots().values()) {
            if (playerUuid.equals(plot.getOwnerUuid()) || playerUuid.equals(plot.getRenterUuid())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si le joueur gère au moins une entreprise
     */
    private boolean hasCompanies(Player player) {
        List<EntrepriseManagerLogic.Entreprise> companies =
            plugin.getEntrepriseManagerLogic().getEntreprisesGereesPar(player.getName());
        return !companies.isEmpty();
    }

    /**
     * Vérifie si le joueur a des amendes impayées
     */
    private boolean hasUnpaidFines(Player player) {
        return plugin.getTownPoliceManager() != null &&
               plugin.getTownPoliceManager().hasUnpaidFines(player.getUniqueId());
    }

    /**
     * Vérifie si le joueur a des dettes dans sa ville
     */
    private boolean hasPlayerDebts(Player player, Town town) {
        return town.hasPlayerDebts(player.getUniqueId());
    }

    public void openMainMenu(Player player) {
        // FIX: Fermer l'inventaire actuel avant d'ouvrir le nouveau
        player.closeInventory();

        String townName = townManager.getPlayerTown(player.getUniqueId());

        if (townName == null) {
            // Joueur sans ville : menu de création/join
            openNoTownMenu(player);
        } else {
            // Joueur avec ville : menu principal de gestion
            openTownMenu(player, townName);
        }
    }

    private void openNoTownMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        // Créer une ville
        ItemStack createItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta createMeta = createItem.getItemMeta();
        createMeta.setDisplayName(ChatColor.GREEN + "Creer une Ville");
        List<String> createLore = new ArrayList<>();
        double cost = plugin.getConfig().getDouble("town.creation-cost", 10000.0);
        createLore.add(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + String.format("%.2f€", cost));
        createLore.add("");
        createLore.add(ChatColor.YELLOW + "Cliquez pour créer votre ville");
        createMeta.setLore(createLore);
        createItem.setItemMeta(createMeta);
        inv.setItem(11, createItem);

        // Rejoindre une ville
        ItemStack joinItem = new ItemStack(Material.OAK_DOOR);
        ItemMeta joinMeta = joinItem.getItemMeta();
        joinMeta.setDisplayName(ChatColor.AQUA + "Rejoindre une Ville");
        List<String> joinLore = new ArrayList<>();
        double joinCost = plugin.getConfig().getDouble("town.join-cost", 100.0);
        joinLore.add(ChatColor.GRAY + "Frais de dossier: " + ChatColor.GOLD + String.format("%.2f€", joinCost));
        joinLore.add("");
        joinLore.add(ChatColor.YELLOW + "Cliquez pour voir les villes disponibles");
        joinMeta.setLore(joinLore);
        joinItem.setItemMeta(joinMeta);
        inv.setItem(15, joinItem);

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs de packet)
        player.openInventory(inv);
    }

    /**
     * Crée un glass pane décoratif pour séparer visuellement les sections
     */
    private ItemStack createDecorativePane(Material material, String name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(name);
        pane.setItemMeta(meta);
        return pane;
    }

    /**
     * Crée un en-tête de section visuel
     */
    private ItemStack createSectionHeader(Material material, String title, ChatColor color) {
        ItemStack header = new ItemStack(material);
        ItemMeta meta = header.getItemMeta();
        meta.setDisplayName(color + "⬛ " + title + " ⬛");
        header.setItemMeta(meta);
        return header;
    }

    private void openTownMenu(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "Menu Ville");

        TownRole role = town.getMemberRole(player.getUniqueId());
        TownMember member = town.getMember(player.getUniqueId());
        boolean isAdmin = (role == TownRole.MAIRE || role == TownRole.ADJOINT);

        // ═══════════════════════════════════════════════════════════
        // BORDURE SUPÉRIEURE (Ligne 0)
        // ═══════════════════════════════════════════════════════════
        ItemStack borderPane = createDecorativePane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderPane);
        }

        // ═══════════════════════════════════════════════════════════
        // EN-TÊTE - INFORMATIONS DE LA VILLE (Ligne 1)
        // ═══════════════════════════════════════════════════════════
        // Séparateurs dorés autour de l'info
        ItemStack goldPane = createDecorativePane(Material.YELLOW_STAINED_GLASS_PANE, " ");
        inv.setItem(11, goldPane);
        inv.setItem(12, goldPane);
        inv.setItem(14, goldPane);
        inv.setItem(15, goldPane);

        // Informations de la ville (slot 13 - CENTRÉ)
        ItemStack infoItem = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "✦ " + townName.toUpperCase() + " ✦");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "▪ Description: " + ChatColor.WHITE + town.getDescription());
        infoLore.add(ChatColor.GRAY + "▪ Membres: " + ChatColor.AQUA + town.getMemberCount());
        infoLore.add(ChatColor.GRAY + "▪ Parcelles: " + ChatColor.GREEN + town.getRealChunkCount());
        infoLore.add(ChatColor.GRAY + "▪ Banque: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));
        infoLore.add("");

        // Afficher tous les rôles du joueur
        Set<TownRole> playerRoles = member != null ? member.getRoles() : new HashSet<>();
        if (playerRoles.size() == 1) {
            infoLore.add(ChatColor.GRAY + "Votre rôle: " + ChatColor.LIGHT_PURPLE + "⚜ " + role.getDisplayName());
        } else {
            infoLore.add(ChatColor.GRAY + "Vos rôles:");
            for (TownRole townRole : playerRoles) {
                infoLore.add(ChatColor.LIGHT_PURPLE + "  ⚜ " + townRole.getDisplayName());
            }
        }
        infoLore.add("");
        infoLore.add(ChatColor.YELLOW + "▶ Cliquez pour plus de détails");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(13, infoItem);

        // ═══════════════════════════════════════════════════════════
        // SECTION PERSONNELLE (Ligne 2) - Thème VERT
        // ═══════════════════════════════════════════════════════════
        // Séparateurs verts
        ItemStack greenPane = createDecorativePane(Material.LIME_STAINED_GLASS_PANE, " ");
        inv.setItem(18, greenPane);
        inv.setItem(26, greenPane);

        // Mes Propriétés (slot 20 - CENTRÉ)
        boolean hasProperties = hasOwnedOrRentedPlots(player, town);
        ItemStack propertyItem = new ItemStack(hasProperties ? Material.EMERALD : Material.GRAY_DYE);
        ItemMeta propertyMeta = propertyItem.getItemMeta();
        propertyMeta.setDisplayName((hasProperties ? ChatColor.GREEN + "💰 " : ChatColor.GRAY + "💰 ") + "Mes Proprietes");
        List<String> propertyLore = new ArrayList<>();
        if (hasProperties) {
            propertyLore.add(ChatColor.GRAY + "Gérer vos terrains");
            propertyLore.add(ChatColor.GRAY + "Terrains possédés et loués");
            propertyLore.add("");
            propertyLore.add(ChatColor.YELLOW + "▶ Cliquez pour accéder");
        } else {
            propertyLore.add(ChatColor.DARK_GRAY + "Aucune propriété actuellement");
            propertyLore.add("");
            propertyLore.add(ChatColor.GRAY + "Contactez le maire pour acheter");
        }
        propertyMeta.setLore(propertyLore);
        propertyItem.setItemMeta(propertyMeta);
        inv.setItem(20, propertyItem);

        // Mes Dettes (slot 22 - CENTRÉ)
        boolean hasDebts = hasPlayerDebts(player, town);
        ItemStack debtsItem = new ItemStack(hasDebts ? Material.REDSTONE_BLOCK : Material.LIME_DYE);
        ItemMeta debtsMeta = debtsItem.getItemMeta();
        debtsMeta.setDisplayName((hasDebts ? ChatColor.RED + "⚠ " : ChatColor.GREEN + "✓ ") + "Mes Dettes");
        List<String> debtsLore = new ArrayList<>();

        if (hasDebts) {
            double totalDebt = town.getTotalPlayerDebt(player.getUniqueId());
            int debtCount = town.getPlayerDebts(player.getUniqueId()).size();
            debtsLore.add(ChatColor.RED + "⚠ " + debtCount + " dette(s) impayée(s) !");
            debtsLore.add(ChatColor.GRAY + "Total: " + ChatColor.GOLD + String.format("%.2f€", totalDebt));
            debtsLore.add("");
            debtsLore.add(ChatColor.YELLOW + "▶ Cliquez pour gérer");
        } else {
            debtsLore.add(ChatColor.GREEN + "✓ Aucune dette");
            debtsLore.add("");
            debtsLore.add(ChatColor.GRAY + "Situation financière saine");
        }
        debtsMeta.setLore(debtsLore);
        debtsItem.setItemMeta(debtsMeta);
        inv.setItem(22, debtsItem);

        // Mes Amendes (slot 24 - CENTRÉ - si amendes)
        if (hasUnpaidFines(player)) {
            ItemStack finesItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta finesMeta = finesItem.getItemMeta();
            finesMeta.setDisplayName(ChatColor.RED + "⚖ Mes Amendes");
            List<String> finesLore = new ArrayList<>();
            finesLore.add(ChatColor.RED + "Amendes impayées !");
            finesLore.add("");
            finesLore.add(ChatColor.YELLOW + "▶ Cliquez pour consulter");
            finesMeta.setLore(finesLore);
            finesItem.setItemMeta(finesMeta);
            inv.setItem(24, finesItem);
        }

        // ═══════════════════════════════════════════════════════════
        // SECTION VILLE (Ligne 3) - Thème BLEU/CYAN
        // ═══════════════════════════════════════════════════════════
        // Séparateurs cyan
        ItemStack cyanPane = createDecorativePane(Material.CYAN_STAINED_GLASS_PANE, " ");
        inv.setItem(27, cyanPane);
        inv.setItem(35, cyanPane);

        // Membres et Rôles (slot 29 - CENTRÉ)
        ItemStack membersItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersItem.getItemMeta();
        membersMeta.setDisplayName(ChatColor.AQUA + "👥 Membres et Roles");
        List<String> membersLore = new ArrayList<>();
        membersLore.add(ChatColor.GRAY + "▪ Total: " + ChatColor.WHITE + town.getMemberCount() + " citoyens");
        if (isAdmin) {
            membersLore.add(ChatColor.GRAY + "▪ Inviter / Exclure");
        }
        if (role == TownRole.MAIRE) {
            membersLore.add(ChatColor.GRAY + "▪ Gérer les rôles");
        }
        membersLore.add("");
        membersLore.add(ChatColor.YELLOW + "▶ Cliquez pour gérer");
        membersMeta.setLore(membersLore);
        membersItem.setItemMeta(membersMeta);
        inv.setItem(29, membersItem);

        // Banque Municipale (slot 31 - CENTRÉ)
        ItemStack bankItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta bankMeta = bankItem.getItemMeta();
        bankMeta.setDisplayName(ChatColor.GOLD + "💰 Banque Municipale");
        List<String> bankLore = new ArrayList<>();
        bankLore.add(ChatColor.GRAY + "Solde: " + ChatColor.GOLD + "" + ChatColor.BOLD + String.format("%.2f€", town.getBankBalance()));
        bankLore.add("");
        bankLore.add(ChatColor.GRAY + "▪ Déposer / Retirer");
        bankLore.add(ChatColor.GRAY + "▪ Historique des transactions");
        bankLore.add(ChatColor.GRAY + "▪ Collecter les taxes");
        bankLore.add("");
        bankLore.add(ChatColor.YELLOW + "▶ Cliquez pour accéder");
        bankMeta.setLore(bankLore);
        bankItem.setItemMeta(bankMeta);
        inv.setItem(31, bankItem);

        // Règlements (slot 33 - CENTRÉ)
        ItemStack rulesItem = new ItemStack(Material.BOOK);
        ItemMeta rulesMeta = rulesItem.getItemMeta();
        rulesMeta.setDisplayName(ChatColor.WHITE + "📜 Reglements");
        List<String> rulesLore = new ArrayList<>();
        rulesLore.add(ChatColor.GRAY + "Consulter les règles");
        rulesLore.add(ChatColor.GRAY + "et lois municipales");
        rulesLore.add("");
        rulesLore.add(ChatColor.YELLOW + "▶ Cliquez pour lire");
        rulesMeta.setLore(rulesLore);
        rulesItem.setItemMeta(rulesMeta);
        inv.setItem(33, rulesItem);

        // ═══════════════════════════════════════════════════════════
        // SECTION ADMINISTRATION (Ligne 4) - Thème VIOLET/MAGENTA
        // ═══════════════════════════════════════════════════════════
        // Séparateurs magenta
        ItemStack magentaPane = createDecorativePane(Material.MAGENTA_STAINED_GLASS_PANE, " ");
        inv.setItem(36, magentaPane);
        inv.setItem(44, magentaPane);

        // Compter le nombre d'items admin pour centrer dynamiquement
        int adminItemCount = 0;
        if (role == TownRole.MAIRE) adminItemCount++;
        if (isAdmin) adminItemCount++;
        if (isAdmin || role == TownRole.ARCHITECTE) adminItemCount++;
        boolean canAccessServices = (role == TownRole.POLICIER || role == TownRole.JUGE || isAdmin || hasUnpaidFines(player));
        if (canAccessServices) adminItemCount++;

        // Calculer le slot de départ pour centrer les items (centre ligne 4 = slot 40)
        int startSlot = 40 - (adminItemCount / 2);
        if (adminItemCount % 2 == 0) startSlot--; // Ajuster pour nombre pair
        int adminSlotCounter = startSlot;

        // Améliorer la Ville (Maire uniquement)
        if (role == TownRole.MAIRE) {
            com.gravityyfh.roleplaycity.town.data.TownLevel currentLevel = town.getLevel();
            com.gravityyfh.roleplaycity.town.data.TownLevel nextLevel = currentLevel.getNextLevel();

            ItemStack upgradeItem = new ItemStack(nextLevel != null ? Material.NETHER_STAR : Material.BEACON);
            ItemMeta upgradeMeta = upgradeItem.getItemMeta();
            upgradeMeta.setDisplayName(ChatColor.GOLD + "⭐ Ameliorer la Ville");
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
            inv.setItem(adminSlotCounter++, upgradeItem);
        }

        // Gestion de la Ville (Maire/Adjoint uniquement)
        if (isAdmin) {
            ItemStack manageItem = new ItemStack(Material.COMMAND_BLOCK);
            ItemMeta manageMeta = manageItem.getItemMeta();
            manageMeta.setDisplayName(ChatColor.RED + "⚙ Gestion de la Ville");
            List<String> manageLore = new ArrayList<>();
            manageLore.add(ChatColor.GRAY + "▪ Modifier le nom");
            manageLore.add(ChatColor.GRAY + "▪ Changer la description");
            if (role == TownRole.MAIRE) {
                manageLore.add(ChatColor.RED + "▪ Supprimer la ville");
            }
            manageLore.add("");
            manageLore.add(ChatColor.YELLOW + "▶ Cliquez pour gérer");
            manageMeta.setLore(manageLore);
            manageItem.setItemMeta(manageMeta);
            inv.setItem(adminSlotCounter++, manageItem);
        }

        // Claims et Terrains (Maire/Adjoint/Architecte)
        if (isAdmin || role == TownRole.ARCHITECTE) {
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
            inv.setItem(adminSlotCounter++, claimsItem);
        }

        // Services Municipaux (selon rôles)
        if (canAccessServices) {
            ItemStack servicesItem = new ItemStack(Material.IRON_BARS);
            ItemMeta servicesMeta = servicesItem.getItemMeta();
            servicesMeta.setDisplayName(ChatColor.BLUE + "🏛 Services Municipaux");
            List<String> servicesLore = new ArrayList<>();
            if (role == TownRole.POLICIER || isAdmin) {
                servicesLore.add(ChatColor.GRAY + "▪ Police");
            }
            if (role == TownRole.JUGE || isAdmin) {
                servicesLore.add(ChatColor.GRAY + "▪ Justice");
            }
            servicesLore.add(ChatColor.GRAY + "▪ Vos amendes");
            servicesLore.add("");
            servicesLore.add(ChatColor.YELLOW + "▶ Cliquez pour accéder");
            servicesMeta.setLore(servicesLore);
            servicesItem.setItemMeta(servicesMeta);
            inv.setItem(adminSlotCounter++, servicesItem);
        }

        // ═══════════════════════════════════════════════════════════
        // BORDURE INFÉRIEURE ET NAVIGATION (Ligne 5) - CENTRÉ
        // ═══════════════════════════════════════════════════════════
        // Bordure noire
        for (int i = 45; i <= 53; i++) {
            if (i != 47 && i != 49 && i != 51) {  // Réserver 47, 49, 51 pour les boutons centrés
                inv.setItem(i, borderPane);
            }
        }

        // Quitter la ville (slot 47 - CENTRÉ À GAUCHE - sauf Maire)
        if (!town.isMayor(player.getUniqueId())) {
            ItemStack leaveItem = new ItemStack(Material.OAK_DOOR);
            ItemMeta leaveMeta = leaveItem.getItemMeta();
            leaveMeta.setDisplayName(ChatColor.RED + "🚪 Quitter");
            List<String> leaveLore = new ArrayList<>();
            leaveLore.add(ChatColor.GRAY + "Quitter " + townName);
            leaveLore.add("");
            leaveLore.add(ChatColor.RED + "⚠ Vous perdrez vos");
            leaveLore.add(ChatColor.RED + "parcelles PARTICULIER");
            leaveLore.add("");
            leaveLore.add(ChatColor.YELLOW + "▶ Cliquez pour quitter");
            leaveMeta.setLore(leaveLore);
            leaveItem.setItemMeta(leaveMeta);
            inv.setItem(47, leaveItem);
        } else {
            inv.setItem(47, borderPane);
        }

        // Info centrale (slot 49 - CENTRE PARFAIT)
        ItemStack centralInfo = new ItemStack(Material.COMPASS);
        ItemMeta centralMeta = centralInfo.getItemMeta();
        centralMeta.setDisplayName(ChatColor.GOLD + "✦ " + townName + " ✦");
        List<String> centralLore = new ArrayList<>();
        centralLore.add(ChatColor.GRAY + "Niveau: " + ChatColor.AQUA + town.getLevel().getDisplayName());
        centralLore.add(ChatColor.GRAY + "Citoyens: " + ChatColor.WHITE + town.getMemberCount());
        centralMeta.setLore(centralLore);
        centralInfo.setItemMeta(centralMeta);
        inv.setItem(49, centralInfo);

        // Fermer (slot 51 - CENTRÉ À DROITE)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        List<String> closeLore = new ArrayList<>();
        closeLore.add(ChatColor.GRAY + "Fermer le menu");
        closeMeta.setLore(closeLore);
        closeItem.setItemMeta(closeMeta);
        inv.setItem(51, closeItem);

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs de packet)
        player.openInventory(inv);
    }

    private void openServicesMenu(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 27, "Services Municipaux");

        // Mes amendes (tous les citoyens)
        ItemStack myFinesItem = new ItemStack(Material.PAPER);
        ItemMeta myFinesMeta = myFinesItem.getItemMeta();
        myFinesMeta.setDisplayName(ChatColor.YELLOW + "Mes Amendes");
        List<String> myFinesLore = new ArrayList<>();
        myFinesLore.add(ChatColor.GRAY + "Voir mes amendes");
        myFinesLore.add(ChatColor.GRAY + "Payer ou contester");
        myFinesLore.add("");
        myFinesLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
        myFinesMeta.setLore(myFinesLore);
        myFinesItem.setItemMeta(myFinesMeta);
        inv.setItem(11, myFinesItem);

        // Police (Policier, Adjoint, Maire)
        if (role == TownRole.POLICIER || role == TownRole.ADJOINT || role == TownRole.MAIRE) {
            ItemStack policeItem = new ItemStack(Material.IRON_CHESTPLATE);
            ItemMeta policeMeta = policeItem.getItemMeta();
            policeMeta.setDisplayName(ChatColor.DARK_BLUE + "Police Municipale");
            List<String> policeLore = new ArrayList<>();
            policeLore.add(ChatColor.GRAY + "Émettre des amendes");
            policeLore.add(ChatColor.GRAY + "Voir les amendes actives");
            policeLore.add("");
            policeLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
            policeMeta.setLore(policeLore);
            policeItem.setItemMeta(policeMeta);
            inv.setItem(13, policeItem);
        }

        // Justice (Juge, Maire)
        if (role == TownRole.JUGE || role == TownRole.MAIRE) {
            ItemStack justiceItem = new ItemStack(Material.GOLDEN_SWORD);
            ItemMeta justiceMeta = justiceItem.getItemMeta();
            justiceMeta.setDisplayName(ChatColor.DARK_PURPLE + "Justice Municipale");
            List<String> justiceLore = new ArrayList<>();
            justiceLore.add(ChatColor.GRAY + "Juger les contestations");
            justiceLore.add(ChatColor.GRAY + "Voir les affaires en cours");
            justiceLore.add("");
            justiceLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
            justiceMeta.setLore(justiceLore);
            justiceItem.setItemMeta(justiceMeta);
            inv.setItem(15, justiceItem);
        }

        // Retour (slot 26 pour standardiser à droite)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(26, backItem);

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs de packet)
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        plugin.getLogger().info("[DEBUG TownMainGUI] onInventoryClick called! Title: '" + title + "'");

        // Exclure les GUIs gérés par d'autres classes
        if (title.contains("Évolution de votre Ville")) {
            return; // Géré par TownUpgradeGUI
        }

        // Exclure les GUIs du système de prison
        if (title.contains("Prison") || title.contains("Prisonnier") || title.contains("Menotté") ||
            title.contains("⛓️") || title.contains("👤") || title.contains("⏱") || title.contains("⚙️")) {
            return; // Géré par ImprisonmentWorkflowGUI et TownPrisonManagementGUI
        }

        if (!title.contains("🏙️") && !title.contains("Ville") && !title.contains("Services Municipaux") &&
            !title.equals(ChatColor.stripColor(JOIN_TOWN_TITLE)) && !title.contains("Gestion -") &&
            !title.equals(MENU_TITLE) && !title.equals(CREATE_TOWN_TITLE)) {
            plugin.getLogger().info("[DEBUG TownMainGUI] Title check FAILED - returning");
            return;
        }
        plugin.getLogger().info("[DEBUG TownMainGUI] Title check PASSED - processing click");

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String displayName = clicked.getItemMeta().getDisplayName();
        String strippedName = ChatColor.stripColor(displayName);
        plugin.getLogger().info("[DEBUG TownMainGUI] Item clicked: '" + strippedName + "' in inventory: '" + title + "'");

        // Menu "Rejoindre une Ville"
        if (title.contains("Rejoindre une Ville")) {
            if (strippedName.contains("Retour")) {
                player.closeInventory();
                openMainMenu(player);
                return;
            }

            // Le joueur clique sur une ville pour la rejoindre
            if (clicked.getType() == Material.GRASS_BLOCK) {
                player.closeInventory();
                handleJoinTown(player, strippedName);
                return;
            }
        }

        // Gérer les actions des boutons
        if (strippedName.contains("Mes Proprietes")) {
            plugin.getLogger().info("[DEBUG TownMainGUI] 'Mes Proprietes' clicked! Stripped name: '" + strippedName + "'");
            player.closeInventory();
            // FIX P3.2: Vérifier si l'item est grisé (vide)
            if (clicked.getType() == Material.GRAY_CONCRETE) {
                NavigationManager.sendInfo(player, "AUCUNE PROPRIÉTÉ", "Vous ne possédez actuellement aucun terrain.");
                return;
            }
            String currentTownName = townManager.getPlayerTown(player.getUniqueId());
            plugin.getLogger().info("[DEBUG TownMainGUI] myPropertyGUI is null: " + (myPropertyGUI == null) + ", townName: " + currentTownName);
            if (myPropertyGUI != null && currentTownName != null) {
                plugin.getLogger().info("[DEBUG TownMainGUI] Opening property menu. myPropertyGUI instance: " + System.identityHashCode(myPropertyGUI));
                myPropertyGUI.openPropertyMenu(player, currentTownName);
            } else {
                NavigationManager.sendError(player, "Le système de propriétés n'est pas disponible.");
            }
        } else if (strippedName.contains("Claims et Terrains")) {
            player.closeInventory();
            if (claimsGUI != null) {
                claimsGUI.openClaimsMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système de claims n'est pas disponible.");
            }
        } else if (strippedName.contains("Banque Municipale")) {
            player.closeInventory();
            if (bankGUI != null) {
                bankGUI.openBankMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système de banque n'est pas disponible.");
            }
        } else if (strippedName.contains("Services Municipaux")) {
            player.closeInventory();
            openServicesMenu(player);
        } else if (strippedName.contains("Mes Amendes")) {
            player.closeInventory();
            if (citizenFinesGUI != null) {
                citizenFinesGUI.openFinesMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système d'amendes n'est pas disponible.");
            }
        } else if (strippedName.contains("Mes Dettes")) {
            player.closeInventory();
            // Vérifier si l'item est grisé (aucune dette)
            if (clicked.getType() == Material.LIME_DYE) {
                NavigationManager.sendInfo(player, "AUCUNE DETTE", "Vous n'avez aucune dette impayée. Parfait !");
                return;
            }
            if (debtManagementGUI != null) {
                String currentTownName = townManager.getPlayerTown(player.getUniqueId());
                if (currentTownName != null) {
                    debtManagementGUI.openDebtMenu(player, currentTownName);
                } else {
                    NavigationManager.sendError(player, "Vous n'êtes pas membre d'une ville.");
                }
            } else {
                NavigationManager.sendError(player, "Le système de gestion des dettes n'est pas disponible.");
            }
        } else if (strippedName.contains("Police Municipale")) {
            player.closeInventory();
            if (policeGUI != null) {
                policeGUI.openPoliceMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système de police n'est pas disponible.");
            }
        } else if (strippedName.contains("Justice Municipale")) {
            player.closeInventory();
            if (justiceGUI != null) {
                justiceGUI.openJusticeMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système de justice n'est pas disponible.");
            }
        } else if (strippedName.contains("Gestion de la Ville")) {
            player.closeInventory();
            String currentTownName = townManager.getPlayerTown(player.getUniqueId());
            if (currentTownName != null) {
                openManageTownMenu(player, currentTownName);
            }
        } else if (strippedName.contains("Informations de la Ville")) {
            player.closeInventory();
            showTownInfo(player);
        } else if (strippedName.contains("Ameliorer la Ville")) {
            player.closeInventory();
            if (upgradeGUI != null) {
                upgradeGUI.openUpgradeMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système d'upgrade n'est pas disponible.");
            }
        } else if (strippedName.contains("Membres et Roles")) {
            player.closeInventory();
            if (membersGUI != null) {
                membersGUI.openMembersMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système de membres n'est pas disponible.");
            }
        } else if (strippedName.contains("Reglements")) {
            player.closeInventory();
            handleShowRules(player);
        } else if (strippedName.contains("Quitter la Ville")) {
            player.closeInventory();
            handleLeaveTown(player);
        } else if (strippedName.contains("Retour")) {
            player.closeInventory();
            openMainMenu(player);
        } else if (strippedName.contains("Creer une Ville")) {
            player.closeInventory();
            handleCreateTown(player);
        } else if (strippedName.contains("Rejoindre une Ville")) {
            player.closeInventory();
            openJoinTownMenu(player);
        } else if (strippedName.contains("Renommer la ville")) {
            player.closeInventory();
            handleRenameTown(player);
        } else if (strippedName.contains("Supprimer la ville")) {
            player.closeInventory();
            handleDeleteTown(player);
        } else if (strippedName.contains("Fermer")) {
            player.closeInventory();
        } else {
            // Fonctionnalités à implémenter dans les prochaines sessions
            NavigationManager.sendInfo(player, "EN DÉVELOPPEMENT", "Fonctionnalité en développement: " + strippedName);
            player.closeInventory();
        }
    }

    private void showTownInfo(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            NavigationManager.sendError(player, "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            NavigationManager.sendError(player, "Erreur: Ville introuvable.");
            return;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());

        // Récupérer le nom du maire via le membre
        String mayorName = "Inconnu";
        if (town.getMember(town.getMayorUuid()) != null) {
            mayorName = town.getMember(town.getMayorUuid()).getPlayerName();
        }

        List<String> info = new ArrayList<>();
        info.add("+Informations générales");
        info.add("");
        info.add("Nom: " + townName);
        info.add("Description: " + town.getDescription());
        info.add("Maire: " + mayorName);
        info.add("Membres: " + town.getMemberCount());
        info.add("Parcelles: " + town.getRealChunkCount());
        info.add("Banque: " + String.format("%.2f€", town.getBankBalance()));
        info.add("");

        // Afficher tous les rôles du joueur
        TownMember member = town.getMember(player.getUniqueId());
        if (member != null) {
            Set<TownRole> playerRoles = member.getRoles();
            if (playerRoles.size() == 1) {
                info.add("+Votre rôle: " + role.getDisplayName());
            } else {
                info.add("+Vos rôles:");
                // FIX BASSE #16: Renamed 'r' → 'townRole' for clarity
                for (TownRole townRole : playerRoles) {
                    info.add("*" + townRole.getDisplayName());
                }
            }
        }

        NavigationManager.sendStyledMessage(player, "📊 " + townName.toUpperCase(), info);
    }

    private void handleShowRules(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            NavigationManager.sendError(player, "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            NavigationManager.sendError(player, "Erreur: Ville introuvable.");
            return;
        }

        List<String> rules = new ArrayList<>();
        rules.add("+Règles générales:");
        rules.add("");
        rules.add("*Respecter les autres citoyens");
        rules.add("*Ne pas griffer les propriétés d'autrui");
        rules.add("*Payer ses taxes et amendes");
        rules.add("*Suivre les instructions des autorités");
        rules.add("");
        rules.add("+Description de la ville:");
        rules.add(town.getDescription());

        NavigationManager.sendStyledMessage(player, "📜 RÈGLEMENTS DE " + townName.toUpperCase(), rules);
    }

    private void handleLeaveTown(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            NavigationManager.sendError(player, "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            NavigationManager.sendError(player, "Erreur: Ville introuvable.");
            return;
        }

        if (town.isMayor(player.getUniqueId())) {
            NavigationManager.sendError(player, "Le maire ne peut pas quitter la ville. Vous devez d'abord supprimer la ville ou transférer la mairie.");
            return;
        }

        if (townManager.leaveTown(player)) {
            NavigationManager.sendSuccess(player, "Vous avez quitté la ville " + townName + ".");
        } else {
            NavigationManager.sendError(player, "Impossible de quitter la ville.");
        }
    }

    public TownManager getTownManager() {
        return townManager;
    }

    private void handleCreateTown(Player player) {
        // Demander le nom de la ville via le chat
        double cost = plugin.getConfig().getDouble("town.creation-cost", 10000.0);
        NavigationManager.sendStyledMessage(player, "🏙 CRÉATION D'UNE VILLE", Arrays.asList(
            "Tapez le nom de votre ville dans le chat",
            "",
            "Coût: " + String.format("%.2f€", cost),
            "",
            "*Le nom doit être unique",
            "*Vous deviendrez le maire de la ville",
            "",
            "Tapez 'annuler' pour annuler"
        ));

        // Enregistrer le joueur comme "en attente de saisie"
        plugin.getChatListener().waitForInput(player.getUniqueId(), (input) -> {
            if (input.equalsIgnoreCase("annuler")) {
                NavigationManager.sendInfo(player, "OPÉRATION ANNULÉE", "Création de ville annulée.");
                return;
            }

            // Créer la ville
            if (townManager.createTown(input, player, cost)) {
                NavigationManager.sendStyledMessage(player, "✓ VILLE CRÉÉE AVEC SUCCÈS", Arrays.asList(
                    "+La ville '" + input + "' a été créée !",
                    "",
                    "*Vous êtes maintenant le maire de cette ville",
                    "*Vous pouvez gérer votre ville dans le menu"
                ));

                // Ouvrir le menu de la ville
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
            } else {
                NavigationManager.sendStyledMessage(player, "⚠ ERREUR DE CRÉATION", Arrays.asList(
                    "!Impossible de créer la ville",
                    "",
                    "Vérifiez:",
                    "*Que vous avez " + String.format("%.2f€", cost),
                    "*Que le nom n'est pas déjà pris",
                    "*Que vous n'êtes pas déjà dans une ville"
                ));
            }
        });
    }

    private void openJoinTownMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, JOIN_TOWN_TITLE);

        List<String> towns = townManager.getTownNames();
        int slot = 0;

        for (String townName : towns) {
            if (slot >= 45) break; // Max 45 villes affichées

            Town town = townManager.getTown(townName);
            if (town == null) continue;

            ItemStack townItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = townItem.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + townName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Description: " + ChatColor.WHITE + town.getDescription());
            lore.add(ChatColor.GRAY + "Membres: " + ChatColor.WHITE + town.getMemberCount());
            lore.add(ChatColor.GRAY + "Parcelles: " + ChatColor.WHITE + town.getRealChunkCount());
            lore.add("");

            double joinCost = plugin.getConfig().getDouble("town.join-cost", 100.0);
            lore.add(ChatColor.YELLOW + "Frais de dossier: " + ChatColor.GOLD + String.format("%.2f€", joinCost));
            lore.add("");
            lore.add(ChatColor.AQUA + "Cliquez pour rejoindre cette ville");

            meta.setLore(lore);
            townItem.setItemMeta(meta);
            inv.setItem(slot, townItem);
            slot++;
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs de packet)
        player.openInventory(inv);
    }

    private void handleJoinTown(Player player, String townName) {
        double joinCost = plugin.getConfig().getDouble("town.join-cost", 100.0);

        if (townManager.joinTown(player, townName, joinCost)) {
            NavigationManager.sendSuccess(player, "Vous avez rejoint la ville " + townName + " !");
            Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
        } else {
            NavigationManager.sendStyledMessage(player, "⚠ ERREUR", Arrays.asList(
                "!Impossible de rejoindre la ville",
                "",
                "Vérifiez:",
                "*Que vous avez " + String.format("%.2f€", joinCost),
                "*Que vous n'êtes pas déjà dans une ville"
            ));
        }
    }

    private void handleRenameTown(Player player) {
        String oldTownName = townManager.getPlayerTown(player.getUniqueId());
        if (oldTownName == null) {
            NavigationManager.sendError(player, "Vous n'êtes dans aucune ville.");
            return;
        }

        double renameCost = plugin.getConfig().getDouble("town.rename-cost", 5000.0);
        NavigationManager.sendStyledMessage(player, "✏ RENOMMER LA VILLE", Arrays.asList(
            "Tapez le nouveau nom dans le chat",
            "",
            "Nom actuel: " + oldTownName,
            "Coût: " + String.format("%.2f€", renameCost),
            "",
            "Tapez 'annuler' pour annuler"
        ));

        plugin.getChatListener().waitForInput(player.getUniqueId(), (input) -> {
            if (input.equalsIgnoreCase("annuler")) {
                NavigationManager.sendInfo(player, "OPÉRATION ANNULÉE", "Renommage annulé.");
                return;
            }

            if (townManager.renameTown(oldTownName, input, renameCost)) {
                NavigationManager.sendSuccess(player, "Ville renommée en '" + input + "' avec succès !");
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
            } else {
                NavigationManager.sendStyledMessage(player, "⚠ ERREUR", Arrays.asList(
                    "!Impossible de renommer la ville",
                    "",
                    "Vérifiez:",
                    "*Que la ville a " + String.format("%.2f€", renameCost) + " en banque",
                    "*Que le nom n'est pas déjà pris"
                ));
            }
        });
    }

    private void handleDeleteTown(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            NavigationManager.sendError(player, "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null || !town.isMayor(player.getUniqueId())) {
            NavigationManager.sendError(player, "Seul le maire peut supprimer la ville.");
            return;
        }

        NavigationManager.sendStyledMessage(player, "⚠ SUPPRESSION DE VILLE", Arrays.asList(
            "!ATTENTION: Cette action est IRRÉVERSIBLE !",
            "",
            "Ville à supprimer: " + townName,
            "",
            "*Tous les membres seront expulsés",
            "*Toutes les parcelles seront libérées",
            "*La banque de la ville sera perdue",
            "",
            "Tapez le nom de la ville pour confirmer:",
            "→ " + townName,
            "",
            "Tapez 'annuler' pour annuler"
        ));

        plugin.getChatListener().waitForInput(player.getUniqueId(), (input) -> {
            if (input.equalsIgnoreCase("annuler")) {
                NavigationManager.sendInfo(player, "OPÉRATION ANNULÉE", "Suppression annulée.");
                return;
            }

            if (!input.equals(townName)) {
                NavigationManager.sendError(player, "Le nom ne correspond pas. Suppression annulée.");
                return;
            }

            if (townManager.deleteTown(townName)) {
                NavigationManager.sendSuccess(player, "La ville " + townName + " a été supprimée.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
            } else {
                NavigationManager.sendError(player, "Impossible de supprimer la ville.");
            }
        });
    }

    private void openManageTownMenu(Player player, String townName) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Gestion - " + townName);

        Town town = townManager.getTown(townName);
        if (town == null) {
            NavigationManager.sendError(player, "Erreur: Ville introuvable.");
            return;
        }

        // Renommer la ville
        ItemStack renameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameItem.getItemMeta();
        renameMeta.setDisplayName(ChatColor.YELLOW + "Renommer la ville");
        List<String> renameLore = new ArrayList<>();
        double renameCost = plugin.getConfig().getDouble("town.rename-cost", 5000.0);
        renameLore.add(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + String.format("%.2f€", renameCost));
        renameLore.add("");
        renameLore.add(ChatColor.YELLOW + "Cliquez pour renommer");
        renameMeta.setLore(renameLore);
        renameItem.setItemMeta(renameMeta);
        inv.setItem(11, renameItem);

        // Supprimer la ville (Maire uniquement)
        if (town.isMayor(player.getUniqueId())) {
            ItemStack deleteItem = new ItemStack(Material.TNT);
            ItemMeta deleteMeta = deleteItem.getItemMeta();
            deleteMeta.setDisplayName(ChatColor.RED + "Supprimer la ville");
            List<String> deleteLore = new ArrayList<>();
            deleteLore.add(ChatColor.GRAY + "ATTENTION: Action irréversible!");
            deleteLore.add(ChatColor.GRAY + "Tous les membres seront expulsés");
            deleteLore.add("");
            deleteLore.add(ChatColor.RED + "Cliquez pour supprimer");
            deleteMeta.setLore(deleteLore);
            deleteItem.setItemMeta(deleteMeta);
            inv.setItem(15, deleteItem);
        }

        // Bouton retour (slot 26 pour standardiser à droite)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(26, backItem);

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs de packet)
        player.openInventory(inv);
    }
}
