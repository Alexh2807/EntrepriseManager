package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import com.gravityyfh.roleplaycity.town.manager.TownPoliceManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

public class TownPoliceGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownPoliceManager policeManager;

    private static final String POLICE_TITLE = ChatColor.DARK_BLUE + "🚔 Police Municipale";
    private static final String SELECT_PLAYER_TITLE = ChatColor.DARK_BLUE + "👤 Sélectionner un joueur";
    private static final String SELECT_FINE_TITLE = ChatColor.DARK_BLUE + "📋 Type d'amende";
    private static final String SELECT_ID_TITLE = ChatColor.DARK_BLUE + "📋 Demander l'Identité";

    private final Map<UUID, FineContext> pendingFines;

    public TownPoliceGUI(RoleplayCity plugin, TownManager townManager, TownPoliceManager policeManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.policeManager = policeManager;
        this.pendingFines = new HashMap<>();
    }

    public void openPoliceMenu(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous devez être dans une ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role != TownRole.POLICIER && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            player.sendMessage(ChatColor.RED + "Vous devez être policier pour accéder à ce menu.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, POLICE_TITLE);

        // Statistiques
        var stats = policeManager.getTownStatistics(townName);

        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.GOLD + "Statistiques");
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "Total amendes: " + ChatColor.WHITE + stats.totalFines());
        statsLore.add(ChatColor.GRAY + "Payées: " + ChatColor.GREEN + stats.paidFines());
        statsLore.add(ChatColor.GRAY + "Contestées: " + ChatColor.YELLOW + stats.contestedFines());
        statsLore.add(ChatColor.GRAY + "En attente: " + ChatColor.RED + stats.pendingFines());
        statsLore.add("");
        statsLore.add(ChatColor.GRAY + "Montant total: " + ChatColor.GOLD + String.format("%.2f€", stats.totalAmount()));
        statsLore.add(ChatColor.GRAY + "Collecté: " + ChatColor.GREEN + String.format("%.2f€", stats.collectedAmount()));
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(4, statsItem);

        // Émettre une amende (slot 10)
        ItemStack issueFineItem = new ItemStack(Material.PAPER);
        ItemMeta issueFineMeta = issueFineItem.getItemMeta();
        issueFineMeta.setDisplayName(ChatColor.RED + "Émettre une Amende");
        List<String> issueFineLore = new ArrayList<>();
        issueFineLore.add(ChatColor.GRAY + "Infliger une amende");
        issueFineLore.add(ChatColor.GRAY + "à un citoyen");
        issueFineLore.add("");
        issueFineLore.add(ChatColor.YELLOW + "Cliquez pour commencer");
        issueFineMeta.setLore(issueFineLore);
        issueFineItem.setItemMeta(issueFineMeta);
        inv.setItem(10, issueFineItem);

        // Amendes en attente (slot 12)
        ItemStack activeFinesItem = new ItemStack(Material.REDSTONE);
        ItemMeta activeFinesMeta = activeFinesItem.getItemMeta();
        activeFinesMeta.setDisplayName(ChatColor.YELLOW + "Amendes en Attente");
        List<String> activeFinesLore = new ArrayList<>();
        activeFinesLore.add(ChatColor.GRAY + "Voir toutes les amendes");
        activeFinesLore.add(ChatColor.GRAY + "qui n'ont pas été payées");
        activeFinesLore.add("");
        activeFinesLore.add(ChatColor.WHITE + "Total: " + stats.pendingFines());
        activeFinesLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        activeFinesMeta.setLore(activeFinesLore);
        activeFinesItem.setItemMeta(activeFinesMeta);
        inv.setItem(12, activeFinesItem);

        // Amendes contestées (slot 14)
        ItemStack contestedItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta contestedMeta = contestedItem.getItemMeta();
        contestedMeta.setDisplayName(ChatColor.GOLD + "Amendes Contestées");
        List<String> contestedLore = new ArrayList<>();
        contestedLore.add(ChatColor.GRAY + "Voir les amendes");
        contestedLore.add(ChatColor.GRAY + "en attente de jugement");
        contestedLore.add("");
        contestedLore.add(ChatColor.WHITE + "Total: " + stats.contestedFines());
        contestedLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        contestedMeta.setLore(contestedLore);
        contestedItem.setItemMeta(contestedMeta);
        inv.setItem(14, contestedItem);

        // Emprisonner un joueur (slot 19 - système de prison)
        if (plugin.getPrisonManager() != null) {
            ItemStack imprisonItem = new ItemStack(Material.IRON_BARS);
            ItemMeta imprisonMeta = imprisonItem.getItemMeta();
            imprisonMeta.setDisplayName(ChatColor.DARK_RED + "⛓️ Emprisonner");
            List<String> imprisonLore = new ArrayList<>();
            imprisonLore.add(ChatColor.GRAY + "Emprisonner un joueur");
            imprisonLore.add(ChatColor.GRAY + "menotté sur un COMMISSARIAT");
            imprisonLore.add("");
            imprisonLore.add(ChatColor.YELLOW + "Cliquez pour commencer");
            imprisonMeta.setLore(imprisonLore);
            imprisonItem.setItemMeta(imprisonMeta);
            inv.setItem(19, imprisonItem);
        }

        // Gérer les prisonniers (slot 21)
        if (plugin.getPrisonManager() != null) {
            int prisonersCount = plugin.getPrisonManager().getImprisonedData().getImprisonedCount();
            ItemStack managePrisonersItem = new ItemStack(Material.IRON_DOOR);
            ItemMeta managePrisonersMeta = managePrisonersItem.getItemMeta();
            managePrisonersMeta.setDisplayName(ChatColor.DARK_RED + "🔒 Gérer les Prisonniers");
            List<String> managePrisonersLore = new ArrayList<>();
            managePrisonersLore.add(ChatColor.GRAY + "Voir tous les prisonniers");
            managePrisonersLore.add(ChatColor.GRAY + "de votre ville");
            managePrisonersLore.add("");
            managePrisonersLore.add(ChatColor.WHITE + "Prisonniers: " + prisonersCount);
            managePrisonersLore.add(ChatColor.YELLOW + "Cliquez pour voir");
            managePrisonersMeta.setLore(managePrisonersLore);
            managePrisonersItem.setItemMeta(managePrisonersMeta);
            inv.setItem(21, managePrisonersItem);
        }

        // Fouiller un joueur (slot 23 - système de fouille)
        if (plugin.getFriskGUI() != null) {
            ItemStack friskItem = new ItemStack(Material.ENDER_EYE);
            ItemMeta friskMeta = friskItem.getItemMeta();
            friskMeta.setDisplayName(ChatColor.DARK_PURPLE + "🔍 Fouiller un joueur");
            List<String> friskLore = new ArrayList<>();
            friskLore.add(ChatColor.GRAY + "Fouiller un suspect");
            friskLore.add(ChatColor.GRAY + "à proximité");
            friskLore.add("");
            friskLore.add(ChatColor.WHITE + "• Menotté: fouille directe");
            friskLore.add(ChatColor.WHITE + "• Libre: consentement requis");
            friskLore.add("");
            friskLore.add(ChatColor.YELLOW + "Cliquez pour commencer");
            friskMeta.setLore(friskLore);
            friskItem.setItemMeta(friskMeta);
            inv.setItem(23, friskItem);
        }

        // Demander l'identité (slot 16)
        ItemStack idRequestItem = new ItemStack(Material.NAME_TAG);
        ItemMeta idRequestMeta = idRequestItem.getItemMeta();
        idRequestMeta.setDisplayName(ChatColor.AQUA + "📋 Demander l'Identité");
        List<String> idRequestLore = new ArrayList<>();
        idRequestLore.add(ChatColor.GRAY + "Demander la carte d'identité");
        idRequestLore.add(ChatColor.GRAY + "d'un citoyen à proximité");
        idRequestLore.add("");
        idRequestLore.add(ChatColor.WHITE + "Le joueur doit accepter");
        idRequestLore.add(ChatColor.WHITE + "de montrer sa carte.");
        idRequestLore.add("");
        idRequestLore.add(ChatColor.YELLOW + "Cliquez pour commencer");
        idRequestMeta.setLore(idRequestLore);
        idRequestItem.setItemMeta(idRequestMeta);
        inv.setItem(16, idRequestItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection de joueur pour demander l'identité
     * N'affiche que les joueurs à proximité (5 blocs)
     */
    private void openIdRequestSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, SELECT_ID_TITLE);

        int slot = 0;
        boolean found = false;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;

            // Vérifier la distance (5 blocs)
            if (!target.getWorld().equals(player.getWorld()) ||
                target.getLocation().distance(player.getLocation()) > 5) {
                continue;
            }

            found = true;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.YELLOW + target.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Distance: " + String.format("%.1fm", target.getLocation().distance(player.getLocation())));
            lore.add("");
            lore.add(ChatColor.GREEN + "► Cliquez pour demander son ID");
            meta.setLore(lore);
            head.setItemMeta(meta);
            inv.setItem(slot++, head);

            if (slot >= 18) break;
        }

        if (!found) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Aucun joueur à proximité");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Rapprochez-vous d'un citoyen",
                ChatColor.GRAY + "pour demander son identité."
            ));
            none.setItemMeta(meta);
            inv.setItem(13, none);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    // PHASE 1: Sélection du joueur en ligne
    private void openPlayerSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECT_PLAYER_TITLE);

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int slot = 0;

        for (Player target : onlinePlayers) {
            if (slot >= 45) break;
            if (target.equals(player)) continue; // Ne pas afficher le policier lui-même

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(target);
            skullMeta.setDisplayName(ChatColor.YELLOW + target.getName());

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GREEN + "Cliquez pour sélectionner");
            skullMeta.setLore(lore);

            playerHead.setItemMeta(skullMeta);
            inv.setItem(slot++, playerHead);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);

        player.openInventory(inv);
    }

    // PHASE 2: Sélection de l'amende prédéfinie
    private void openFineSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECT_FINE_TITLE);

        // Charger les amendes prédéfinies depuis la config
        List<Map<?, ?>> predefinedFines = plugin.getConfig().getMapList("town.predefined-fines");
        int slot = 0;

        for (Map<?, ?> fineData : predefinedFines) {
            if (slot >= 45) break;

            String title = (String) fineData.get("title");
            double amount = ((Number) fineData.get("amount")).doubleValue();
            String description = (String) fineData.get("description");

            ItemStack fineItem = new ItemStack(Material.PAPER);
            ItemMeta fineMeta = fineItem.getItemMeta();
            fineMeta.setDisplayName(ChatColor.RED + title);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + amount + "€");
            lore.add("");
            lore.add(ChatColor.WHITE + description);
            lore.add("");
            lore.add(ChatColor.GREEN + "Cliquez pour sélectionner");
            fineMeta.setLore(lore);

            fineItem.setItemMeta(fineMeta);
            inv.setItem(slot++, fineItem);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

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

        // Menu principal de la police
        if (title.equals(POLICE_TITLE)) {
            event.setCancelled(true);
            String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            if (displayName.contains("Émettre une Amende")) {
                // Initialiser le contexte et ouvrir la phase 1
                pendingFines.put(player.getUniqueId(), new FineContext());
                openPlayerSelectionMenu(player);
            } else if (displayName.contains("Emprisonner")) {
                // Ouvrir le workflow d'emprisonnement
                if (plugin.getImprisonmentWorkflowGUI() != null) {
                    plugin.getImprisonmentWorkflowGUI().openPrisonerSelectionMenu(player);
                }
            } else if (displayName.contains("Gérer les Prisonniers")) {
                // Ouvrir le menu de gestion des prisonniers
                if (plugin.getTownPrisonManagementGUI() != null) {
                    plugin.getTownPrisonManagementGUI().openPrisonManagementMenu(player);
                }
            } else if (displayName.contains("Fouiller un joueur")) {
                if (plugin.getFriskGUI() != null) {
                    plugin.getFriskGUI().openTargetSelection(player);
                }
            } else if (displayName.contains("Demander l'Identité")) {
                openIdRequestSelectionMenu(player);
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            }
        }
        // Menu de sélection pour demande d'identité
        else if (title.equals(SELECT_ID_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta idSkullMeta = (SkullMeta) clicked.getItemMeta();
                org.bukkit.OfflinePlayer idOwningPlayer = idSkullMeta.getOwningPlayer();
                Player target = idOwningPlayer != null ? Bukkit.getPlayer(idOwningPlayer.getUniqueId()) : null;

                if (target != null && target.isOnline()) {
                    player.closeInventory();

                    // Vérifier la distance
                    var requestManager = plugin.getInteractionRequestManager();
                    if (requestManager == null) {
                        player.sendMessage(ChatColor.RED + "Système non disponible.");
                        return;
                    }

                    // Vérifier s'il n'y a pas déjà une demande en cours
                    if (requestManager.hasPendingRequest(player.getUniqueId(), target.getUniqueId(),
                            com.gravityyfh.roleplaycity.service.InteractionRequest.RequestType.REQUEST_ID)) {
                        player.sendMessage(ChatColor.YELLOW + "Une demande d'identité est déjà en attente pour ce joueur.");
                        return;
                    }

                    var request = requestManager.createIdRequest(player, target);
                    if (request != null) {
                        player.sendMessage(ChatColor.GREEN + "Demande d'identité envoyée à " + target.getName() + ".");
                        player.sendMessage(ChatColor.GRAY + "En attente de sa réponse (30 secondes)...");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Le joueur n'est plus en ligne.");
                    player.closeInventory();
                }
            } else if (clicked.getType() == Material.ARROW) {
                openPoliceMenu(player);
            } else if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        }
        // PHASE 1: Sélection du joueur
        else if (title.equals(SELECT_PLAYER_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) clicked.getItemMeta();
                // Utiliser getOwningPlayer pour récupérer le joueur (plus fiable que le nom d'affichage)
                org.bukkit.OfflinePlayer owningPlayer = skullMeta.getOwningPlayer();
                Player target = owningPlayer != null ? Bukkit.getPlayer(owningPlayer.getUniqueId()) : null;

                if (target != null && target.isOnline()) {
                    FineContext context = pendingFines.get(player.getUniqueId());
                    if (context != null) {
                        context.targetUuid = target.getUniqueId();
                        context.targetName = target.getName();
                        context.targetDisplayName = target.getName();
                        context.step = 1;

                        // Passer à la phase 2
                        openFineSelectionMenu(player);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Le joueur n'est plus en ligne.");
                    player.closeInventory();
                    pendingFines.remove(player.getUniqueId());
                }
            } else if (clicked.getType() == Material.ARROW) {
                openPoliceMenu(player);
                pendingFines.remove(player.getUniqueId());
            } else if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                pendingFines.remove(player.getUniqueId());
            }
        }
        // PHASE 2: Sélection de l'amende
        else if (title.equals(SELECT_FINE_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.PAPER) {
                FineContext context = pendingFines.get(player.getUniqueId());
                if (context != null) {
                    String fineTitle = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

                    // Récupérer les informations de l'amende depuis le lore
                    List<String> lore = clicked.getItemMeta().getLore();
                    String amountLine = ChatColor.stripColor(lore.get(0)); // "Montant: 500.0€"
                    double amount = Double.parseDouble(amountLine.replaceAll("[^0-9.]", ""));

                    context.fineTitle = fineTitle;
                    context.amount = amount;
                    context.step = 2;

                    // Passer à la phase 3: demander la description
                    player.closeInventory();
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§c🚔 §lÉMETTRE UNE AMENDE");
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§7Contrevenant: §e" + context.targetDisplayName);
                    player.sendMessage("§7Type: §c" + fineTitle);
                    player.sendMessage("§7Montant: §6" + amount + "€");
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§eÉtape 3/3: Entrez une description détaillée");
                    player.sendMessage("§7(Minimum 20 caractères)");
                    player.sendMessage("§7(Tapez 'annuler' pour abandonner)");
                }
            } else if (clicked.getType() == Material.ARROW) {
                openPlayerSelectionMenu(player);
            } else if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                pendingFines.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FineContext context = pendingFines.get(player.getUniqueId());

        if (context == null || context.step != 2) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingFines.remove(player.getUniqueId());
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§e✖ Émission d'amende annulée");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Vérifier la longueur minimale
            if (input.length() < 20) {
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§c✖ Description trop courte");
                player.sendMessage("§7Minimum: §f20 caractères");
                player.sendMessage("§7Actuel: §f" + input.length() + " / 20");
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                return;
            }

            // Émettre l'amende
            String townName = townManager.getPlayerTown(player.getUniqueId());
            String fullReason = context.fineTitle + " - " + input;

            Fine fine = policeManager.issueFine(
                townName,
                context.targetUuid,
                context.targetName,
                player,
                fullReason,
                context.amount
            );

            if (fine != null) {
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§a✔ §lAMENDE ÉMISE AVEC SUCCÈS");
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§7Contrevenant: §e" + context.targetDisplayName);
                player.sendMessage("§7Type: §c" + context.fineTitle);
                player.sendMessage("§7Montant: §6" + context.amount + "€");
                player.sendMessage("§7Description: §f" + input);
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            } else {
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§c✖ Erreur lors de l'émission");
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            }

            pendingFines.remove(player.getUniqueId());
        });
    }

    private static class FineContext {
        int step = 0;
        UUID targetUuid;
        String targetName; // Nom Minecraft (pour logs)
        String targetDisplayName; // Nom d'identité (pour affichage)
        String fineTitle;
        double amount;
    }
}
