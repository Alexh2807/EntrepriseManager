package com.gravityyfh.entreprisemanager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType; // Ajout pour identifier l'inventaire du joueur
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.logging.Level; // Pour les messages de log

public class EntrepriseGUI implements Listener {

    private final EntrepriseManager plugin;
    private final EntrepriseManagerLogic entrepriseLogic;

    private final Map<Player, String> selectedGerantForCreation = new HashMap<>();
    private final Map<Player, String> selectedTypeForCreation = new HashMap<>();
    private final Map<UUID, Long> clickTimestamps = new HashMap<>();

    private final Map<Player, String> currentOpenEntreprise = new HashMap<>();
    private final Map<Player, String> selectedEmployeeForManagement = new HashMap<>();

    private final Map<UUID, String> pendingRename_OldName = new HashMap<>();
    // private final Map<Player, Inventory> previousInventories = new HashMap<>(); // Peut-être pas nécessaire avec la logique de retour simplifiée

    private static final long CLICK_DELAY_MS = 500; // Anti double-clic

    // --- Liste de tous les titres de tes GUIs ---
    // Il est CRUCIAL que cette liste soit exhaustive et correcte.
    private final Set<String> pluginMenuTitles = new HashSet<>(Arrays.asList(
            ChatColor.DARK_BLUE + "Menu Principal Entreprises",
            ChatColor.DARK_BLUE + "Sélectionner Gérant Cible",
            ChatColor.DARK_BLUE + "Sélectionner Type d'Entreprise",
            ChatColor.DARK_BLUE + "Mes Entreprises (Gérant/Employé)",
            // Les titres avec "startsWith" devront être gérés un peu différemment ou listés explicitement si possible
            // Pour l'instant, isPluginMenu va les gérer avec startsWith, mais c'est moins précis que des titres exacts.
            ChatColor.DARK_BLUE + "Recruter Employé",
            ChatColor.DARK_BLUE + "Confirmer Recrutement",
            ChatColor.DARK_BLUE + "Gérer Employés",
            ChatColor.DARK_BLUE + "Définir Prime Horaire",
            ChatColor.DARK_BLUE + "Confirmer Suppression Entreprise",
            ChatColor.DARK_BLUE + "Lister Entreprises par Ville",
            ChatColor.RED + "Menu Administration"
            // Ajoutez ici TOUS les autres titres exacts de vos GUIs
    ));

    // Préfixes pour les titres dynamiques (moins idéal, mais fonctionne)
    private final List<String> pluginMenuTitlePrefixes = Arrays.asList(
            ChatColor.DARK_BLUE + "Gérer: ",
            ChatColor.BLUE + "Détails: ",
            ChatColor.DARK_BLUE + "Options pour ",
            ChatColor.DARK_BLUE + "Entreprises à ",
            ChatColor.DARK_RED + "Quitter " // Assurez-vous que l'espace à la fin est voulu
    );


    public EntrepriseGUI(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- Méthode pour vérifier si l'inventaire est un menu du plugin ---
    private boolean isPluginMenu(String inventoryTitle) {
        if (inventoryTitle == null) {
            return false;
        }
        if (pluginMenuTitles.contains(inventoryTitle)) {
            return true;
        }
        for (String prefix : pluginMenuTitlePrefixes) {
            if (inventoryTitle.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        Inventory clickedInventory = event.getClickedInventory(); // L'inventaire où le clic a eu lieu
        Inventory topInventory = event.getView().getTopInventory(); // L'inventaire du haut (le GUI)
        String topInventoryTitle = event.getView().getTitle();

        // Si le clic n'est pas dans un inventaire du tout (ex: clic en dehors), on ignore.
        if (clickedInventory == null) {
            return;
        }

        // Vérifier si l'inventaire du haut est l'un de nos menus
        boolean isOurMenuOpen = isPluginMenu(topInventoryTitle);

        // Si ce n'est PAS l'un de nos menus qui est ouvert, on ne fait RIEN.
        // Cela permet les interactions normales dans l'inventaire du joueur ou d'autres plugins.
        if (!isOurMenuOpen) {
            // Si le joueur clique dans son propre inventaire ALORS QU'AUCUN de nos GUIs n'est ouvert,
            // on ne fait rien. Mais si un de nos GUIs EST ouvert, on pourrait vouloir annuler
            // les clics dans l'inventaire du joueur (SHIFT-CLICKS, etc.)
            // La condition ci-dessous gère le cas où notre GUI est ouvert et le joueur clique dans son inventaire.
            // Si event.getRawSlot() < topInventory.getSize(), le clic est dans le GUI du haut.
            // Sinon, il est dans l'inventaire du joueur (ou un autre inventaire du bas).
            if (event.getRawSlot() >= topInventory.getSize()) { // Clic dans l'inventaire du joueur PENDANT que notre GUI est ouvert
                // Décidez si vous voulez annuler ces clics (ex: pour empêcher le shift-click vers votre GUI)
                // Pour l'instant, on va laisser passer, mais c'est un point à considérer.
                // event.setCancelled(true);
                // plugin.getLogger().info("[EntrepriseGUI DEBUG] Player inventory click while plugin GUI '" + topInventoryTitle + "' was open. Cancelled: " + event.isCancelled());
            }
            return;
        }

        // À partir d'ici, nous savons que event.getView().getTopInventory() est l'un de nos GUIs.
        // Donc, par défaut, on annule l'événement pour empêcher de prendre/déplacer les items du GUI.
        event.setCancelled(true);
        plugin.getLogger().log(Level.INFO, "[EntrepriseGUI DEBUG] Click cancelled in plugin menu: \"" + topInventoryTitle + "\" by player " + player.getName());


        // Anti-double clic (déjà présent et correct)
        long currentTime = System.currentTimeMillis();
        if (clickTimestamps.getOrDefault(playerId, 0L) + CLICK_DELAY_MS > currentTime) {
            // Déjà annulé plus haut, mais on return pour ne pas traiter la logique du clic
            return;
        }
        clickTimestamps.put(playerId, currentTime);

        ItemStack clickedItem = event.getCurrentItem();

        // Si on clique sur un slot vide ou un item sans métadonnées/nom dans NOTRE GUI
        if (clickedItem == null || !clickedItem.hasItemMeta() || clickedItem.getItemMeta().getDisplayName() == null) {
            // L'événement est déjà annulé si c'est notre menu, donc on ne fait rien de plus.
            return;
        }

        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // --- Gestion des clics spécifiques à chaque menu ---
        // Note: event.setCancelled(true) a déjà été fait globalement pour nos menus.
        // Donc plus besoin de le répéter dans chaque 'if' ou 'case'.

        if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Menu Principal Entreprises")) {
            handleMainMenuClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Sélectionner Gérant Cible")) {
            handleSelectGerantForCreationClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Sélectionner Type d'Entreprise")) {
            handleSelectTypeForCreationClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Mes Entreprises (Gérant/Employé)")) {
            handleMyEntreprisesMenuClick(player, itemName);
        } else if (topInventoryTitle.startsWith(ChatColor.DARK_BLUE + "Gérer: ")) {
            handleManageSpecificEntrepriseMenuClick(player, itemName);
        } else if (topInventoryTitle.startsWith(ChatColor.BLUE + "Détails: ")) {
            handleViewSpecificEntrepriseMenuClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Recruter Employé")) {
            handleRecruitEmployeeSelectionClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Confirmer Recrutement")) {
            handleRecruitConfirmationClick(player, clickedItem);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Gérer Employés")) {
            handleManageEmployeesListClick(player, itemName);
        } else if (topInventoryTitle.startsWith(ChatColor.DARK_BLUE + "Options pour ")) {
            handleSpecificEmployeeOptionsMenuClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Définir Prime Horaire")) {
            handleSetPrimeAmountClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Confirmer Suppression Entreprise")) {
            handleDeleteConfirmationClick(player, itemName);
        } else if (topInventoryTitle.equals(ChatColor.DARK_BLUE + "Lister Entreprises par Ville")) {
            handleListTownsMenuClick(player, itemName);
        } else if (topInventoryTitle.startsWith(ChatColor.DARK_BLUE + "Entreprises à ")) {
            handleViewEntrepriseFromListClick(player, itemName, topInventoryTitle);
        } else if (topInventoryTitle.equals(ChatColor.RED + "Menu Administration")) {
            handleAdminMenuClick(player, itemName);
        } else if (topInventoryTitle.startsWith(ChatColor.DARK_RED + "Quitter ")) {
            handleLeaveConfirmationClick(player, itemName);
        }
        // Gérer le bouton "Retour" s'il est cliqué DANS un de nos menus
        else if (itemName.equals("Retour") || itemName.startsWith("Retour (")) {
            // isPluginMenu(topInventoryTitle) est déjà vrai ici
            openPreviousInventoryOrMain(player);
        }
    }


    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Menu Principal Entreprises");
        inv.setItem(10, createMenuItem(Material.WRITABLE_BOOK, ChatColor.GOLD + "Créer une Entreprise", Arrays.asList(ChatColor.GRAY + "Pour les Maires.")));
        inv.setItem(12, createMenuItem(Material.MAP, ChatColor.GOLD + "Lister les Entreprises", Arrays.asList(ChatColor.GRAY + "Voir les entreprises par ville.")));
        inv.setItem(14, createMenuItem(Material.CHEST, ChatColor.GOLD + "Mes Entreprises", Arrays.asList(ChatColor.GRAY + "Gérer ou voir vos entreprises.")));
        if (player.hasPermission("entreprisemanager.admin")) {
            inv.setItem(16, createMenuItem(Material.COMMAND_BLOCK, ChatColor.RED + "Menu Administration", Arrays.asList(ChatColor.GRAY + "Actions réservées aux admins.")));
        }
        player.openInventory(inv);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMenuItem(Material material, String name) {
        return createMenuItem(material, name, null);
    }

    private ItemStack createPlayerHead(String playerName, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName); // Utilise Bukkit.getOfflinePlayer(UUID) si possible pour la performance et la pérennité des noms
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(ChatColor.AQUA + playerName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }


    private void openPreviousInventoryOrMain(Player player) {
        // Pour l'instant, retour simple au menu principal.
        // Pour une gestion plus avancée, vous pourriez utiliser une pile d'inventaires précédents.
        openMainMenu(player);
    }


    private void handleMainMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "Créer une Entreprise":
                if (entrepriseLogic.estMaire(player)) {
                    openCreateEntrepriseSelectGerantMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Seuls les maires peuvent initier la création d'entreprises.");
                    player.closeInventory();
                }
                break;
            case "Lister les Entreprises":
                openListTownsMenu(player);
                break;
            case "Mes Entreprises":
                openMyEntreprisesMenu(player);
                break;
            case "Menu Administration":
                if (player.hasPermission("entreprisemanager.admin")) {
                    openAdminMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
                    player.closeInventory();
                }
                break;
        }
    }

    private void openCreateEntrepriseSelectGerantMenu(Player maire) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Sélectionner Gérant Cible");
        Collection<String> residents = entrepriseLogic.getPlayersInMayorTown(maire);
        boolean foundEligible = false;
        if (residents.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun résident dans votre ville."));
        } else {
            for (String residentName : residents) {
                Player residentPlayer = Bukkit.getPlayerExact(residentName);
                if (residentPlayer != null) { // On ne traite que les joueurs en ligne pour la sélection
                    if (entrepriseLogic.peutCreerEntreprise(residentPlayer)) {
                        inv.addItem(createPlayerHead(residentName, Arrays.asList(ChatColor.GRAY + "Cliquez pour sélectionner comme gérant.")));
                        foundEligible = true;
                    }
                }
            }
            if (!foundEligible) {
                inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun résident éligible et en ligne pour être gérant."));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        maire.openInventory(inv);
    }

    private void handleSelectGerantForCreationClick(Player maire, String itemName) {
        if (itemName.equals("Retour")) {
            openMainMenu(maire);
            return;
        }
        // Le itemName est le nom du joueur (gérant cible)
        selectedGerantForCreation.put(maire, itemName);
        openCreateEntrepriseSelectTypeMenu(maire);
    }

    private void openCreateEntrepriseSelectTypeMenu(Player maire) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Sélectionner Type d'Entreprise");
        Set<String> types = entrepriseLogic.getTypesEntreprise();
        if (types.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun type d'entreprise n'est configuré."));
        } else {
            for (String type : types) {
                double cout = plugin.getConfig().getDouble("types-entreprise." + type + ".cout-creation", 0);
                inv.addItem(createMenuItem(Material.PAPER, ChatColor.AQUA + type, Collections.singletonList(ChatColor.GOLD + "Coût: " + String.format("%,.2f", cout) + "€")));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour")); // Retour vers la sélection du gérant
        maire.openInventory(inv);
    }

    private void handleSelectTypeForCreationClick(Player maire, String itemName) {
        if (itemName.equals("Retour")) {
            selectedGerantForCreation.remove(maire);
            openCreateEntrepriseSelectGerantMenu(maire);
            return;
        }
        String typeEntreprise = itemName;
        String gerantCibleNom = selectedGerantForCreation.get(maire);

        if (gerantCibleNom == null) {
            maire.sendMessage(ChatColor.RED + "Erreur: Aucun gérant cible n'a été sélectionné. Veuillez recommencer.");
            openCreateEntrepriseSelectGerantMenu(maire);
            return;
        }
        Player gerantCiblePlayer = Bukkit.getPlayerExact(gerantCibleNom);
        if (gerantCiblePlayer == null || !gerantCiblePlayer.isOnline()) {
            maire.sendMessage(ChatColor.RED + "Le gérant cible '" + gerantCibleNom + "' n'est plus en ligne. Processus annulé.");
            selectedGerantForCreation.remove(maire);
            openCreateEntrepriseSelectGerantMenu(maire);
            return;
        }
        String villeDuMaire = entrepriseLogic.getTownNameFromPlayer(maire);
        if (villeDuMaire == null) {
            maire.sendMessage(ChatColor.RED + "Erreur: Impossible de déterminer votre ville.");
            maire.closeInventory();
            selectedGerantForCreation.remove(maire);
            return;
        }
        String nomPropose = typeEntreprise + "_" + gerantCibleNom.substring(0, Math.min(gerantCibleNom.length(), 4)) + "_" + (new Random().nextInt(9000) + 1000);
        String siretPropose = entrepriseLogic.generateSiret();

        entrepriseLogic.proposerCreationEntreprise(maire, gerantCiblePlayer, typeEntreprise, villeDuMaire, nomPropose, siretPropose);
        maire.closeInventory();
        selectedGerantForCreation.remove(maire);
    }

    private void openMyEntreprisesMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Mes Entreprises (Gérant/Employé)");
        boolean found = false;
        List<EntrepriseManagerLogic.Entreprise> gerees = entrepriseLogic.getEntreprisesGereesPar(player.getName());
        for (EntrepriseManagerLogic.Entreprise e : gerees) {
            inv.addItem(createMenuItem(Material.GOLD_BLOCK, ChatColor.GOLD + e.getNom(), Arrays.asList(ChatColor.YELLOW + "Rôle: Gérant", ChatColor.GRAY + "Type: " + e.getType())));
            found = true;
        }
        for (EntrepriseManagerLogic.Entreprise e : entrepriseLogic.getEntreprises()) {
            if (e.getEmployes().contains(player.getName()) && !gerees.contains(e)) {
                inv.addItem(createMenuItem(Material.IRON_INGOT, ChatColor.AQUA + e.getNom(), Arrays.asList(ChatColor.YELLOW + "Rôle: Employé", ChatColor.GRAY + "Type: " + e.getType())));
                found = true;
            }
        }
        if (!found) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Vous n'êtes lié à aucune entreprise."));
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        player.openInventory(inv);
    }

    private void handleMyEntreprisesMenuClick(Player player, String itemName) {
        if (itemName.equals("Retour")) {
            openMainMenu(player);
            return;
        }
        String nomEntreprise = itemName;
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'a pas été trouvée.");
            openMyEntreprisesMenu(player);
            return;
        }
        currentOpenEntreprise.put(player, nomEntreprise);
        if (entreprise.getGerant().equalsIgnoreCase(player.getName())) {
            openManageSpecificEntrepriseMenu(player, entreprise);
        } else if (entreprise.getEmployes().contains(player.getName())) {
            openViewSpecificEntrepriseMenu(player, entreprise);
        } else {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas affilié à l'entreprise '" + nomEntreprise + "'.");
            currentOpenEntreprise.remove(player);
            openMyEntreprisesMenu(player);
        }
    }

    private void openManageSpecificEntrepriseMenu(Player gerant, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 36, ChatColor.DARK_BLUE + "Gérer: " + entreprise.getNom());
        inv.setItem(0, createMenuItem(Material.BOOK, ChatColor.AQUA + "Infos Entreprise", Arrays.asList(ChatColor.GRAY + "Voir les détails", ChatColor.GREEN + "Solde: " + String.format("%,.2f", entreprise.getSolde()) + "€")));
        inv.setItem(1, createMenuItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Déposer Argent"));
        inv.setItem(2, createMenuItem(Material.IRON_INGOT, ChatColor.GOLD + "Retirer Argent"));
        inv.setItem(9, createMenuItem(Material.PLAYER_HEAD, ChatColor.GREEN + "Gérer Employés"));
        inv.setItem(10, createMenuItem(Material.NAME_TAG, ChatColor.GREEN + "Recruter Employé"));
        inv.setItem(11, createMenuItem(Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "Renommer Entreprise", Arrays.asList(ChatColor.GRAY + "Coût: " + plugin.getConfig().getDouble("rename-cost", 0) + "€")));
        inv.setItem(18, createMenuItem(Material.BARRIER, ChatColor.RED + "Dissoudre Entreprise"));
        inv.setItem(31, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void openViewSpecificEntrepriseMenu(Player employe, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Détails: " + entreprise.getNom());
        inv.setItem(11, createMenuItem(Material.BOOK, ChatColor.AQUA + "Informations", Arrays.asList(ChatColor.GREEN + "Solde: " + String.format("%,.2f", entreprise.getSolde()) + "€", ChatColor.GRAY + "Type: " + entreprise.getType(), ChatColor.GRAY + "Gérant: " + entreprise.getGerant())));
        double maPrime = entreprise.getPrimePourEmploye(employe.getName());
        inv.setItem(13, createMenuItem(Material.GOLD_NUGGET, ChatColor.GOLD + "Ma Prime Horaire", Collections.singletonList(String.format("%,.2f", maPrime) + "€/h")));
        inv.setItem(15, createMenuItem(Material.BELL, ChatColor.YELLOW + "Quitter l'Entreprise"));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        employe.openInventory(inv);
    }

    private void handleManageSpecificEntrepriseMenuClick(Player gerant, String itemName) {
        String nomEntreprise = currentOpenEntreprise.get(gerant);
        if (nomEntreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(gerant);
            return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: L'entreprise n'existe plus.");
            currentOpenEntreprise.remove(gerant);
            openMyEntreprisesMenu(gerant);
            return;
        }
        switch (itemName) {
            case "Infos Entreprise":
                displayEntrepriseInfo(gerant, entreprise);
                break;
            case "Déposer Argent":
                plugin.getChatListener().attendreMontantDepot(gerant, nomEntreprise);
                gerant.closeInventory();
                break;
            case "Retirer Argent":
                plugin.getChatListener().attendreMontantRetrait(gerant, nomEntreprise);
                gerant.closeInventory();
                break;
            case "Gérer Employés":
                openManageEmployeesListMenu(gerant, entreprise);
                break;
            case "Recruter Employé":
                openRecruitEmployeeProximityMenu(gerant, entreprise);
                break;
            case "Renommer Entreprise":
                plugin.getChatListener().attendreNouveauNomEntreprise(gerant, nomEntreprise); // Modifié pour utiliser ChatListener directement
                gerant.closeInventory();
                break;
            case "Dissoudre Entreprise":
                openDeleteConfirmationMenu(gerant, nomEntreprise);
                break;
            case "Retour":
                currentOpenEntreprise.remove(gerant);
                openMyEntreprisesMenu(gerant);
                break;
        }
    }

    private void handleViewSpecificEntrepriseMenuClick(Player employe, String itemName) {
        String nomEntreprise = currentOpenEntreprise.get(employe);
        if (nomEntreprise == null) {
            employe.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(employe);
            return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            employe.sendMessage(ChatColor.RED + "Erreur: L'entreprise n'existe plus.");
            currentOpenEntreprise.remove(employe);
            openMyEntreprisesMenu(employe);
            return;
        }
        switch (itemName) {
            case "Informations":
                displayEntrepriseInfo(employe, entreprise);
                break;
            case "Ma Prime Horaire":
                double maPrime = entreprise.getPrimePourEmploye(employe.getName());
                employe.sendMessage(ChatColor.GOLD + "Votre prime horaire pour '" + ChatColor.AQUA + nomEntreprise + ChatColor.GOLD + "' est de: " + ChatColor.YELLOW + String.format("%,.2f", maPrime) + "€/h.");
                break;
            case "Quitter l'Entreprise":
                openLeaveConfirmationMenu(employe, entreprise);
                break;
            case "Retour":
                currentOpenEntreprise.remove(employe);
                openMyEntreprisesMenu(employe);
                break;
        }
    }

    private void openLeaveConfirmationMenu(Player employe, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Quitter " + entreprise.getNom() + "?");
        inv.setItem(11, createMenuItem(Material.RED_WOOL, ChatColor.DARK_RED + "OUI, Quitter " + entreprise.getNom()));
        inv.setItem(15, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "NON, Rester"));
        employe.openInventory(inv);
    }

    private void handleLeaveConfirmationClick(Player employe, String itemName) {
        String nomEntreprise = currentOpenEntreprise.get(employe); // Devrait être défini
        if (nomEntreprise == null) {
            employe.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(employe);
            return;
        }
        if (itemName.startsWith("OUI, Quitter ")) {
            entrepriseLogic.leaveEntreprise(employe, nomEntreprise);
            employe.closeInventory();
            currentOpenEntreprise.remove(employe);
        } else if (itemName.equals("NON, Rester")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
            if (entreprise != null) openViewSpecificEntrepriseMenu(employe, entreprise);
            else openMyEntreprisesMenu(employe);
        }
    }

    private void openRecruitEmployeeProximityMenu(Player gerant, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Recruter Employé");
        Collection<String> nearbyPlayers = entrepriseLogic.getNearbyPlayers(gerant, plugin.getConfig().getInt("invitation.distance-max", 10));
        boolean foundCandidate = false;
        if (nearbyPlayers.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucun joueur à proximité."));
        } else {
            for (String targetName : nearbyPlayers) {
                if (!targetName.equals(gerant.getName()) && entrepriseLogic.getNomEntrepriseDuMembre(targetName) == null && entrepriseLogic.joueurPeutRejoindreAutreEntreprise(targetName)) {
                    inv.addItem(createPlayerHead(targetName, Collections.singletonList(ChatColor.GRAY + "Cliquez pour inviter.")));
                    foundCandidate = true;
                }
            }
            if (!foundCandidate) {
                inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucun joueur éligible à proximité."));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void handleRecruitEmployeeSelectionClick(Player gerant, String itemName) {
        if (itemName.equals("Retour")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(currentOpenEntreprise.get(gerant));
            if (entreprise != null) openManageSpecificEntrepriseMenu(gerant, entreprise);
            else openMyEntreprisesMenu(gerant);
            return;
        }
        String targetPlayerName = itemName;
        Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);
        String nomEntreprise = currentOpenEntreprise.get(gerant);
        if (nomEntreprise == null) { /* ... */ return; }
        if (targetPlayer == null || !targetPlayer.isOnline()) { /* ... */ return; }
        openRecruitConfirmationMenu(gerant, targetPlayerName, nomEntreprise); // nomEntreprise est déjà connu via currentOpenEntreprise
    }

    private void openRecruitConfirmationMenu(Player gerant, String targetPlayerName, String nomEntreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer Recrutement");
        inv.setItem(11, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "Oui, inviter " + targetPlayerName));
        inv.setItem(15, createMenuItem(Material.RED_WOOL, ChatColor.RED + "Non, annuler"));
        // currentOpenEntreprise.put(gerant, nomEntreprise); // Pas besoin, déjà fait
        // selectedEmployeeForManagement.put(gerant, targetPlayerName); // Plutôt pour gérer un employé existant
        gerant.openInventory(inv);
    }

    private void handleRecruitConfirmationClick(Player gerant, ItemStack clickedItem) {
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        String nomEntreprise = currentOpenEntreprise.get(gerant);
        if (nomEntreprise == null) { /* ... */ return; }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
        if (entreprise == null) { /* ... */ return; }

        if (itemName.startsWith("Oui, inviter ")) {
            String targetPlayerName = itemName.substring("Oui, inviter ".length());
            Player targetOnlinePlayer = Bukkit.getPlayerExact(targetPlayerName);
            if (targetOnlinePlayer != null && targetOnlinePlayer.isOnline()) {
                entrepriseLogic.inviterEmploye(gerant, nomEntreprise, targetOnlinePlayer);
            } else {
                gerant.sendMessage(ChatColor.RED + "Le joueur " + targetPlayerName + " n'est plus en ligne.");
            }
        } else if (itemName.equals("Non, annuler")) {
            gerant.sendMessage(ChatColor.YELLOW + "Recrutement annulé.");
        }
        openManageSpecificEntrepriseMenu(gerant, entreprise); // Revenir au menu de gestion
    }

    private void openManageEmployeesListMenu(Player gerant, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Gérer Employés");
        Set<String> employes = entrepriseLogic.getEmployesDeLEntreprise(entreprise.getNom());
        if (employes.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Cette entreprise n'a aucun employé."));
        } else {
            for (String empName : employes) {
                double prime = entreprise.getPrimePourEmploye(empName);
                inv.addItem(createPlayerHead(empName, Arrays.asList(ChatColor.GOLD + "Prime: " + String.format("%,.2f", prime) + "€/h", ChatColor.GRAY + "Cliquez pour options.")));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void handleManageEmployeesListClick(Player gerant, String itemName) {
        if (itemName.equals("Retour")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(currentOpenEntreprise.get(gerant));
            if (entreprise != null) openManageSpecificEntrepriseMenu(gerant, entreprise);
            else openMyEntreprisesMenu(gerant);
            return;
        }
        String selectedEmpName = itemName;
        String nomEntrepriseGeree = currentOpenEntreprise.get(gerant);
        if (nomEntrepriseGeree == null) { /* ... */ return; }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntrepriseGeree);
        if (entreprise == null || !entreprise.getEmployes().contains(selectedEmpName)) { /* ... */ return; }

        selectedEmployeeForManagement.put(gerant, selectedEmpName);
        openSpecificEmployeeOptionsMenu(gerant, selectedEmpName, entreprise);
    }

    private void openSpecificEmployeeOptionsMenu(Player gerant, String employeNom, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Options pour " + employeNom);
        double primeActuelle = entreprise.getPrimePourEmploye(employeNom);
        inv.setItem(11, createMenuItem(Material.GOLD_INGOT, ChatColor.GREEN + "Définir Prime Horaire", Collections.singletonList(ChatColor.GRAY + "Actuelle: " + String.format("%,.2f", primeActuelle) + "€/h")));
        inv.setItem(15, createMenuItem(Material.RED_WOOL, ChatColor.RED + "Virer " + employeNom));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void handleSpecificEmployeeOptionsMenuClick(Player gerant, String itemName) {
        String employeNom = selectedEmployeeForManagement.get(gerant);
        String nomEntrepriseGeree = currentOpenEntreprise.get(gerant);
        if (employeNom == null || nomEntrepriseGeree == null) { /* ... */ return; }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntrepriseGeree);
        if (entreprise == null) { /* ... */ selectedEmployeeForManagement.remove(gerant); return; }

        if (itemName.equals("Définir Prime Horaire")) {
            openSetPrimeAmountMenu(gerant, employeNom, entreprise);
        } else if (itemName.startsWith("Virer ")) {
            entrepriseLogic.kickEmploye(gerant, nomEntrepriseGeree, employeNom);
            selectedEmployeeForManagement.remove(gerant);
            openManageEmployeesListMenu(gerant, entreprise);
        } else if (itemName.equals("Retour")) {
            selectedEmployeeForManagement.remove(gerant);
            openManageEmployeesListMenu(gerant, entreprise);
        }
    }

    private void openSetPrimeAmountMenu(Player gerant, String employeNom, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Définir Prime Horaire");
        // Mettre employeNom dans le titre pour plus de clarté
        // Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Prime pour " + employeNom);
        double primeActuelle = entreprise.getPrimePourEmploye(employeNom);
        List<Double> montantsProposes = Arrays.asList(0.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0, 750.0, 1000.0, 1250.0, 1500.0, 2000.0);
        for (double montant : montantsProposes) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + String.format("%,.2f", montant) + "€", Collections.singletonList((montant == primeActuelle) ? ChatColor.GREEN + "(Actuelle)" : "")));
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void handleSetPrimeAmountClick(Player gerant, String itemName) {
        String employeNom = selectedEmployeeForManagement.get(gerant);
        String nomEntrepriseGeree = currentOpenEntreprise.get(gerant);
        if (employeNom == null || nomEntrepriseGeree == null) { /* ... */ return; }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntrepriseGeree);
        if (entreprise == null) { /* ... */ selectedEmployeeForManagement.remove(gerant); return; }

        if (itemName.equals("Retour")) {
            openSpecificEmployeeOptionsMenu(gerant, employeNom, entreprise);
            return;
        }
        try {
            String montantStr = itemName.replace("€", "").trim().replace(",", ".");
            double nouvellePrime = Double.parseDouble(montantStr);
            if (nouvellePrime < 0) { /* ... */ return; }
            entrepriseLogic.definirPrime(nomEntrepriseGeree, employeNom, nouvellePrime);
            gerant.sendMessage(ChatColor.GREEN + "Prime de " + employeNom + " définie à " + String.format("%,.2f", nouvellePrime) + "€ pour '" + nomEntrepriseGeree + "'.");
            Player employePlayer = Bukkit.getPlayerExact(employeNom);
            if (employePlayer != null && employePlayer.isOnline()) {
                employePlayer.sendMessage(ChatColor.GOLD + "Votre prime pour '" + nomEntrepriseGeree + "' est maintenant de " + String.format("%,.2f", nouvellePrime) + "€/h.");
            }
            openSpecificEmployeeOptionsMenu(gerant, employeNom, entreprise); // Retour aux options
        } catch (NumberFormatException e) {
            gerant.sendMessage(ChatColor.RED + "Montant invalide: '" + itemName + "'");
            openSetPrimeAmountMenu(gerant, employeNom, entreprise);
        }
    }

    private void openDeleteConfirmationMenu(Player gerant, String nomEntreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer Suppression Entreprise");
        inv.setItem(11, createMenuItem(Material.RED_WOOL, ChatColor.DARK_RED + "OUI, Dissoudre '" + nomEntreprise + "'"));
        inv.setItem(15, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "NON, Annuler"));
        gerant.openInventory(inv);
    }

    private void handleDeleteConfirmationClick(Player gerant, String itemName) {
        String nomEntreprise = currentOpenEntreprise.get(gerant);
        if (nomEntreprise == null) { /* ... */ return; }
        if (itemName.startsWith("OUI, Dissoudre")) {
            entrepriseLogic.supprimerEntreprise(gerant, nomEntreprise);
            currentOpenEntreprise.remove(gerant);
            openMainMenu(gerant);
        } else if (itemName.equals("NON, Annuler")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
            if (entreprise != null) openManageSpecificEntrepriseMenu(gerant, entreprise);
            else openMyEntreprisesMenu(gerant);
        }
    }

    private void openListTownsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Lister Entreprises par Ville");
        Collection<String> towns = entrepriseLogic.getAllTownsNames();
        if (towns.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune ville trouvée."));
        } else {
            for (String townName : towns) {
                inv.addItem(createMenuItem(Material.PAPER, ChatColor.AQUA + townName, Collections.singletonList(ChatColor.GRAY + "Cliquez pour voir.")));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        player.openInventory(inv);
    }

    private void handleListTownsMenuClick(Player player, String itemName) {
        if (itemName.equals("Retour")) {
            openMainMenu(player);
            return;
        }
        openListEntreprisesInTownMenu(player, itemName);
    }

    private void openListEntreprisesInTownMenu(Player player, String townName) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Entreprises à " + townName);
        List<EntrepriseManagerLogic.Entreprise> entreprises = entrepriseLogic.getEntreprisesByVille(townName);
        if (entreprises.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune entreprise dans cette ville."));
        } else {
            for (EntrepriseManagerLogic.Entreprise e : entreprises) {
                inv.addItem(createMenuItem(Material.BOOK, ChatColor.GOLD + e.getNom(), Arrays.asList(ChatColor.GRAY + "Type: " + e.getType(), ChatColor.GRAY + "Gérant: " + e.getGerant())));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        player.openInventory(inv);
    }

    private void handleViewEntrepriseFromListClick(Player player, String itemName, String inventoryTitle) {
        if (itemName.equals("Retour")) {
            openListTownsMenu(player);
            return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(itemName);
        if (entreprise != null) {
            displayEntrepriseInfo(player, entreprise);
        } else {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + itemName + "' n'existe pas.");
            // Pour ré-ouvrir le menu actuel :
            // String townNameFromTitle = inventoryTitle.substring((ChatColor.DARK_BLUE + "Entreprises à ").length());
            // openListEntreprisesInTownMenu(player, townNameFromTitle);
        }
    }

    private void openAdminMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.RED + "Menu Administration");
        inv.setItem(11, createMenuItem(Material.CLOCK, ChatColor.YELLOW + "Forcer Cycle Paiements"));
        inv.setItem(13, createMenuItem(Material.COMMAND_BLOCK_MINECART, ChatColor.AQUA + "Recharger Configuration"));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour Menu Principal"));
        player.openInventory(inv);
    }

    private void handleAdminMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "Forcer Cycle Paiements":
                if (player.hasPermission("entreprisemanager.admin.forcepay")) {
                    plugin.getEntrepriseLogic().traiterChiffreAffairesHoraire();
                    plugin.getEntrepriseLogic().payerPrimesHorairesAuxEmployes();
                    plugin.getEntrepriseLogic().payerAllocationChomageHoraire();
                    player.sendMessage(ChatColor.GREEN + "Cycle de paiement horaire global forcé !");
                } else {
                    player.sendMessage(ChatColor.RED + "Permission manquante (entreprisemanager.admin.forcepay).");
                }
                player.closeInventory();
                break;
            case "Recharger Configuration":
                if (player.hasPermission("entreprisemanager.admin.reload")) {
                    plugin.reloadPlugin();
                    player.sendMessage(ChatColor.GREEN + "Plugin EntrepriseManager et données rechargés.");
                } else {
                    player.sendMessage(ChatColor.RED + "Permission manquante (entreprisemanager.admin.reload).");
                }
                player.closeInventory();
                break;
            case "Retour Menu Principal":
                openMainMenu(player);
                break;
        }
    }

    // Dans EntrepriseGUI.java
    public void displayEntrepriseInfo(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Informations Entreprise: " + ChatColor.AQUA + entreprise.getNom() + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + entreprise.getVille());
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + entreprise.getType());
        player.sendMessage(ChatColor.YELLOW + "Gérant: " + ChatColor.WHITE + entreprise.getGerant());
        player.sendMessage(ChatColor.YELLOW + "Employés: " + ChatColor.WHITE + entreprise.getEmployes().size() + "/" + plugin.getConfig().getInt("finance.max-employer-par-entreprise", 10));
        player.sendMessage(ChatColor.YELLOW + "Solde Actuel: " + ChatColor.GREEN + String.format("%,.2f", entreprise.getSolde()) + "€");

        double caPotentiel = entrepriseLogic.getActiviteHoraireValeurPour(entreprise.getNom());
        player.sendMessage(ChatColor.YELLOW + "CA Potentiel (cette heure): " + ChatColor.AQUA + String.format("%,.2f", caPotentiel) + "€");
        player.sendMessage(ChatColor.YELLOW + "Chiffre d'Affaires Total (brut): " + ChatColor.DARK_GREEN + String.format("%,.2f", entreprise.getChiffreAffairesTotal()) + "€");
        player.sendMessage(ChatColor.YELLOW + "SIRET: " + ChatColor.WHITE + entreprise.getSiret());

        if (entreprise.getGerant().equalsIgnoreCase(player.getName()) || player.hasPermission("entreprisemanager.admin.info")) {
            if (!entreprise.getPrimes().isEmpty()) {
                player.sendMessage(ChatColor.GOLD + "Primes Horaires des Employés:");
                for (Map.Entry<String, Double> primeEntry : entreprise.getPrimes().entrySet()) {
                    player.sendMessage(ChatColor.GRAY + "  - " + primeEntry.getKey() + ": " + ChatColor.YELLOW + String.format("%,.2f", primeEntry.getValue()) + "€/h");
                }
            } else {
                player.sendMessage(ChatColor.GOLD + "Primes Horaires: " + ChatColor.GRAY + "Aucune définie.");
            }
            // Pour afficher la liste des employés et leurs noms :
            if (!entreprise.getEmployes().isEmpty()) {
                player.sendMessage(ChatColor.GOLD + "Liste des Employés:");
                for (String nomEmploye : entreprise.getEmployes()) {
                    // Récupérer la prime de l'employé pour l'afficher à côté si souhaité
                    double primeEmploye = entreprise.getPrimePourEmploye(nomEmploye);
                    player.sendMessage(ChatColor.GRAY + "  - " + nomEmploye + ChatColor.YELLOW + " (Prime: " + String.format("%,.2f", primeEmploye) + "€/h)");
                }
            } else {
                player.sendMessage(ChatColor.GOLD + "Liste des Employés: " + ChatColor.GRAY + "Aucun employé.");
            }

        }
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "==============================================");
    }
    @EventHandler // Gestion du renommage via chat (si ChatListener ne le fait pas)
    public void onAsyncPlayerChatForRename(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingRename_OldName.containsKey(playerId)) {
            event.setCancelled(true);
            plugin.getLogger().info("[EntrepriseGUI DEBUG] AsyncPlayerChatEvent cancelled for rename by " + player.getName());
            String nouveauNom = event.getMessage().trim();
            String ancienNom = pendingRename_OldName.remove(playerId);

            if (nouveauNom.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Renommage annulé.");
                // Optionnel: réouvrir le menu de gestion si possible
                return;
            }
            // ... (logique de validation du nom) ...
            if (!nouveauNom.matches("^[a-zA-Z0-9_\\-]+$")) {
                player.sendMessage(ChatColor.RED + "Le nom contient des caractères invalides.");
                pendingRename_OldName.put(playerId, ancienNom); // Redemander
                return;
            }
            if (entrepriseLogic.getEntreprise(nouveauNom) != null) {
                player.sendMessage(ChatColor.RED + "Ce nom d'entreprise existe déjà.");
                pendingRename_OldName.put(playerId, ancienNom); // Redemander
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                entrepriseLogic.renameEntreprise(player, ancienNom, nouveauNom);
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        // Nettoyage des états si nécessaire, mais attention à ne pas interférer
        // avec la navigation entre menus ou les processus en plusieurs étapes comme la création.
        // selectedGerantForCreation.remove(player); // Plutôt nettoyer à la fin de la création ou annulation.
        // currentOpenEntreprise n'est pas nettoyé ici pour permettre la navigation.
    }
}