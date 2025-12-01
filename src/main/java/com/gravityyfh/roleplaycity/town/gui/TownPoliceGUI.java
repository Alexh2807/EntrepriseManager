package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;
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
    private TownMainGUI mainGUI;

    private static final String POLICE_TITLE = ChatColor.DARK_BLUE + "🚔 Police Municipale";
    private static final String SELECT_PLAYER_TITLE = ChatColor.DARK_BLUE + "👤 Sélectionner un joueur";
    private static final String SELECT_FINE_TITLE = ChatColor.DARK_BLUE + "📋 Type d'amende";
    private static final String SELECT_ID_TITLE = ChatColor.DARK_BLUE + "📋 Demander l'Identité";
    private static final String FINES_DOSSIERS_TITLE = ChatColor.DARK_BLUE + "📂 Dossiers Amendes";

    private final Map<UUID, FineContext> pendingFines;

    public TownPoliceGUI(RoleplayCity plugin, TownManager townManager, TownPoliceManager policeManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.policeManager = policeManager;
        this.pendingFines = new HashMap<>();
    }

    public void setMainGUI(TownMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    private boolean reVerifyPoliceAccess(Player player) {
        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Vous n'êtes plus dans une ville.");
            return false;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return false;
        }

        // Admin override = bypass toutes les vérifications
        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        if (isAdminOverride) {
            return true;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role != TownRole.POLICIER) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Vous n'avez plus accès au menu Police.");
            return false;
        }

        ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
        if (serviceManager != null
                && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.POLICE)) {
            player.closeInventory();
            serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.POLICE);
            return false;
        }

        return true;
    }

    private boolean reVerifyPoliceAccessNoClose(Player player) {
        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes plus dans une ville.");
            return false;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return false;
        }

        // Admin override = bypass toutes les vérifications
        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        if (isAdminOverride) {
            return true;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role != TownRole.POLICIER) {
            player.sendMessage(ChatColor.RED + "Vous n'avez plus accès aux fonctions de police.");
            return false;
        }

        ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
        if (serviceManager != null
                && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.POLICE)) {
            serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.POLICE);
            return false;
        }

        return true;
    }

    public void openPoliceMenu(Player player) {
        if (!reVerifyPoliceAccess(player))
            return;

        String townName = townManager.getEffectiveTown(player);
        Inventory inv = Bukkit.createInventory(null, 45, POLICE_TITLE);

        // --- SECTION INTERVENTIONS (Ligne 2) ---
        // Séparateurs Rouges
        ItemStack redPane = createDecorativePane(Material.RED_STAINED_GLASS_PANE, " ");
        inv.setItem(9, redPane);
        inv.setItem(17, redPane);

        // 1. Émettre Amende (Slot 11)
        ItemStack fineItem = new ItemStack(Material.PAPER);
        ItemMeta fineMeta = fineItem.getItemMeta();
        fineMeta.setDisplayName(ChatColor.RED + "📝 Émettre Amende");
        fineMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Verbaliser un citoyen",
                "",
                ChatColor.YELLOW + "▶ Cliquez pour sélectionner"));
        fineItem.setItemMeta(fineMeta);
        inv.setItem(11, fineItem);

        // 2. Demander Identité (Slot 12)
        ItemStack idItem = new ItemStack(Material.NAME_TAG);
        ItemMeta idMeta = idItem.getItemMeta();
        idMeta.setDisplayName(ChatColor.AQUA + "🆔 Contrôle d'Identité");
        idMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Demander les papiers",
                ChatColor.GRAY + "d'un citoyen proche",
                "",
                ChatColor.YELLOW + "▶ Cliquez pour demander"));
        idItem.setItemMeta(idMeta);
        inv.setItem(12, idItem);

        // 3. Fouiller (Slot 14)
        if (plugin.getFriskGUI() != null) {
            ItemStack friskItem = new ItemStack(Material.ENDER_EYE);
            ItemMeta friskMeta = friskItem.getItemMeta();
            friskMeta.setDisplayName(ChatColor.DARK_PURPLE + "🔍 Fouiller Suspect");
            friskMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Inspecter l'inventaire",
                    ChatColor.GRAY + "(Menotté ou Consentement)",
                    "",
                    ChatColor.YELLOW + "▶ Cliquez pour fouiller"));
            friskItem.setItemMeta(friskMeta);
            inv.setItem(14, friskItem);
        }

        // 4. Emprisonner (Slot 15)
        if (plugin.getPrisonManager() != null) {
            ItemStack prisonItem = new ItemStack(Material.IRON_BARS);
            ItemMeta prisonMeta = prisonItem.getItemMeta();
            prisonMeta.setDisplayName(ChatColor.DARK_RED + "⛓️ Incarcérer");
            prisonMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Mettre en cellule un",
                    ChatColor.GRAY + "suspect menotté",
                    "",
                    ChatColor.YELLOW + "▶ Cliquez pour emprisonner"));
            prisonItem.setItemMeta(prisonMeta);
            inv.setItem(15, prisonItem);
        }

        // --- SECTION ADMINISTRATION (Ligne 4) ---
        // Séparateurs Bleus
        ItemStack bluePane = createDecorativePane(Material.BLUE_STAINED_GLASS_PANE, " ");
        inv.setItem(27, bluePane);
        inv.setItem(35, bluePane);

        var stats = policeManager.getTownStatistics(townName);

        // 1. Amendes en cours (Slot 29)
        ItemStack activeFinesItem = new ItemStack(Material.FILLED_MAP);
        ItemMeta activeFinesMeta = activeFinesItem.getItemMeta();
        activeFinesMeta.setDisplayName(ChatColor.GOLD + "📂 Dossiers Amendes");
        activeFinesMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "En attente: " + ChatColor.RED + stats.pendingFines(),
                ChatColor.GRAY + "Contestées: " + ChatColor.YELLOW + stats.contestedFines(),
                "",
                ChatColor.YELLOW + "▶ Cliquez pour gérer"));
        activeFinesItem.setItemMeta(activeFinesMeta);
        inv.setItem(29, activeFinesItem);

        // 2. Gestion Prisonniers (Slot 31)
        if (plugin.getPrisonManager() != null) {
            int prisonersCount = plugin.getPrisonManager().getImprisonedData().getImprisonedCount();
            ItemStack prisonersItem = new ItemStack(Material.IRON_DOOR);
            ItemMeta prisonersMeta = prisonersItem.getItemMeta();
            prisonersMeta.setDisplayName(ChatColor.DARK_AQUA + "👥 Détenus");
            prisonersMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Actuellement en cellule: " + ChatColor.WHITE + prisonersCount,
                    "",
                    ChatColor.YELLOW + "▶ Cliquez pour gérer"));
            prisonersItem.setItemMeta(prisonersMeta);
            inv.setItem(31, prisonersItem);
        }

        // 3. Statistiques (Slot 33)
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.WHITE + "📊 Registre");
        statsMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Total: " + ChatColor.WHITE + stats.totalFines() + " amendes",
                ChatColor.GRAY + "Collecté: " + ChatColor.GREEN + String.format("%.2f€", stats.collectedAmount()),
                "",
                ChatColor.YELLOW + "▶ Cliquez pour détails"));
        statsItem.setItemMeta(statsMeta);
        inv.setItem(33, statsItem);

        // --- HEADER ---
        // Retour au menu principal (haut gauche)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backMeta.setLore(Arrays.asList(ChatColor.GRAY + "Retour au menu ville"));
        backItem.setItemMeta(backMeta);
        inv.setItem(0, backItem);

        // Fermer (haut droite)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(8, closeItem);

        player.openInventory(inv);
    }

    private ItemStack createDecorativePane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    // ... (Keep existing selection menus and event handlers, but update them to
    // redirect to openPoliceMenu)
    // For brevity, I'm including the essential parts. The selection menus logic
    // remains largely the same
    // but needs to be adapted to the new structure if needed.
    // I will include the full class content to be safe.

    private void openIdRequestSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, SELECT_ID_TITLE);
        int slot = 0;
        boolean found = false;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player))
                continue;
            if (!target.getWorld().equals(player.getWorld()) || target.getLocation().distance(player.getLocation()) > 5)
                continue;
            found = true;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.YELLOW + target.getName());
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Distance: "
                            + String.format("%.1fm", target.getLocation().distance(player.getLocation())),
                    "", ChatColor.GREEN + "► Cliquez pour demander ID"));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
            if (slot >= 18)
                break;
        }
        if (!found) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Aucun joueur à proximité");
            none.setItemMeta(meta);
            inv.setItem(13, none);
        }
        addBackAndCloseButtons(inv);
        player.openInventory(inv);
    }

    private void openPlayerSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECT_PLAYER_TITLE);
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int slot = 0;
        for (Player target : onlinePlayers) {
            if (slot >= 45)
                break;
            if (target.equals(player))
                continue;
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(target);
            skullMeta.setDisplayName(ChatColor.YELLOW + target.getName());
            skullMeta.setLore(Arrays.asList("", ChatColor.GREEN + "Cliquez pour sélectionner"));
            playerHead.setItemMeta(skullMeta);
            inv.setItem(slot++, playerHead);
        }
        addBackAndCloseButtons(inv);
        player.openInventory(inv);
    }

    private void openFineSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECT_FINE_TITLE);
        List<Map<?, ?>> predefinedFines = plugin.getConfig().getMapList("town.predefined-fines");
        int slot = 0;
        for (Map<?, ?> fineData : predefinedFines) {
            if (slot >= 45)
                break;
            String title = (String) fineData.get("title");
            double amount = ((Number) fineData.get("amount")).doubleValue();
            String description = (String) fineData.get("description");
            ItemStack fineItem = new ItemStack(Material.PAPER);
            ItemMeta fineMeta = fineItem.getItemMeta();
            fineMeta.setDisplayName(ChatColor.RED + title);
            fineMeta.setLore(Arrays.asList(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + amount + "€", "",
                    ChatColor.WHITE + description, "", ChatColor.GREEN + "Cliquez pour sélectionner"));
            fineItem.setItemMeta(fineMeta);
            inv.setItem(slot++, fineItem);
        }
        addBackAndCloseButtons(inv);
        player.openInventory(inv);
    }

    private void addBackAndCloseButtons(Inventory inv) {
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(inv.getSize() - 5, backItem); // Center-ish

        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(inv.getSize() - 1, closeItem);
    }

    /**
     * Ouvre le menu des dossiers d'amendes (en attente + contestées)
     */
    private void openFinesDossiersMenu(Player player) {
        String townName = townManager.getEffectiveTown(player);
        if (townName == null) return;

        List<Fine> townFines = policeManager.getTownFines(townName);

        // Filtrer: uniquement PENDING, CONTESTED et JUDGED_VALID (à payer)
        List<Fine> activeFines = townFines.stream()
                .filter(f -> f.getStatus() == Fine.FineStatus.PENDING
                          || f.getStatus() == Fine.FineStatus.CONTESTED
                          || f.getStatus() == Fine.FineStatus.JUDGED_VALID)
                .sorted((a, b) -> b.getIssueDate().compareTo(a.getIssueDate())) // Plus récentes en premier
                .toList();

        Inventory inv = Bukkit.createInventory(null, 54, FINES_DOSSIERS_TITLE);

        // En-tête avec stats
        var stats = policeManager.getTownStatistics(townName);
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "📊 Résumé des Dossiers");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Total actifs: " + ChatColor.WHITE + activeFines.size());
        infoLore.add(ChatColor.RED + "• En attente: " + stats.pendingFines());
        infoLore.add(ChatColor.YELLOW + "• Contestées: " + stats.contestedFines());
        infoLore.add("");
        infoLore.add(ChatColor.GRAY + "Légende des couleurs:");
        infoLore.add(ChatColor.RED + "■ " + ChatColor.GRAY + "En attente de paiement");
        infoLore.add(ChatColor.YELLOW + "■ " + ChatColor.GRAY + "Contestée (attente juge)");
        infoLore.add(ChatColor.GOLD + "■ " + ChatColor.GRAY + "Confirmée par juge");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Liste des amendes
        int slot = 9;
        for (Fine fine : activeFines) {
            if (slot >= 45) break;

            Material mat = switch (fine.getStatus()) {
                case PENDING -> Material.RED_STAINED_GLASS_PANE;
                case CONTESTED -> Material.YELLOW_STAINED_GLASS_PANE;
                case JUDGED_VALID -> Material.ORANGE_STAINED_GLASS_PANE;
                default -> Material.GRAY_STAINED_GLASS_PANE;
            };

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + fine.getOffenderName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + fine.getFineId().toString().substring(0, 8));
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + String.format("%.2f€", fine.getAmount()));
            lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + fine.getIssueDate().toLocalDate());
            lore.add(ChatColor.GRAY + "Statut: " + getStatusColor(fine.getStatus()) + fine.getStatus().getDisplayName());
            lore.add("");
            lore.add(ChatColor.GRAY + "Motif:");
            // Tronquer le motif s'il est trop long
            String reason = fine.getReason();
            if (reason.length() > 40) {
                lore.add(ChatColor.WHITE + reason.substring(0, 40) + "...");
            } else {
                lore.add(ChatColor.WHITE + reason);
            }

            if (fine.getStatus() == Fine.FineStatus.CONTESTED && fine.getContestReason() != null) {
                lore.add("");
                lore.add(ChatColor.YELLOW + "Contestation:");
                String contestReason = fine.getContestReason();
                if (contestReason.length() > 35) {
                    lore.add(ChatColor.WHITE + contestReason.substring(0, 35) + "...");
                } else {
                    lore.add(ChatColor.WHITE + contestReason);
                }
            }

            lore.add("");
            lore.add(ChatColor.RED + "Clic droit: Annuler l'amende");

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        if (activeFines.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            ItemMeta emptyMeta = emptyItem.getItemMeta();
            emptyMeta.setDisplayName(ChatColor.GREEN + "✓ Aucun dossier actif");
            emptyMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Toutes les amendes ont été",
                    ChatColor.GRAY + "payées ou traitées."));
            emptyItem.setItemMeta(emptyMeta);
            inv.setItem(22, emptyItem);
        }

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(45, backItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);

        player.openInventory(inv);
    }

    private ChatColor getStatusColor(Fine.FineStatus status) {
        return switch (status) {
            case PENDING -> ChatColor.RED;
            case CONTESTED -> ChatColor.YELLOW;
            case JUDGED_VALID -> ChatColor.GOLD;
            case PAID -> ChatColor.GREEN;
            default -> ChatColor.GRAY;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;
        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (title.equals(POLICE_TITLE)) {
            event.setCancelled(true);
            if (displayName.contains("Émettre Amende")) {
                if (!reVerifyPoliceAccess(player))
                    return;
                pendingFines.put(player.getUniqueId(), new FineContext());
                openPlayerSelectionMenu(player);
            } else if (displayName.contains("Contrôle d'Identité")) {
                if (!reVerifyPoliceAccess(player))
                    return;
                openIdRequestSelectionMenu(player);
            } else if (displayName.contains("Fouiller Suspect")) {
                if (!reVerifyPoliceAccess(player))
                    return;
                if (plugin.getFriskGUI() != null)
                    plugin.getFriskGUI().openTargetSelection(player);
            } else if (displayName.contains("Incarcérer")) {
                if (!reVerifyPoliceAccess(player))
                    return;
                if (plugin.getImprisonmentWorkflowGUI() != null)
                    plugin.getImprisonmentWorkflowGUI().openPrisonerSelectionMenu(player);
            } else if (displayName.contains("Dossiers Amendes")) {
                if (!reVerifyPoliceAccess(player))
                    return;
                openFinesDossiersMenu(player);
            } else if (displayName.contains("Détenus")) {
                if (!reVerifyPoliceAccess(player))
                    return;
                if (plugin.getTownPrisonManagementGUI() != null)
                    plugin.getTownPrisonManagementGUI().openPrisonManagementMenu(player);
            } else if (displayName.contains("Retour")) {
                player.closeInventory();
                // Retour au menu principal de la ville
                if (mainGUI != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        mainGUI.openMainMenu(player);
                    }, 1L);
                }
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            }
        } else if (title.equals(SELECT_PLAYER_TITLE)) {
            event.setCancelled(true);
            if (clicked.getType() == Material.PLAYER_HEAD) {
                if (!reVerifyPoliceAccess(player)) {
                    pendingFines.remove(player.getUniqueId());
                    return;
                }
                SkullMeta skullMeta = (SkullMeta) clicked.getItemMeta();
                org.bukkit.OfflinePlayer owningPlayer = skullMeta.getOwningPlayer();
                Player target = owningPlayer != null ? Bukkit.getPlayer(owningPlayer.getUniqueId()) : null;
                if (target != null && target.isOnline()) {
                    FineContext context = pendingFines.get(player.getUniqueId());
                    if (context != null) {
                        context.targetUuid = target.getUniqueId();
                        context.targetName = target.getName();
                        context.targetDisplayName = target.getName();
                        context.step = 1;
                        openFineSelectionMenu(player);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Joueur hors ligne.");
                    player.closeInventory();
                    pendingFines.remove(player.getUniqueId());
                }
            } else if (displayName.contains("Retour")) {
                openPoliceMenu(player);
                pendingFines.remove(player.getUniqueId());
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
                pendingFines.remove(player.getUniqueId());
            }
        } else if (title.equals(SELECT_FINE_TITLE)) {
            event.setCancelled(true);
            if (clicked.getType() == Material.PAPER) {
                if (!reVerifyPoliceAccess(player)) {
                    pendingFines.remove(player.getUniqueId());
                    return;
                }
                FineContext context = pendingFines.get(player.getUniqueId());
                if (context != null) {
                    List<String> lore = clicked.getItemMeta().getLore();
                    String amountLine = ChatColor.stripColor(lore.get(0));
                    double amount = Double.parseDouble(amountLine.replaceAll("[^0-9.]", ""));
                    context.fineTitle = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                    context.amount = amount;
                    context.step = 2;
                    player.closeInventory();
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§c🚔 §lÉMETTRE UNE AMENDE");
                    player.sendMessage("§7Contrevenant: §e" + context.targetDisplayName);
                    player.sendMessage("§7Type: §c" + context.fineTitle);
                    player.sendMessage("§7Montant: §6" + amount + "€");
                    player.sendMessage("§eEntrez une description (min 20 chars) ou 'annuler'");
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                }
            } else if (displayName.contains("Retour")) {
                openPlayerSelectionMenu(player);
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
                pendingFines.remove(player.getUniqueId());
            }
        } else if (title.equals(SELECT_ID_TITLE)) {
            event.setCancelled(true);
            if (clicked.getType() == Material.PLAYER_HEAD) {
                if (!reVerifyPoliceAccess(player))
                    return;
                SkullMeta idSkullMeta = (SkullMeta) clicked.getItemMeta();
                org.bukkit.OfflinePlayer idOwningPlayer = idSkullMeta.getOwningPlayer();
                Player target = idOwningPlayer != null ? Bukkit.getPlayer(idOwningPlayer.getUniqueId()) : null;
                if (target != null && target.isOnline()) {
                    player.closeInventory();
                    var requestManager = plugin.getInteractionRequestManager();
                    if (requestManager != null
                            && !requestManager.hasPendingRequest(player.getUniqueId(), target.getUniqueId(),
                                    com.gravityyfh.roleplaycity.service.InteractionRequest.RequestType.REQUEST_ID)) {
                        requestManager.createIdRequest(player, target);
                        player.sendMessage(ChatColor.GREEN + "Demande envoyée à " + target.getName());
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Demande déjà en cours ou système indisponible.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Joueur hors ligne.");
                    player.closeInventory();
                }
            } else if (displayName.contains("Retour")) {
                openPoliceMenu(player);
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            }
        } else if (title.equals(FINES_DOSSIERS_TITLE)) {
            event.setCancelled(true);

            if (displayName.contains("Retour")) {
                openPoliceMenu(player);
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            } else if (clicked.getType().name().contains("STAINED_GLASS_PANE")
                    && !clicked.getType().name().contains("GREEN")) {
                // Clic sur une amende - Récupérer l'ID depuis le lore
                if (!reVerifyPoliceAccess(player))
                    return;

                if (event.isRightClick()) {
                    // Annuler l'amende
                    List<String> lore = clicked.getItemMeta().getLore();
                    if (lore != null && !lore.isEmpty()) {
                        String firstLine = ChatColor.stripColor(lore.get(0));
                        if (firstLine.startsWith("ID: ")) {
                            String fineIdPrefix = firstLine.substring(4);
                            String townName = townManager.getEffectiveTown(player);
                            List<Fine> townFines = policeManager.getTownFines(townName);

                            for (Fine fine : townFines) {
                                if (fine.getFineId().toString().startsWith(fineIdPrefix)) {
                                    fine.cancel();
                                    player.sendMessage(ChatColor.GREEN + "✓ Amende annulée pour " + fine.getOffenderName());
                                    // Rafraîchir le menu
                                    openFinesDossiersMenu(player);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FineContext context = pendingFines.get(player.getUniqueId());
        if (context == null || context.step != 2)
            return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingFines.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Action annulée.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!reVerifyPoliceAccessNoClose(player)) {
                pendingFines.remove(player.getUniqueId());
                return;
            }
            if (input.length() < 20) {
                player.sendMessage(ChatColor.RED + "Description trop courte (min 20).");
                return;
            }
            String townName = townManager.getEffectiveTown(player);
            Fine fine = policeManager.issueFine(townName, context.targetUuid, context.targetName, player,
                    context.fineTitle + " - " + input, context.amount);
            if (fine != null) {
                player.sendMessage(ChatColor.GREEN + "Amende émise avec succès !");
            } else {
                player.sendMessage(ChatColor.RED + "Erreur lors de l'émission.");
            }
            pendingFines.remove(player.getUniqueId());
        });
    }

    private static class FineContext {
        int step = 0;
        UUID targetUuid;
        String targetName;
        String targetDisplayName;
        String fineTitle;
        double amount;
    }
}
