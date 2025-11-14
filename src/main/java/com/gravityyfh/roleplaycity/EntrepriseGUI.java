package com.gravityyfh.roleplaycity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic.DetailedActionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise;


public class EntrepriseGUI implements Listener {

    private final RoleplayCity plugin;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final Map<UUID, BukkitTask> guiUpdateTasks = new HashMap<>();

    private static class PlayerGUIContext {
        String currentMenuTitle;
        String currentEntrepriseNom;
        String selectedGerantPourCreation;
        String selectedEmployeeForManagement;
        int currentPage;
        LocalDateTime[] currentProductionPeriod;
        UUID currentViewingEmployeeStatsUUID;
        DetailedActionType currentViewingActionType;

        Stack<String> menuHistory = new Stack<>();

        public PlayerGUIContext(String initialMenuTitle) {
            this.currentMenuTitle = initialMenuTitle;
            this.menuHistory.push(initialMenuTitle);
            this.currentPage = 0;
            this.currentViewingActionType = DetailedActionType.BLOCK_BROKEN;
        }

        void navigateTo(String newMenuTitle) {
            if (menuHistory.isEmpty() || !menuHistory.peek().equals(newMenuTitle)) {
                this.menuHistory.push(newMenuTitle);
            }
            this.currentMenuTitle = newMenuTitle;
            this.currentPage = 0;
        }

        String goBack() {
            if (menuHistory.size() > 1) {
                menuHistory.pop();
                this.currentMenuTitle = menuHistory.peek();
            } else {
                this.currentMenuTitle = getMainMenuTitle();
                if (menuHistory.isEmpty() || !menuHistory.peek().equals(this.currentMenuTitle)) {
                    menuHistory.push(this.currentMenuTitle);
                }
            }
            this.currentPage = 0;
            return this.currentMenuTitle;
        }

        EntrepriseManagerLogic.Entreprise getCurrentEntreprise(EntrepriseManagerLogic logic) {
            return currentEntrepriseNom != null ? logic.getEntreprise(currentEntrepriseNom) : null;
        }
    }

    private final Map<UUID, PlayerGUIContext> playerContexts = new HashMap<>();
    private final Map<UUID, Long> clickTimestamps = new HashMap<>();
    private static final long CLICK_DELAY_MS = 500;
    private static final int ITEMS_PER_PAGE_DEFAULT = 36;
    private static final int ITEMS_PER_PAGE_MATERIALS = 45;

    private static final String TITLE_MAIN_MENU = ChatColor.DARK_BLUE + "Menu Principal Entreprises";
    private static final String TITLE_SELECT_GERANT = ChatColor.DARK_BLUE + "Sélectionner Gérant Cible";
    private static final String TITLE_SELECT_TYPE = ChatColor.DARK_BLUE + "Sélectionner Type d'Entreprise";
    private static final String TITLE_MY_ENTREPRISES = ChatColor.DARK_BLUE + "Mes Entreprises";
    private static final String TITLE_MANAGE_SPECIFIC_PREFIX = ChatColor.DARK_BLUE + "Gérer: ";
    private static final String TITLE_VIEW_SPECIFIC_PREFIX = ChatColor.BLUE + "Détails: ";
    private static final String TITLE_RECRUIT_EMPLOYEE = ChatColor.DARK_BLUE + "Recruter Employé";
    private static final String TITLE_CONFIRM_RECRUIT = ChatColor.DARK_BLUE + "Confirmer Recrutement";
    private static final String TITLE_MANAGE_EMPLOYEES = ChatColor.DARK_BLUE + "Gérer Employés";
    private static final String TITLE_EMPLOYEE_OPTIONS_PREFIX = ChatColor.DARK_BLUE + "Options pour ";
    private static final String TITLE_SET_PRIME = ChatColor.DARK_BLUE + "Définir Prime Horaire";
    private static final String TITLE_CONFIRM_DELETE = ChatColor.DARK_BLUE + "Confirmer Suppression";
    private static final String TITLE_LIST_TOWNS = ChatColor.DARK_BLUE + "Lister Entreprises par Ville";
    private static final String TITLE_ENTREPRISES_IN_TOWN_PREFIX = ChatColor.DARK_BLUE + "Entreprises à ";
    private static final String TITLE_ADMIN_MENU = ChatColor.RED + "Menu Administration";
    private static final String TITLE_CONFIRM_LEAVE_PREFIX = ChatColor.DARK_RED + "Quitter ";
    private static final String TITLE_STATS_MENU_PREFIX = ChatColor.DARK_GREEN + "Statistiques: ";
    private static final String TITLE_PROFIT_LOSS_PERIODS_PREFIX = ChatColor.AQUA + "Profit/Perte Périodes: ";
    private static final String TITLE_TRANSACTIONS_PREFIX = ChatColor.DARK_AQUA + "Transactions: ";
    private static final String TITLE_EMPLOYEE_STATS_LIST_PREFIX = ChatColor.DARK_PURPLE + "Stats Employés: ";
    private static final String TITLE_PROD_STATS_ACTION_TYPE_CHOICE_PREFIX = ChatColor.DARK_GREEN + "Stats Prod. - Type Action: ";
    private static final String TITLE_PROD_STATS_PERIODS_PREFIX = ChatColor.DARK_GREEN + "Stats Prod. Périodes: ";
    private static final String TITLE_PROD_STATS_MATERIALS_PREFIX = ChatColor.DARK_GREEN + "Stats Prod. Matériaux: ";


    public EntrepriseGUI(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // FIX HAUTE: Nettoyer les contextes GUI orphelins lors de la déconnexion
    public void cleanupPlayerContext(UUID playerUUID) {
        if (playerUUID != null) {
            playerContexts.remove(playerUUID);
            clickTimestamps.remove(playerUUID);
        }
    }

    private PlayerGUIContext getPlayerContext(Player player) {
        return playerContexts.computeIfAbsent(player.getUniqueId(), k -> new PlayerGUIContext(getMainMenuTitle()));
    }

    private void resetPlayerContext(Player player) {
        playerContexts.remove(player.getUniqueId());
    }

    private static String getMainMenuTitle() {
        return TITLE_MAIN_MENU;
    }

    private boolean isPluginMenu(String inventoryTitle) {
        if (inventoryTitle == null) return false;
        // MODIFIÉ: Ne vérifie plus les titres de CV, car ils sont gérés par PlayerCVGUI.isPluginCVMenu()
        return inventoryTitle.equals(TITLE_MAIN_MENU) ||
                inventoryTitle.equals(TITLE_SELECT_GERANT) ||
                inventoryTitle.equals(TITLE_SELECT_TYPE) ||
                inventoryTitle.equals(TITLE_MY_ENTREPRISES) ||
                inventoryTitle.startsWith(TITLE_MANAGE_SPECIFIC_PREFIX) ||
                inventoryTitle.startsWith(TITLE_VIEW_SPECIFIC_PREFIX) ||
                inventoryTitle.startsWith(TITLE_RECRUIT_EMPLOYEE) ||
                inventoryTitle.equals(TITLE_CONFIRM_RECRUIT) ||
                inventoryTitle.startsWith(TITLE_MANAGE_EMPLOYEES) ||
                inventoryTitle.startsWith(TITLE_EMPLOYEE_OPTIONS_PREFIX) ||
                inventoryTitle.startsWith(TITLE_SET_PRIME) ||
                inventoryTitle.startsWith(TITLE_CONFIRM_DELETE) ||
                inventoryTitle.equals(TITLE_LIST_TOWNS) ||
                inventoryTitle.startsWith(TITLE_ENTREPRISES_IN_TOWN_PREFIX) ||
                inventoryTitle.equals(TITLE_ADMIN_MENU) ||
                inventoryTitle.startsWith(TITLE_CONFIRM_LEAVE_PREFIX) ||
                inventoryTitle.startsWith(TITLE_STATS_MENU_PREFIX) ||
                inventoryTitle.startsWith(TITLE_PROFIT_LOSS_PERIODS_PREFIX) ||
                inventoryTitle.startsWith(TITLE_TRANSACTIONS_PREFIX) ||
                inventoryTitle.startsWith(TITLE_EMPLOYEE_STATS_LIST_PREFIX) ||
                inventoryTitle.startsWith(TITLE_PROD_STATS_ACTION_TYPE_CHOICE_PREFIX) ||
                inventoryTitle.startsWith(TITLE_PROD_STATS_PERIODS_PREFIX) ||
                inventoryTitle.startsWith(TITLE_PROD_STATS_MATERIALS_PREFIX);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String topInventoryTitle = event.getView().getTitle();
        // --- NOUVELLE LOGIQUE ---
        // D'abord, on vérifie si le clic a lieu dans un menu géré par une autre classe.
        // Si c'est le cas, on ignore complètement l'événement dans CETTE classe.
        if (plugin.getPlayerCVGUI() != null && plugin.getPlayerCVGUI().isPluginCVMenu(topInventoryTitle)) {
            return; // Laisser PlayerCVGUI gérer ce clic.
        }

        // Si on arrive ici, on sait que ce n'est pas un menu Shop ou CV.
        // On vérifie maintenant si c'est un menu appartenant à EntrepriseGUI.
        if (!isPluginMenu(topInventoryTitle)) {
            return; // Ce n'est pas un de nos menus, on ne fait rien.
        }

        // C'est bien un menu géré par EntrepriseGUI. On peut annuler l'événement et le traiter.
        event.setCancelled(true);
        // --- FIN DE LA NOUVELLE LOGIQUE ---
        long currentTime = System.currentTimeMillis();
        if (clickTimestamps.getOrDefault(player.getUniqueId(), 0L) + CLICK_DELAY_MS > currentTime) return;
        clickTimestamps.put(player.getUniqueId(), currentTime);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || clickedItem.getItemMeta().getDisplayName() == null) return;

        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        PlayerGUIContext context = getPlayerContext(player);
        EntrepriseManagerLogic.Entreprise entrepriseContext = context.getCurrentEntreprise(entrepriseLogic);

        if (itemName.equalsIgnoreCase("Retour") || itemName.startsWith("Retour (")) {
            handleGoBack(player, context);
            return;
        }
        handleMenuClick(player, context, itemName, clickedItem, entrepriseContext);
    }

    private void handleGoBack(Player player, PlayerGUIContext context) {
        String newCurrentMenuTitleFromHistory = context.goBack();
        EntrepriseManagerLogic.Entreprise currentEntreprise = context.getCurrentEntreprise(entrepriseLogic);

        if (newCurrentMenuTitleFromHistory.equals(TITLE_MAIN_MENU)) openMainMenu(player);
        else if (newCurrentMenuTitleFromHistory.equals(TITLE_MY_ENTREPRISES)) openMyEntreprisesMenu(player, context);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_MANAGE_SPECIFIC_PREFIX) && currentEntreprise != null) openManageSpecificEntrepriseMenu(player, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_VIEW_SPECIFIC_PREFIX) && currentEntreprise != null) openViewSpecificEntrepriseMenu(player, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.equals(TITLE_SELECT_GERANT)) openCreateEntrepriseSelectGerantMenu(player, context);
        else if (newCurrentMenuTitleFromHistory.equals(TITLE_SELECT_TYPE)) openCreateEntrepriseSelectTypeMenu(player, context);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_STATS_MENU_PREFIX) && currentEntreprise != null) openEntrepriseStatsMenu(player, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_EMPLOYEE_OPTIONS_PREFIX) && currentEntreprise != null && context.selectedEmployeeForManagement != null) openSpecificEmployeeOptionsMenu(player, context, context.selectedEmployeeForManagement, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_MANAGE_EMPLOYEES) && currentEntreprise != null) openManageEmployeesListMenu(player, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_RECRUIT_EMPLOYEE) && currentEntreprise != null) openRecruitEmployeeProximityMenu(player, context, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.equals(TITLE_LIST_TOWNS)) openListTownsMenu(player, context);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_ENTREPRISES_IN_TOWN_PREFIX)) {
            String townName = newCurrentMenuTitleFromHistory.substring(TITLE_ENTREPRISES_IN_TOWN_PREFIX.length());
            openListEntreprisesInTownMenu(player, context, townName);
        }
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_PROD_STATS_ACTION_TYPE_CHOICE_PREFIX) && currentEntreprise != null) openProductionStatsActionTypeChoiceMenu(player, context, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_PROD_STATS_PERIODS_PREFIX) && currentEntreprise != null) openProductionStatsPeriodsChoiceMenu(player, context, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_PROFIT_LOSS_PERIODS_PREFIX) && currentEntreprise != null) openProfitLossPeriodsMenu(player, context, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_TRANSACTIONS_PREFIX) && currentEntreprise != null) openTransactionHistoryMenu(player, context, currentEntreprise);
        else if (newCurrentMenuTitleFromHistory.startsWith(TITLE_EMPLOYEE_STATS_LIST_PREFIX) && currentEntreprise != null) openEmployeeStatsListMenu(player, context, currentEntreprise);
        else if (plugin.getPlayerCVGUI() != null && plugin.getPlayerCVGUI().isPluginCVMenu(newCurrentMenuTitleFromHistory)) {
            plugin.getLogger().info("Retour vers un menu CV: " + newCurrentMenuTitleFromHistory + ". PlayerCVGUI devrait gérer.");
            openMainMenu(player); // Fallback
        }
        else {
            plugin.getLogger().warning("Bouton Retour: Menu précédent non géré explicitement '" + newCurrentMenuTitleFromHistory + "'. Redirection vers le menu principal.");
            openMainMenu(player);
        }
    }

    private void handleMenuClick(Player player, PlayerGUIContext context, String itemName, ItemStack clickedItem, EntrepriseManagerLogic.Entreprise entreprise) {
        String currentTitle = context.currentMenuTitle;

        if (currentTitle.equals(TITLE_MAIN_MENU)) handleMainMenuClick(player, context, itemName);
        else if (currentTitle.equals(TITLE_SELECT_GERANT)) handleSelectGerantForCreationClick(player, context, itemName);
        else if (currentTitle.equals(TITLE_SELECT_TYPE)) handleSelectTypeForCreationClick(player, context, itemName);
        else if (currentTitle.equals(TITLE_MY_ENTREPRISES)) handleMyEntreprisesMenuClick(player, context, itemName);
        else if (currentTitle.startsWith(TITLE_MANAGE_SPECIFIC_PREFIX) && entreprise != null) handleManageSpecificEntrepriseMenuClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_VIEW_SPECIFIC_PREFIX) && entreprise != null) handleViewSpecificEntrepriseMenuClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_RECRUIT_EMPLOYEE) && entreprise != null) handleRecruitEmployeeSelectionClick(player, context, itemName, entreprise);
        else if (currentTitle.equals(TITLE_CONFIRM_RECRUIT) && entreprise != null) handleRecruitConfirmationClick(player, context, clickedItem, entreprise);
        else if (currentTitle.startsWith(TITLE_MANAGE_EMPLOYEES) && entreprise != null) handleManageEmployeesListClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_EMPLOYEE_OPTIONS_PREFIX) && entreprise != null) handleSpecificEmployeeOptionsMenuClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_SET_PRIME) && entreprise != null) handleSetPrimeAmountClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_CONFIRM_DELETE) && entreprise != null) handleDeleteConfirmationClick(player, context, itemName, entreprise);
        else if (currentTitle.equals(TITLE_LIST_TOWNS)) handleListTownsMenuClick(player, context, itemName);
        else if (currentTitle.startsWith(TITLE_ENTREPRISES_IN_TOWN_PREFIX)) handleViewEntrepriseFromListClick(player, context, itemName);
        else if (currentTitle.equals(TITLE_ADMIN_MENU)) handleAdminMenuClick(player, itemName);
        else if (currentTitle.startsWith(TITLE_CONFIRM_LEAVE_PREFIX) && entreprise != null) handleLeaveConfirmationClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_STATS_MENU_PREFIX) && entreprise != null) handleEntrepriseStatsMenuClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_PROFIT_LOSS_PERIODS_PREFIX) && entreprise != null) handleProfitLossPeriodsMenuClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_TRANSACTIONS_PREFIX) && entreprise != null) handleTransactionHistoryMenuClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_EMPLOYEE_STATS_LIST_PREFIX) && entreprise != null) handleEmployeeStatsListMenuClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_PROD_STATS_ACTION_TYPE_CHOICE_PREFIX) && entreprise != null) handleProductionStatsActionTypeChoiceClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_PROD_STATS_PERIODS_PREFIX) && entreprise != null) handleProductionStatsPeriodsChoiceClick(player, context, itemName, entreprise);
        else if (currentTitle.startsWith(TITLE_PROD_STATS_MATERIALS_PREFIX) && entreprise != null) handleProductionMaterialsDisplayClick(player, context, itemName, entreprise);
        else {
            plugin.getLogger().warning("Clic non géré dans GUI Entreprise: " + currentTitle + " (Item: " + itemName + ")");
        }
    }

    public void openMainMenu(Player player) {
        resetPlayerContext(player);
        PlayerGUIContext context = getPlayerContext(player);
        context.navigateTo(TITLE_MAIN_MENU);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN_MENU);

        inv.setItem(10, createMenuItem(Material.WRITABLE_BOOK, ChatColor.GOLD + "Créer une Entreprise", List.of(ChatColor.GRAY + "Pour les Maires.")));
        inv.setItem(12, createMenuItem(Material.MAP, ChatColor.GOLD + "Lister les Entreprises", List.of(ChatColor.GRAY + "Voir les entreprises par ville.")));
        inv.setItem(14, createMenuItem(Material.CHEST, ChatColor.GOLD + "Mes Entreprises", List.of(ChatColor.GRAY + "Gérer ou voir vos entreprises.")));
        inv.setItem(16, createMenuItem(Material.PLAYER_HEAD, ChatColor.DARK_GREEN + "Consulter / Montrer mon CV", List.of(ChatColor.GRAY + "Accéder aux options de CV.")));
        if (player.hasPermission("entreprisemanager.admin")) {
            inv.setItem(8, createMenuItem(Material.COMMAND_BLOCK, ChatColor.RED + "Menu Administration", List.of(ChatColor.GRAY + "Actions réservées aux admins.")));
        }

        inv.setItem(0, createMenuItem(Material.CLOCK, ChatColor.GOLD + "Cycle Économique", List.of(ChatColor.GRAY + "Calcul en cours...")));
        player.openInventory(inv);

        if (guiUpdateTasks.containsKey(player.getUniqueId())) {
            guiUpdateTasks.get(player.getUniqueId()).cancel();
        }

        BukkitTask updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.getOpenInventory().getTitle().equals(TITLE_MAIN_MENU)) {
                    this.cancel();
                    guiUpdateTasks.remove(player.getUniqueId());
                    return;
                }

                LocalDateTime nextPayment = entrepriseLogic.getNextPaymentTime();
                String countdownDisplay = ChatColor.RED + "N/A";
                String nextPaymentTimeDisplay = ChatColor.RED + "N/A";

                if (nextPayment != null) {
                    nextPaymentTimeDisplay = nextPayment.format(DateTimeFormatter.ofPattern("HH'H'mm"));

                    Duration remaining = Duration.between(LocalDateTime.now(), nextPayment);
                    if (!remaining.isNegative()) {
                        long minutes = remaining.toMinutesPart();
                        long seconds = remaining.toSecondsPart();
                        countdownDisplay = String.format("%dmin %02dsec", minutes, seconds);
                    } else {
                        countdownDisplay = ChatColor.YELLOW + "En cours...";
                    }
                }

                ItemStack clockItem = inv.getItem(0);
                if (clockItem != null) {
                    ItemMeta meta = clockItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.GOLD + "Prochain Cycle Économique");
                        meta.setLore(List.of(
                                ChatColor.AQUA + "Heure du prochain cycle",
                                ChatColor.WHITE + nextPaymentTimeDisplay,
                                "",
                                ChatColor.AQUA + "Temps restant",
                                ChatColor.WHITE + countdownDisplay
                        ));
                        clockItem.setItemMeta(meta);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        guiUpdateTasks.put(player.getUniqueId(), updateTask);
    }

    private void handleMainMenuClick(Player player, PlayerGUIContext context, String itemName) {
        if (itemName.equals("Créer une Entreprise")) {
            if (entrepriseLogic.estMaire(player)) {
                openCreateEntrepriseSelectGerantMenu(player, context);
            } else {
                player.sendMessage(ChatColor.RED + "Seuls les maires peuvent créer des entreprises.");
                player.closeInventory();
            }
        } else if (itemName.equals("Lister les Entreprises")) {
            openListTownsMenu(player, context);
        } else if (itemName.equals("Mes Entreprises")) {
            openMyEntreprisesMenu(player, context);
        } else if (itemName.equals("Consulter / Montrer mon CV")) {
            if (plugin.getPlayerCVGUI() != null) {
                plugin.getPlayerCVGUI().openCVMainMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le module CV n'est pas correctement initialisé.");
                plugin.getLogger().severe("PlayerCVGUI est null lors de l'appel depuis EntrepriseGUI#handleMainMenuClick");
            }
        } else if (itemName.equals("Menu Administration") && player.hasPermission("entreprisemanager.admin")) {
            openAdminMenu(player, context);
        }
    }

    private void openCreateEntrepriseSelectGerantMenu(Player maire, PlayerGUIContext context) {
        context.navigateTo(TITLE_SELECT_GERANT);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SELECT_GERANT);
        Collection<String> residentsInTown = entrepriseLogic.getPlayersInMayorTown(maire);
        boolean foundEligible = false;
        // FIX BASSE #26: Utiliser ConfigDefaults
        int maxManagedByGerantConfig = plugin.getConfig().getInt("finance.max-entreprises-par-gerant",
            com.gravityyfh.roleplaycity.util.ConfigDefaults.FINANCE_MAX_ENTREPRISES_PAR_GERANT);
        Set<String> potentialGerants = new HashSet<>(residentsInTown);
        potentialGerants.add(maire.getName());

        for (String residentName : potentialGerants) {
            OfflinePlayer offlineResident = Bukkit.getOfflinePlayer(residentName);
            if (offlineResident.hasPlayedBefore() || offlineResident.isOnline()) {
                Player residentPlayer = offlineResident.getPlayer();
                if (residentPlayer != null && residentPlayer.isOnline()) {
                    int currentlyManaging = entrepriseLogic.getEntreprisesGereesPar(residentName).size();
                    if (currentlyManaging < maxManagedByGerantConfig) {
                        int salariedJobs = entrepriseLogic.countPlayerSalariedJobs(residentName);
                        String displayName = ChatColor.AQUA + residentName + (residentPlayer.equals(maire) ? ChatColor.GOLD + " (Vous-même)" : "");
                        inv.addItem(createPlayerHead(residentName, displayName, List.of(
                                ChatColor.GRAY + "Gère: " + currentlyManaging + "/" + maxManagedByGerantConfig + " ent.",
                                ChatColor.GRAY + "Emplois salariés: " + salariedJobs,
                                ChatColor.GREEN + "Éligible pour gérance."
                        )));
                        foundEligible = true;
                    }
                }
            }
        }
        if (!foundEligible) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun résident éligible", List.of(ChatColor.GRAY+"Limite de gérance peut-être atteinte.")));
        }
        addBackButton(inv, 49, "(Menu Principal)");
        maire.openInventory(inv);
    }

    private void handleSelectGerantForCreationClick(Player maire, PlayerGUIContext context, String itemNameFromClickedItem) {
        String selectedGerantName = ChatColor.stripColor(itemNameFromClickedItem);
        if (selectedGerantName.contains(" (Vous-même)")) {
            selectedGerantName = selectedGerantName.substring(0, selectedGerantName.indexOf(" (Vous-même)")).trim();
        }
        context.selectedGerantPourCreation = selectedGerantName;
        openCreateEntrepriseSelectTypeMenu(maire, context);
    }

    private void openCreateEntrepriseSelectTypeMenu(Player maire, PlayerGUIContext context) {
        context.navigateTo(TITLE_SELECT_TYPE);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SELECT_TYPE);
        Set<String> types = entrepriseLogic.getTypesEntreprise();
        if (types.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun type d'entreprise configuré."));
        } else {
            for (String type : types) {
                double cout = plugin.getConfig().getDouble("types-entreprise." + type + ".cout-creation", 0);
                inv.addItem(createMenuItem(Material.PAPER, ChatColor.AQUA + type, List.of(ChatColor.GOLD + "Coût: " + String.format("%,.2f", cout) + "€", ChatColor.GRAY + "Cliquez pour choisir.")));
            }
        }
        addBackButton(inv, 49, "(Choix Gérant)");
        maire.openInventory(inv);
    }

    private void handleSelectTypeForCreationClick(Player maire, PlayerGUIContext context, String typeEntreprise) {
        String gerantCibleNom = context.selectedGerantPourCreation;
        if (gerantCibleNom == null) {
            maire.sendMessage(ChatColor.RED + "Erreur: Gérant cible non sélectionné.");
            openCreateEntrepriseSelectGerantMenu(maire, context);
            return;
        }
        Player gerantCiblePlayer = Bukkit.getPlayerExact(gerantCibleNom);
        if (gerantCiblePlayer == null || !gerantCiblePlayer.isOnline()) {
            maire.sendMessage(ChatColor.RED + "'" + gerantCibleNom + "' n'est plus en ligne. Veuillez recommencer.");
            context.selectedGerantPourCreation = null;
            openCreateEntrepriseSelectGerantMenu(maire, context);
            return;
        }
        String villeDuMaire = entrepriseLogic.getTownNameFromPlayer(maire);
        if (villeDuMaire == null) {
            maire.sendMessage(ChatColor.RED + "Erreur: Impossible de déterminer votre ville.");
            maire.closeInventory();
            return;
        }
        String nomPropose = typeEntreprise + "_" + gerantCibleNom.substring(0, Math.min(gerantCibleNom.length(), 4)) + "_" + (new Random().nextInt(9000) + 1000);
        String siretPropose = entrepriseLogic.generateSiret();
        entrepriseLogic.proposerCreationEntreprise(maire, gerantCiblePlayer, typeEntreprise, villeDuMaire, nomPropose, siretPropose);
        maire.closeInventory();
        context.selectedGerantPourCreation = null;
    }

    private void openMyEntreprisesMenu(Player player, PlayerGUIContext context) {
        context.navigateTo(TITLE_MY_ENTREPRISES);
        context.currentEntrepriseNom = null;
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MY_ENTREPRISES);
        boolean found = false;
        List<EntrepriseManagerLogic.Entreprise> gerees = entrepriseLogic.getEntreprisesGereesPar(player.getName());
        for (EntrepriseManagerLogic.Entreprise e : gerees) {
            inv.addItem(createMenuItem(Material.GOLD_BLOCK, ChatColor.GOLD + e.getNom(), List.of(ChatColor.YELLOW + "Rôle: Gérant", ChatColor.GRAY + "Type: " + e.getType())));
            found = true;
        }
        for (EntrepriseManagerLogic.Entreprise e : entrepriseLogic.getEntreprises()) {
            if (e.getEmployes().contains(player.getName()) && !gerees.contains(e)) {
                inv.addItem(createMenuItem(Material.IRON_INGOT, ChatColor.AQUA + e.getNom(), List.of(ChatColor.YELLOW + "Rôle: Employé", ChatColor.GRAY + "Type: " + e.getType())));
                found = true;
            }
        }
        if (!found) inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Vous n'êtes lié à aucune entreprise."));
        addBackButton(inv, 49, "(Menu Principal)");
        player.openInventory(inv);
    }

    private void handleMyEntreprisesMenuClick(Player player, PlayerGUIContext context, String itemName) {
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(itemName);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + itemName + "' n'a pas été trouvée.");
            openMyEntreprisesMenu(player, context);
            return;
        }
        context.currentEntrepriseNom = entreprise.getNom();
        if (entreprise.getGerant().equalsIgnoreCase(player.getName())) {
            openManageSpecificEntrepriseMenu(player, entreprise);
        } else if (entreprise.getEmployes().contains(player.getName())) {
            openViewSpecificEntrepriseMenu(player, entreprise);
        } else {
            player.sendMessage(ChatColor.RED + "Vous n'êtes plus affilié à '" + itemName + "'.");
            context.currentEntrepriseNom = null;
            openMyEntreprisesMenu(player, context);
        }
    }

    public void openManageSpecificEntrepriseMenu(Player gerant, Entreprise entreprise) {
        PlayerGUIContext context = getPlayerContext(gerant);
        context.navigateTo(TITLE_MANAGE_SPECIFIC_PREFIX + entreprise.getNom());
        context.currentEntrepriseNom = entreprise.getNom();

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MANAGE_SPECIFIC_PREFIX + entreprise.getNom());

        // Ligne 1 & 2
        inv.setItem(1, createMenuItem(Material.BOOK, ChatColor.AQUA + "Infos Entreprise", List.of(ChatColor.GRAY + "Détails & Solde", ChatColor.GREEN + String.format("%,.2f", entreprise.getSolde()) + "€")));
        inv.setItem(3, createMenuItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Déposer Argent"));
        inv.setItem(4, createMenuItem(Material.IRON_INGOT, ChatColor.GOLD + "Retirer Argent"));
        inv.setItem(7, createMenuItem(Material.MAP, ChatColor.DARK_GREEN + "Statistiques & Rapports"));
        inv.setItem(10, createMenuItem(Material.PLAYER_HEAD, ChatColor.GREEN + "Gérer Employés"));
        inv.setItem(11, createMenuItem(Material.EMERALD, ChatColor.GREEN + "Recruter Employé"));

        // MODIFICATION ICI : Le nom et la description du bouton ont été mis à jour.
        inv.setItem(13, createMenuItem(Material.CHEST, ChatColor.AQUA + "Mes Boutiques",
                List.of(ChatColor.GRAY + "Gérer tous les points de vente", ChatColor.GRAY + "de votre entreprise.")));

        // --- SECTION AMÉLIORATIONS ---
        int niveauActuelEmployes = entreprise.getNiveauMaxEmployes();
        int maxEmployesActuel = entrepriseLogic.getLimiteMaxEmployesActuelle(entreprise);
        double coutProchainNiveauEmployes = entrepriseLogic.getCoutProchaineAmeliorationEmployes(entreprise);
        List<String> loreEmployes = new ArrayList<>();
        loreEmployes.add(ChatColor.GRAY + "Niveau actuel: " + ChatColor.WHITE + niveauActuelEmployes);
        loreEmployes.add(ChatColor.GRAY + "Employés max actuels: " + ChatColor.WHITE + maxEmployesActuel);
        if (coutProchainNiveauEmployes >= 0) {
            int prochainNiveauEmp = niveauActuelEmployes + 1;
            int employesProchainNiveau = plugin.getConfig().getInt("finance.max-employer-par-entreprise." + prochainNiveauEmp, maxEmployesActuel);
            loreEmployes.add(ChatColor.YELLOW + "Prochain niveau (" + prochainNiveauEmp + "): " + ChatColor.WHITE + employesProchainNiveau + " employés");
            loreEmployes.add(ChatColor.GOLD + "Coût amélioration: " + ChatColor.WHITE + String.format("%,.2f", coutProchainNiveauEmployes) + "€");
        } else {
            loreEmployes.add(ChatColor.GREEN + "Niveau maximum pour employés atteint !");
        }
        inv.setItem(19, createMenuItem(Material.EXPERIENCE_BOTTLE, ChatColor.LIGHT_PURPLE + "Améliorer Capacité Employés", loreEmployes));

        int niveauActuelSolde = entreprise.getNiveauMaxSolde();
        double maxSoldeActuel = entrepriseLogic.getLimiteMaxSoldeActuelle(entreprise);
        double coutProchainNiveauSolde = entrepriseLogic.getCoutProchaineAmeliorationSolde(entreprise);
        List<String> loreSolde = new ArrayList<>();
        loreSolde.add(ChatColor.GRAY + "Niveau actuel: " + ChatColor.WHITE + niveauActuelSolde);
        loreSolde.add(ChatColor.GRAY + "Solde max actuel: " + ChatColor.WHITE + String.format("%,.2f", maxSoldeActuel) + "€");
        if (coutProchainNiveauSolde >= 0) {
            int prochainNiveauSld = niveauActuelSolde + 1;
            double soldeProchainNiveau = plugin.getConfig().getDouble("finance.max-solde-par-niveau." + prochainNiveauSld, maxSoldeActuel);
            loreSolde.add(ChatColor.YELLOW + "Prochain niveau (" + prochainNiveauSld + "): " + ChatColor.WHITE + String.format("%,.2f", soldeProchainNiveau) + "€");
            loreSolde.add(ChatColor.GOLD + "Coût amélioration: " + ChatColor.WHITE + String.format("%,.2f", coutProchainNiveauSolde) + "€");
        } else {
            loreSolde.add(ChatColor.GREEN + "Niveau maximum pour solde atteint !");
        }
        inv.setItem(20, createMenuItem(Material.CHEST_MINECART, ChatColor.LIGHT_PURPLE + "Améliorer Solde Maximum", loreSolde));
        // --- FIN SECTION AMÉLIORATIONS ---

        inv.setItem(28, createMenuItem(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + "Renommer Entreprise", List.of(ChatColor.GRAY + "Coût: " + plugin.getConfig().getDouble("rename-cost", 0) + "€")));
        inv.setItem(inv.getSize() - 9, createMenuItem(Material.TNT, ChatColor.DARK_RED + "Dissoudre Entreprise", List.of(ChatColor.RED + "Action irréversible !")));
        addBackButton(inv, inv.getSize() - 5, "(Mes Entreprises)");
        gerant.openInventory(inv);
    }

    private void handleManageSpecificEntrepriseMenuClick(Player gerant, PlayerGUIContext context, String itemName, Entreprise entreprise) {
        if (itemName.equals("Infos Entreprise")) {
            displayEntrepriseInfo(gerant, entreprise);
        } else if (itemName.equals("Déposer Argent")) {
            plugin.getChatListener().attendreMontantDepot(gerant, entreprise.getNom());
            gerant.closeInventory();
        } else if (itemName.equals("Retirer Argent")) {
            plugin.getChatListener().attendreMontantRetrait(gerant, entreprise.getNom());
            gerant.closeInventory();
        } else if (itemName.equals("Statistiques & Rapports")) {
            openEntrepriseStatsMenu(gerant, entreprise);
        } else if (itemName.equals("Gérer Employés")) {
            openManageEmployeesListMenu(gerant, entreprise);
        } else if (itemName.equals("Recruter Employé")) {
            openRecruitEmployeeProximityMenu(gerant, context, entreprise);

            // Boutiques
        } else if (itemName.equals("Mes Boutiques")) {
            if (plugin.getShopListGUI() != null) {
                plugin.getShopListGUI().openShopList(gerant, entreprise, 0);
            } else {
                gerant.sendMessage(ChatColor.RED + "Erreur: Système de boutiques non disponible.");
                gerant.closeInventory();
            }

        } else if (itemName.equals("Renommer Entreprise")) {
            plugin.getChatListener().attendreNouveauNomEntreprise(gerant, entreprise.getNom());
            gerant.closeInventory();
        } else if (itemName.equals("Dissoudre Entreprise")) {
            openDeleteConfirmationMenu(gerant, context, entreprise);
        } else if (itemName.equals("Améliorer Capacité Employés")) {
            String resultat = entrepriseLogic.tenterAmeliorationNiveauMaxEmployes(entreprise, gerant);
            gerant.sendMessage(resultat);
            if (resultat.startsWith(ChatColor.GREEN.toString())) {
                Entreprise updatedEntreprise = entrepriseLogic.getEntreprise(entreprise.getNom());
                if (updatedEntreprise != null) {
                    openManageSpecificEntrepriseMenu(gerant, updatedEntreprise);
                } else {
                    gerant.closeInventory();
                    gerant.sendMessage(ChatColor.RED + "Erreur lors du rechargement de l'entreprise.");
                }
            }
        } else if (itemName.equals("Améliorer Solde Maximum")) {
            String resultat = entrepriseLogic.tenterAmeliorationNiveauMaxSolde(entreprise, gerant);
            gerant.sendMessage(resultat);
            if (resultat.startsWith(ChatColor.GREEN.toString())) {
                Entreprise updatedEntreprise = entrepriseLogic.getEntreprise(entreprise.getNom());
                if (updatedEntreprise != null) {
                    openManageSpecificEntrepriseMenu(gerant, updatedEntreprise);
                } else {
                    gerant.closeInventory();
                    gerant.sendMessage(ChatColor.RED + "Erreur lors du rechargement de l'entreprise.");
                }
            }
        }
    }

    public void openViewSpecificEntrepriseMenu(Player employe, EntrepriseManagerLogic.Entreprise entreprise) {
        PlayerGUIContext context = getPlayerContext(employe);
        context.navigateTo(TITLE_VIEW_SPECIFIC_PREFIX + entreprise.getNom());
        context.currentEntrepriseNom = entreprise.getNom();
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_VIEW_SPECIFIC_PREFIX + entreprise.getNom());
        inv.setItem(10, createMenuItem(Material.BOOK, ChatColor.AQUA + "Infos Générales", List.of(ChatColor.GRAY + "Type: " + entreprise.getType(), ChatColor.GRAY + "Gérant: " + entreprise.getGerant(), ChatColor.GREEN + "Solde Ent.: " + String.format("%,.2f", entreprise.getSolde()) + "€")));
        EntrepriseManagerLogic.EmployeeActivityRecord rec = entreprise.getEmployeeActivityRecord(employe.getUniqueId());
        List<String> situationLore = new ArrayList<>(List.of(ChatColor.GOLD + "Prime: " + String.format("%.2f€/h", entreprise.getPrimePourEmploye(employe.getUniqueId().toString()))));
        if (rec != null) {
            situationLore.add(ChatColor.LIGHT_PURPLE + "Ancienneté: " + rec.getFormattedSeniority());
            situationLore.add(ChatColor.GREEN + "Val. Gén. (Total): " + String.format("%.2f€", rec.totalValueGenerated));
            if(rec.isActive()) situationLore.add(ChatColor.YELLOW + "Session active"); else situationLore.add(ChatColor.GRAY + "Session inactive");
        } else situationLore.add(ChatColor.GRAY + "Aucune donnée d'activité.");
        inv.setItem(12, createMenuItem(Material.GOLD_NUGGET, ChatColor.YELLOW + "Ma Situation", situationLore));
        inv.setItem(13, createMenuItem(Material.PAPER, ChatColor.DARK_AQUA + "Transactions Entreprise"));
        inv.setItem(14, createMenuItem(Material.DIAMOND_PICKAXE, ChatColor.DARK_GREEN + "Mes Stats Production"));
        inv.setItem(16, createMenuItem(Material.REDSTONE_BLOCK, ChatColor.DARK_RED + "Quitter l'Entreprise"));
        addBackButton(inv, 22, "(Mes Entreprises)");
        employe.openInventory(inv);
    }

    private void handleViewSpecificEntrepriseMenuClick(Player employe, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        if (itemName.equals("Infos Générales")) displayEntrepriseInfo(employe, entreprise);
        else if (itemName.equals("Ma Situation")) {
            EntrepriseManagerLogic.EmployeeActivityRecord rec = entreprise.getEmployeeActivityRecord(employe.getUniqueId());
            if (rec != null) {
                employe.closeInventory();
                employe.sendMessage(ChatColor.GOLD + "--- Ma Situation dans '" + entreprise.getNom() + "' ---");
                employe.sendMessage(ChatColor.YELLOW + "Ancienneté: " + ChatColor.WHITE + rec.getFormattedSeniority());
                employe.sendMessage(ChatColor.YELLOW + "Prime: " + ChatColor.WHITE + String.format("%.2f€/h", entreprise.getPrimePourEmploye(employe.getUniqueId().toString())));
                employe.sendMessage(ChatColor.YELLOW + "Valeur générée: " + ChatColor.GREEN + String.format("%.2f€", rec.totalValueGenerated));
                long totalActions = rec.actionsPerformedCount.values().stream().mapToLong(Long::longValue).sum();
                employe.sendMessage(ChatColor.YELLOW + "Actions productives: " + ChatColor.WHITE + totalActions);
                employe.sendMessage(ChatColor.YELLOW + "Session: " + (rec.isActive() ? ChatColor.GREEN + "Active" : ChatColor.GRAY + "Inactive"));
                if (rec.lastActivityTime != null) {
                    employe.sendMessage(ChatColor.YELLOW + "Dernière activité: " + ChatColor.WHITE + rec.lastActivityTime.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")));
                }
                employe.sendMessage(ChatColor.GOLD + "------------------------------------");
            } else {
                employe.sendMessage(ChatColor.YELLOW + "Aucune donnée d'activité pour vous.");
            }
        } else if (itemName.equals("Transactions Entreprise")) {
            context.currentPage = 0;
            openTransactionHistoryMenu(employe, context, entreprise);
        } else if (itemName.equals("Mes Stats Production")) {
            context.currentViewingEmployeeStatsUUID = employe.getUniqueId();
            openProductionStatsActionTypeChoiceMenu(employe, context, entreprise);
        } else if (itemName.equals("Quitter l'Entreprise")) {
            openLeaveConfirmationMenu(employe, context, entreprise);
        }
    }

    private void openEntrepriseStatsMenu(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        PlayerGUIContext context = getPlayerContext(player);
        context.navigateTo(TITLE_STATS_MENU_PREFIX + entreprise.getNom());
        context.currentEntrepriseNom = entreprise.getNom();
        context.currentViewingEmployeeStatsUUID = null;
        Inventory inv = Bukkit.createInventory(null, 36, TITLE_STATS_MENU_PREFIX + entreprise.getNom());
        inv.setItem(10, createMenuItem(Material.GOLD_BLOCK, ChatColor.GOLD + "Aperçu Financier", List.of(ChatColor.YELLOW + "Solde: " + ChatColor.GREEN + String.format("%,.2f", entreprise.getSolde()) + "€", ChatColor.AQUA + "CA Brut: " + ChatColor.WHITE + String.format("%,.2f", entreprise.getChiffreAffairesTotal()) + "€")));
        inv.setItem(12, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Profit/Perte par Périodes"));
        inv.setItem(14, createMenuItem(Material.WRITABLE_BOOK, ChatColor.DARK_AQUA + "Historique des Transactions"));
        boolean isGerantOuAdmin = player.getName().equalsIgnoreCase(entreprise.getGerant()) || player.hasPermission("entreprisemanager.admin.viewallstats");
        if (isGerantOuAdmin) {
            inv.setItem(19, createMenuItem(Material.PLAYER_HEAD, ChatColor.DARK_PURPLE + "Statistiques des Employés (Global)"));
            inv.setItem(20, createMenuItem(Material.DIAMOND_PICKAXE, ChatColor.DARK_GREEN + "Statistiques de Production (Global)"));
        } else {
            EntrepriseManagerLogic.EmployeeActivityRecord rec = entreprise.getEmployeeActivityRecord(player.getUniqueId());
            if (rec != null) {
                inv.setItem(19, createMenuItem(Material.IRON_CHESTPLATE, ChatColor.LIGHT_PURPLE + "Mon Activité (Résumé)", List.of(ChatColor.GREEN + "Valeur Générée: " + String.format("%,.2f€", rec.totalValueGenerated), ChatColor.GRAY + "Ancienneté: " + rec.getFormattedSeniority())));
                inv.setItem(20, createMenuItem(Material.DIAMOND_PICKAXE, ChatColor.DARK_GREEN + "Mes Statistiques de Production"));
            } else {
                inv.setItem(19, createMenuItem(Material.BARRIER, ChatColor.GRAY + "Données d'activité non disponibles"));
                inv.setItem(20, createMenuItem(Material.BARRIER, ChatColor.GRAY + "Stats production non disponibles"));
            }
        }
        String retourLabel = player.getName().equalsIgnoreCase(entreprise.getGerant()) ? "(Gestion Ent.)" : "(Détails Ent.)";
        addBackButton(inv, 31, retourLabel);
        player.openInventory(inv);
    }

    private void handleEntrepriseStatsMenuClick(Player player, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        boolean isGerantOuAdmin = player.getName().equalsIgnoreCase(entreprise.getGerant()) || player.hasPermission("entreprisemanager.admin.viewallstats");
        if (itemName.equals("Aperçu Financier")) displayEntrepriseInfo(player, entreprise);
        else if (itemName.equals("Profit/Perte par Périodes")) openProfitLossPeriodsMenu(player, context, entreprise);
        else if (itemName.equals("Historique des Transactions")) { context.currentPage = 0; openTransactionHistoryMenu(player, context, entreprise); }
        else if (itemName.equals("Statistiques des Employés (Global)") && isGerantOuAdmin) { context.currentPage = 0; openEmployeeStatsListMenu(player, context, entreprise); }
        else if (itemName.equals("Statistiques de Production (Global)") && isGerantOuAdmin) {
            context.currentViewingEmployeeStatsUUID = null;
            openProductionStatsActionTypeChoiceMenu(player, context, entreprise);
        } else if (itemName.equals("Mon Activité (Résumé)")) {
            EntrepriseManagerLogic.EmployeeActivityRecord rec = entreprise.getEmployeeActivityRecord(player.getUniqueId());
            if(rec != null) {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD+"--- Mon Activité (Résumé) pour '"+entreprise.getNom()+"' ---");
                player.sendMessage(ChatColor.YELLOW + "Ancienneté: "+ChatColor.WHITE+rec.getFormattedSeniority());
                player.sendMessage(ChatColor.YELLOW + "Valeur générée: "+ChatColor.GREEN+String.format("%,.2f€", rec.totalValueGenerated));
                long totalActions = rec.actionsPerformedCount.values().stream().mapToLong(Long::longValue).sum();
                player.sendMessage(ChatColor.YELLOW + "Actions productives: "+ChatColor.WHITE+totalActions);
                if (rec.isActive()) player.sendMessage(ChatColor.YELLOW + "Session: " + ChatColor.GREEN + "Active");
                else player.sendMessage(ChatColor.YELLOW + "Session: " + ChatColor.GRAY + "Inactive");
                player.sendMessage(ChatColor.GOLD+"----------------------------------------------------");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Aucune donnée d'activité pour vous.");
            }
        } else if (itemName.equals("Mes Statistiques de Production")) {
            context.currentViewingEmployeeStatsUUID = player.getUniqueId();
            openProductionStatsActionTypeChoiceMenu(player, context, entreprise);
        }
    }

    private void openProfitLossPeriodsMenu(Player player, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_PROFIT_LOSS_PERIODS_PREFIX + entreprise.getNom());
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PROFIT_LOSS_PERIODS_PREFIX + entreprise.getNom());
        inv.setItem(10, createMenuItem(Material.CLOCK, ChatColor.AQUA + "3 Dernières Heures"));
        inv.setItem(11, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Dernier Jour (24h)"));
        inv.setItem(12, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Dernière Semaine (7j)"));
        inv.setItem(13, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Dernier Mois (30j)"));
        inv.setItem(14, createMenuItem(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "Depuis Création (Total)"));
        addBackButton(inv, 22, "(Statistiques)");
        player.openInventory(inv);
    }

    private void handleProfitLossPeriodsMenuClick(Player player, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        LocalDateTime end = LocalDateTime.now(); LocalDateTime start = null; String periodName = "";
        switch (itemName) {
            case "3 Dernières Heures": start = end.minusHours(3); periodName = "des 3 dernières heures"; break;
            case "Dernier Jour (24h)": start = end.minusDays(1); periodName = "du dernier jour"; break;
            case "Dernière Semaine (7j)": start = end.minusWeeks(1); periodName = "de la semaine dernière"; break;
            case "Dernier Mois (30j)": start = end.minusMonths(1); periodName = "du mois dernier"; break;
            case "Depuis Création (Total)": start = entreprise.getTransactionLog().stream().min(Comparator.comparing(tx -> tx.timestamp)).map(tx -> tx.timestamp).orElse(end); periodName = "depuis la création"; break;
            default: return;
        }
        player.closeInventory();
        if (start.isEqual(end) && !itemName.equals("Depuis Création (Total)")) { player.sendMessage(ChatColor.YELLOW + "Pas de données pour " + periodName + "."); return; }
        double profitLoss = entreprise.calculateProfitLoss(start, end);
        ChatColor color = profitLoss >= 0 ? ChatColor.GREEN : ChatColor.RED; String prefix = profitLoss >= 0 ? "+" : "";
        player.sendMessage(ChatColor.GOLD + "--- Profit/Perte pour '" + entreprise.getNom() + "' (" + periodName + ") ---");
        player.sendMessage(ChatColor.YELLOW + "Résultat: " + color + prefix + String.format("%,.2f", profitLoss) + "€");
        player.sendMessage(ChatColor.GRAY + "(Période du " + start.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")) + " au " + end.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")) + ")");
        player.sendMessage(ChatColor.GOLD + "------------------------------------------------" + ChatColor.stripColor(ChatColor.GOLD + entreprise.getNom()).replaceAll(".", "-") + "----------------");
    }

    private void openTransactionHistoryMenu(Player player, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_TRANSACTIONS_PREFIX + entreprise.getNom());
        List<EntrepriseManagerLogic.Transaction> transactions = new ArrayList<>(entreprise.getTransactionLog());
        Collections.reverse(transactions);
        int page = context.currentPage; int totalItems = transactions.size(); int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE_DEFAULT);
        page = Math.max(0, Math.min(page, Math.max(0, totalPages - 1))); context.currentPage = page;
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_TRANSACTIONS_PREFIX + entreprise.getNom());
        int startIndex = page * ITEMS_PER_PAGE_DEFAULT; int endIndex = Math.min(startIndex + ITEMS_PER_PAGE_DEFAULT, totalItems);
        if (transactions.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune transaction."));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                EntrepriseManagerLogic.Transaction tx = transactions.get(i);
                Material itemMaterial = (tx.amount >= 0 && tx.type != EntrepriseManagerLogic.TransactionType.WITHDRAWAL && !tx.type.isOperationalExpense()) || tx.type == EntrepriseManagerLogic.TransactionType.DEPOSIT ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
                String amountPrefix = (itemMaterial == Material.LIME_STAINED_GLASS_PANE && tx.amount > 0) ? "+" : "";
                ChatColor amountColor = (itemMaterial == Material.LIME_STAINED_GLASS_PANE) ? ChatColor.GREEN : ChatColor.RED;
                String itemNameText = amountColor + amountPrefix + String.format("%,.2f€", tx.amount) + ChatColor.GRAY + " (" + tx.type.getDisplayName() + ")";
                List<String> lore = List.of(ChatColor.GRAY + "Date: " + ChatColor.WHITE + tx.timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")), ChatColor.GRAY + "Desc: " + ChatColor.WHITE + tx.description, ChatColor.GRAY + "Par: " + ChatColor.WHITE + tx.initiatedBy);
                inv.setItem(i - startIndex, createMenuItem(itemMaterial, itemNameText, lore));
            }
        }
        if (page > 0) inv.setItem(45, createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Précédente"));
        inv.setItem(49, createMenuItem(Material.PAPER, ChatColor.GOLD + "Page " + (page + 1) + "/" + Math.max(1, totalPages)));
        if (page < totalPages - 1) inv.setItem(53, createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Suivante"));
        addBackButton(inv, 48, "(Statistiques)");
        player.openInventory(inv);
    }

    private void handleTransactionHistoryMenuClick(Player player, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        if (itemName.equals("Page Précédente")) { if (context.currentPage > 0) { context.currentPage--; openTransactionHistoryMenu(player, context, entreprise); } }
        else if (itemName.equals("Page Suivante")) {
            List<EntrepriseManagerLogic.Transaction> transactions = entreprise.getTransactionLog();
            int totalPages = (int) Math.ceil((double) transactions.size() / ITEMS_PER_PAGE_DEFAULT);
            if (context.currentPage < totalPages - 1) { context.currentPage++; openTransactionHistoryMenu(player, context, entreprise); }
        }
    }

    private void openManageEmployeesListMenu(Player gerant, EntrepriseManagerLogic.Entreprise entreprise) {
        PlayerGUIContext context = getPlayerContext(gerant);
        context.navigateTo(TITLE_MANAGE_EMPLOYEES + ": " + entreprise.getNom());
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MANAGE_EMPLOYEES + ": " + entreprise.getNom());
        Set<String> employesNoms = entreprise.getEmployes();
        if (employesNoms.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucun employé."));
        } else {
            int i = 0;
            for (String empName : employesNoms) {
                if (i >= ITEMS_PER_PAGE_DEFAULT) break;
                OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(empName); UUID empUUID = offlineEmp.getUniqueId();
                double prime = entreprise.getPrimePourEmploye(empUUID.toString()); String anciennete = entreprise.getEmployeeSeniorityFormatted(empUUID);
                EntrepriseManagerLogic.EmployeeActivityRecord rec = entreprise.getEmployeeActivityRecord(empUUID); boolean isActive = rec != null && rec.isActive();
                List<String> lore = List.of(ChatColor.LIGHT_PURPLE + "Ancienneté: " + ChatColor.WHITE + anciennete, ChatColor.GOLD + "Prime: " + ChatColor.WHITE + String.format("%,.2f€/h", prime), (isActive ? ChatColor.GREEN + "Session Active" : ChatColor.GRAY + "Session Inactive"), ChatColor.DARK_AQUA + "Cliquez pour options...");
                inv.addItem(createPlayerHead(empName, ChatColor.AQUA + empName, lore));
                i++;
            }
        }
        addBackButton(inv, 49, "(Gestion Ent.)");
        gerant.openInventory(inv);
    }

    private void handleManageEmployeesListClick(Player gerant, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        if (!entreprise.getEmployes().contains(itemName)) {
            gerant.sendMessage(ChatColor.RED + "Employé '" + itemName + "' introuvable.");
            openManageEmployeesListMenu(gerant, entreprise); return;
        }
        context.selectedEmployeeForManagement = itemName;
        openSpecificEmployeeOptionsMenu(gerant, context, itemName, entreprise);
    }

    private void openSpecificEmployeeOptionsMenu(Player gerant, PlayerGUIContext context, String employeNom, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_EMPLOYEE_OPTIONS_PREFIX + employeNom);
        OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(employeNom); UUID empUUID = offlineEmp.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_EMPLOYEE_OPTIONS_PREFIX + employeNom);
        double primeActuelle = entreprise.getPrimePourEmploye(empUUID.toString());
        inv.setItem(11, createMenuItem(Material.GOLD_INGOT, ChatColor.GREEN + "Définir Prime Horaire", List.of(ChatColor.GRAY + "Actuelle: " + String.format("%,.2f", primeActuelle) + "€/h")));
        inv.setItem(13, createMenuItem(Material.DIAMOND_PICKAXE, ChatColor.DARK_AQUA + "Voir Stats Production Employé"));
        inv.setItem(15, createMenuItem(Material.RED_WOOL, ChatColor.RED + "Virer " + employeNom));
        addBackButton(inv, 22, "(Liste Employés)");
        gerant.openInventory(inv);
    }

    private void handleSpecificEmployeeOptionsMenuClick(Player gerant, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        String employeNom = context.selectedEmployeeForManagement;
        if (employeNom == null) { gerant.sendMessage(ChatColor.RED + "Erreur contexte."); openManageEmployeesListMenu(gerant, entreprise); return; }
        if (itemName.equals("Définir Prime Horaire")) {
            openSetPrimeAmountMenu(gerant, context, employeNom, entreprise);
        } else if (itemName.equals("Voir Stats Production Employé")) {
            OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(employeNom);
            context.currentViewingEmployeeStatsUUID = offlineEmp.getUniqueId();
            openProductionStatsActionTypeChoiceMenu(gerant, context, entreprise);
        } else if (itemName.startsWith("Virer ")) {
            entrepriseLogic.kickEmploye(gerant, entreprise.getNom(), employeNom);
            context.selectedEmployeeForManagement = null;
            openManageEmployeesListMenu(gerant, entreprise);
        }
    }

    private void openSetPrimeAmountMenu(Player gerant, PlayerGUIContext context, String employeNom, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_SET_PRIME + " pour " + employeNom);
        OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(employeNom); UUID empUUID = offlineEmp.getUniqueId();
        double primeActuelle = entreprise.getPrimePourEmploye(empUUID.toString());
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SET_PRIME + " pour " + employeNom);
        List<Double> montantsPredefinis = List.of(0.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0, 750.0, 1000.0, 1500.0, 2000.0);
        for (double montant : montantsPredefinis) {
            List<String> lore = new ArrayList<>(); if (montant == primeActuelle) lore.add(ChatColor.GREEN + "(Prime Actuelle)");
            lore.add(ChatColor.GRAY + "Cliquez pour définir.");
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + String.format("%,.2f", montant) + "€/h", lore));
        }
        addBackButton(inv, 49, "(Options Employé)");
        gerant.openInventory(inv);
    }

    private void handleSetPrimeAmountClick(Player gerant, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        String employeNom = context.selectedEmployeeForManagement;
        if (employeNom == null) { gerant.sendMessage(ChatColor.RED + "Erreur contexte prime."); handleGoBack(gerant, context); return; }
        try {
            String montantStr = itemName.split("€")[0].trim().replace(",", "."); double nouvellePrime = Double.parseDouble(montantStr);
            if (nouvellePrime < 0) { gerant.sendMessage(ChatColor.RED + "Prime non négative."); return; }
            entrepriseLogic.definirPrime(entreprise.getNom(), employeNom, nouvellePrime);
            gerant.sendMessage(ChatColor.GREEN + "Prime de " + employeNom + " pour '" + entreprise.getNom() + "' -> " + String.format("%,.2f€/h", nouvellePrime) + ".");
            Player employePlayer = Bukkit.getPlayerExact(employeNom);
            if (employePlayer != null && employePlayer.isOnline()) employePlayer.sendMessage(ChatColor.GOLD + "Votre prime pour '" + entreprise.getNom() + "' est " + String.format("%,.2f€/h", nouvellePrime) + ".");
            openSpecificEmployeeOptionsMenu(gerant, context, employeNom, entreprise);
        } catch (NumberFormatException e) { gerant.sendMessage(ChatColor.RED + "Montant prime invalide: " + itemName); }
    }

    private void openRecruitEmployeeProximityMenu(Player gerant, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_RECRUIT_EMPLOYEE + " pour " + entreprise.getNom());
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_RECRUIT_EMPLOYEE + " pour " + entreprise.getNom());
        Collection<String> nearbyPlayers = entrepriseLogic.getNearbyPlayers(gerant, plugin.getConfig().getInt("invitation.distance-max", 10));
        boolean foundEligible = false;

        if (!nearbyPlayers.isEmpty()) {
            for (String targetName : nearbyPlayers) {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                if ((!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline())) continue;

                if (targetName.equalsIgnoreCase(gerant.getName())) {
                    continue;
                }

                if (entreprise.getEmployes().contains(targetName)) {
                    continue;
                }

                if (entreprise.getGerant().equalsIgnoreCase(targetName)) {
                    continue;
                }

                int maxSalariedJobs = plugin.getConfig().getInt("finance.max-travail-joueur", 1);
                int currentSalariedJobs = entrepriseLogic.countPlayerSalariedJobs(targetName);

                if (currentSalariedJobs < maxSalariedJobs) {
                    int maxEmployesActuel = entrepriseLogic.getLimiteMaxEmployesActuelle(entreprise);
                    if (entreprise.getEmployes().size() < maxEmployesActuel) {
                        List<String> lore = new ArrayList<>();
                        lore.add(ChatColor.GRAY + "Inviter à '" + entreprise.getNom() + "'.");
                        lore.add(ChatColor.YELLOW + "Emplois salariés actuels: " + currentSalariedJobs + "/" + maxSalariedJobs);

                        inv.addItem(createPlayerHead(targetName, ChatColor.AQUA + targetName, lore));
                        foundEligible = true;
                    }
                }
            }
        }

        if (!foundEligible) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucun joueur éligible proche."));
        }
        addBackButton(inv, 49, "(Gestion Ent.)");
        gerant.openInventory(inv);
    }

    private void handleRecruitEmployeeSelectionClick(Player gerant, PlayerGUIContext context, String targetPlayerName, EntrepriseManagerLogic.Entreprise entreprise) {
        Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            gerant.sendMessage(ChatColor.RED + "'" + targetPlayerName + "' hors-ligne.");
            openRecruitEmployeeProximityMenu(gerant, context, entreprise); return;
        }
        context.selectedEmployeeForManagement = targetPlayerName;
        openRecruitConfirmationMenu(gerant, context, targetPlayerName, entreprise);
    }

    private void openRecruitConfirmationMenu(Player gerant, PlayerGUIContext context, String targetPlayerName, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_CONFIRM_RECRUIT);
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONFIRM_RECRUIT);
        inv.setItem(11, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "Oui, inviter " + targetPlayerName));
        inv.setItem(15, createMenuItem(Material.RED_WOOL, ChatColor.RED + "Non, annuler"));
        gerant.openInventory(inv);
    }

    private void handleRecruitConfirmationClick(Player gerant, PlayerGUIContext context, ItemStack clickedItem, EntrepriseManagerLogic.Entreprise entreprise) {
        String itemNameText = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        String targetPlayerName = context.selectedEmployeeForManagement;
        if (targetPlayerName == null) { gerant.sendMessage(ChatColor.RED + "Erreur contexte recrutement."); handleGoBack(gerant, context); return; }
        if (itemNameText.startsWith("Oui, inviter ")) {
            Player targetOnline = Bukkit.getPlayerExact(targetPlayerName);
            if (targetOnline != null && targetOnline.isOnline()) {
                entrepriseLogic.inviterEmploye(gerant, entreprise.getNom(), targetOnline);
                gerant.sendMessage(ChatColor.GREEN + "Invitation envoyée à " + targetPlayerName + ".");
            } else {
                gerant.sendMessage(ChatColor.RED + targetPlayerName + " n'est plus en ligne.");
            }
        } else if (itemNameText.equals("Non, annuler")) {
            gerant.sendMessage(ChatColor.YELLOW + "Recrutement annulé.");
        }
        context.selectedEmployeeForManagement = null;
        openManageSpecificEntrepriseMenu(gerant, entreprise);
    }

    private void openDeleteConfirmationMenu(Player gerant, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_CONFIRM_DELETE + " : " + entreprise.getNom());
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONFIRM_DELETE + " : " + entreprise.getNom());
        inv.setItem(11, createMenuItem(Material.RED_WOOL, ChatColor.DARK_RED + "OUI, Dissoudre '" + entreprise.getNom() + "'", List.of(ChatColor.RED + "IRRÉVERSIBLE !")));
        inv.setItem(15, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "NON, Annuler"));
        gerant.openInventory(inv);
    }

    private void handleDeleteConfirmationClick(Player gerant, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        if (itemName.startsWith("OUI, Dissoudre")) {
            entrepriseLogic.supprimerEntreprise(gerant, entreprise.getNom());
            openMainMenu(gerant);
        } else if (itemName.equals("NON, Annuler")) {
            gerant.sendMessage(ChatColor.YELLOW + "Dissolution annulée.");
            openManageSpecificEntrepriseMenu(gerant, entreprise);
        }
    }

    private void openLeaveConfirmationMenu(Player employe, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_CONFIRM_LEAVE_PREFIX + entreprise.getNom() + "?");
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONFIRM_LEAVE_PREFIX + entreprise.getNom() + "?");
        inv.setItem(11, createMenuItem(Material.RED_WOOL, ChatColor.DARK_RED + "OUI, Quitter " + entreprise.getNom()));
        inv.setItem(15, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "NON, Rester"));
        employe.openInventory(inv);
    }

    private void handleLeaveConfirmationClick(Player employe, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        if (itemName.startsWith("OUI, Quitter")) {
            entrepriseLogic.leaveEntreprise(employe, entreprise.getNom());
            openMyEntreprisesMenu(employe, getPlayerContext(employe));
        } else if (itemName.equals("NON, Rester")) {
            employe.sendMessage(ChatColor.YELLOW + "Vous restez membre.");
            openViewSpecificEntrepriseMenu(employe, entreprise);
        }
    }

    private void openListTownsMenu(Player player, PlayerGUIContext context) {
        context.navigateTo(TITLE_LIST_TOWNS);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_LIST_TOWNS);
        Collection<String> townsWithEntreprises = entrepriseLogic.getAllTownsNames().stream()
                .filter(townName -> !entrepriseLogic.getEntreprisesByVille(townName).isEmpty())
                .collect(Collectors.toSet());
        if (townsWithEntreprises.isEmpty()) inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune ville n'a d'entreprises."));
        else { for (String townName : townsWithEntreprises) inv.addItem(createMenuItem(Material.PAPER, ChatColor.AQUA + townName, List.of(ChatColor.GRAY + "Voir les entreprises."))); }
        addBackButton(inv, 49, "(Menu Principal)");
        player.openInventory(inv);
    }

    private void handleListTownsMenuClick(Player player, PlayerGUIContext context, String townName) {
        openListEntreprisesInTownMenu(player, context, townName);
    }

    private void openListEntreprisesInTownMenu(Player player, PlayerGUIContext context, String townName) {
        context.navigateTo(TITLE_ENTREPRISES_IN_TOWN_PREFIX + townName);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_ENTREPRISES_IN_TOWN_PREFIX + townName);
        List<EntrepriseManagerLogic.Entreprise> entreprises = entrepriseLogic.getEntreprisesByVille(townName);
        if (entreprises.isEmpty()) inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune entreprise à " + townName + "."));
        else { for (EntrepriseManagerLogic.Entreprise e : entreprises) inv.addItem(createMenuItem(Material.BOOK, ChatColor.GOLD + e.getNom(), List.of(ChatColor.GRAY + "Type: " + e.getType(), ChatColor.GRAY + "Gérant: " + e.getGerant(), ChatColor.DARK_AQUA + "Cliquez pour détails."))); }
        addBackButton(inv, 49, "(Liste Villes)");
        player.openInventory(inv);
    }

    private void handleViewEntrepriseFromListClick(Player player, PlayerGUIContext context, String entrepriseNom) {
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);
        if (entreprise == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + entrepriseNom + "' introuvable."); handleGoBack(player, context); return; }
        context.currentEntrepriseNom = entreprise.getNom();
        if (entreprise.getGerant().equalsIgnoreCase(player.getName())) openManageSpecificEntrepriseMenu(player, entreprise);
        else if (entreprise.getEmployes().contains(player.getName())) openViewSpecificEntrepriseMenu(player, entreprise);
        else displayEntrepriseInfo(player, entreprise);
    }

    private void openAdminMenu(Player player, PlayerGUIContext context) {
        if (!player.hasPermission("entreprisemanager.admin")) { player.sendMessage(ChatColor.RED + "Permission refusée."); player.closeInventory(); return; }
        context.navigateTo(TITLE_ADMIN_MENU);
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_ADMIN_MENU);
        inv.setItem(11, createMenuItem(Material.CLOCK, ChatColor.YELLOW + "Forcer Cycle Paiements"));
        inv.setItem(13, createMenuItem(Material.COMMAND_BLOCK_MINECART, ChatColor.AQUA + "Recharger Configuration"));
        inv.setItem(15, createMenuItem(Material.ANVIL, ChatColor.GOLD + "Forcer Sauvegarde Données"));
        addBackButton(inv, 22, "(Menu Principal)");
        player.openInventory(inv);
    }

    private void handleAdminMenuClick(Player player, String itemName) {
        if (itemName.equals("Forcer Cycle Paiements")) {
            if (player.hasPermission("entreprisemanager.admin.forcepay")) {
                // 1. Informer le joueur et fermer l'inventaire IMMÉDIATEMENT.
                player.sendMessage(ChatColor.YELLOW + "Lancement du cycle financier forcé... Le traitement s'effectue en arrière-plan.");
                player.closeInventory();

                // 2. Lancer la tâche longue de manière asynchrone.
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Cette partie s'exécute sur un autre thread, ne bloquant pas le serveur.
                        entrepriseLogic.executerCycleFinancierHoraire();

                        // 3. Envoyer le message final au joueur en revenant sur le thread principal.
                        // Les actions sur les joueurs (comme sendMessage) doivent se faire sur le thread principal.
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) { // Toujours vérifier si le joueur est en ligne
                                    player.sendMessage(ChatColor.GREEN + "Cycle financier forcé terminé avec succès !");
                                }
                            }
                        }.runTask(plugin); // .runTask(plugin) s'exécute sur le thread principal.
                    }
                }.runTaskAsynchronously(plugin); // .runTaskAsynchronously(plugin) lance la tâche sur un autre thread.

            } else {
                player.sendMessage(ChatColor.RED + "Permission refusée.");
                player.closeInventory();
            }
        } else if (itemName.equals("Recharger Configuration")) {
            if (player.hasPermission("entreprisemanager.admin.reload")) {
                // FIX BASSE #4: Utiliser la nouvelle méthode au lieu de deprecated
                plugin.reloadPluginConfig();
                player.sendMessage(ChatColor.GREEN + "Plugin rechargé.");
            } else {
                player.sendMessage(ChatColor.RED + "Permission refusée.");
            }
            player.closeInventory();

        } else if (itemName.equals("Forcer Sauvegarde Données")) {
            if (player.hasPermission("entreprisemanager.admin.forcesave")) {
                // Note : La sauvegarde de données devrait aussi être asynchrone si elle est longue.
                entrepriseLogic.saveEntreprises();
                player.sendMessage(ChatColor.GREEN + "Données sauvegardées !");
            } else {
                player.sendMessage(ChatColor.RED + "Permission refusée.");
            }
            player.closeInventory();
        }
    }

    public void displayEntrepriseInfo(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Infos: " + ChatColor.AQUA + entreprise.getNom() + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.YELLOW + "SIRET: " + ChatColor.WHITE + entreprise.getSiret());
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + entreprise.getVille());
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + entreprise.getType());
        player.sendMessage(ChatColor.YELLOW + "Gérant: " + ChatColor.WHITE + entreprise.getGerant());

        int maxEmployesActuel = entrepriseLogic.getLimiteMaxEmployesActuelle(entreprise);
        int niveauEmployes = entreprise.getNiveauMaxEmployes();
        player.sendMessage(ChatColor.YELLOW + "Employés: " + ChatColor.WHITE + entreprise.getEmployes().size() + "/" + maxEmployesActuel + ChatColor.GRAY + " (Niv. " + niveauEmployes + ")");

        double soldeMaxActuel = entrepriseLogic.getLimiteMaxSoldeActuelle(entreprise);
        int niveauSolde = entreprise.getNiveauMaxSolde();
        player.sendMessage(ChatColor.YELLOW + "Solde: " + ChatColor.GREEN + String.format("%,.2f", entreprise.getSolde()) + "€" + ChatColor.WHITE + " / " + String.format("%,.2f", soldeMaxActuel) + "€" + ChatColor.GRAY + " (Niv. " + niveauSolde + ")");

        player.sendMessage(ChatColor.YELLOW + "CA Brut (Total): " + ChatColor.DARK_GREEN + String.format("%,.2f", entreprise.getChiffreAffairesTotal()) + "€");
        player.sendMessage(ChatColor.YELLOW + "CA Potentiel Horaire: " + ChatColor.AQUA + String.format("%,.2f", entrepriseLogic.getActiviteHoraireValeurPour(entreprise.getNom())) + "€");

        boolean showEmployeeDetails = player.getName().equalsIgnoreCase(entreprise.getGerant()) || player.hasPermission("entreprisemanager.admin.info");
        if (showEmployeeDetails) {
            if (!entreprise.getEmployes().isEmpty()) {
                player.sendMessage(ChatColor.GOLD + "Employés et Primes:");
                for (String nomEmploye : entreprise.getEmployes()) {
                    OfflinePlayer offEmp = Bukkit.getOfflinePlayer(nomEmploye);
                    UUID empUUID = null;
                    try {
                        empUUID = offEmp.getUniqueId();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Impossible d'obtenir l'UUID pour l'employé " + nomEmploye + " lors de l'affichage des infos.");
                        continue;
                    }

                    double prime = entreprise.getPrimePourEmploye(empUUID.toString());
                    String anciennete = entreprise.getEmployeeSeniorityFormatted(empUUID);
                    player.sendMessage(ChatColor.GRAY + "  - " + nomEmploye + ChatColor.YELLOW + " (Prime: " + String.format("%,.2f€/h", prime) + ", Ancienneté: " + anciennete + ")");
                }
            } else {
                player.sendMessage(ChatColor.GOLD + "Employés: " + ChatColor.GRAY + "Aucun.");
            }
        }
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "====================================");
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); if (lore != null && !lore.isEmpty()) meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }
    private ItemStack createMenuItem(Material material, String name) { return createMenuItem(material, name, null); }

    private ItemStack createPlayerHead(String playerName, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1); SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            meta.setOwningPlayer(offlinePlayer); meta.setDisplayName(displayName); if (lore != null && !lore.isEmpty()) meta.setLore(lore); item.setItemMeta(meta);
        }
        return item;
    }
    private void addBackButton(Inventory inv, int slot, String contextHint) { inv.setItem(slot, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour " + ChatColor.GRAY + contextHint)); }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (plugin.getChatListener() != null && plugin.getChatListener().isPlayerWaitingForInput(player.getUniqueId())) {
            return;
        }
    }

    // --- STATISTIQUES DE PRODUCTION ---

    private void openProductionStatsActionTypeChoiceMenu(Player player, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        String newTitle = TITLE_PROD_STATS_ACTION_TYPE_CHOICE_PREFIX + entreprise.getNom();
        context.navigateTo(newTitle);
        Inventory inv = Bukkit.createInventory(null, 36, newTitle);

        String targetName;
        if (context.currentViewingEmployeeStatsUUID == null) targetName = "Global";
        else if (context.currentViewingEmployeeStatsUUID.equals(player.getUniqueId())) targetName = "Mes Stats";
        else { OfflinePlayer emp = Bukkit.getOfflinePlayer(context.currentViewingEmployeeStatsUUID); targetName = emp.getName() != null ? "Stats de " + emp.getName() : "Stats Employé"; }

        inv.setItem(4, createMenuItem(Material.BOOK, ChatColor.AQUA + "Cible Actuelle: " + ChatColor.WHITE + targetName));
        inv.setItem(19, createMenuItem(Material.DIAMOND_PICKAXE, ChatColor.YELLOW + DetailedActionType.BLOCK_BROKEN.getDisplayName()));
        inv.setItem(22, createMenuItem(Material.CRAFTING_TABLE, ChatColor.YELLOW + DetailedActionType.ITEM_CRAFTED.getDisplayName()));
        inv.setItem(25, createMenuItem(Material.GRASS_BLOCK, ChatColor.YELLOW + DetailedActionType.BLOCK_PLACED.getDisplayName()));
        addBackButton(inv, inv.getSize() - 5, "(Menu Stats)");
        player.openInventory(inv);
    }

    private void handleProductionStatsActionTypeChoiceClick(Player player, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        DetailedActionType selectedActionType = null;
        if (itemName.equals(DetailedActionType.BLOCK_BROKEN.getDisplayName())) selectedActionType = DetailedActionType.BLOCK_BROKEN;
        else if (itemName.equals(DetailedActionType.ITEM_CRAFTED.getDisplayName())) selectedActionType = DetailedActionType.ITEM_CRAFTED;
        else if (itemName.equals(DetailedActionType.BLOCK_PLACED.getDisplayName())) selectedActionType = DetailedActionType.BLOCK_PLACED;

        if (selectedActionType != null) {
            context.currentViewingActionType = selectedActionType;
            context.currentPage = 0;
            openProductionStatsPeriodsChoiceMenu(player, context, entreprise);
        }
    }

    private void openProductionStatsPeriodsChoiceMenu(Player player, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        String actionTypeDisplay = (context.currentViewingActionType != null) ? context.currentViewingActionType.getDisplayName() : "Type d'Action Non Défini";
        String newTitle = TITLE_PROD_STATS_PERIODS_PREFIX + actionTypeDisplay + " - " + entreprise.getNom();
        context.navigateTo(newTitle);
        Inventory inv = Bukkit.createInventory(null, 36, newTitle);

        String targetName;
        if (context.currentViewingEmployeeStatsUUID == null) targetName = "Global";
        else if (context.currentViewingEmployeeStatsUUID.equals(player.getUniqueId())) targetName = "Mes Stats";
        else { OfflinePlayer emp = Bukkit.getOfflinePlayer(context.currentViewingEmployeeStatsUUID); targetName = emp.getName() != null ? "Stats de " + emp.getName() : "Stats Employé"; }

        inv.setItem(3, createMenuItem(Material.BOOK, ChatColor.AQUA + "Cible: " + ChatColor.WHITE + targetName));
        inv.setItem(5, createMenuItem(Material.PAPER, ChatColor.GREEN + "Type d'Action: " + ChatColor.WHITE + actionTypeDisplay));
        inv.setItem(19, createMenuItem(Material.CLOCK, ChatColor.AQUA + "3 Dernières Heures"));
        inv.setItem(20, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Dernier Jour (24h)"));
        inv.setItem(21, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Dernière Semaine (7j)"));
        inv.setItem(22, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Dernier Mois (30j)"));
        String totalLabel = (context.currentViewingEmployeeStatsUUID == null) ? "Depuis Création (Global)" : "Depuis Mon Entrée";
        inv.setItem(23, createMenuItem(Material.ENDER_CHEST, ChatColor.GOLD + totalLabel));
        addBackButton(inv, inv.getSize() - 5, "(Choix Type Action)");
        player.openInventory(inv);
    }

    private void handleProductionStatsPeriodsChoiceClick(Player player, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        LocalDateTime end = LocalDateTime.now(); LocalDateTime start = null;
        UUID targetEmpUUID = context.currentViewingEmployeeStatsUUID;

        switch (itemName) {
            case "3 Dernières Heures": start = end.minusHours(3); break;
            case "Dernier Jour (24h)": start = end.minusDays(1); break;
            case "Dernière Semaine (7j)": start = end.minusWeeks(1); break;
            case "Dernier Mois (30j)": start = end.minusMonths(1); break;
            case "Depuis Création (Global)": case "Depuis Mon Entrée":
                if (targetEmpUUID != null) {
                    EntrepriseManagerLogic.EmployeeActivityRecord rec = entreprise.getEmployeeActivityRecord(targetEmpUUID);
                    start = (rec != null && rec.joinDate != null) ? rec.joinDate : LocalDateTime.MIN;
                } else {
                    start = entreprise.getGlobalProductionLog().stream()
                            .min(Comparator.comparing(prodRec -> prodRec.timestamp))
                            .map(prodRec -> prodRec.timestamp)
                            .orElse(LocalDateTime.MIN);
                    if (start == LocalDateTime.MIN && !entreprise.getGlobalProductionLog().isEmpty()) start = end;
                }
                break;
            default: if (!itemName.startsWith("Cible:") && !itemName.startsWith("Type d'Action:")) return; return;
        }
        context.currentProductionPeriod = new LocalDateTime[]{start, end};
        context.currentPage = 0;
        openProductionMaterialsDisplay(player, context, entreprise);
    }

    private void openProductionMaterialsDisplay(Player player, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        LocalDateTime[] period = context.currentProductionPeriod;
        UUID targetEmployeeUUID = context.currentViewingEmployeeStatsUUID;
        DetailedActionType actionTypeFilter = context.currentViewingActionType;

        if (period == null || period.length < 2) { player.sendMessage(ChatColor.RED + "Erreur: Période non définie."); openProductionStatsPeriodsChoiceMenu(player, context, entreprise); return; }
        if (actionTypeFilter == null) { player.sendMessage(ChatColor.RED + "Erreur: Type d'action non défini."); openProductionStatsActionTypeChoiceMenu(player, context, entreprise); return; }

        LocalDateTime startDate = period[0]; LocalDateTime endDate = period[1];
        Map<Material, Integer> productionData; String titleSuffix;

        if (targetEmployeeUUID != null) {
            productionData = entrepriseLogic.getEmployeeProductionStatsForPeriod(entreprise.getNom(), targetEmployeeUUID, startDate, endDate, actionTypeFilter);
            OfflinePlayer emp = Bukkit.getOfflinePlayer(targetEmployeeUUID); titleSuffix = (emp.getName() != null ? emp.getName() : "Employé") + " - " + actionTypeFilter.getDisplayName();
        } else {
            productionData = entrepriseLogic.getCompanyProductionStatsForPeriod(entreprise.getNom(), startDate, endDate, actionTypeFilter);
            titleSuffix = "Global - " + actionTypeFilter.getDisplayName();
        }

        List<Map.Entry<Material, Integer>> sortedMaterials = productionData.entrySet().stream().sorted(Map.Entry.<Material, Integer>comparingByValue().reversed()).collect(Collectors.toList());
        int page = context.currentPage; int totalItems = sortedMaterials.size(); int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE_MATERIALS);
        page = Math.max(0, Math.min(page, Math.max(0, totalPages - 1))); context.currentPage = page;
        String periodDisplay = startDate.format(DateTimeFormatter.ofPattern("dd/MM")) + "-" + endDate.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));

        String newTitle = TITLE_PROD_STATS_MATERIALS_PREFIX + titleSuffix;
        Inventory inv = Bukkit.createInventory(null, 54, newTitle);
        int startIndex = page * ITEMS_PER_PAGE_MATERIALS; int endIndex = Math.min(startIndex + ITEMS_PER_PAGE_MATERIALS, totalItems);

        if (sortedMaterials.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune production de '" + actionTypeFilter.getDisplayName() + "' pour cette période."));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Map.Entry<Material, Integer> entry = sortedMaterials.get(i); Material material = entry.getKey(); int quantity = entry.getValue();
                String materialName = Arrays.stream(material.toString().toLowerCase().replace("_", " ").split(" ")).map(word -> word.substring(0, 1).toUpperCase() + word.substring(1)).collect(Collectors.joining(" "));
                inv.setItem(i - startIndex, createMenuItem(material, ChatColor.AQUA + materialName, List.of(ChatColor.GOLD + "Quantité: " + String.format("%,d", quantity))));
            }
        }
        if (page > 0) inv.setItem(45, createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Précédente"));
        inv.setItem(49, createMenuItem(Material.PAPER, ChatColor.GOLD + "Page " + (page + 1) + "/" + Math.max(1, totalPages) + ChatColor.GRAY + " (" + periodDisplay + ")"));
        if (page < totalPages - 1) inv.setItem(53, createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Suivante"));
        addBackButton(inv, 48, "(Choix Période Prod.)");
        player.openInventory(inv);
    }

    private void handleProductionMaterialsDisplayClick(Player player, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        if (itemName.equals("Page Précédente")) { if (context.currentPage > 0) { context.currentPage--; openProductionMaterialsDisplay(player, context, entreprise); } }
        else if (itemName.equals("Page Suivante")) {
            LocalDateTime[] period = context.currentProductionPeriod; UUID targetEmployeeUUID = context.currentViewingEmployeeStatsUUID; DetailedActionType actionTypeFilter = context.currentViewingActionType;
            if (period == null || actionTypeFilter == null) { player.sendMessage(ChatColor.RED + "Erreur contexte pagination."); openProductionStatsActionTypeChoiceMenu(player, context, entreprise); return; }
            Map<Material, Integer> productionData;
            if (targetEmployeeUUID != null) productionData = entrepriseLogic.getEmployeeProductionStatsForPeriod(entreprise.getNom(),targetEmployeeUUID, period[0], period[1], actionTypeFilter);
            else productionData = entrepriseLogic.getCompanyProductionStatsForPeriod(entreprise.getNom(), period[0], period[1], actionTypeFilter);
            int totalItems = productionData.size(); int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE_MATERIALS);
            if (context.currentPage < totalPages - 1) { context.currentPage++; openProductionMaterialsDisplay(player, context, entreprise); }
        }
    }

    private void openEmployeeStatsListMenu(Player player, PlayerGUIContext context, EntrepriseManagerLogic.Entreprise entreprise) {
        context.navigateTo(TITLE_EMPLOYEE_STATS_LIST_PREFIX + entreprise.getNom());
        List<EntrepriseManagerLogic.EmployeeActivityRecord> records = new ArrayList<>(entreprise.getEmployeeActivityRecords().values());
        records.sort(Comparator.comparing(r -> r.employeeName.toLowerCase()));
        int page = context.currentPage; int totalItems = records.size(); int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE_DEFAULT);
        page = Math.max(0, Math.min(page, Math.max(0, totalPages - 1))); context.currentPage = page;
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_EMPLOYEE_STATS_LIST_PREFIX + entreprise.getNom());
        int startIndex = page * ITEMS_PER_PAGE_DEFAULT; int endIndex = Math.min(startIndex + ITEMS_PER_PAGE_DEFAULT, totalItems);
        if (records.isEmpty()) inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune donnée d'employé."));
        else {
            for (int i = startIndex; i < endIndex; i++) {
                EntrepriseManagerLogic.EmployeeActivityRecord r = records.get(i);
                List<String> lore = new ArrayList<>(List.of(ChatColor.LIGHT_PURPLE + "Ancienneté: " + ChatColor.WHITE + r.getFormattedSeniority(), ChatColor.GREEN + "Valeur Générée: " + ChatColor.WHITE + String.format("%,.2f€", r.totalValueGenerated), ChatColor.GOLD + "Prime: " + ChatColor.WHITE + String.format("%,.2f€/h", entreprise.getPrimePourEmploye(r.employeeId.toString()))));
                if (r.isActive()) lore.add(ChatColor.GREEN + "Session: Active"); else { lore.add(ChatColor.GRAY + "Session: Inactive"); if (r.lastActivityTime != null) lore.add(ChatColor.GRAY + "Dernière act.: " + r.lastActivityTime.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))); }
                lore.add(ChatColor.DARK_AQUA + "Clic détails (chat) / stats prod");
                inv.setItem(i - startIndex, createPlayerHead(r.employeeName, ChatColor.AQUA + r.employeeName, lore));
            }
        }
        if (page > 0) inv.setItem(45, createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Précédente"));
        inv.setItem(49, createMenuItem(Material.PAPER, ChatColor.GOLD + "Page " + (page + 1) + "/" + Math.max(1, totalPages)));
        if (page < totalPages - 1) inv.setItem(53, createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Suivante"));
        addBackButton(inv, 48, "(Statistiques)");
        player.openInventory(inv);
    }

    private void handleEmployeeStatsListMenuClick(Player player, PlayerGUIContext context, String itemName, EntrepriseManagerLogic.Entreprise entreprise) {
        if (itemName.equals("Page Précédente")) { if (context.currentPage > 0) { context.currentPage--; openEmployeeStatsListMenu(player, context, entreprise); } }
        else if (itemName.equals("Page Suivante")) {
            List<EntrepriseManagerLogic.EmployeeActivityRecord> records = new ArrayList<>(entreprise.getEmployeeActivityRecords().values());
            int totalPages = (int) Math.ceil((double) records.size() / ITEMS_PER_PAGE_DEFAULT);
            if (context.currentPage < totalPages - 1) { context.currentPage++; openEmployeeStatsListMenu(player, context, entreprise); }
        } else {
            EntrepriseManagerLogic.EmployeeActivityRecord rec = entreprise.getEmployeeActivityRecords().values().stream().filter(r -> r.employeeName.equalsIgnoreCase(itemName)).findFirst().orElse(null);
            if (rec != null) {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "--- Détails Employé: " + ChatColor.AQUA + rec.employeeName + ChatColor.GOLD + " (" + entreprise.getNom() + ") ---");
                player.sendMessage(ChatColor.YELLOW + "Ancienneté: " + ChatColor.WHITE + rec.getFormattedSeniority());
                player.sendMessage(ChatColor.YELLOW + "Valeur Générée: " + ChatColor.GREEN + String.format("%,.2f€", rec.totalValueGenerated));
                player.sendMessage(ChatColor.YELLOW + "Prime: " + ChatColor.WHITE + String.format("%,.2f€/h", entreprise.getPrimePourEmploye(rec.employeeId.toString())));
                long totalActions = rec.actionsPerformedCount.values().stream().mapToLong(Long::longValue).sum();
                player.sendMessage(ChatColor.YELLOW + "Actions Enregistrées: " + ChatColor.WHITE + totalActions);
                if (rec.isActive()) player.sendMessage(ChatColor.YELLOW + "Session: " + ChatColor.GREEN + "Active");
                else { player.sendMessage(ChatColor.YELLOW + "Session: " + ChatColor.GRAY + "Inactive"); if (rec.lastActivityTime != null) player.sendMessage(ChatColor.GRAY + " (Dernière act.: " + rec.lastActivityTime.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")) + ")"); }
                if (!rec.actionsPerformedCount.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Détail actions (Total):");
                    rec.actionsPerformedCount.forEach((actionKey, count) -> {
                        String[] parts = actionKey.split(":"); String actionTypeStr = parts.length > 0 ? parts[0].replace("_", " ").toLowerCase() : "Action inconnue"; actionTypeStr = actionTypeStr.substring(0,1).toUpperCase() + actionTypeStr.substring(1);
                        String materialName = parts.length > 1 ? parts[1].replace("_", " ").toLowerCase() : ""; materialName = Arrays.stream(materialName.split(" ")).map(word -> word.substring(0, 1).toUpperCase() + word.substring(1)).collect(Collectors.joining(" "));
                        player.sendMessage(ChatColor.GRAY + "  - " + actionTypeStr + (parts.length > 1 ? " de " + ChatColor.AQUA + materialName : "") + ": " + ChatColor.WHITE + count);
                    });
                }
                player.sendMessage(ChatColor.GOLD + "---------------------------------------------------------");
                player.sendMessage(ChatColor.DARK_AQUA + "Pour stats production détaillées: 'Gérer Employés' -> Clic employé -> 'Voir Stats Prod.'");
            } else player.sendMessage(ChatColor.RED + "Données introuvables pour " + itemName + ".");
        }
    }
}
