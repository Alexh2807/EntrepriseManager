package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
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
import java.util.List;
import java.util.Set;

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

    private static final String MENU_TITLE = ChatColor.DARK_GREEN + "🏙️ Menu Principal - Ville";
    private static final String CREATE_TOWN_TITLE = ChatColor.DARK_BLUE + "Créer une Ville";
    private static final String JOIN_TOWN_TITLE = ChatColor.DARK_BLUE + "Rejoindre une Ville";

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

    public void openMainMenu(Player player) {
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
        createMeta.setDisplayName(ChatColor.GREEN + "Créer une Ville");
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

        player.openInventory(inv);
    }

    private void openTownMenu(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "🏙️ " + townName);

        TownRole role = town.getMemberRole(player.getUniqueId());

        // Informations de la ville (slot 4)
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Informations de la Ville");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Nom: " + ChatColor.WHITE + townName);
        infoLore.add(ChatColor.GRAY + "Description: " + ChatColor.WHITE + town.getDescription());
        infoLore.add(ChatColor.GRAY + "Membres: " + ChatColor.WHITE + town.getMemberCount());
        infoLore.add(ChatColor.GRAY + "Parcelles: " + ChatColor.WHITE + town.getTotalClaims());
        infoLore.add(ChatColor.GRAY + "Banque: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));

        // Afficher tous les rôles du joueur
        Set<TownRole> playerRoles = member.getRoles();
        if (playerRoles.size() == 1) {
            infoLore.add(ChatColor.GRAY + "Votre rôle: " + ChatColor.AQUA + role.getDisplayName());
        } else {
            infoLore.add(ChatColor.GRAY + "Vos rôles: ");
            for (TownRole r : playerRoles) {
                infoLore.add(ChatColor.AQUA + "  • " + r.getDisplayName());
            }
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Gestion de la ville (Maire/Adjoint)
        if (role.canManageTown() || role == TownRole.ADJOINT) {
            ItemStack manageItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta manageMeta = manageItem.getItemMeta();
            manageMeta.setDisplayName(ChatColor.GREEN + "Gestion de la Ville");
            List<String> manageLore = new ArrayList<>();
            manageLore.add(ChatColor.GRAY + "Modifier le nom, la description");
            manageLore.add(ChatColor.GRAY + "Supprimer la ville (Maire uniquement)");
            manageLore.add("");
            manageLore.add(ChatColor.YELLOW + "Cliquez pour gérer");
            manageMeta.setLore(manageLore);
            manageItem.setItemMeta(manageMeta);
            inv.setItem(10, manageItem);
        }

        // Membres et Rôles
        ItemStack membersItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersItem.getItemMeta();
        membersMeta.setDisplayName(ChatColor.AQUA + "Membres et Rôles");
        List<String> membersLore = new ArrayList<>();
        membersLore.add(ChatColor.GRAY + "Voir les membres");
        membersLore.add(ChatColor.GRAY + "Inviter / Exclure");
        if (role == TownRole.MAIRE) {
            membersLore.add(ChatColor.GRAY + "Gérer les rôles");
        }
        membersLore.add("");
        membersLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
        membersMeta.setLore(membersLore);
        membersItem.setItemMeta(membersMeta);
        inv.setItem(12, membersItem);

        // Claims et Terrains
        ItemStack claimsItem = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta claimsMeta = claimsItem.getItemMeta();
        claimsMeta.setDisplayName(ChatColor.GREEN + "Claims et Terrains");
        List<String> claimsLore = new ArrayList<>();
        claimsLore.add(ChatColor.GRAY + "Gérer les parcelles");
        claimsLore.add(ChatColor.GRAY + "Claim / Unclaim des chunks");
        claimsLore.add(ChatColor.GRAY + "Vendre / Louer des parcelles");
        claimsLore.add("");
        claimsLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
        claimsMeta.setLore(claimsLore);
        claimsItem.setItemMeta(claimsMeta);
        inv.setItem(14, claimsItem);

        // Banque Municipale
        ItemStack bankItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta bankMeta = bankItem.getItemMeta();
        bankMeta.setDisplayName(ChatColor.GOLD + "Banque Municipale");
        List<String> bankLore = new ArrayList<>();
        bankLore.add(ChatColor.GRAY + "Solde: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));
        bankLore.add(ChatColor.GRAY + "Dépôt / Retrait");
        bankLore.add(ChatColor.GRAY + "Voir les transactions");
        bankLore.add("");
        bankLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
        bankMeta.setLore(bankLore);
        bankItem.setItemMeta(bankMeta);
        inv.setItem(16, bankItem);

        // Services (Police/Justice)
        ItemStack servicesItem = new ItemStack(Material.IRON_BARS);
        ItemMeta servicesMeta = servicesItem.getItemMeta();
        servicesMeta.setDisplayName(ChatColor.BLUE + "Services Municipaux");
        List<String> servicesLore = new ArrayList<>();
        servicesLore.add(ChatColor.GRAY + "Police: Amendes");
        servicesLore.add(ChatColor.GRAY + "Justice: Contestations");
        servicesLore.add("");
        servicesLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
        servicesMeta.setLore(servicesLore);
        servicesItem.setItemMeta(servicesMeta);
        inv.setItem(28, servicesItem);

        // Règlements
        ItemStack rulesItem = new ItemStack(Material.PAPER);
        ItemMeta rulesMeta = rulesItem.getItemMeta();
        rulesMeta.setDisplayName(ChatColor.WHITE + "Règlements de la Ville");
        List<String> rulesLore = new ArrayList<>();
        rulesLore.add(ChatColor.GRAY + "Voir les règles municipales");
        rulesLore.add("");
        rulesLore.add(ChatColor.YELLOW + "Cliquez pour lire");
        rulesMeta.setLore(rulesLore);
        rulesItem.setItemMeta(rulesMeta);
        inv.setItem(30, rulesItem);

        // Quitter la ville
        if (!town.isMayor(player.getUniqueId())) {
            ItemStack leaveItem = new ItemStack(Material.BARRIER);
            ItemMeta leaveMeta = leaveItem.getItemMeta();
            leaveMeta.setDisplayName(ChatColor.RED + "Quitter la Ville");
            List<String> leaveLore = new ArrayList<>();
            leaveLore.add(ChatColor.GRAY + "Vous perdrez vos parcelles");
            leaveLore.add("");
            leaveLore.add(ChatColor.YELLOW + "Cliquez pour quitter");
            leaveMeta.setLore(leaveLore);
            leaveItem.setItemMeta(leaveMeta);
            inv.setItem(32, leaveItem);
        }

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(49, closeItem);

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

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "⚖️ Services Municipaux");

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

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.contains("🏙️") && !title.contains("Ville") && !title.contains("Services Municipaux") &&
            !title.equals(ChatColor.stripColor(JOIN_TOWN_TITLE)) && !title.contains("Gestion -")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = clicked.getItemMeta().getDisplayName();
        String strippedName = ChatColor.stripColor(displayName);

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
        if (strippedName.contains("Claims et Terrains")) {
            player.closeInventory();
            if (claimsGUI != null) {
                claimsGUI.openClaimsMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système de claims n'est pas disponible.");
            }
        } else if (strippedName.contains("Banque Municipale")) {
            player.closeInventory();
            if (bankGUI != null) {
                bankGUI.openBankMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système de banque n'est pas disponible.");
            }
        } else if (strippedName.contains("Services Municipaux")) {
            player.closeInventory();
            openServicesMenu(player);
        } else if (strippedName.contains("Mes Amendes")) {
            player.closeInventory();
            if (citizenFinesGUI != null) {
                citizenFinesGUI.openFinesMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système d'amendes n'est pas disponible.");
            }
        } else if (strippedName.contains("Police Municipale")) {
            player.closeInventory();
            if (policeGUI != null) {
                policeGUI.openPoliceMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système de police n'est pas disponible.");
            }
        } else if (strippedName.contains("Justice Municipale")) {
            player.closeInventory();
            if (justiceGUI != null) {
                justiceGUI.openJusticeMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système de justice n'est pas disponible.");
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
        } else if (strippedName.contains("Membres et Rôles")) {
            player.closeInventory();
            if (membersGUI != null) {
                membersGUI.openMembersMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système de membres n'est pas disponible.");
            }
        } else if (strippedName.contains("Règlements")) {
            player.closeInventory();
            handleShowRules(player);
        } else if (strippedName.contains("Quitter la Ville")) {
            player.closeInventory();
            handleLeaveTown(player);
        } else if (strippedName.contains("Retour")) {
            player.closeInventory();
            openMainMenu(player);
        } else if (strippedName.contains("Créer une Ville")) {
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
            player.sendMessage(ChatColor.YELLOW + "Fonctionnalité en développement: " + strippedName);
            player.closeInventory();
        }
    }

    private void showTownInfo(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());

        player.sendMessage(ChatColor.GOLD + "========= " + ChatColor.AQUA + townName + ChatColor.GOLD + " =========");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Informations générales :");
        player.sendMessage(ChatColor.GRAY + "- Nom: " + ChatColor.WHITE + townName);
        player.sendMessage(ChatColor.GRAY + "- Description: " + ChatColor.WHITE + town.getDescription());

        // Récupérer le nom du maire via le membre
        String mayorName = "Inconnu";
        if (town.getMember(town.getMayorUuid()) != null) {
            mayorName = town.getMember(town.getMayorUuid()).getPlayerName();
        }
        player.sendMessage(ChatColor.GRAY + "- Maire: " + ChatColor.GOLD + mayorName);
        player.sendMessage(ChatColor.GRAY + "- Membres: " + ChatColor.WHITE + town.getMemberCount());
        player.sendMessage(ChatColor.GRAY + "- Parcelles: " + ChatColor.WHITE + town.getTotalClaims());
        player.sendMessage(ChatColor.GRAY + "- Banque: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));
        player.sendMessage("");

        // Afficher tous les rôles du joueur
        TownMember member = town.getMember(player.getUniqueId());
        if (member != null) {
            Set<TownRole> playerRoles = member.getRoles();
            if (playerRoles.size() == 1) {
                player.sendMessage(ChatColor.YELLOW + "Votre rôle : " + ChatColor.AQUA + role.getDisplayName());
            } else {
                player.sendMessage(ChatColor.YELLOW + "Vos rôles : ");
                for (TownRole r : playerRoles) {
                    player.sendMessage(ChatColor.AQUA + "  • " + r.getDisplayName());
                }
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "================================================");
    }

    private void handleShowRules(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "========= " + ChatColor.AQUA + "Règlements de " + townName + ChatColor.GOLD + " =========");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Règles générales :");
        player.sendMessage(ChatColor.GRAY + "- Respecter les autres citoyens");
        player.sendMessage(ChatColor.GRAY + "- Ne pas griffer les propriétés d'autrui");
        player.sendMessage(ChatColor.GRAY + "- Payer ses taxes et amendes");
        player.sendMessage(ChatColor.GRAY + "- Suivre les instructions des autorités");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Description de la ville :");
        player.sendMessage(ChatColor.WHITE + town.getDescription());
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "================================================");
    }

    private void handleLeaveTown(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        if (town.isMayor(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Le maire ne peut pas quitter la ville. Vous devez d'abord supprimer la ville ou transférer la mairie.");
            return;
        }

        if (townManager.leaveTown(player)) {
            player.sendMessage(ChatColor.GREEN + "Vous avez quitté la ville " + ChatColor.GOLD + townName + ChatColor.GREEN + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Impossible de quitter la ville.");
        }
    }

    public TownManager getTownManager() {
        return townManager;
    }

    private void handleCreateTown(Player player) {
        // Demander le nom de la ville via le chat
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.GREEN + "Création d'une nouvelle ville");
        player.sendMessage(ChatColor.GRAY + "Tapez le nom de votre ville dans le chat");
        player.sendMessage(ChatColor.GRAY + "(ou tapez 'annuler' pour annuler)");
        player.sendMessage(ChatColor.GOLD + "========================================");

        // Enregistrer le joueur comme "en attente de saisie"
        plugin.getChatListener().waitForInput(player.getUniqueId(), (input) -> {
            if (input.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Création de ville annulée.");
                return;
            }

            // Créer la ville
            double cost = plugin.getConfig().getDouble("town.creation-cost", 10000.0);
            if (townManager.createTown(input, player, cost)) {
                player.sendMessage(ChatColor.GREEN + "Ville '" + ChatColor.GOLD + input + ChatColor.GREEN + "' créée avec succès!");
                player.sendMessage(ChatColor.GRAY + "Vous êtes maintenant le maire de cette ville.");

                // Ouvrir le menu de la ville
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
            } else {
                player.sendMessage(ChatColor.RED + "Impossible de créer la ville. Vérifiez:");
                player.sendMessage(ChatColor.GRAY + "- Que vous avez " + cost + "€");
                player.sendMessage(ChatColor.GRAY + "- Que le nom n'est pas déjà pris");
                player.sendMessage(ChatColor.GRAY + "- Que vous n'êtes pas déjà dans une ville");
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
            lore.add(ChatColor.GRAY + "Parcelles: " + ChatColor.WHITE + town.getTotalClaims());
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

        player.openInventory(inv);
    }

    private void handleJoinTown(Player player, String townName) {
        double joinCost = plugin.getConfig().getDouble("town.join-cost", 100.0);

        if (townManager.joinTown(player, townName, joinCost)) {
            player.sendMessage(ChatColor.GREEN + "Vous avez rejoint la ville " + ChatColor.GOLD + townName + ChatColor.GREEN + "!");
            Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
        } else {
            player.sendMessage(ChatColor.RED + "Impossible de rejoindre la ville. Vérifiez:");
            player.sendMessage(ChatColor.GRAY + "- Que vous avez " + joinCost + "€");
            player.sendMessage(ChatColor.GRAY + "- Que vous n'êtes pas déjà dans une ville");
        }
    }

    private void handleRenameTown(Player player) {
        String oldTownName = townManager.getPlayerTown(player.getUniqueId());
        if (oldTownName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.GREEN + "Renommer la ville");
        player.sendMessage(ChatColor.GRAY + "Tapez le nouveau nom dans le chat");
        player.sendMessage(ChatColor.GRAY + "(ou tapez 'annuler' pour annuler)");
        player.sendMessage(ChatColor.GOLD + "========================================");

        plugin.getChatListener().waitForInput(player.getUniqueId(), (input) -> {
            if (input.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Renommage annulé.");
                return;
            }

            double renameCost = plugin.getConfig().getDouble("town.rename-cost", 5000.0);
            if (townManager.renameTown(oldTownName, input, renameCost)) {
                player.sendMessage(ChatColor.GREEN + "Ville renommée en '" + ChatColor.GOLD + input + ChatColor.GREEN + "' avec succès!");
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
            } else {
                player.sendMessage(ChatColor.RED + "Impossible de renommer la ville. Vérifiez:");
                player.sendMessage(ChatColor.GRAY + "- Que la ville a " + renameCost + "€ en banque");
                player.sendMessage(ChatColor.GRAY + "- Que le nom n'est pas déjà pris");
            }
        });
    }

    private void handleDeleteTown(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Seul le maire peut supprimer la ville.");
            return;
        }

        player.sendMessage(ChatColor.RED + "========================================");
        player.sendMessage(ChatColor.DARK_RED + "ATTENTION: Suppression de ville");
        player.sendMessage(ChatColor.GRAY + "Cette action est IRRÉVERSIBLE!");
        player.sendMessage(ChatColor.GRAY + "Tapez le nom de la ville pour confirmer:");
        player.sendMessage(ChatColor.YELLOW + townName);
        player.sendMessage(ChatColor.GRAY + "(ou tapez 'annuler' pour annuler)");
        player.sendMessage(ChatColor.RED + "========================================");

        plugin.getChatListener().waitForInput(player.getUniqueId(), (input) -> {
            if (input.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.GREEN + "Suppression annulée.");
                return;
            }

            if (!input.equals(townName)) {
                player.sendMessage(ChatColor.RED + "Le nom ne correspond pas. Suppression annulée.");
                return;
            }

            if (townManager.deleteTown(townName)) {
                player.sendMessage(ChatColor.GREEN + "La ville " + ChatColor.GOLD + townName + ChatColor.GREEN + " a été supprimée.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 20L);
            } else {
                player.sendMessage(ChatColor.RED + "Impossible de supprimer la ville.");
            }
        });
    }

    private void openManageTownMenu(Player player, String townName) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Gestion - " + townName);

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
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

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);
    }
}
