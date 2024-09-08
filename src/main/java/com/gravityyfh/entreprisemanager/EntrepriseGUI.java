package com.gravityyfh.entreprisemanager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
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
    private final Map<UUID, String> pendingRenames = new HashMap<>();

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

        String title = event.getView().getTitle();

        // Gérer les interactions dans le coffre de l'entreprise
        if (title.startsWith(ChatColor.DARK_BLUE + "Coffre: ")) {
            handleVirtualChestInteraction(event, player, title);
            return;
        }

        // Bloquer l'interaction pour tous les autres menus sauf le coffre de l'entreprise
        if (title.startsWith(ChatColor.DARK_BLUE.toString()) || title.startsWith(ChatColor.BLUE.toString())) {
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
            } if (title.equals(ChatColor.DARK_BLUE + "Gérer " + selectedEmployee.get(player))) {
                event.setCancelled(false); // Permettre les clics dans ce menu
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
            } else if (title.equals(ChatColor.DARK_BLUE + "Confirmer la suppression")) {
                handleDeleteConfirmationClick(player, clickedItem);
            } else if (title.equals(ChatColor.DARK_BLUE + "Confirmer le nouveau nom")) {
                handleConfirmNewNameClick(player, clickedItem);
            } else if (title.equals(ChatColor.RED + "Retour")) {
                openPreviousInventory(player);
            } else if (title.equals(ChatColor.DARK_BLUE + "Retirer de l'argent")) {
                handleWithdrawMoneyClick(player, clickedItem);
            } else if (title.startsWith(ChatColor.BLUE + "Entreprise: ")) {
                handleEmployeInterfaceClick(player, clickedItem);
            }else if (title.equals(ChatColor.DARK_BLUE + "Définir la prime")) {
                handlePrimeSelectionClick(player, clickedItem);
            }


        }
    }


    private void handleVirtualChestInteraction(InventoryClickEvent event, Player player, String title) {
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = player.getOpenInventory().getTopInventory();

        // Vérifie si l'inventaire cliqué est le coffre virtuel de l'entreprise
        if (topInventory != null && topInventory.equals(clickedInventory)) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null) return;

            // Empêcher le déplacement du bouton "Valider"
            if (event.getSlot() == 26 && clickedItem.getType() == Material.GREEN_CONCRETE && ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).equals("Valider")) {
                event.setCancelled(true);
                validateAndSellItems(player, topInventory, title);
                return;
            }

            // Empêcher de déposer des items non relatifs à l'entreprise
            if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.PLACE_ONE) {
                ItemStack item = event.getCursor();
                String entrepriseNom = ChatColor.stripColor(title.replace("Coffre: ", ""));
                EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);

                if (entreprise != null) {
                    String typeEntreprise = entreprise.getType();
                    List<String> authorizedBlocks = plugin.getConfig().getStringList("types-entreprise." + typeEntreprise + ".blocs-autorisés");

                    if (!authorizedBlocks.contains(item.getType().name())) {
                        player.sendMessage(ChatColor.RED + "Cet item n'est pas autorisé à être placé dans le coffre de cette entreprise.");
                        event.setCancelled(true); // Bloque l'ajout de l'item
                        return;
                    }
                }
            }

            // Permettre la manipulation des autres items dans le coffre
            event.setCancelled(false);
        }
    }


    private void validateAndSellItems(Player player, Inventory chestInventory, String title) {
        // Récupérer le nom de l'entreprise à partir du titre de l'inventaire
        String entrepriseNom = ChatColor.stripColor(title.replace("Coffre: ", ""));

        // Récupérer l'entreprise via EntrepriseManagerLogic
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);

        // Vérification de l'existence de l'entreprise
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Erreur : entreprise '" + entrepriseNom + "' introuvable.");
            return;
        }

        String typeEntreprise = entreprise.getType();
        double totalMontant = 0;
        List<ItemStack> soldItems = new ArrayList<>();

        // Boucle pour parcourir le coffre virtuel et calculer la vente
        for (int i = 0; i < chestInventory.getSize(); i++) {
            ItemStack item = chestInventory.getItem(i);

            // Ignorer les items null et les "air"
            if (item == null || item.getType().equals(Material.AIR)) {
                continue;
            }

            // Ignorer l'item "Valider" (GREEN_CONCRETE)
            if (item.getType() == Material.GREEN_CONCRETE && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("Valider")) {
                continue;
            }

            Material material = item.getType();

            // Vérifier si l'item fait partie des ressources que l'entreprise peut vendre
            double paiement = plugin.getConfig().getDouble("types-entreprise." + typeEntreprise + ".paiement-ressources." + material.name(), 0);

            if (paiement > 0) {
                // Calcul du montant total pour cet item
                double montantTotal = paiement * item.getAmount();

                // Vérifier si l'entreprise a suffisamment d'argent
                if (entreprise.getSolde() >= montantTotal) {
                    totalMontant += montantTotal;
                    soldItems.add(item); // Ajouter l'item à la liste des items vendus
                    chestInventory.setItem(i, null); // Supprimer l'item du coffre
                } else {
                    player.sendMessage(ChatColor.RED + "L'entreprise n'a pas assez d'argent pour payer tous les items.");
                    break;  // Sortir de la boucle si l'entreprise n'a pas assez d'argent
                }
            } else {
                player.sendMessage(ChatColor.RED + "Cet item ne peut pas être vendu par cette entreprise : " + material.name());
            }
        }

        // Paiement du joueur et enregistrement des items vendus
        if (totalMontant > 0) {
            entreprise.setSolde(entreprise.getSolde() - totalMontant);
            EntrepriseManager.getEconomy().depositPlayer(player, totalMontant);
            player.sendMessage(ChatColor.GREEN + "Vous avez été payé " + totalMontant + "€ pour les items déposés.");

            // Sauvegarder les items vendus dans le coffre virtuel
            EntrepriseVirtualChest virtualChest = entreprise.getVirtualChest();
            virtualChest.addSoldItems(soldItems);

            // Sauvegarder le coffre virtuel dans le fichier YAML de l'entreprise
            File entrepriseFile = new File(plugin.getDataFolder(), "entreprise.yml");
            YamlConfiguration entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);
            String path = "entreprises." + entrepriseNom; // Chemin pour le coffre virtuel dans la config
            virtualChest.saveToConfig(entrepriseConfig, path); // Sauvegarde dans le fichier YAML

            // Sauvegarder le fichier après mise à jour
            try {
                entrepriseConfig.save(entrepriseFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Erreur lors de la sauvegarde du coffre virtuel pour l'entreprise " + entrepriseNom + ": " + e.getMessage());
            }
        } else {
            player.sendMessage(ChatColor.RED + "Aucun item n'a pu être vendu.");
        }

        player.closeInventory();  // Fermer l'inventaire après la transaction
    }


    private void openEmployeeList(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.BLUE + "Employés de " + entreprise.getNom());

        for (String employeName : entreprise.getEmployes()) {
            inv.addItem(createPlayerHead(employeName));
        }
        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handleManageEntrepriseClick(InventoryClickEvent event, Player player, ItemStack clickedItem, String title) {
        String entrepriseNom = currentEntreprise.get(player);  // Récupère l'entreprise sélectionnée

        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Aucune entreprise trouvée.");
            return;
        }

        switch (clickedItem.getType()) {
            case NAME_TAG:
                openRecruitEmployeeMenu(player);
                break;
            case PLAYER_HEAD:
                openManageEmployeesMenu(player, entrepriseNom);
                break;
            case CHEST:
                handleRetrieveItemsClick(player, entreprise);  // Récupérer les items
                break;
            case PAPER: // Le joueur souhaite renommer son entreprise
                initiateRenameProcess(player, entrepriseNom);
                break;
            case BARRIER:
                if (ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).equals("Retour")) {
                    returnToPreviousMenu(player);
                } else {
                    openDeleteConfirmationMenu(player, entrepriseNom);
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



    private void initiateRenameProcess(Player player, String entrepriseNom) {
        player.sendMessage(ChatColor.YELLOW + "Veuillez entrer le nouveau nom de votre entreprise dans le chat. Tapez 'annuler' pour annuler.");
        pendingRenames.put(player.getUniqueId(), entrepriseNom);  // Associe l'entreprise à renommer avec le joueur
        player.closeInventory();  // Fermer l'inventaire pour permettre l'entrée dans le chat
    }


    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingRenames.containsKey(playerId)) {
            event.setCancelled(true);  // Empêche le message d'apparaître dans le chat général

            String nouveauNom = event.getMessage().trim();

            // Vérification si le joueur veut annuler
            if (nouveauNom.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Renommage annulé.");
                pendingRenames.remove(playerId);
                return;
            }

            // Vérifiez si le nom est valide (pas d'espaces, pas de caractères spéciaux)
            if (!nouveauNom.matches("^[a-zA-Z0-9_-]+$")) {
                player.sendMessage(ChatColor.RED + "Le nom de l'entreprise ne doit pas contenir d'espaces ou de caractères spéciaux.");
                return;
            }

            // Stocker le nouveau nom pour confirmation
            String entrepriseNom = pendingRenames.get(playerId);
            pendingRenames.remove(playerId);

            // Exécuter la tâche de confirmation sur le thread principal
            Bukkit.getScheduler().runTask(plugin, () -> openConfirmNewNameMenu(player, entrepriseNom, nouveauNom));
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

        // Ajout des entreprises où le joueur est gérant
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

        // Ajout des entreprises où le joueur est employé
        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprises()) {
            if (entreprise.getEmployes().contains(player.getName())) {
                ItemStack item = createMenuItem(Material.PAPER, ChatColor.AQUA + entreprise.getNom());
                ItemMeta meta = item.getItemMeta();
                meta.setLore(List.of(
                        ChatColor.YELLOW + "Employé",
                        ChatColor.YELLOW + "Type: " + entreprise.getType()
                ));
                item.setItemMeta(meta);
                inv.addItem(item);
            }
        }

        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
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
        inv.setItem(22, createMenuItem(Material.CHEST, ChatColor.GOLD + "Récupérer les items")); // Nouveau bouton
        inv.setItem(24, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handleRetrieveItemsClick(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        EntrepriseVirtualChest virtualChest = entreprise.getVirtualChest();
        List<ItemStack> soldItems = virtualChest.getSoldItems();

        boolean hasTransferredItems = false;
        List<ItemStack> remainingItems = new ArrayList<>();

        for (ItemStack item : soldItems) {
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
            if (remaining.isEmpty()) {
                hasTransferredItems = true;
            } else {
                remainingItems.add(remaining.get(0)); // Garder les items restants si l'inventaire est plein
            }
        }

        if (hasTransferredItems) {
            player.sendMessage(ChatColor.GREEN + "Vous avez récupéré les items vendus par les employés de votre entreprise.");
            virtualChest.getSoldItems().clear(); // Effacer la liste des items vendus
            virtualChest.addSoldItems(remainingItems); // Remettre les items non transférés dans le coffre
        } else {
            player.sendMessage(ChatColor.RED + "Aucun item vendu n'a pu être récupéré.");
        }

        if (!remainingItems.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Votre inventaire est plein, certains items sont restés dans le coffre virtuel.");
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        // Vérifiez si c'est le coffre virtuel
        if (event.getView().getTitle().startsWith(ChatColor.DARK_BLUE + "Coffre: ")) {
            // Parcourez tous les items du coffre
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && !item.getType().equals(Material.GREEN_CONCRETE)) {
                    // Tentez de rendre les items au joueur
                    player.getInventory().addItem(item);
                    inventory.setItem(i, null); // Retire l'item du coffre
                }
            }
        }
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
        player.sendMessage(ChatColor.YELLOW + "[Debug] Nom de l'entreprise cliquée : " + entrepriseNom);

        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);

        if (entreprise != null) {
            player.sendMessage(ChatColor.YELLOW + "[Debug] Entreprise trouvée : " + entreprise.getNom());
            // Stocker le nom de l'entreprise sélectionnée pour une utilisation future
            currentEntreprise.put(player, entrepriseNom);

            // Vérifiez si le joueur est le gérant de l'entreprise
            if (entreprise.getGerant().equalsIgnoreCase(player.getName())) {
                // Ouvre l'interface de gestion pour le gérant
                openManageEntrepriseMenu(player, entrepriseNom);
            } else {
                // Ouvre l'interface d'employé pour les autres
                openEmployeInterface(player, entreprise);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Erreur : l'entreprise n'a pas été trouvée.");
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

        // Récupérer l'entreprise via la logique de l'entreprise
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Erreur : l'entreprise n'a pas été trouvée.");
            return;
        }

        // Récupérer le nom du joueur à recruter
        String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Vérifier si le joueur à recruter fait déjà partie de l'entreprise
        if (entreprise.getEmployes().contains(playerName)) {
            player.sendMessage(ChatColor.RED + "Ce joueur fait déjà partie de votre entreprise.");
            return;
        }

        // Si le joueur n'est pas déjà employé, ouvrir la confirmation de recrutement
        openRecruitConfirmationMenu(player, playerName, entrepriseNom);
    }


    private void handleManageEmployeesClick(InventoryClickEvent event, Player player, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            player.sendMessage(ChatColor.RED + "[Debug] Aucun item valide n'a été cliqué dans 'Gérer les employés'.");
            return;
        }

        // Ajout de message de débogage pour vérifier quel employé est cliqué
        String employeNom = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "[Debug] Employé cliqué: " + employeNom);

        if (employeNom == null || employeNom.isEmpty()) {
            player.sendMessage(ChatColor.RED + "[Debug] Aucun employé n'a été sélectionné.");
            return;
        }

        // Stocker le nom de l'employé sélectionné
        selectedEmployee.put(player, employeNom);

        // Appeler la méthode pour ouvrir le sous-menu de gestion de l'employé
        openEmployeeManagementMenu(player, employeNom);
    }


    private void openEmployeeManagementMenu(Player player, String employeNom) {
        player.sendMessage(ChatColor.YELLOW + "[Debug] Ouverture du menu pour gérer l'employé: " + employeNom);

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Gérer " + employeNom);

        inv.setItem(11, createMenuItem(Material.RED_CONCRETE, ChatColor.RED + "Virer l'employé"));
        inv.setItem(15, createMenuItem(Material.GOLD_INGOT, ChatColor.GREEN + "Définir une prime quotidienne")); // Modifié de "salaire" à "prime"

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }



    private void handleEmployeeManagementClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            player.sendMessage(ChatColor.RED + "[Debug] Aucun item valide cliqué.");
            return;
        }

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "[Debug] Item cliqué: " + displayName);

        // Vérification des actions
        if (clickedItem.getType() == Material.RED_CONCRETE && displayName.equals("Virer l'employé")) {
            player.sendMessage(ChatColor.YELLOW + "[Debug] Tentative de suppression de l'employé.");
            String entrepriseNom = currentEntreprise.get(player);
            String employeNom = selectedEmployee.get(player);
            if (entrepriseNom != null && employeNom != null) {
                player.performCommand("entreprise confirmkick " + entrepriseNom + " " + employeNom);
            } else {
                player.sendMessage(ChatColor.RED + "[Debug] Erreur: L'entreprise ou l'employé est null.");
            }
        } else if (clickedItem.getType() == Material.GOLD_INGOT && displayName.equals("Définir une prime quotidienne")) {
            player.sendMessage(ChatColor.YELLOW + "[Debug] Ouverture de l'interface de définition de prime.");
            openPrimeSelectionMenu(player, selectedEmployee.get(player));  // Utilise openPrimeSelectionMenu
        } else {
            player.sendMessage(ChatColor.RED + "[Debug] Aucun item reconnu pour l'action.");
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
        String employeNom = selectedEmployee.get(player);
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

    private void openPrimeSelectionMenu(Player player, String employeNom) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Définir la prime");

        int[] primes = {0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900, 2000, 2100, 2200, 2300, 2400, 2500, 2600, 2700, 2800, 2900, 3000};
        for (int prime : primes) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + String.valueOf(prime) + " €"));
        }

        inv.setItem(49, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }

    private void handlePrimeSelectionClick(Player player, ItemStack clickedItem) {
        String employeNom = selectedEmployee.get(player);
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
            double prime = Double.parseDouble(ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).replace("€", "").trim());
            String entrepriseNom = currentEntreprise.get(player);

            // Obtenir le gérant et l'employé
            Player gerant = player;  // Le joueur actuel est le gérant
            OfflinePlayer employe = Bukkit.getOfflinePlayer(employeNom);  // Chercher l'employé (connecté ou non)

            // Sauvegarder la prime dans le fichier entreprises.yml
            entrepriseLogic.definirPrime(entrepriseNom, employeNom, prime);

            // Envoyer un message de confirmation
            player.sendMessage(ChatColor.GREEN + "La prime de " + employeNom + " a été définie à " + prime + " €.");
            entrepriseLogic.envoyerMessagePrimesDefinie(gerant, employe, entrepriseNom, prime);

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

    private void openEmployeChest(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        EntrepriseVirtualChest virtualChest = entreprise.getVirtualChest();
        Inventory chestInventory = virtualChest.getInventory();

        if (chestInventory == null) {
            player.sendMessage(ChatColor.RED + "Le coffre virtuel de l'entreprise n'a pas pu être ouvert.");
            return;
        }

        // Ajouter le bouton "Valider" dans l'inventaire du coffre
        ItemStack validateButton = createMenuItem(Material.GREEN_CONCRETE, ChatColor.GOLD + "Valider");
        chestInventory.setItem(26, validateButton); // Positionne le bouton "Valider" dans la dernière case (index 26 dans un inventaire de 27 cases)

        player.openInventory(chestInventory);
    }



    private void openEmployeInterface(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Entreprise: " + entreprise.getNom());

        // Afficher la prime de l'employé
        double prime = entreprise.getPrimePourEmploye(player.getName());
        inv.setItem(11, createMenuItem(Material.GOLD_INGOT, ChatColor.GOLD + "Votre prime: " + prime + " €"));

        inv.setItem(13, createMenuItem(Material.PAPER, ChatColor.GOLD + "Infos Entreprise"));
        inv.setItem(15, createMenuItem(Material.PLAYER_HEAD, ChatColor.GOLD + "Voir les Employés"));
        inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Quitter l'entreprise"));

        previousInventories.put(player, player.getOpenInventory().getTopInventory());
        player.openInventory(inv);
    }





    private void handleEmployeInterfaceClick(Player player, ItemStack clickedItem) {
        String entrepriseNom = currentEntreprise.get(player);
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Erreur : l'entreprise n'a pas été trouvée.");
            return;
        }

        switch (clickedItem.getType()) {
            case CHEST:
                openEmployeChest(player, entreprise);
                break;
            case PAPER:
                displayEntrepriseInfo(player, entreprise);
                break;
            case PLAYER_HEAD:
                openEmployeeList(player, entreprise);
                break;
            case BARRIER:
                handleEmployeeLeaveCompany(player, entreprise);
                break;
            case OAK_DOOR:
                returnToPreviousMenu(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Action non reconnue.");
                break;
        }
    }

    private void handleEmployeeLeaveCompany(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        String playerName = player.getName();

        // Vérifier si le joueur fait partie des employés
        if (!entreprise.getEmployes().contains(playerName)) {
            player.sendMessage(ChatColor.RED + "Vous ne faites pas partie de cette entreprise.");
            return;
        }

        // Supprimer l'employé de la liste des employés de l'entreprise
        entreprise.getEmployes().remove(playerName);

        // Mettre à jour le fichier players.yml pour supprimer l'entreprise de l'employé
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        // Supprimer l'entreprise associée à cet employé
        playersConfig.set("players." + playerName + "." + entreprise.getNom(), null);

        // Sauvegarder les modifications dans players.yml
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Sauvegarder les modifications des entreprises
        entrepriseLogic.saveEntreprises();

        // Informer le joueur qu'il a quitté l'entreprise
        player.sendMessage(ChatColor.GREEN + "Vous avez quitté l'entreprise '" + entreprise.getNom() + "' avec succès.");
    }




    // Nouvelle méthode pour afficher les informations de l'entreprise
    private void displayEntrepriseInfo(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Informations sur l'Entreprise ===");
        player.sendMessage(ChatColor.YELLOW + "Nom: " + ChatColor.WHITE + entreprise.getNom());
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + entreprise.getVille());
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + entreprise.getType());
        player.sendMessage(ChatColor.YELLOW + "Gérant: " + ChatColor.WHITE + entreprise.getGerant());
        player.sendMessage(ChatColor.YELLOW + "Nombre d'employés: " + ChatColor.WHITE + entreprise.getEmployes().size());
        player.sendMessage(ChatColor.YELLOW + "Revenus BRUT/jour: " + ChatColor.GREEN + entreprise.getRevenusBrutsJournaliers() + "€");
        player.sendMessage(ChatColor.YELLOW + "Montant d'argent dans la société: " + ChatColor.GREEN + entreprise.getSolde() + "€");
        player.sendMessage(ChatColor.YELLOW + "Chiffre d'affaires total: " + ChatColor.GREEN + entreprise.getChiffreAffairesTotal() + "€");
        player.sendMessage(ChatColor.YELLOW + "SIRET: " + ChatColor.WHITE + entreprise.getSiret());
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "====================================");
    }
}
