package com.gravityyfh.roleplaycity.postal.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.manager.MailboxManager;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.milkbowl.vault.economy.Economy;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Interface graphique pour le système de livraison La Poste
 */
public class LaPosteGUI implements Listener {
    private static final String MAIN_MENU_TITLE = ChatColor.GOLD + "📮 La Poste";
    private static final String DESTINATION_TYPE_TITLE = ChatColor.GOLD + "Type de Destination";
    private static final String TOWN_SELECT_TITLE = ChatColor.GOLD + "Choisir une Ville";
    private static final String RESIDENT_SELECT_TITLE = ChatColor.GOLD + "Choisir un Résident";
    private static final String PLOT_SELECT_TITLE = ChatColor.GOLD + "Choisir un Terrain";
    private static final String MUNICIPAL_PLOT_SELECT_TITLE = ChatColor.GOLD + "Terrains Municipaux";
    private static final String CONFIRM_TITLE = ChatColor.GOLD + "Confirmer l'envoi";

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final MailboxManager mailboxManager;
    private final Economy economy;

    // Contexte de navigation : joueur -> état actuel
    private final Map<UUID, PostalContext> contexts;

    // Attente de saisie de message (lettre)
    private final Map<UUID, PostalContext> awaitingMessage;

    // Inventaire temporaire pour les colis
    private final Map<UUID, Inventory> parcelInventories;

    public LaPosteGUI(RoleplayCity plugin, TownManager townManager,
                      MailboxManager mailboxManager, Economy economy) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.mailboxManager = mailboxManager;
        this.economy = economy;
        this.contexts = new HashMap<>();
        this.awaitingMessage = new HashMap<>();
        this.parcelInventories = new HashMap<>();
    }

    /**
     * Ouvre le menu principal de La Poste
     */
    public void openMainMenu(Player player, String senderTownName) {
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);

        // Titre
        ItemStack titleItem = new ItemStack(Material.PAPER);
        titleItem.setItemMeta(createMeta(titleItem, ChatColor.GOLD + "📮 Bienvenue à La Poste",
            Arrays.asList(
                ChatColor.GRAY + "Envoyez du courrier et des colis",
                ChatColor.GRAY + "à travers toutes les villes !",
                "",
                ChatColor.YELLOW + "Choisissez un type d'envoi ci-dessous"
            )));
        inv.setItem(4, titleItem);

        // Envoyer une lettre
        ItemStack letterItem = new ItemStack(Material.WRITABLE_BOOK);
        letterItem.setItemMeta(createMeta(letterItem, ChatColor.GREEN + "✉ Envoyer une Lettre",
            Arrays.asList(
                ChatColor.GRAY + "Envoyez un message écrit",
                ChatColor.GRAY + "à un résident",
                "",
                ChatColor.GOLD + "Prix: 5€",
                "",
                ChatColor.YELLOW + "Cliquez pour continuer"
            )));
        inv.setItem(11, letterItem);

        // Envoyer un colis
        ItemStack parcelItem = new ItemStack(Material.CHEST);
        parcelItem.setItemMeta(createMeta(parcelItem, ChatColor.AQUA + "📦 Envoyer un Colis",
            Arrays.asList(
                ChatColor.GRAY + "Envoyez des objets",
                ChatColor.GRAY + "à un résident",
                "",
                ChatColor.GOLD + "Prix: 20€",
                "",
                ChatColor.YELLOW + "Cliquez pour continuer"
            )));
        inv.setItem(15, parcelItem);

        player.openInventory(inv);

        // Créer le contexte
        PostalContext context = new PostalContext();
        context.senderTownName = senderTownName;
        contexts.put(player.getUniqueId(), context);
    }

    /**
     * Ouvre le menu de choix du type de destination (Résident ou Terrain Municipal)
     */
    private void openDestinationTypeMenu(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, DESTINATION_TYPE_TITLE);

        // Titre
        ItemStack titleItem = new ItemStack(Material.COMPASS);
        titleItem.setItemMeta(createMeta(titleItem, ChatColor.GOLD + "Choisir la Destination",
            Arrays.asList(
                ChatColor.GRAY + "Sélectionnez le type",
                ChatColor.GRAY + "de destinataire",
                "",
                ChatColor.YELLOW + (context.isLetter ? "Lettre" : "Colis") + " en cours d'envoi"
            )));
        inv.setItem(4, titleItem);

        // Option 1: Envoyer à un résident
        ItemStack residentItem = new ItemStack(Material.PLAYER_HEAD);
        residentItem.setItemMeta(createMeta(residentItem, ChatColor.GREEN + "👤 Envoyer à un Résident",
            Arrays.asList(
                ChatColor.GRAY + "Livrer à un joueur",
                ChatColor.GRAY + "sur son terrain personnel",
                "",
                ChatColor.YELLOW + "Cliquez pour continuer"
            )));
        inv.setItem(11, residentItem);

        // Option 2: Envoyer à un terrain municipal
        ItemStack municipalItem = new ItemStack(Material.BRICK);
        municipalItem.setItemMeta(createMeta(municipalItem, ChatColor.AQUA + "🏛 Terrain Municipal",
            Arrays.asList(
                ChatColor.GRAY + "Livrer à un bâtiment",
                ChatColor.GRAY + "municipal d'une ville",
                "",
                ChatColor.GRAY + "• Mairie, Commissariat",
                ChatColor.GRAY + "• Banque, Hôpital, etc.",
                "",
                ChatColor.YELLOW + "Cliquez pour continuer"
            )));
        inv.setItem(15, municipalItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection de ville
     */
    private void openTownSelectionMenu(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, TOWN_SELECT_TITLE);

        List<Town> towns = new ArrayList<>(townManager.getAllTowns());

        int slot = 0;
        for (Town town : towns) {
            if (slot >= 54) break;

            ItemStack townItem = new ItemStack(Material.GRASS_BLOCK);
            townItem.setItemMeta(createMeta(townItem, ChatColor.YELLOW + town.getName(),
                Arrays.asList(
                    ChatColor.GRAY + "Population: " + ChatColor.WHITE + town.getMemberCount(),
                    ChatColor.GRAY + "Niveau: " + town.getLevel().getDisplayName(),
                    "",
                    ChatColor.YELLOW + "Cliquez pour choisir cette ville"
                )));
            inv.setItem(slot, townItem);
            slot++;
        }

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection de résident
     */
    private void openResidentSelectionMenu(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null || context.targetTownName == null) return;

        Town town = townManager.getTown(context.targetTownName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, RESIDENT_SELECT_TITLE);

        List<TownMember> members = new ArrayList<>(town.getMembers().values());

        int slot = 0;
        for (TownMember member : members) {
            if (slot >= 54) break;

            // Ne pas permettre de s'envoyer à soi-même
            if (member.getPlayerUuid().equals(player.getUniqueId())) {
                continue;
            }

            ItemStack residentItem = new ItemStack(Material.PLAYER_HEAD);
            residentItem.setItemMeta(createMeta(residentItem, ChatColor.GREEN + member.getPlayerName(),
                Arrays.asList(
                    ChatColor.GRAY + "Rôle: " + ChatColor.AQUA + member.getRole().getDisplayName(),
                    "",
                    ChatColor.YELLOW + "Cliquez pour choisir ce résident"
                )));
            inv.setItem(slot, residentItem);
            slot++;
        }

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection de terrain
     */
    private void openPlotSelectionMenu(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null || context.targetResidentUuid == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Contexte invalide");
            return;
        }

        Town town = townManager.getTown(context.targetTownName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable");
            return;
        }

        // Récupérer tous les terrains du résident (propriétaire ou locataire)
        List<Plot> plots = town.getPlots().values().stream()
            .filter(p -> (p.getOwnerUuid() != null && p.getOwnerUuid().equals(context.targetResidentUuid)) ||
                        (p.getRenterUuid() != null && p.getRenterUuid().equals(context.targetResidentUuid)))
            .collect(Collectors.toList());

        if (plots.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Ce résident n'a aucun terrain dans " + context.targetTownName + ".");
            player.closeInventory();
            contexts.remove(player.getUniqueId());
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, PLOT_SELECT_TITLE);

        int slot = 0;
        for (Plot plot : plots) {
            if (slot >= 54) break;

            boolean hasMailbox = mailboxManager.hasMailbox(plot);
            Material icon = hasMailbox ? Material.GREEN_WOOL : Material.RED_WOOL;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE + plot.getDisplayInfo());
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + plot.getType().getDisplayName());
            lore.add("");

            if (hasMailbox) {
                lore.add(ChatColor.GREEN + "✓ Boîte aux lettres disponible");
                lore.add("");
                lore.add(ChatColor.YELLOW + "Cliquez pour envoyer ici");
            } else {
                lore.add(ChatColor.RED + "✗ Aucune boîte aux lettres");
                lore.add(ChatColor.GRAY + "Impossible de livrer ici");
            }

            ItemStack plotItem = new ItemStack(icon);
            plotItem.setItemMeta(createMeta(plotItem,
                ChatColor.GOLD + "Terrain " + (hasMailbox ? "" : ChatColor.RED + "(Non disponible)"),
                lore));
            inv.setItem(slot, plotItem);
            slot++;
        }

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection de terrains municipaux
     */
    private void openMunicipalPlotSelectionMenu(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null || context.targetTownName == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Contexte invalide");
            return;
        }

        Town town = townManager.getTown(context.targetTownName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable");
            return;
        }

        // Récupérer tous les terrains municipaux de la ville
        List<Plot> municipalPlots = town.getPlots().values().stream()
            .filter(p -> p.getType() == PlotType.MUNICIPAL)
            .collect(Collectors.toList());

        if (municipalPlots.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Cette ville n'a aucun terrain municipal.");
            player.closeInventory();
            contexts.remove(player.getUniqueId());
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, MUNICIPAL_PLOT_SELECT_TITLE);

        int slot = 0;
        for (Plot plot : municipalPlots) {
            if (slot >= 54) break;

            boolean hasMailbox = mailboxManager.hasMailbox(plot);
            MunicipalSubType subType = plot.getMunicipalSubType();

            // Icône selon le sous-type
            Material icon;
            if (!hasMailbox) {
                icon = Material.RED_WOOL;
            } else {
                icon = subType != null ? subType.getIcon() : Material.BRICK;
            }

            // Nom du terrain
            String plotName;
            if (subType != null && subType != MunicipalSubType.NONE) {
                plotName = subType.getDisplayName();
            } else {
                plotName = "Municipal";
            }

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "Municipal");
            if (subType != null && subType != MunicipalSubType.NONE) {
                lore.add(ChatColor.GRAY + "Fonction: " + ChatColor.LIGHT_PURPLE + subType.getDisplayName());
            }
            lore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE + plot.getCoordinates());
            lore.add("");

            if (hasMailbox) {
                lore.add(ChatColor.GREEN + "✓ Boîte aux lettres disponible");
                lore.add("");
                lore.add(ChatColor.YELLOW + "Cliquez pour envoyer ici");
            } else {
                lore.add(ChatColor.RED + "✗ Aucune boîte aux lettres");
                lore.add(ChatColor.GRAY + "Impossible de livrer ici");
            }

            ItemStack plotItem = new ItemStack(icon);
            plotItem.setItemMeta(createMeta(plotItem,
                (hasMailbox ? ChatColor.GOLD : ChatColor.RED) + plotName,
                lore));
            inv.setItem(slot, plotItem);
            slot++;
        }

        player.openInventory(inv);
    }

    /**
     * Démarre le processus d'envoi de lettre
     */
    private void startLetterProcess(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null) return;

        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "✉ Écrivez votre message dans le chat :");
        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");

        awaitingMessage.put(player.getUniqueId(), context);
    }

    /**
     * Démarre le processus d'envoi de colis
     */
    private void startParcelProcess(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null) return;

        player.closeInventory();

        // Créer un inventaire temporaire pour le colis
        Inventory parcelInv = Bukkit.createInventory(null, 27,
            ChatColor.GOLD + "Votre Colis - Placez vos objets");

        parcelInventories.put(player.getUniqueId(), parcelInv);

        player.sendMessage(ChatColor.GREEN + "📦 Préparez votre colis...");
        player.sendMessage(ChatColor.YELLOW + "Un inventaire va s'ouvrir. Placez-y vos objets.");
        player.sendMessage(ChatColor.GRAY + "Fermez l'inventaire pour valider.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.openInventory(parcelInv);
        }, 10L);
    }

    /**
     * Ouvre le menu de confirmation d'envoi
     */
    private void openConfirmationMenu(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null) return;

        double price = context.isLetter ? 5.0 : 20.0;

        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);

        // Informations
        ItemStack infoItem = new ItemStack(context.isLetter ? Material.WRITABLE_BOOK : Material.CHEST);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + (context.isLetter ? "Lettre" : "Colis"));
        lore.add(ChatColor.GRAY + "Destinataire: " + ChatColor.YELLOW + context.targetResidentName);
        lore.add(ChatColor.GRAY + "Ville: " + ChatColor.YELLOW + context.targetTownName);
        lore.add("");
        lore.add(ChatColor.GOLD + "Prix: " + price + "€");
        lore.add("");
        lore.add(ChatColor.GRAY + "L'argent sera versé à la ville");
        lore.add(ChatColor.GRAY + "de " + context.senderTownName);

        infoItem.setItemMeta(createMeta(infoItem, ChatColor.AQUA + "Récapitulatif", lore));
        inv.setItem(4, infoItem);

        // Confirmer
        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL);
        confirmItem.setItemMeta(createMeta(confirmItem, ChatColor.GREEN + "✓ Confirmer l'envoi",
            Arrays.asList(
                ChatColor.GRAY + "Payer " + ChatColor.GOLD + price + "€",
                ChatColor.GRAY + "et envoyer le courrier"
            )));
        inv.setItem(11, confirmItem);

        // Annuler
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        cancelItem.setItemMeta(createMeta(cancelItem, ChatColor.RED + "✗ Annuler",
            Collections.singletonList(ChatColor.GRAY + "Annuler l'envoi")));
        inv.setItem(15, cancelItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.contains("La Poste") && !title.contains("Ville") &&
            !title.contains("Résident") && !title.contains("Terrain") &&
            !title.contains("Confirmer") && !title.contains("Destination")) {

            // Gérer l'inventaire du colis
            if (title.contains("Votre Colis")) {
                // Ne pas annuler pour permettre de placer des objets
                return;
            }

            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return; // Ignorer les clics sur des slots vides
        }

        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null) {
            return; // Pas de contexte, ignorer
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            return; // Pas de nom d'item, ignorer
        }

        String displayName = ChatColor.stripColor(meta.getDisplayName());

        // Menu principal
        if (title.equals(MAIN_MENU_TITLE)) {
            if (displayName.contains("Envoyer une Lettre") || displayName.contains("Lettre")) {
                context.isLetter = true;
                player.closeInventory();
                openDestinationTypeMenu(player);
            } else if (displayName.contains("Envoyer un Colis") || displayName.contains("Colis")) {
                context.isLetter = false;
                player.closeInventory();
                openDestinationTypeMenu(player);
            } else if (displayName.contains("Bienvenue") || displayName.isEmpty()) {
                // Clic sur le titre ou un slot vide, ignorer
            }
        }
        // Sélection du type de destination
        else if (title.equals(DESTINATION_TYPE_TITLE)) {
            if (displayName.contains("Résident") || displayName.contains("Particulier")) {
                context.isMunicipalDelivery = false;
                player.closeInventory();
                openTownSelectionMenu(player);
            } else if (displayName.contains("Municipal") || displayName.contains("Bâtiment")) {
                context.isMunicipalDelivery = true;
                player.closeInventory();
                openTownSelectionMenu(player);
            }
        }
        // Sélection de ville
        else if (title.equals(TOWN_SELECT_TITLE)) {
            context.targetTownName = ChatColor.stripColor(displayName);
            player.closeInventory();

            if (context.isMunicipalDelivery) {
                openMunicipalPlotSelectionMenu(player);
            } else {
                openResidentSelectionMenu(player);
            }
        }
        // Sélection de résident
        else if (title.equals(RESIDENT_SELECT_TITLE)) {
            // Enlever les codes de couleur pour récupérer le nom pur
            String cleanName = ChatColor.stripColor(displayName);
            context.targetResidentName = cleanName;

            // Trouver l'UUID du résident
            Town town = townManager.getTown(context.targetTownName);
            if (town != null) {
                for (TownMember member : town.getMembers().values()) {
                    if (member.getPlayerName().equals(cleanName)) {
                        context.targetResidentUuid = member.getPlayerUuid();
                        break;
                    }
                }
            }

            if (context.targetResidentUuid == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Impossible de trouver le joueur " + cleanName);
                player.closeInventory();
                return;
            }

            player.closeInventory();
            openPlotSelectionMenu(player);
        }
        // Sélection de terrain
        else if (title.equals(PLOT_SELECT_TITLE)) {
            if (clicked.getType() == Material.RED_WOOL) {
                player.sendMessage(ChatColor.RED + "Ce terrain n'a pas de boîte aux lettres.");
                return;
            }

            // Récupérer le plot sélectionné
            int slot = event.getSlot();
            Town town = townManager.getTown(context.targetTownName);
            if (town != null) {
                List<Plot> plots = town.getPlots().values().stream()
                    .filter(p -> (p.getOwnerUuid() != null && p.getOwnerUuid().equals(context.targetResidentUuid)) ||
                                (p.getRenterUuid() != null && p.getRenterUuid().equals(context.targetResidentUuid)))
                    .collect(Collectors.toList());

                if (slot < plots.size()) {
                    context.targetPlot = plots.get(slot);

                    player.closeInventory();

                    if (context.isLetter) {
                        startLetterProcess(player);
                    } else {
                        startParcelProcess(player);
                    }
                }
            }
        }
        // Sélection de terrain municipal
        else if (title.equals(MUNICIPAL_PLOT_SELECT_TITLE)) {
            if (clicked.getType() == Material.RED_WOOL) {
                player.sendMessage(ChatColor.RED + "Ce terrain municipal n'a pas de boîte aux lettres.");
                return;
            }

            // Récupérer le plot municipal sélectionné
            int slot = event.getSlot();
            Town town = townManager.getTown(context.targetTownName);
            if (town != null) {
                List<Plot> municipalPlots = town.getPlots().values().stream()
                    .filter(p -> p.getType() == PlotType.MUNICIPAL)
                    .collect(Collectors.toList());

                if (slot < municipalPlots.size()) {
                    Plot selectedPlot = municipalPlots.get(slot);

                    // Vérifier la boîte aux lettres
                    if (!mailboxManager.hasMailbox(selectedPlot)) {
                        player.sendMessage(ChatColor.RED + "Ce terrain n'a pas de boîte aux lettres.");
                        return;
                    }

                    context.targetPlot = selectedPlot;
                    // Pour les terrains municipaux, pas de destinataire spécifique
                    MunicipalSubType subType = selectedPlot.getMunicipalSubType();
                    if (subType != null && subType != MunicipalSubType.NONE) {
                        context.targetResidentName = subType.getDisplayName() + " de " + context.targetTownName;
                    } else {
                        context.targetResidentName = "Terrain Municipal de " + context.targetTownName;
                    }

                    player.closeInventory();

                    if (context.isLetter) {
                        startLetterProcess(player);
                    } else {
                        startParcelProcess(player);
                    }
                }
            }
        }
        // Confirmation
        else if (title.equals(CONFIRM_TITLE)) {
            if (displayName.contains("Confirmer")) {
                processSendMail(player);
            } else if (displayName.contains("Annuler")) {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Envoi annulé.");
                contexts.remove(player.getUniqueId());
                parcelInventories.remove(player.getUniqueId());
            } else {
                // Clic sur le récapitulatif ou un slot vide, ignorer
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Si c'est l'inventaire du colis, passer à la confirmation
        if (title.contains("Votre Colis")) {
            PostalContext context = contexts.get(player.getUniqueId());
            if (context != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openConfirmationMenu(player);
                }, 5L);
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PostalContext context = awaitingMessage.remove(player.getUniqueId());

        if (context == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("annuler")) {
            player.sendMessage(ChatColor.YELLOW + "Envoi annulé.");
            contexts.remove(player.getUniqueId());
            return;
        }

        context.letterMessage = message;

        Bukkit.getScheduler().runTask(plugin, () -> {
            openConfirmationMenu(player);
        });
    }

    /**
     * Traite l'envoi du courrier
     */
    private void processSendMail(Player player) {
        PostalContext context = contexts.get(player.getUniqueId());
        if (context == null) return;

        double price = context.isLetter ? 5.0 : 20.0;

        // Vérifier l'argent
        if (!economy.has(player, price)) {
            player.sendMessage(ChatColor.RED + "✗ Vous n'avez pas assez d'argent !");
            player.sendMessage(ChatColor.GRAY + "Prix: " + price + "€");
            player.closeInventory();
            contexts.remove(player.getUniqueId());
            return;
        }

        // Retirer l'argent et l'ajouter à la ville
        economy.withdrawPlayer(player, price);
        Town senderTown = townManager.getTown(context.senderTownName);
        if (senderTown != null) {
            senderTown.deposit(price);
            townManager.saveTownsNow();
        }

        // Vérifier si la boîte aux lettres existe
        if (!context.targetPlot.hasMailbox()) {
            player.sendMessage(ChatColor.RED + "✗ Erreur: Boîte aux lettres introuvable.");
            economy.depositPlayer(player, price); // Rembourser
            player.closeInventory();
            contexts.remove(player.getUniqueId());
            return;
        }

        // Récupérer la mailbox pour ajouter les items directement
        com.gravityyfh.roleplaycity.postal.data.Mailbox mailbox = context.targetPlot.getMailbox();
        if (mailbox == null) {
            player.sendMessage(ChatColor.RED + "✗ Erreur: Impossible d'accéder à la boîte aux lettres.");
            economy.depositPlayer(player, price); // Rembourser
            player.closeInventory();
            contexts.remove(player.getUniqueId());
            return;
        }

        // Envoyer le courrier
        if (context.isLetter) {
            // Créer un livre signé
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
            bookMeta.setTitle("Lettre de " + player.getName());
            bookMeta.setAuthor(player.getName());
            bookMeta.setPages(context.letterMessage);
            book.setItemMeta(bookMeta);

            // Ajouter à la boîte aux lettres (trouver le premier slot vide)
            for (int slot = 0; slot < 27; slot++) {
                if (mailbox.getItem(slot) == null) {
                    mailbox.setItem(slot, book);
                    break;
                }
            }

            player.sendMessage(ChatColor.GREEN + "✓ Lettre envoyée avec succès !");
        } else {
            // Transférer les objets du colis
            Inventory parcelInv = parcelInventories.remove(player.getUniqueId());
            if (parcelInv != null) {
                int slot = 0;
                for (ItemStack item : parcelInv.getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        // Trouver le prochain slot vide
                        while (slot < 27 && mailbox.getItem(slot) != null) {
                            slot++;
                        }
                        if (slot < 27) {
                            mailbox.setItem(slot, item);
                            slot++;
                        }
                    }
                }
            }

            player.sendMessage(ChatColor.GREEN + "✓ Colis envoyé avec succès !");
        }

        // Sauvegarder les données du plot
        townManager.saveTownsNow();

        player.sendMessage(ChatColor.GRAY + "Le destinataire sera notifié à sa prochaine visite.");
        player.closeInventory();
        contexts.remove(player.getUniqueId());
    }

    /**
     * Utilitaire pour créer des ItemMeta
     */
    private ItemMeta createMeta(ItemStack item, String displayName, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        return meta;
    }

    /**
     * Classe interne pour stocker le contexte d'envoi postal
     */
    private static class PostalContext {
        String senderTownName;
        boolean isLetter;
        boolean isMunicipalDelivery; // true si livraison vers terrain municipal
        String targetTownName;
        String targetResidentName;
        UUID targetResidentUuid;
        Plot targetPlot;
        String letterMessage;
    }
}
