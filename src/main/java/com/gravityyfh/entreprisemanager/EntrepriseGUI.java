package com.gravityyfh.entreprisemanager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class EntrepriseGUI implements Listener {

    private final EntrepriseManager plugin;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final Map<Player, String> selectedGerant = new HashMap<>();
    private final Map<Player, String> selectedType = new HashMap<>();
    private final Map<UUID, Long> clickTimestamps = new HashMap<>();
    private final Map<Player, Inventory> previousInventories = new HashMap<>();
    private final Map<Player, String> recruitingPlayer = new HashMap<>();
    private final Map<Player, String> recruitingEntreprise = new HashMap<>();
    private Map<Player, String> selectedEntreprise = new HashMap<>();
    private final Map<Player, String> currentEntreprise = new HashMap<>();
    private final Map<UUID, String> waitingForNameChange = new HashMap<>();
    private final Map<UUID, String> nameChangeConfirmations = new HashMap<>();
    private final Map<Player, String> pendingRenames = new HashMap<>();
    private Map<Player, String> selectedEmployee = new HashMap<>();

    private static final long CLICK_DELAY = 500;

    public EntrepriseGUI(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Menu Entreprise");

        inv.setItem(10, createMenuItem(Material.PAPER, ChatColor.GOLD + "Créer une entreprise"));
        inv.setItem(12, createMenuItem(Material.BOOK, ChatColor.GOLD + "Liste des entreprises"));
        inv.setItem(14, createMenuItem(Material.CHEST, ChatColor.GOLD + "Mes entreprises"));
        if (player.hasPermission("entreprisemanager.admin")) {
            inv.setItem(16, createMenuItem(Material.EMERALD, ChatColor.GOLD + "Admin"));
        }

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private ItemStack createMenuItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerHead(String playerName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));  // Utilise le nom du joueur pour créer la tête
        meta.setDisplayName(ChatColor.GOLD + playerName);  // Définit le nom affiché
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Vérifiez si le joueur clique dans un de vos menus personnalisés
        String title = event.getView().getTitle();

        // Si le titre de l'inventaire ne correspond pas à l'un des titres de vos menus, autorisez l'interaction
        if (!title.startsWith(ChatColor.DARK_BLUE.toString())) {
            return;  // Ne pas annuler l'événement si ce n'est pas un de vos menus
        }

        // Bloquer l'interaction si c'est un de vos menus
        event.setCancelled(true);

        // Ajout d'un délai pour empêcher les clics rapides
        if (clickTimestamps.containsKey(playerId)) {
            long lastClickTime = clickTimestamps.get(playerId);
            if (currentTime - lastClickTime < CLICK_DELAY) {
                return;
            }
        }
        clickTimestamps.put(playerId, currentTime);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        Material type = clickedItem.getType();

        // Gestion des différents menus
        if (title.equals(ChatColor.DARK_BLUE + "Gérer les employés")) {
            handleManageEmployeesClick(event, player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Gérer " + currentEntreprise.get(player))) {
            handleEmployeeManagementClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Définir le salaire")) {
            handleSalarySelectionClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Recruter un salarié")) {
            handleRecruitEmployeeClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Confirmer le recrutement")) {
            handleRecruitConfirmationClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Menu Entreprise")) {
            handleMainMenuClick(player, type);
        } else if (title.equals(ChatColor.DARK_BLUE + "Créer une entreprise")) {
            handleCreateEntrepriseClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Sélectionnez un gérant")) {
            handleSelectGerantClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Sélectionnez un type")) {
            handleSelectTypeClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Mes entreprises")) {
            handleMyEntreprisesClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Liste des villes")) {
            handleTownSelectionClick(player, clickedItem);
        } else if (title.startsWith(ChatColor.DARK_BLUE + "Liste des entreprises - ")) {
            handleListEntreprisesClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Admin")) {
            handleAdminMenuClick(player, type);
        } else if (title.startsWith(ChatColor.DARK_BLUE + "Gérer l'entreprise - ")) {
            handleManageEntrepriseClick(event, player, clickedItem, title);
        } else if (title.equals(ChatColor.DARK_BLUE + "Confirmer le renommage")) {
            handleRenameConfirmationClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Confirmer la suppression")) {
            handleDeleteConfirmationClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Confirmer le nouveau nom")) {
            handleConfirmNewNameClick(player, clickedItem);
        } else if (title.equals(ChatColor.RED + "Retour")) {
            openPreviousInventory(player);
        } else if (title.equals(ChatColor.DARK_BLUE + "Retirer de l'argent")) {
            handleWithdrawMoneyClick(player, clickedItem);
        }
    }

    private void handleManageEntrepriseClick(InventoryClickEvent event, Player player, ItemStack clickedItem, String title) {
        String entrepriseNom = currentEntreprise.get(player);  // Récupère l'entreprise sélectionnée

        // Débogage : Vérification du nom de l'entreprise
        player.sendMessage(ChatColor.YELLOW + "[Debug] Nom de l'entreprise (extrait du titre) : " + entrepriseNom);

        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "[Debug] Aucune entreprise trouvée avec le nom : " + entrepriseNom);
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "[Debug] Entreprise trouvée : " + entreprise.getNom());

        switch (clickedItem.getType()) {
            case NAME_TAG:
                openRecruitEmployeeMenu(player);
                break;
            case PLAYER_HEAD:
                openManageEmployeesMenu(player, entrepriseNom);
                break;
            case PAPER:
                openRenameConfirmationMenu(player, entrepriseNom);
                break;
            case BARRIER:
                if (ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).equals("Retour")) {
                    returnToPreviousMenu(player);
                } else {
                    openDeleteConfirmationMenu(player, entrepriseNom);  // Appel du menu de confirmation de suppression
                }
                break;
            case GOLD_INGOT:
                openWithdrawMoneyMenu(player, entrepriseNom);
                break;
        }
    }


    private void openWithdrawMoneyMenu(Player player, String entrepriseNom) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Retirer de l'argent");

        int[] amounts = {100, 500, 1000, 5000, 10000};
        for (int amount : amounts) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + String.valueOf(amount) + " €"));
        }

        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }


    private void handleWithdrawMoneyClick(Player player, ItemStack clickedItem) {
        String entrepriseNom = currentEntreprise.get(player);
        if (entrepriseNom == null) {
            player.sendMessage(ChatColor.RED + "Une erreur est survenue. Veuillez réessayer.");
            returnToPreviousMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.PAPER) {
            int amount = Integer.parseInt(ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).replace("€", "").trim());

            entrepriseLogic.retirerArgent(player, entrepriseNom, amount);
            player.closeInventory();
        }
    }



    private void openRecruitConfirmationMenu(Player player, String playerName, String entrepriseNom) {
        player.sendMessage(ChatColor.YELLOW + "[Debug] Confirmation du recrutement de " + playerName + " pour l'entreprise : " + entrepriseNom);

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer le recrutement");

        inv.setItem(11, createMenuItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Oui, recruter " + playerName));
        inv.setItem(15, createMenuItem(Material.RED_CONCRETE, ChatColor.RED + "Annuler"));

        // Stocker les informations nécessaires
        recruitingPlayer.put(player, playerName);
        recruitingEntreprise.put(player, entrepriseNom);

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handleSetSalaryClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            openManageEntrepriseMenu(player, selectedGerant.get(player));
            return;
        }

        int salary = Integer.parseInt(ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).replace("€", ""));
        player.performCommand("salaire add " + selectedGerant.get(player) + " " + salary);
        player.closeInventory();
    }

    private void handleRenameConfirmationClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            pendingRenames.remove(player);
            return;
        }

        if (clickedItem.getType() == Material.GREEN_CONCRETE) {
            String nouveauNom = pendingRenames.get(player);
            if (nouveauNom == null) {
                player.sendMessage(ChatColor.RED + "Aucun nom de remplacement en attente trouvé.");
                returnToPreviousMenu(player);
                return;
            }

            double renameCost = plugin.getConfig().getDouble("rename-cost", 2500);
            if (EntrepriseManager.getEconomy().has(player, renameCost)) {
                EntrepriseManager.getEconomy().withdrawPlayer(player, renameCost);
                entrepriseLogic.renameEntreprise(player, currentEntreprise.get(player), nouveauNom);
                player.sendMessage(ChatColor.GREEN + "L'entreprise a été renommée avec succès en " + nouveauNom + ".");
                pendingRenames.remove(player);
            } else {
                player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent pour renommer l'entreprise.");
            }

            player.closeInventory();
        }
    }

    private void initiateRenameProcess(Player player, String entrepriseNom) {
        player.sendMessage(ChatColor.YELLOW + "Veuillez écrire le nouveau nom de l'entreprise dans le chat. Tapez 'annuler' pour annuler.");
        pendingRenames.put(player, entrepriseNom);  // Associe l'entreprise à renommer avec le joueur
        player.closeInventory();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (pendingRenames.containsKey(player)) {
            event.setCancelled(true); // Empêcher le message d'apparaître dans le chat général

            String nouveauNom = event.getMessage().trim();

            // Vérification si le joueur veut annuler
            if (nouveauNom.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Renommage annulé.");
                pendingRenames.remove(player);
                return;
            }

            // Vérifiez si le nouveau nom est valide
            if (nouveauNom.isEmpty() || nouveauNom.contains(" ")) {
                player.sendMessage(ChatColor.RED + "Le nom de l'entreprise ne doit pas être vide ou contenir des espaces.");
                return;
            }

            // Stocker le nouveau nom pour confirmation
            String entrepriseNom = pendingRenames.get(player);
            pendingRenames.remove(player);

            // Exécuter la tâche de confirmation sur le thread principal
            Bukkit.getScheduler().runTask(plugin, () -> {
                openConfirmNewNameMenu(player, entrepriseNom, nouveauNom);
            });
        }
    }

    private void openConfirmNewNameMenu(Player player, String entrepriseNom, String newName) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer le nouveau nom");

        double renameCost = plugin.getConfig().getDouble("rename-cost", 2500);

        inv.setItem(11, createMenuItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Confirmer"));
        inv.setItem(15, createMenuItem(Material.RED_CONCRETE, ChatColor.RED + "Annuler"));

        ItemStack nameItem = createMenuItem(Material.NAME_TAG, ChatColor.YELLOW + "Nouveau nom: " + newName);
        ItemMeta meta = nameItem.getItemMeta();
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Coût: " + renameCost + " €",
                ChatColor.GRAY + "Nom actuel: " + entrepriseNom
        ));
        nameItem.setItemMeta(meta);
        inv.setItem(13, nameItem);

        // Stocker les informations nécessaires pour la confirmation
        nameChangeConfirmations.put(player.getUniqueId(), newName);
        currentEntreprise.put(player, entrepriseNom);

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handleConfirmNewNameClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.RED_CONCRETE) {
            player.sendMessage(ChatColor.RED + "Opération de renommage annulée.");
            returnToPreviousMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.GREEN_CONCRETE) {
            String newName = nameChangeConfirmations.get(player.getUniqueId());
            String entrepriseNom = currentEntreprise.get(player);

            double renameCost = plugin.getConfig().getDouble("rename-cost", 2500);
            if (EntrepriseManager.getEconomy().has(player, renameCost)) {
                EntrepriseManager.getEconomy().withdrawPlayer(player, renameCost);
                entrepriseLogic.renameEntreprise(player, entrepriseNom, newName);
                player.sendMessage(ChatColor.GREEN + "L'entreprise a été renommée en '" + newName + "'.");
            } else {
                player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent pour renommer l'entreprise.");
            }

            nameChangeConfirmations.remove(player.getUniqueId());
            currentEntreprise.remove(player);

            returnToPreviousMenu(player);
        }
    }

    private void handleDeleteConfirmationClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.RED_CONCRETE) {
            returnToPreviousMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.GREEN_CONCRETE) {
            String entrepriseNom = selectedEntreprise.get(player);  // Utilise l'entreprise sélectionnée

            if (entrepriseNom != null) {
                entrepriseLogic.supprimerEntreprise(player, entrepriseNom);  // Supprime l'entreprise
                selectedEntreprise.remove(player);  // Nettoyer après suppression
            } else {
                player.sendMessage(ChatColor.RED + "Erreur : aucune entreprise sélectionnée pour la suppression.");
            }

            player.closeInventory();
        }
    }

    private void openPreviousInventory(Player player) {
        Inventory previousInventory = previousInventories.get(player);
        if (previousInventory != null) {
            player.openInventory(previousInventory);
        } else {
            openMainMenu(player);
        }
    }

    private void handleMainMenuClick(Player player, Material type) {
        switch (type) {
            case PAPER:
                if (entrepriseLogic.estMaire(player)) {
                    openCreateEntrepriseMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Vous devez être le maire de votre ville pour créer une entreprise.");
                }
                break;
            case BOOK:
                openListTownsMenu(player);
                break;
            case CHEST:
                openMyEntreprisesMenu(player);
                break;
            case EMERALD:
                if (player.hasPermission("entreprisemanager.admin")) {
                    openAdminMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'accéder à cette section.");
                }
                break;
        }
    }

    private void openCreateEntrepriseMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Créer une entreprise");

        inv.setItem(11, createMenuItem(Material.NAME_TAG, ChatColor.GREEN + "Nom du gérant"));
        inv.setItem(13, createMenuItem(Material.ANVIL, ChatColor.GREEN + "Type d'entreprise"));
        inv.setItem(15, createMenuItem(Material.WRITABLE_BOOK, ChatColor.GREEN + "Créer"));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory()); // Enregistrer le menu précédent
        player.openInventory(inv);
    }

    private void openSelectGerantMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Sélectionnez un gérant");

        Collection<String> playersInTown = entrepriseLogic.getPlayersInMayorTown(player);
        for (String playerName : playersInTown) {
            inv.addItem(createPlayerHead(playerName));
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory()); // Enregistrer le menu précédent
        player.openInventory(inv);
    }

    private void openSelectTypeMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Sélectionnez un type");

        for (String type : entrepriseLogic.getTypesEntreprise()) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + type));
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory()); // Enregistrer le menu précédent
        player.openInventory(inv);
    }

    private void openListTownsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Liste des villes");

        for (String ville : entrepriseLogic.getAllTowns()) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + ville));
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory()); // Enregistrer le menu précédent
        player.openInventory(inv);
    }

    private void openListEntreprisesMenu(Player player, String ville) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Liste des entreprises - " + ville);

        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprisesByVille(ville)) {
            ItemStack item = createMenuItem(Material.PAPER, ChatColor.GOLD + entreprise.getNom());
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of(
                    ChatColor.YELLOW + "Gérant: " + entreprise.getGerant(),
                    ChatColor.YELLOW + "Type: " + entreprise.getType()
            ));
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory()); // Enregistrer le menu précédent
        player.openInventory(inv);
    }

    private void openMyEntreprisesMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Mes entreprises");

        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntrepriseDuGerant(player.getName())) {
            ItemStack item = createMenuItem(Material.CHEST, ChatColor.GOLD + entreprise.getNom());
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of(
                    ChatColor.YELLOW + "Gérant: " + entreprise.getGerant(),
                    ChatColor.YELLOW + "Type: " + entreprise.getType()
            ));
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory()); // Enregistrer le menu précédent
        player.openInventory(inv);
    }

    private void openAdminMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Admin");

        inv.setItem(11, createMenuItem(Material.EMERALD, ChatColor.RED + "Forcer les paiements"));
        inv.setItem(15, createMenuItem(Material.REDSTONE, ChatColor.RED + "Recharger le plugin"));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory()); // Enregistrer le menu précédent
        player.openInventory(inv);
    }

    private void openManageEntrepriseMenu(Player player, String entrepriseNom) {
        player.sendMessage(ChatColor.YELLOW + "[Debug] Ouverture du menu de gestion pour l'entreprise : " + entrepriseNom);

        if (entrepriseNom == null) {
            player.sendMessage(ChatColor.RED + "Erreur : Nom de l'entreprise non défini.");
            returnToPreviousMenu(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Gérer l'entreprise - " + entrepriseNom);

        inv.setItem(10, createMenuItem(Material.NAME_TAG, ChatColor.GREEN + "Recruter un salarié"));
        inv.setItem(12, createMenuItem(Material.PLAYER_HEAD, ChatColor.GREEN + "Gérer les employés"));
        inv.setItem(14, createMenuItem(Material.PAPER, ChatColor.GREEN + "Modifier le nom"));
        inv.setItem(16, createMenuItem(Material.BARRIER, ChatColor.RED + "Supprimer l'entreprise"));
        inv.setItem(20, createMenuItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Retirer de l'argent"));
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }


    private void openRecruitEmployeeMenu(Player player) {
        // Vérifiez si une entreprise est sélectionnée pour ce joueur
        String entrepriseNom = currentEntreprise.get(player);

        if (entrepriseNom == null) {
            player.sendMessage(ChatColor.RED + "[Debug] Aucune entreprise sélectionnée lors de l'ouverture du menu de recrutement.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "[Debug] Ouverture du menu de recrutement pour l'entreprise : " + entrepriseNom);

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Recruter un salarié");

        // Récupérer le gérant de l'entreprise pour le filtrer de la liste
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);
        String gerantName = entreprise.getGerant();

        // Ajoutez les joueurs à recruter dans l'inventaire, en filtrant le gérant
        Collection<String> nearbyPlayers = entrepriseLogic.getNearbyPlayers(player, plugin.getConfig().getInt("invitation.distance-max"));
        for (String playerName : nearbyPlayers) {
            if (!playerName.equals(gerantName)) {  // Exclure le gérant
                inv.addItem(createPlayerHead(playerName));
            }
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void openManageEmployeesMenu(Player player, String entrepriseNom) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Gérer les employés");

        Set<String> employes = entrepriseLogic.getEmployes(entrepriseNom);
        for (String employeName : employes) {
            inv.addItem(createPlayerHead(employeName));  // Affiche le nom du joueur
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        currentEntreprise.put(player, entrepriseNom);  // Stocker l'entreprise en cours de gestion

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void openRenameConfirmationMenu(Player player, String nouveauNom) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer le renommage");

        inv.setItem(11, createMenuItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Confirmer"));
        inv.setItem(15, createMenuItem(Material.RED_CONCRETE, ChatColor.RED + "Annuler"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void openDeleteConfirmationMenu(Player player, String entrepriseNom) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer la suppression");

        inv.setItem(11, createMenuItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Oui, supprimer"));
        inv.setItem(15, createMenuItem(Material.RED_CONCRETE, ChatColor.RED + "Annuler"));

        // Stocker l'entreprise en cours de suppression
        selectedEntreprise.put(player, entrepriseNom);

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handleCreateEntrepriseClick(Player player, ItemStack clickedItem) {
        switch (clickedItem.getType()) {
            case NAME_TAG:
                openSelectGerantMenu(player);
                break;
            case ANVIL:
                openSelectTypeMenu(player);
                break;
            case WRITABLE_BOOK:
                String gerant = selectedGerant.get(player);
                String type = selectedType.get(player);
                if (gerant != null && type != null) {
                    player.performCommand("entreprise create " + gerant + " " + type);
                    player.closeInventory();
                } else {
                    player.sendMessage(ChatColor.RED + "Vous devez sélectionner un gérant et un type avant de créer l'entreprise.");
                }
                break;
            case OAK_DOOR:
                returnToPreviousMenu(player);
                break;
        }
    }

    private void handleSelectGerantClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        String gerant = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        selectedGerant.put(player, gerant);
        player.sendMessage(ChatColor.GREEN + "Gérant sélectionné: " + gerant);
        returnToPreviousMenu(player);
    }

    private void handleSelectTypeClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        String type = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        selectedType.put(player, type);
        player.sendMessage(ChatColor.GREEN + "Type sélectionné: " + type);
        returnToPreviousMenu(player);
    }

    private void handleMyEntreprisesClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        String entrepriseNom = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);
        if (entreprise != null) {
            // Stocker le nom de l'entreprise sélectionnée pour une utilisation future
            currentEntreprise.put(player, entrepriseNom);

            openManageEntrepriseMenu(player, entrepriseNom);
        } else {
            player.sendMessage(ChatColor.RED + "Entreprise non trouvée.");
        }
    }

    private void handleListEntreprisesClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        String entrepriseNom = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);
        if (entreprise != null) {
            String command = String.format("entreprise info %s %s", entreprise.getGerant(), entreprise.getType());
            player.performCommand(command);
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "Entreprise non trouvée.");
        }
    }

    private void handleTownSelectionClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        String ville = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        openListEntreprisesMenu(player, ville);
    }

    private void handleAdminMenuClick(Player player, Material type) {
        switch (type) {
            case EMERALD:
                entrepriseLogic.traiterPaiementsJournaliers();
                player.sendMessage(ChatColor.GREEN + "Paiements journaliers forcés.");
                player.closeInventory();
                break;
            case REDSTONE:
                plugin.reloadPlugin();
                player.sendMessage(ChatColor.GREEN + "Plugin rechargé.");
                player.closeInventory();
                break;
            case OAK_DOOR:
                returnToPreviousMenu(player);
                break;
        }
    }

    private void handleRecruitConfirmationClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        String playerName = recruitingPlayer.get(player);
        String entrepriseNom = recruitingEntreprise.get(player);

        if (playerName == null || entrepriseNom == null) {
            player.sendMessage(ChatColor.RED + "Une erreur est survenue lors de la récupération des informations.");
            returnToPreviousMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.GREEN_CONCRETE) {
            Player invitee = Bukkit.getPlayerExact(playerName);
            if (invitee != null) {
                entrepriseLogic.inviterEmploye(player, entrepriseNom, invitee);
            }
        }

        player.closeInventory();
    }

    private void handleRecruitEmployeeClick(Player player, ItemStack clickedItem) {
        // Récupérer l'entreprise sélectionnée
        String entrepriseNom = currentEntreprise.get(player);

        if (entrepriseNom == null) {
            player.sendMessage(ChatColor.RED + "[Debug] Aucune entreprise sélectionnée lors du clic pour recruter.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "[Debug] Recrutement pour l'entreprise : " + entrepriseNom);

        // Récupérer le nom du joueur à recruter
        String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        openRecruitConfirmationMenu(player, playerName, entrepriseNom);
    }

    private void handleManageEmployeesClick(InventoryClickEvent event, Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        String employeNom = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        if (employeNom == null || employeNom.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Impossible de déterminer l'employé. Veuillez réessayer.");
            returnToPreviousMenu(player);
            return;
        }

        // Stocker le nom de l'employé sélectionné
        selectedEmployee.put(player, employeNom);

        // Ouvrir le menu pour gérer l'employé (virer, définir salaire, etc.)
        openEmployeeManagementMenu(player, employeNom);
    }

    private void openEmployeeManagementMenu(Player player, String employeNom) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Gérer " + employeNom);

        inv.setItem(11, createMenuItem(Material.RED_CONCRETE, ChatColor.RED + "Virer l'employé"));
        inv.setItem(15, createMenuItem(Material.GOLD_INGOT, ChatColor.GREEN + "Définir le salaire"));

        // Stocker l'employé en cours de gestion
        currentEntreprise.put(player, employeNom);

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handleEmployeeManagementClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.OAK_DOOR) {
            // Récupérer les informations stockées
            String entrepriseNom = selectedEntreprise.get(player);
            String employeNom = selectedEmployee.get(player);

            if (entrepriseNom != null && employeNom != null) {
                // Exécuter la commande pour virer l'employé
                player.performCommand("entreprise confirmkick " + entrepriseNom + " " + employeNom);

                player.sendMessage(ChatColor.GREEN + employeNom + " a été viré de " + entrepriseNom + ".");
                returnToPreviousMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Erreur lors de la tentative de suppression de l'employé.");
                returnToPreviousMenu(player);
            }
        } else if (clickedItem.getType() == Material.GOLD_INGOT) {
            // Gérer la sélection du salaire, etc.
            openSalarySelectionMenu(player, selectedEmployee.get(player));
        }
    }

    private void openSalarySelectionMenu(Player player, String employeNom) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Définir le salaire");

        int[] salaries = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        for (int salary : salaries) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + String.valueOf(salary) + " €"));
        }

        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handleSalarySelectionClick(Player player, ItemStack clickedItem) {
        String employeNom = currentEntreprise.get(player);
        if (employeNom == null) {
            player.sendMessage(ChatColor.RED + "Une erreur est survenue. Veuillez réessayer.");
            returnToPreviousMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.OAK_DOOR) {
            returnToPreviousMenu(player);
            return;
        }

        if (clickedItem.getType() == Material.PAPER) {
            int salary = Integer.parseInt(ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).replace("€", "").trim());
            String command = String.format("salaire add %s %d", employeNom, salary);
            player.performCommand(command);
            player.sendMessage(ChatColor.GREEN + "Le salaire de " + employeNom + " a été défini à " + salary + " €.");
            player.closeInventory();
        }
    }

    private void returnToPreviousMenu(Player player) {
        selectedEntreprise.remove(player);
        selectedEmployee.remove(player);

        Inventory previousInventory = previousInventories.get(player);
        if (previousInventory != null) {
            player.openInventory(previousInventory);
        } else {
            openMainMenu(player); // Retourne au menu principal si aucun inventaire précédent n'est trouvé
        }
    }
}
