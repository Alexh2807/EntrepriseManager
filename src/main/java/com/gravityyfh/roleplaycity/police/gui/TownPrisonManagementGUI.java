package com.gravityyfh.roleplaycity.police.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.ImprisonedPlayerData;
import com.gravityyfh.roleplaycity.police.data.PrisonData;
import com.gravityyfh.roleplaycity.police.manager.PrisonManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;
import com.gravityyfh.roleplaycity.town.data.Town;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI de gestion de prison pour les policiers/maire/adjoints
 */
public class TownPrisonManagementGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final PrisonManager prisonManager;

    private static final String PRISON_MANAGEMENT_TITLE = ChatColor.DARK_RED + "⛓️ Gestion Prison";
    private static final String PRISONER_LIST_TITLE = ChatColor.DARK_RED + "👤 Liste Prisonniers";
    private static final String PRISONER_ACTIONS_TITLE = ChatColor.DARK_RED + "⚙️ Actions Prisonnier";
    private static final String EXTEND_DURATION_TITLE = ChatColor.DARK_RED + "⏱ Prolonger Peine";

    private final Map<UUID, UUID> selectedPrisoner; // Policier -> Prisonnier sélectionné
    private final Map<UUID, String> awaitingExtendInput; // Policiers en attente d'input prolongation

    public TownPrisonManagementGUI(RoleplayCity plugin, TownManager townManager, PrisonManager prisonManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.prisonManager = prisonManager;
        this.selectedPrisoner = new HashMap<>();
        this.awaitingExtendInput = new HashMap<>();
    }

    /**
     * Ouvre le menu principal de gestion prison
     */
    public void openPrisonManagementMenu(Player player) {
        // Vérifier que le joueur est en service POLICE
        ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
        if (serviceManager != null && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.POLICE)) {
            serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.POLICE);
            return;
        }

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
        if (role != TownRole.POLICIER) {
            player.sendMessage(ChatColor.RED + "Vous devez être policier pour accéder à ce menu.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, PRISON_MANAGEMENT_TITLE);

        ImprisonedPlayerData imprisonedData = prisonManager.getImprisonedPlayerData();
        List<PrisonData> townPrisoners = imprisonedData.getPrisonersByTown(townName);

        // Statistiques
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.GOLD + "Statistiques Prison");
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "Prisonniers actuels: " + ChatColor.WHITE + townPrisoners.size());
        statsLore.add("");

        if (!townPrisoners.isEmpty()) {
            statsLore.add(ChatColor.YELLOW + "Détails:");
            for (PrisonData data : townPrisoners) {
                Player prisoner = Bukkit.getPlayer(data.getPlayerUuid());
                String prisonerName = prisoner != null ? prisoner.getName() : data.getPlayerUuid().toString();
                statsLore.add(ChatColor.GRAY + "  • " + prisonerName + ": " +
                    ChatColor.RED + data.getFormattedRemainingTime());
            }
        }

        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(4, statsItem);

        // Voir liste prisonniers
        ItemStack listItem = new ItemStack(Material.IRON_BARS);
        ItemMeta listMeta = listItem.getItemMeta();
        listMeta.setDisplayName(ChatColor.YELLOW + "Liste des Prisonniers");
        List<String> listLore = new ArrayList<>();
        listLore.add(ChatColor.GRAY + "Gérer les prisonniers");
        listLore.add(ChatColor.GRAY + "actuellement emprisonnés");
        listLore.add("");
        listLore.add(ChatColor.WHITE + "Total: " + townPrisoners.size());
        listLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        listMeta.setLore(listLore);
        listItem.setItemMeta(listMeta);
        inv.setItem(13, listItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre la liste des prisonniers
     */
    private void openPrisonerList(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, PRISONER_LIST_TITLE);

        ImprisonedPlayerData imprisonedData = prisonManager.getImprisonedPlayerData();
        List<PrisonData> townPrisoners = imprisonedData.getPrisonersByTown(townName);

        int slot = 0;
        for (PrisonData prisonData : townPrisoners) {
            if (slot >= 45) break;

            Player prisoner = Bukkit.getPlayer(prisonData.getPlayerUuid());
            String prisonerName = prisoner != null ? prisoner.getName() : prisonData.getPlayerUuid().toString();

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            if (prisoner != null) {
                skullMeta.setOwningPlayer(prisoner);
            }
            skullMeta.setDisplayName(ChatColor.YELLOW + prisonerName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Temps restant: " + ChatColor.RED + prisonData.getFormattedRemainingTime());
            lore.add(ChatColor.GRAY + "Durée totale: " + ChatColor.WHITE + prisonData.getCurrentDurationMinutes() + " min");
            lore.add(ChatColor.GRAY + "Raison: " + ChatColor.WHITE + prisonData.getReason());
            lore.add(ChatColor.GRAY + "Plot: " + ChatColor.WHITE + prisonData.getPlotIdentifier());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Cliquez pour gérer");

            skullMeta.setLore(lore);
            playerHead.setItemMeta(skullMeta);
            inv.setItem(slot++, playerHead);
        }

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(53, backItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu d'actions pour un prisonnier
     */
    private void openPrisonerActions(Player player, UUID prisonerUuid) {
        ImprisonedPlayerData imprisonedData = prisonManager.getImprisonedPlayerData();
        PrisonData prisonData = imprisonedData.getPrisonData(prisonerUuid);

        if (prisonData == null) {
            player.sendMessage(ChatColor.RED + "Ce joueur n'est plus emprisonné.");
            openPrisonerList(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, PRISONER_ACTIONS_TITLE);

        Player prisoner = Bukkit.getPlayer(prisonerUuid);
        String prisonerName = prisoner != null ? prisoner.getName() : prisonerUuid.toString();

        // Info prisonnier
        ItemStack infoItem = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta infoMeta = (SkullMeta) infoItem.getItemMeta();
        if (prisoner != null) {
            infoMeta.setOwningPlayer(prisoner);
        }
        infoMeta.setDisplayName(ChatColor.GOLD + prisonerName);
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Temps restant: " + ChatColor.RED + prisonData.getFormattedRemainingTime());
        infoLore.add(ChatColor.GRAY + "Durée: " + ChatColor.WHITE + prisonData.getCurrentDurationMinutes() + " min");
        infoLore.add(ChatColor.GRAY + "Raison: " + ChatColor.WHITE + prisonData.getReason());
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Libérer
        ItemStack releaseItem = new ItemStack(Material.LIME_DYE);
        ItemMeta releaseMeta = releaseItem.getItemMeta();
        releaseMeta.setDisplayName(ChatColor.GREEN + "Libérer");
        List<String> releaseLore = new ArrayList<>();
        releaseLore.add(ChatColor.GRAY + "Libérer ce prisonnier");
        releaseLore.add(ChatColor.GRAY + "avant la fin de sa peine");
        releaseLore.add("");
        releaseLore.add(ChatColor.YELLOW + "Cliquez pour libérer");
        releaseMeta.setLore(releaseLore);
        releaseItem.setItemMeta(releaseMeta);
        inv.setItem(11, releaseItem);

        // Prolonger
        ItemStack extendItem = new ItemStack(Material.CLOCK);
        ItemMeta extendMeta = extendItem.getItemMeta();
        extendMeta.setDisplayName(ChatColor.GOLD + "Prolonger Peine");
        List<String> extendLore = new ArrayList<>();
        extendLore.add(ChatColor.GRAY + "Ajouter du temps à la peine");
        extendLore.add("");
        extendLore.add(ChatColor.YELLOW + "Cliquez pour prolonger");
        extendMeta.setLore(extendLore);
        extendItem.setItemMeta(extendMeta);
        inv.setItem(13, extendItem);

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(26, backItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection durée prolongation
     */
    private void openExtendDurationMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, EXTEND_DURATION_TITLE);

        int[] durations = {5, 10, 15, 20, 30};
        int[] slots = {10, 11, 12, 13, 14};

        for (int i = 0; i < durations.length; i++) {
            int duration = durations[i];
            ItemStack durationItem = new ItemStack(Material.CLOCK);
            ItemMeta meta = durationItem.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "+" + duration + " minutes");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Ajouter " + duration + " minutes");
            lore.add(ChatColor.GRAY + "à la peine actuelle");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Cliquez pour prolonger");
            meta.setLore(lore);
            durationItem.setItemMeta(meta);
            inv.setItem(slots[i], durationItem);
        }

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(26, backItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Menu principal
        if (title.equals(PRISON_MANAGEMENT_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.IRON_BARS) {
                openPrisonerList(player);
            } else if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        }

        // Liste prisonniers
        else if (title.equals(PRISONER_LIST_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.PLAYER_HEAD) {
                String prisonerName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                Player prisoner = Bukkit.getPlayer(prisonerName);

                if (prisoner != null) {
                    selectedPrisoner.put(player.getUniqueId(), prisoner.getUniqueId());
                    openPrisonerActions(player, prisoner.getUniqueId());
                }
            } else if (clicked.getType() == Material.ARROW) {
                openPrisonManagementMenu(player);
            }
        }

        // Actions prisonnier
        else if (title.equals(PRISONER_ACTIONS_TITLE)) {
            event.setCancelled(true);

            UUID prisonerUuid = selectedPrisoner.get(player.getUniqueId());
            if (prisonerUuid == null) {
                player.closeInventory();
                return;
            }

            if (clicked.getType() == Material.LIME_DYE) {
                // Vérifier que le joueur est en service POLICE
                ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
                if (serviceManager != null && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.POLICE)) {
                    player.closeInventory();
                    serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.POLICE);
                    return;
                }

                // Libérer
                prisonManager.releasePrisoner(prisonerUuid, false);
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§a✔ §lPRISONNIER LIBÉRÉ");
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                selectedPrisoner.remove(player.getUniqueId());
                openPrisonerList(player);
            } else if (clicked.getType() == Material.CLOCK) {
                // Prolonger
                openExtendDurationMenu(player);
            } else if (clicked.getType() == Material.ARROW) {
                selectedPrisoner.remove(player.getUniqueId());
                openPrisonerList(player);
            }
        }

        // Prolongation durée
        else if (title.equals(EXTEND_DURATION_TITLE)) {
            event.setCancelled(true);

            UUID prisonerUuid = selectedPrisoner.get(player.getUniqueId());
            if (prisonerUuid == null) {
                player.closeInventory();
                return;
            }

            if (clicked.getType() == Material.CLOCK) {
                // Vérifier que le joueur est en service POLICE
                ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
                if (serviceManager != null && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.POLICE)) {
                    player.closeInventory();
                    serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.POLICE);
                    return;
                }

                String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                String[] parts = displayName.replace("+", "").split(" ");

                if (parts.length > 0) {
                    try {
                        int minutes = Integer.parseInt(parts[0]);
                        prisonManager.extendPrison(prisonerUuid, minutes, player);
                        openPrisonerActions(player, prisonerUuid);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Erreur lors de la prolongation.");
                    }
                }
            } else if (clicked.getType() == Material.ARROW) {
                openPrisonerActions(player, prisonerUuid);
            }
        }
    }
}
