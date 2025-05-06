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
import org.bukkit.event.player.AsyncPlayerChatEvent; // Gardé pour le renommage si géré ici
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class EntrepriseGUI implements Listener {

    private final EntrepriseManager plugin;
    private final EntrepriseManagerLogic entrepriseLogic;

    private final Map<Player, String> selectedGerantForCreation = new HashMap<>();
    private final Map<Player, String> selectedTypeForCreation = new HashMap<>();
    private final Map<UUID, Long> clickTimestamps = new HashMap<>();

    private final Map<Player, String> currentOpenEntreprise = new HashMap<>();
    private final Map<Player, String> selectedEmployeeForManagement = new HashMap<>();

    // Pour le renommage via chat (si ChatListener ne le gère pas entièrement)
    private final Map<UUID, String> pendingRename_OldName = new HashMap<>();
    private final Map<Player, Inventory> previousInventories = new HashMap<>(); // Pour le bouton retour


    private static final long CLICK_DELAY_MS = 500;

    public EntrepriseGUI(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Si ChatListener gère la saisie du nom, pas besoin d'enregistrer AsyncPlayerChatEvent ici.
        // Sinon, décommentez :
        // plugin.getServer().getPluginManager().registerEvents(this, plugin); // Pour AsyncPlayerChatEvent
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
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(ChatColor.AQUA + playerName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        String inventoryTitle = event.getView().getTitle();

        long currentTime = System.currentTimeMillis();
        if (clickTimestamps.getOrDefault(playerId, 0L) + CLICK_DELAY_MS > currentTime) {
            event.setCancelled(true);
            return;
        }
        clickTimestamps.put(playerId, currentTime);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || clickedItem.getItemMeta().getDisplayName() == null) {
            return;
        }
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // MODIFIÉ : Suppression de l'ancien bloc if/else qui causait le 'return' prématuré.
        // La logique de event.setCancelled(true) sera gérée dans chaque bloc de gestion de menu.

        if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Menu Principal Entreprises")) {
            event.setCancelled(true); // AJOUTÉ
            handleMainMenuClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Sélectionner Gérant Cible")) {
            event.setCancelled(true); // AJOUTÉ
            handleSelectGerantForCreationClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Sélectionner Type d'Entreprise")) {
            event.setCancelled(true); // AJOUTÉ
            handleSelectTypeForCreationClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Mes Entreprises (Gérant/Employé)")) {
            event.setCancelled(true); // AJOUTÉ
            handleMyEntreprisesMenuClick(player, itemName);
        } else if (inventoryTitle.startsWith(ChatColor.DARK_BLUE + "Gérer: ")) {
            event.setCancelled(true); // AJOUTÉ
            handleManageSpecificEntrepriseMenuClick(player, itemName);
        } else if (inventoryTitle.startsWith(ChatColor.BLUE + "Détails: ")) { // Titres commençant par BLUE
            event.setCancelled(true); // AJOUTÉ
            handleViewSpecificEntrepriseMenuClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Recruter Employé")) {
            event.setCancelled(true); // AJOUTÉ
            handleRecruitEmployeeSelectionClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Confirmer Recrutement")) {
            event.setCancelled(true); // AJOUTÉ
            handleRecruitConfirmationClick(player, clickedItem);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Gérer Employés")) {
            event.setCancelled(true); // AJOUTÉ
            handleManageEmployeesListClick(player, itemName);
        } else if (inventoryTitle.startsWith(ChatColor.DARK_BLUE + "Options pour ")) {
            event.setCancelled(true); // AJOUTÉ
            handleSpecificEmployeeOptionsMenuClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Définir Prime Horaire")) {
            event.setCancelled(true); // AJOUTÉ
            handleSetPrimeAmountClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Confirmer Suppression Entreprise")) {
            event.setCancelled(true); // AJOUTÉ
            handleDeleteConfirmationClick(player, itemName);
        } else if (inventoryTitle.equals(ChatColor.DARK_BLUE + "Lister Entreprises par Ville")) {
            event.setCancelled(true); // AJOUTÉ
            handleListTownsMenuClick(player, itemName);
        } else if (inventoryTitle.startsWith(ChatColor.DARK_BLUE + "Entreprises à ")) {
            event.setCancelled(true); // AJOUTÉ
            handleViewEntrepriseFromListClick(player, itemName, inventoryTitle);
        } else if (inventoryTitle.equals(ChatColor.RED + "Menu Administration")) { // Titre du menu admin
            event.setCancelled(true); // AJOUTÉ - Crucial pour le menu admin
            handleAdminMenuClick(player, itemName);
        } else if (inventoryTitle.startsWith(ChatColor.DARK_RED + "Quitter ")) { // Titre de confirmation pour quitter
            event.setCancelled(true); // AJOUTÉ - Crucial pour la confirmation
            handleLeaveConfirmationClick(player, itemName);
        } else if (itemName.equals("Retour") || itemName.startsWith("Retour (") ) {
            // Assurez-vous que ce bouton retour est dans un contexte où l'inventaire est géré par ce plugin.
            // S'il peut apparaître dans d'autres inventaires, cette logique pourrait être problématique.
            // Pour l'instant, on suppose qu'il s'agit d'un "Retour" dans les menus de ce plugin.
            event.setCancelled(true); // AJOUTÉ
            openPreviousInventoryOrMain(player);
        }
        // Si aucun des if/else if ci-dessus ne correspond, l'événement n'est pas annulé ici,
        // ce qui permet à d'autres plugins ou au comportement par défaut de fonctionner.
    }

    private void openPreviousInventoryOrMain(Player player) {
        // Cette méthode est un placeholder. Idéalement, chaque menu gère son "retour" spécifiquement.
        // Pour une solution simple, on retourne au menu principal.
        // Pour une meilleure UX, stocker une pile d'inventaires précédents.
        // previousInventories.get(player) n'est pas utilisé ici pour l'instant.
        openMainMenu(player);
    }


    private void handleMainMenuClick(Player player, String itemName) {
        // ... (comme avant, mais s'assurer que les permissions et conditions sont à jour)
        switch (itemName) {
            case "Créer une Entreprise":
                if (entrepriseLogic.estMaire(player)) { // estMaire() est dans EntrepriseManagerLogic
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
                if (residentPlayer != null) {
                    if (entrepriseLogic.peutCreerEntreprise(residentPlayer)) {
                        inv.addItem(createPlayerHead(residentName, Arrays.asList(ChatColor.GRAY + "Cliquez pour sélectionner comme gérant.")));
                        foundEligible = true;
                    }
                } else {
                    // Pourrait nécessiter une logique pour vérifier les joueurs hors ligne si cela est souhaité.
                    // Pour l'instant, on suppose que le gérant doit être en ligne pour une sélection facile.
                    // Alternative: lister quand même avec une indication (hors ligne).
                    // Pour simplifier, on ne liste que les joueurs en ligne et éligibles.
                }
            }
            if (!foundEligible) {
                inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun résident éligible ou en ligne pour être gérant."));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        maire.openInventory(inv);
    }

    private void handleSelectGerantForCreationClick(Player maire, String itemName) {
        if (itemName.equals("Retour")) { // MODIFIÉ: "Retour au Menu Principal" était plus spécifique, "Retour" est plus générique ici
            openMainMenu(maire); // MODIFIÉ : Assumant que le retour ici est vers le menu principal.
            return;
        }
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
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        maire.openInventory(inv);
    }
    private void handleSelectTypeForCreationClick(Player maire, String itemName) {
        if (itemName.equals("Retour")) {
            selectedGerantForCreation.remove(maire); // Nettoyer l'état
            openCreateEntrepriseSelectGerantMenu(maire);
            return;
        }
        String typeEntreprise = itemName; // Le nom de l'item est le type
        String gerantCibleNom = selectedGerantForCreation.get(maire);

        if (gerantCibleNom == null) {
            maire.sendMessage(ChatColor.RED + "Erreur: Aucun gérant cible n'a été sélectionné. Veuillez recommencer.");
            openCreateEntrepriseSelectGerantMenu(maire); // Retour à l'étape précédente
            return;
        }

        Player gerantCiblePlayer = Bukkit.getPlayerExact(gerantCibleNom);

        if (gerantCiblePlayer == null || !gerantCiblePlayer.isOnline()){ // Il est préférable de vérifier aussi s'il est en ligne pour la proposition de contrat
            maire.sendMessage(ChatColor.RED + "Le gérant cible '" + gerantCibleNom + "' n'est plus en ligne ou est invalide. Processus annulé.");
            selectedGerantForCreation.remove(maire); // Nettoyer
            openCreateEntrepriseSelectGerantMenu(maire); // Retourner à la sélection du gérant
            return;
        }
        String villeDuMaire = entrepriseLogic.getTownNameFromPlayer(maire);
        if (villeDuMaire == null) {
            maire.sendMessage(ChatColor.RED + "Erreur: Impossible de déterminer votre ville. Assurez-vous d'être maire d'une ville Towny.");
            maire.closeInventory(); // Fermer car le processus ne peut continuer
            selectedGerantForCreation.remove(maire);
            return;
        }

        String nomPropose = typeEntreprise + "_" + gerantCibleNom.substring(0, Math.min(gerantCibleNom.length(), 4)) + "_" + (new Random().nextInt(9000) + 1000);
        String siretPropose = entrepriseLogic.generateSiret();

        entrepriseLogic.proposerCreationEntreprise(maire, gerantCiblePlayer, typeEntreprise, villeDuMaire, nomPropose, siretPropose);
        maire.closeInventory(); // Fermer le GUI après la proposition
        selectedGerantForCreation.remove(maire); // Nettoyer l'état
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
            if (e.getEmployes().contains(player.getName()) && !gerees.contains(e)) { // Assurez-vous que 'gerees' compare correctement les objets Entreprise ou leurs noms.
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
        String nomEntreprise = itemName; // Le nom de l'item est le nom de l'entreprise
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'a pas été trouvée.");
            openMyEntreprisesMenu(player); // Revenir au menu précédent
            return;
        }
        currentOpenEntreprise.put(player, nomEntreprise); // Stocker l'entreprise ouverte

        if (entreprise.getGerant().equalsIgnoreCase(player.getName())) {
            openManageSpecificEntrepriseMenu(player, entreprise);
        } else if (entreprise.getEmployes().contains(player.getName())) {
            openViewSpecificEntrepriseMenu(player, entreprise);
        } else {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas affilié à l'entreprise '" + nomEntreprise + "'.");
            currentOpenEntreprise.remove(player); // Nettoyer si pas affilié
            openMyEntreprisesMenu(player);
        }
    }

    private void openManageSpecificEntrepriseMenu(Player gerant, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 36, ChatColor.DARK_BLUE + "Gérer: " + entreprise.getNom());
        inv.setItem(0, createMenuItem(Material.BOOK, ChatColor.AQUA + "Infos Entreprise", Arrays.asList(ChatColor.GRAY + "Voir les détails", ChatColor.GREEN+"Solde: " + String.format("%,.2f", entreprise.getSolde())+"€")));
        inv.setItem(1, createMenuItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Déposer Argent"));
        inv.setItem(2, createMenuItem(Material.IRON_INGOT, ChatColor.GOLD + "Retirer Argent"));
        inv.setItem(9, createMenuItem(Material.PLAYER_HEAD, ChatColor.GREEN + "Gérer Employés"));
        inv.setItem(10, createMenuItem(Material.NAME_TAG, ChatColor.GREEN + "Recruter Employé"));
        inv.setItem(11, createMenuItem(Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "Renommer Entreprise", Arrays.asList(ChatColor.GRAY+"Coût: " + plugin.getConfig().getDouble("rename-cost", 0)+"€")));
        inv.setItem(18, createMenuItem(Material.BARRIER, ChatColor.RED + "Dissoudre Entreprise"));
        inv.setItem(31, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour")); // Retour à "Mes Entreprises"
        gerant.openInventory(inv);
    }

    private void openViewSpecificEntrepriseMenu(Player employe, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Détails: " + entreprise.getNom());
        inv.setItem(11, createMenuItem(Material.BOOK, ChatColor.AQUA + "Informations", Arrays.asList(ChatColor.GREEN+"Solde: " + String.format("%,.2f", entreprise.getSolde())+"€", ChatColor.GRAY+"Type: "+entreprise.getType(), ChatColor.GRAY+"Gérant: "+entreprise.getGerant())));
        double maPrime = entreprise.getPrimePourEmploye(employe.getName());
        inv.setItem(13, createMenuItem(Material.GOLD_NUGGET, ChatColor.GOLD + "Ma Prime Horaire", Collections.singletonList(String.format("%,.2f", maPrime) + "€/h")));
        inv.setItem(15, createMenuItem(Material.BELL, ChatColor.YELLOW + "Quitter l'Entreprise"));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour")); // Retour à "Mes Entreprises"
        employe.openInventory(inv);
    }

    private void handleManageSpecificEntrepriseMenuClick(Player gerant, String itemName) {
        String nomEntreprise = currentOpenEntreprise.get(gerant);
        if (nomEntreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(gerant); return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: L'entreprise n'existe plus.");
            currentOpenEntreprise.remove(gerant); // Nettoyer
            openMyEntreprisesMenu(gerant); return;
        }

        switch (itemName) {
            case "Infos Entreprise":
                displayEntrepriseInfo(gerant, entreprise);
                // Ne pas fermer l'inventaire, l'info est dans le chat.
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
                gerant.sendMessage(ChatColor.YELLOW + "Entrez le nouveau nom pour '" + nomEntreprise + "' dans le chat, ou 'annuler'.");
                plugin.getChatListener().attendreNouveauNomEntreprise(gerant, nomEntreprise);
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
                employe.sendMessage(ChatColor.GOLD + "Votre prime horaire pour '" + ChatColor.AQUA + nomEntreprise +
                        ChatColor.GOLD + "' est de: " + ChatColor.YELLOW + String.format("%,.2f", maPrime) + "€" + ChatColor.GOLD + ".");
                break;
            case "Quitter l'Entreprise":
                openLeaveConfirmationMenu(employe, entreprise);
                break;
            case "Retour": // MODIFIÉ : Le nom du bouton Retour est juste "Retour"
                currentOpenEntreprise.remove(employe);
                openMyEntreprisesMenu(employe);
                break;
            default:
                employe.sendMessage(ChatColor.YELLOW + "Action non reconnue dans ce menu.");
                break;
        }
    }

    private void openLeaveConfirmationMenu(Player employe, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Quitter " + entreprise.getNom() + "?");
        inv.setItem(11, createMenuItem(Material.RED_WOOL, ChatColor.DARK_RED + "OUI, Quitter " + entreprise.getNom()));
        inv.setItem(15, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "NON, Rester"));
        // currentOpenEntreprise devrait déjà être défini
        employe.openInventory(inv);
    }

    private void handleLeaveConfirmationClick(Player employe, String itemName) {
        String nomEntreprise = currentOpenEntreprise.get(employe);
        if (nomEntreprise == null) {
            employe.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu pour la confirmation.");
            openMyEntreprisesMenu(employe);
            return;
        }

        if (itemName.startsWith("OUI, Quitter ")) {
            entrepriseLogic.leaveEntreprise(employe, nomEntreprise);
            employe.closeInventory();
            currentOpenEntreprise.remove(employe); // Nettoyer
            // Optionnel: rediriger vers un autre menu, ex: openMainMenu(employe);
        } else if (itemName.equals("NON, Rester")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
            if (entreprise != null) {
                openViewSpecificEntrepriseMenu(employe, entreprise); // Retourner au menu précédent
            } else {
                openMyEntreprisesMenu(employe); // Si l'entreprise a disparu
            }
        } else {
            employe.sendMessage(ChatColor.YELLOW + "Action de confirmation non reconnue.");
        }
    }

    private void openRecruitEmployeeProximityMenu(Player gerant, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Recruter Employé");
        Collection<String> nearbyPlayers = entrepriseLogic.getNearbyPlayers(gerant, plugin.getConfig().getInt("invitation.distance-max", 10));
        boolean foundCandidate = false;
        if (nearbyPlayers.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW+"Aucun joueur à proximité."));
        } else {
            for (String targetName : nearbyPlayers) {
                if (!targetName.equals(gerant.getName()) && entrepriseLogic.getNomEntrepriseDuMembre(targetName) == null && entrepriseLogic.joueurPeutRejoindreAutreEntreprise(targetName)) {
                    inv.addItem(createPlayerHead(targetName, Collections.singletonList(ChatColor.GRAY + "Cliquez pour inviter.")));
                    foundCandidate = true;
                }
            }
            if (!foundCandidate) {
                inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW+"Aucun joueur éligible à proximité."));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void handleRecruitEmployeeSelectionClick(Player gerant, String itemName) {
        if (itemName.equals("Retour")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(currentOpenEntreprise.get(gerant));
            if(entreprise != null) openManageSpecificEntrepriseMenu(gerant, entreprise); else openMyEntreprisesMenu(gerant);
            return;
        }
        String targetPlayerName = itemName; // Le nom de l'item est le nom du joueur
        Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);
        String nomEntreprise = currentOpenEntreprise.get(gerant);

        if (nomEntreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur : contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(gerant);
            return;
        }
        if (targetPlayer == null || !targetPlayer.isOnline()) { // Vérifier si le joueur est en ligne
            gerant.sendMessage(ChatColor.RED + "Le joueur " + targetPlayerName + " n'est plus en ligne ou est invalide.");
            // Optionnel : réouvrir le menu de recrutement pour choisir un autre joueur
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
            if (entreprise != null) openRecruitEmployeeProximityMenu(gerant, entreprise);
            return;
        }
        openRecruitConfirmationMenu(gerant, targetPlayerName, nomEntreprise);
    }

    private void openRecruitConfirmationMenu(Player gerant, String targetPlayerName, String nomEntreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer Recrutement");
        inv.setItem(11, createMenuItem(Material.GREEN_WOOL, ChatColor.GREEN + "Oui, inviter " + targetPlayerName));
        inv.setItem(15, createMenuItem(Material.RED_WOOL, ChatColor.RED + "Non, annuler"));
        gerant.openInventory(inv);
    }

    private void handleRecruitConfirmationClick(Player gerant, ItemStack clickedItem) {
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        String nomEntreprise = currentOpenEntreprise.get(gerant);

        if (nomEntreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(gerant);
            return;
        }

        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise); // Récupérer l'objet entreprise
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: L'entreprise n'existe plus.");
            openMyEntreprisesMenu(gerant);
            return;
        }

        if (itemName.startsWith("Oui, inviter ")) {
            String targetPlayerName = itemName.substring("Oui, inviter ".length());
            Player targetOnlinePlayer = Bukkit.getPlayerExact(targetPlayerName);
            if (targetOnlinePlayer != null && targetOnlinePlayer.isOnline()) { // Double vérification
                entrepriseLogic.inviterEmploye(gerant, nomEntreprise, targetOnlinePlayer);
            } else {
                gerant.sendMessage(ChatColor.RED + "Le joueur " + targetPlayerName + " n'est plus en ligne.");
            }
        } else if (itemName.equals("Non, annuler")) {
            gerant.sendMessage(ChatColor.YELLOW + "Recrutement annulé.");
        }
        // Toujours revenir au menu de gestion de l'entreprise après une action ou une annulation
        openManageSpecificEntrepriseMenu(gerant, entreprise);
    }


    private void openManageEmployeesListMenu(Player gerant, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Gérer Employés");
        Set<String> employes = entrepriseLogic.getEmployesDeLEntreprise(entreprise.getNom());
        if (employes.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Cette entreprise n'a aucun employé."));
        } else {
            for (String empName : employes) {
                double prime = entreprise.getPrimePourEmploye(empName);
                inv.addItem(createPlayerHead(empName, Arrays.asList(ChatColor.GOLD+"Prime: "+String.format("%,.2f",prime)+"€/h", ChatColor.GRAY+"Cliquez pour options.")));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void handleManageEmployeesListClick(Player gerant, String itemName) {
        if (itemName.equals("Retour")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(currentOpenEntreprise.get(gerant));
            if(entreprise!=null) openManageSpecificEntrepriseMenu(gerant, entreprise); else openMyEntreprisesMenu(gerant);
            return;
        }
        String selectedEmpName = itemName; // Le nom de l'item est le nom de l'employé
        String nomEntrepriseGeree = currentOpenEntreprise.get(gerant);
        if (nomEntrepriseGeree == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(gerant); return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntrepriseGeree);
        if (entreprise == null || !entreprise.getEmployes().contains(selectedEmpName)) {
            gerant.sendMessage(ChatColor.RED + "Erreur: Employé ou entreprise introuvable.");
            openManageSpecificEntrepriseMenu(gerant, entreprise != null ? entreprise : entrepriseLogic.getEntreprise(nomEntrepriseGeree)); // Tenter de rouvrir avec l'entreprise si elle existe encore
            return;
        }

        selectedEmployeeForManagement.put(gerant, selectedEmpName);
        openSpecificEmployeeOptionsMenu(gerant, selectedEmpName, entreprise);
    }

    private void openSpecificEmployeeOptionsMenu(Player gerant, String employeNom, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Options pour " + employeNom);
        double primeActuelle = entreprise.getPrimePourEmploye(employeNom);
        inv.setItem(11, createMenuItem(Material.GOLD_INGOT, ChatColor.GREEN + "Définir Prime Horaire", Collections.singletonList(ChatColor.GRAY+"Actuelle: " + String.format("%,.2f", primeActuelle) + "€/h")));
        inv.setItem(15, createMenuItem(Material.RED_WOOL, ChatColor.RED + "Virer " + employeNom));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));
        gerant.openInventory(inv);
    }

    private void handleSpecificEmployeeOptionsMenuClick(Player gerant, String itemName) {
        String employeNom = selectedEmployeeForManagement.get(gerant);
        String nomEntrepriseGeree = currentOpenEntreprise.get(gerant);
        if (employeNom == null || nomEntrepriseGeree == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: Informations de l'employé ou de l'entreprise perdues.");
            openMyEntreprisesMenu(gerant); return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntrepriseGeree);
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: L'entreprise n'existe plus.");
            selectedEmployeeForManagement.remove(gerant); // Nettoyer
            openMyEntreprisesMenu(gerant); return;
        }

        if (itemName.equals("Définir Prime Horaire")) {
            openSetPrimeAmountMenu(gerant, employeNom, entreprise);
        } else if (itemName.startsWith("Virer ")) { // S'assurer que le nom correspond bien
            entrepriseLogic.kickEmploye(gerant, nomEntrepriseGeree, employeNom);
            selectedEmployeeForManagement.remove(gerant); // Nettoyer après l'action
            openManageEmployeesListMenu(gerant, entreprise); // Revenir à la liste
        } else if (itemName.equals("Retour")) {
            selectedEmployeeForManagement.remove(gerant); // Nettoyer
            openManageEmployeesListMenu(gerant, entreprise);
        }
    }

    private void openSetPrimeAmountMenu(Player gerant, String employeNom, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Définir Prime Horaire");
        double primeActuelle = entreprise.getPrimePourEmploye(employeNom);
        List<Double> montantsProposes = Arrays.asList(0.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0, 750.0, 1000.0, 1250.0, 1500.0, 2000.0); // Plus d'options
        // Afficher l'employé concerné dans le titre ou via un item spécial si besoin.
        // Pour l'instant, on se fie au contexte stocké.
        for (double montant : montantsProposes) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + String.format("%,.2f", montant) + "€", Collections.singletonList((montant == primeActuelle) ? ChatColor.GREEN + "(Actuelle)" : "")));
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour")); // Retour aux options de l'employé
        gerant.openInventory(inv);
    }

    private void handleSetPrimeAmountClick(Player gerant, String itemName) {
        String employeNom = selectedEmployeeForManagement.get(gerant);
        String nomEntrepriseGeree = currentOpenEntreprise.get(gerant);

        if (employeNom == null || nomEntrepriseGeree == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur : informations de l'employé ou de l'entreprise manquantes.");
            openMyEntreprisesMenu(gerant);
            return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntrepriseGeree);
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur : l'entreprise n'existe plus.");
            selectedEmployeeForManagement.remove(gerant);
            openMyEntreprisesMenu(gerant);
            return;
        }

        if (itemName.equals("Retour")) { // MODIFIÉ : Le nom du bouton Retour est juste "Retour"
            openSpecificEmployeeOptionsMenu(gerant, employeNom, entreprise);
            return;
        }

        try {
            String montantStr = itemName.replace("€", "").trim().replace(",", ".");
            double nouvellePrime = Double.parseDouble(montantStr);

            if (nouvellePrime < 0) {
                gerant.sendMessage(ChatColor.RED + "Le montant de la prime ne peut pas être négatif.");
                openSetPrimeAmountMenu(gerant, employeNom, entreprise);
                return;
            }

            entrepriseLogic.definirPrime(nomEntrepriseGeree, employeNom, nouvellePrime);
            gerant.sendMessage(ChatColor.GREEN + "Prime horaire de " + ChatColor.YELLOW + employeNom +
                    ChatColor.GREEN + " définie à " + ChatColor.GOLD + String.format("%,.2f", nouvellePrime) + "€" +
                    ChatColor.GREEN + " pour l'entreprise '" + ChatColor.AQUA + nomEntrepriseGeree + ChatColor.GREEN + "'.");

            Player employePlayer = Bukkit.getPlayerExact(employeNom);
            if(employePlayer != null && employePlayer.isOnline()){
                employePlayer.sendMessage(ChatColor.GOLD + "Votre prime horaire pour l'entreprise '"+ ChatColor.AQUA + nomEntrepriseGeree +
                        ChatColor.GOLD + "' a été mise à jour à " + ChatColor.YELLOW + String.format("%,.2f", nouvellePrime) + "€" + ChatColor.GOLD + ".");
            }

            openSpecificEmployeeOptionsMenu(gerant, employeNom, entreprise);
        } catch (NumberFormatException e) {
            gerant.sendMessage(ChatColor.RED + "Montant de prime invalide cliqué : '" + itemName + "'");
            plugin.getLogger().warning("Erreur de parsing du montant de la prime depuis le GUI: " + itemName + " pour " + gerant.getName());
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
        if (nomEntreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Erreur: Contexte de l'entreprise perdu.");
            openMyEntreprisesMenu(gerant); return;
        }

        if (itemName.startsWith("OUI, Dissoudre")) {
            entrepriseLogic.supprimerEntreprise(gerant, nomEntreprise);
            currentOpenEntreprise.remove(gerant); // Nettoyer
            openMainMenu(gerant); // Revenir au menu principal après suppression
        } else if (itemName.equals("NON, Annuler")) {
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
            if(entreprise!=null) openManageSpecificEntrepriseMenu(gerant, entreprise); else openMyEntreprisesMenu(gerant);
        }
    }


    private void openListTownsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Lister Entreprises par Ville");
        Collection<String> towns = entrepriseLogic.getAllTownsNames();
        if (towns.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune ville trouvée."));
        } else {
            for (String townName : towns) {
                inv.addItem(createMenuItem(Material.PAPER, ChatColor.AQUA + townName, Collections.singletonList(ChatColor.GRAY+"Cliquez pour voir.")));
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
        openListEntreprisesInTownMenu(player, itemName); // itemName est le nom de la ville
    }

    private void openListEntreprisesInTownMenu(Player player, String townName) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Entreprises à " + townName);
        List<EntrepriseManagerLogic.Entreprise> entreprises = entrepriseLogic.getEntreprisesByVille(townName);
        if (entreprises.isEmpty()) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Aucune entreprise dans cette ville."));
        } else {
            for (EntrepriseManagerLogic.Entreprise e : entreprises) {
                inv.addItem(createMenuItem(Material.BOOK, ChatColor.GOLD + e.getNom(), Arrays.asList(ChatColor.GRAY+"Type: "+e.getType(), ChatColor.GRAY+"Gérant: "+e.getGerant())));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour")); // Retour à la liste des villes
        player.openInventory(inv);
    }

    private void handleViewEntrepriseFromListClick(Player player, String itemName, String inventoryTitle) {
        if (itemName.equals("Retour")) {
            openListTownsMenu(player); // Revenir à la liste des villes
            return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(itemName); // itemName est le nom de l'entreprise
        if (entreprise != null) {
            displayEntrepriseInfo(player, entreprise);
            // Ne pas fermer l'inventaire, l'info est dans le chat.
        } else {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + itemName + "' n'existe pas.");
            // Ré-ouvrir le menu actuel pour permettre une autre sélection ou un retour.
            String townNameFromTitle = inventoryTitle.substring((ChatColor.DARK_BLUE + "Entreprises à ").length());
            openListEntreprisesInTownMenu(player, townNameFromTitle);
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
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission (entreprisemanager.admin.forcepay).");
                }
                player.closeInventory();
                break;
            case "Recharger Configuration":
                if (player.hasPermission("entreprisemanager.admin.reload")) {
                    plugin.reloadPlugin();
                    player.sendMessage(ChatColor.GREEN + "Plugin EntrepriseManager et données rechargés.");
                } else {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission (entreprisemanager.admin.reload).");
                }
                player.closeInventory();
                break;
            case "Retour Menu Principal":
                openMainMenu(player);
                break;
            default:
                player.sendMessage(ChatColor.YELLOW + "Action admin GUI non reconnue: " + itemName);
                break;
        }
    }

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
        }
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "==============================================");
    }

    @EventHandler
    public void onAsyncPlayerChatForRename(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingRename_OldName.containsKey(playerId)) {
            event.setCancelled(true);
            String nouveauNom = event.getMessage().trim();
            String ancienNom = pendingRename_OldName.remove(playerId);

            if (nouveauNom.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Renommage annulé.");
                return;
            }
            if (!nouveauNom.matches("^[a-zA-Z0-9_\\-]+$")) {
                player.sendMessage(ChatColor.RED + "Le nom contient des caractères invalides. Utilisez uniquement lettres, chiffres, _ et -.");
                pendingRename_OldName.put(playerId, ancienNom);
                return;
            }
            if (entrepriseLogic.getEntreprise(nouveauNom) != null) {
                player.sendMessage(ChatColor.RED + "Une entreprise avec le nom '" + nouveauNom + "' existe déjà.");
                pendingRename_OldName.put(playerId, ancienNom);
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
        // selectedGerantForCreation.remove(player); // Peut être nettoyé à la fin du processus de création ou si annulé.
        // selectedTypeForCreation.remove(player);
        // selectedEmployeeForManagement.remove(player); // Nettoyé lorsque le menu d'options de l'employé est quitté ou une action est faite.
        // currentOpenEntreprise n'est pas nettoyé ici pour permettre la navigation entre sous-menus liés à une entreprise.
        // pendingRename_OldName est géré par onAsyncPlayerChatForRename.
    }
}