package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.gui.NavigationManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.data.TownTeleportCooldown;
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

    private TownPoliceGUI policeGUI;
    private TownJusticeGUI justiceGUI;

    private TownMembersGUI membersGUI;
    private TownUpgradeGUI upgradeGUI;
    private MyPropertyGUI myPropertyGUI;

    private DebtManagementGUI debtManagementGUI;
    private TownListGUI townListGUI;
    private NoTownGUI noTownGUI;
    private TownAdminGUI adminGUI;
    private TownPlotManagementGUI plotManagementGUI;
    private TownCitizenFinesGUI citizenFinesGUI;
    private MyCompaniesGUI myCompaniesGUI;
    private TownTeleportCooldown cooldownManager;

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

    public void setPoliceGUI(TownPoliceGUI policeGUI) {
        this.policeGUI = policeGUI;
    }

    public void setJusticeGUI(TownJusticeGUI justiceGUI) {
        this.justiceGUI = justiceGUI;
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

    public void setDebtManagementGUI(DebtManagementGUI debtManagementGUI) {
        this.debtManagementGUI = debtManagementGUI;
    }

    public void setTownListGUI(TownListGUI townListGUI) {
        this.townListGUI = townListGUI;
    }

    public void setNoTownGUI(NoTownGUI noTownGUI) {
        this.noTownGUI = noTownGUI;
    }

    public void setAdminGUI(TownAdminGUI adminGUI) {
        this.adminGUI = adminGUI;
    }

    public void setPlotManagementGUI(TownPlotManagementGUI plotManagementGUI) {
        this.plotManagementGUI = plotManagementGUI;
    }

    public void setCitizenFinesGUI(TownCitizenFinesGUI citizenFinesGUI) {
        this.citizenFinesGUI = citizenFinesGUI;
    }

    public void setMyCompaniesGUI(MyCompaniesGUI myCompaniesGUI) {
        this.myCompaniesGUI = myCompaniesGUI;
    }

    public void setCooldownManager(TownTeleportCooldown cooldownManager) {
        this.cooldownManager = cooldownManager;
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
            // Joueur sans ville : ouvrir le nouveau NoTownGUI
            if (noTownGUI != null) {
                noTownGUI.openNoTownMenu(player);
            } else {
                // Fallback vers l'ancien menu si NoTownGUI n'est pas initialisé
                openNoTownMenu(player);
            }
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

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs
        // de packet)
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
        boolean isArchitect = (role == TownRole.ARCHITECTE);

        // ═══════════════════════════════════════════════════════════
        // BORDURE SUPÉRIEURE (Ligne 0)
        // ═══════════════════════════════════════════════════════════
        ItemStack borderPane = createDecorativePane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderPane);
        }

        // Bouton "Villes du Serveur" (slot 8 - coin supérieur droit)
        ItemStack townListItem = new ItemStack(Material.COMPASS);
        ItemMeta townListMeta = townListItem.getItemMeta();
        townListMeta.setDisplayName(ChatColor.AQUA + "🌍 Villes du Serveur");
        List<String> townListLore = new ArrayList<>();
        townListLore.add(ChatColor.GRAY + "Explorez les villes existantes");
        townListLore.add(ChatColor.GRAY + "et téléportez-vous !");
        townListLore.add("");
        townListLore.add(ChatColor.YELLOW + "▶ Cliquez pour voir la liste");
        townListMeta.setLore(townListLore);
        townListItem.setItemMeta(townListMeta);
        inv.setItem(8, townListItem);

        // ═══════════════════════════════════════════════════════════
        // EN-TÊTE - INFORMATIONS DE LA VILLE (Ligne 1)
        // ═══════════════════════════════════════════════════════════
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
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem); // Slot 4 (Ligne 0, milieu) ou 13 (Ligne 1, milieu) ? Le plan disait Lignes 1-2.
                                  // Mettons-le en 13.
        inv.setItem(13, infoItem);

        // ═══════════════════════════════════════════════════════════
        // SECTION PERSONNELLE (Ligne 3 - Slots 18-26) - "Ma Citoyenneté"
        // ═══════════════════════════════════════════════════════════

        // Mes Propriétés (slot 20)
        boolean hasProperties = hasOwnedOrRentedPlots(player, town);
        ItemStack propertyItem = new ItemStack(hasProperties ? Material.EMERALD : Material.GRAY_DYE);
        ItemMeta propertyMeta = propertyItem.getItemMeta();
        propertyMeta
                .setDisplayName((hasProperties ? ChatColor.GREEN + "💰 " : ChatColor.GRAY + "💰 ") + "Mes Proprietes");
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

        // Mes Finances (Dettes & Amendes) (slot 22)
        boolean hasDebts = hasPlayerDebts(player, town);
        boolean hasFines = hasUnpaidFines(player);

        Material financeMat = (hasDebts || hasFines) ? Material.REDSTONE_BLOCK : Material.GOLD_NUGGET;
        ItemStack financeItem = new ItemStack(financeMat);
        ItemMeta financeMeta = financeItem.getItemMeta();
        financeMeta.setDisplayName(ChatColor.GOLD + "Mes Finances");
        List<String> financeLore = new ArrayList<>();

        if (hasDebts) {
            double totalDebt = town.getTotalPlayerDebt(player.getUniqueId());
            financeLore.add(ChatColor.RED + "⚠ " + town.getPlayerDebts(player.getUniqueId()).size() + " dette(s): "
                    + String.format("%.2f€", totalDebt));
        } else {
            financeLore.add(ChatColor.GREEN + "✓ Aucune dette");
        }

        if (hasFines) {
            financeLore.add(ChatColor.RED + "⚠ Amendes impayées !");
        } else {
            financeLore.add(ChatColor.GREEN + "✓ Casier vierge");
        }

        financeLore.add("");
        financeLore.add(ChatColor.YELLOW + "▶ Cliquez pour gérer");
        financeMeta.setLore(financeLore);
        financeItem.setItemMeta(financeMeta);
        inv.setItem(22, financeItem);

        // Mon Métier (slot 24) - Accès rapide Police/Justice si applicable
        if (role == TownRole.POLICIER) {
            ItemStack jobItem = new ItemStack(Material.IRON_CHESTPLATE);
            ItemMeta jobMeta = jobItem.getItemMeta();
            jobMeta.setDisplayName(ChatColor.DARK_BLUE + "👮 Police Municipale");
            jobMeta.setLore(
                    List.of(ChatColor.GRAY + "Accès au commissariat", "", ChatColor.YELLOW + "▶ Cliquez pour ouvrir"));
            jobItem.setItemMeta(jobMeta);
            inv.setItem(24, jobItem);
        } else if (role == TownRole.JUGE) {
            ItemStack jobItem = new ItemStack(Material.GOLDEN_SWORD);
            ItemMeta jobMeta = jobItem.getItemMeta();
            jobMeta.setDisplayName(ChatColor.DARK_PURPLE + "⚖ Justice Municipale");
            jobMeta.setLore(
                    List.of(ChatColor.GRAY + "Accès au tribunal", "", ChatColor.YELLOW + "▶ Cliquez pour ouvrir"));
            jobItem.setItemMeta(jobMeta);
            inv.setItem(24, jobItem);
        }

        // ═══════════════════════════════════════════════════════════
        // SECTION COMMUNAUTAIRE (Ligne 4 - Slots 27-35) - "Vie de la Ville"
        // ═══════════════════════════════════════════════════════════

        // Membres (slot 29)
        ItemStack membersItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersItem.getItemMeta();
        membersMeta.setDisplayName(ChatColor.AQUA + "👥 Citoyens");
        List<String> membersLore = new ArrayList<>();
        membersLore.add(ChatColor.GRAY + "Total: " + ChatColor.WHITE + town.getMemberCount());
        membersLore.add("");
        membersLore.add(ChatColor.YELLOW + "▶ Cliquez pour voir la liste");
        membersMeta.setLore(membersLore);
        membersItem.setItemMeta(membersMeta);
        inv.setItem(29, membersItem);

        // Banque Municipale (slot 31)
        ItemStack bankItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta bankMeta = bankItem.getItemMeta();
        bankMeta.setDisplayName(ChatColor.GOLD + "💰 Banque Municipale");
        List<String> bankLore = new ArrayList<>();
        bankLore.add(ChatColor.GRAY + "Solde: " + ChatColor.GOLD + ChatColor.BOLD
                + String.format("%.2f€", town.getBankBalance()));
        bankLore.add("");
        bankLore.add(ChatColor.YELLOW + "▶ Cliquez pour accéder");
        bankMeta.setLore(bankLore);
        bankItem.setItemMeta(bankMeta);
        inv.setItem(31, bankItem);

        // Services & Règlements (slot 33)
        ItemStack servicesItem = new ItemStack(Material.BOOK);
        ItemMeta servicesMeta = servicesItem.getItemMeta();
        servicesMeta.setDisplayName(ChatColor.BLUE + "🏛 Services & Lois");
        List<String> servicesLore = new ArrayList<>();
        servicesLore.add(ChatColor.GRAY + "Consulter les règles");
        servicesLore.add(ChatColor.GRAY + "et services publics");
        servicesLore.add("");
        servicesLore.add(ChatColor.YELLOW + "▶ Cliquez pour voir");
        servicesMeta.setLore(servicesLore);
        servicesItem.setItemMeta(servicesMeta);
        inv.setItem(33, servicesItem);

        // ═══════════════════════════════════════════════════════════
        // SECTION ADMINISTRATION (Ligne 5 - Slots 36-44)
        // ═══════════════════════════════════════════════════════════

        // Bouton Administration (slot 40) - Visible seulement pour le staff
        if (isAdmin || isArchitect) {
            ItemStack adminItem = new ItemStack(Material.NETHER_STAR);
            ItemMeta adminMeta = adminItem.getItemMeta();
            adminMeta.setDisplayName(ChatColor.DARK_RED + "⚙ Administration");
            List<String> adminLore = new ArrayList<>();
            adminLore.add(ChatColor.GRAY + "Gérer la ville, claims,");
            adminLore.add(ChatColor.GRAY + "améliorations et paramètres.");
            adminLore.add("");
            adminLore.add(ChatColor.YELLOW + "▶ Cliquez pour gérer");
            adminMeta.setLore(adminLore);
            adminItem.setItemMeta(adminMeta);
            inv.setItem(40, adminItem);
        }

        // ═══════════════════════════════════════════════════════════
        // PIED DE PAGE (Ligne 6 - Slots 45-53)
        // ═══════════════════════════════════════════════════════════

        // Quitter la ville (slot 47)
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
        }

        // Fermer (haut droite)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(8, closeItem);

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

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs
        // de packet)
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

        // FIX: Utiliser equals("Menu Ville") au lieu de contains("Ville") pour éviter
        // d'intercepter "🌍 Villes du Serveur" de TownListGUI
        if (!title.contains("🏙️") && !title.equals("Menu Ville") && !title.contains("Services Municipaux") &&
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
        plugin.getLogger()
                .info("[DEBUG TownMainGUI] Item clicked: '" + strippedName + "' in inventory: '" + title + "'");

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
            plugin.getLogger()
                    .info("[DEBUG TownMainGUI] 'Mes Proprietes' clicked! Stripped name: '" + strippedName + "'");
            player.closeInventory();
            // FIX P3.2: Vérifier si l'item est grisé (vide)
            if (clicked.getType() == Material.GRAY_CONCRETE) {
                NavigationManager.sendInfo(player, "AUCUNE PROPRIÉTÉ", "Vous ne possédez actuellement aucun terrain.");
                return;
            }
            String currentTownName = townManager.getPlayerTown(player.getUniqueId());
            plugin.getLogger().info("[DEBUG TownMainGUI] myPropertyGUI is null: " + (myPropertyGUI == null)
                    + ", townName: " + currentTownName);
            if (myPropertyGUI != null && currentTownName != null) {
                plugin.getLogger().info("[DEBUG TownMainGUI] Opening property menu. myPropertyGUI instance: "
                        + System.identityHashCode(myPropertyGUI));
                myPropertyGUI.openPropertyMenu(player, currentTownName);
            } else {
                NavigationManager.sendError(player, "Le système de propriétés n'est pas disponible.");
            }
        } else if (strippedName.contains("Claims et Terrains")) {
            // OBSOLETE: Déplacé dans Administration
            // Mais on garde pour compatibilité si jamais
            player.closeInventory();
            if (claimsGUI != null) {
                claimsGUI.openClaimsMenu(player);
            }
        } else if (strippedName.contains("Banque Municipale")) {
            player.closeInventory();
            if (bankGUI != null) {
                bankGUI.openBankMenu(player);
            } else {
                NavigationManager.sendError(player, "Le système de banque n'est pas disponible.");
            }
        } else if (strippedName.contains("Services & Lois")) {
            player.closeInventory();
            openServicesMenu(player);
        } else if (strippedName.contains("Mes Finances")) {
            player.closeInventory();
            // Ouvrir le menu des dettes par défaut, ou un menu combiné
            if (debtManagementGUI != null) {
                String currentTownName = townManager.getPlayerTown(player.getUniqueId());
                debtManagementGUI.openDebtMenu(player, currentTownName);
            }
        } else if (strippedName.contains("Police Municipale")) {
            player.closeInventory();
            if (policeGUI != null) {
                policeGUI.openPoliceMenu(player);
            }
        } else if (strippedName.contains("Justice Municipale")) {
            player.closeInventory();
            if (justiceGUI != null) {
                justiceGUI.openJusticeMenu(player);
            }
        } else if (strippedName.contains("Administration")) {
            player.closeInventory();
            String currentTownName = townManager.getPlayerTown(player.getUniqueId());
            if (currentTownName != null && adminGUI != null) {
                Town town = townManager.getTown(currentTownName);
                if (town != null) {
                    adminGUI.openAdminMenu(player, town);
                }
            } else {
                NavigationManager.sendError(player, "Menu administration indisponible.");
            }
        } else if (strippedName.contains("Citoyens")) {
            player.closeInventory();
            if (membersGUI != null) {
                membersGUI.openMembersMenu(player);
            }
        } else if (strippedName.contains("✦") || strippedName.contains("Informations de la Ville")) {
            // Le bouton info contient "✦ NOMVILLE ✦" (slot 13 et 49)
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
        } else if (strippedName.contains("Villes du Serveur")) {
            player.closeInventory();
            if (townListGUI != null) {
                townListGUI.openTownList(player, true); // showBackButton = true
            } else {
                NavigationManager.sendError(player, "Le système de liste des villes n'est pas disponible.");
            }
        } else if (strippedName.contains("Quitter")) {
            // Le bouton s'appelle "Quitter" (pas "Quitter la Ville")
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
            NavigationManager.sendError(player,
                    "Le maire ne peut pas quitter la ville. Vous devez d'abord supprimer la ville ou transférer la mairie.");
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
                "Tapez 'annuler' pour annuler"));

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
                        "*Vous pouvez gérer votre ville dans le menu"));

                // Ouvrir le menu de la ville
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
            } else {
                NavigationManager.sendStyledMessage(player, "⚠ ERREUR DE CRÉATION", Arrays.asList(
                        "!Impossible de créer la ville",
                        "",
                        "Vérifiez:",
                        "*Que vous avez " + String.format("%.2f€", cost),
                        "*Que le nom n'est pas déjà pris",
                        "*Que vous n'êtes pas déjà dans une ville"));
            }
        });
    }

    private void openJoinTownMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, JOIN_TOWN_TITLE);

        List<String> towns = townManager.getTownNames();
        int slot = 0;

        for (String townName : towns) {
            if (slot >= 45)
                break; // Max 45 villes affichées

            Town town = townManager.getTown(townName);
            if (town == null)
                continue;

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

        // FIX: Ouverture directe de l'inventaire (NavigationManager cause des erreurs
        // de packet)
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
                    "*Que vous n'êtes pas déjà dans une ville"));
        }
    }

}
