package com.gravityyfh.roleplaycity.police.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData;
import com.gravityyfh.roleplaycity.police.manager.PrisonManager;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
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

/**
 * GUI workflow pour emprisonner un joueur (3 phases)
 * Phase 1: Sélection joueur menotté
 * Phase 2: Sélection durée
 * Phase 3: Raison (chat input)
 */
public class ImprisonmentWorkflowGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final PrisonManager prisonManager;
    private final HandcuffedPlayerData handcuffedData;

    private static final String SELECT_PRISONER_TITLE = ChatColor.DARK_RED + "👤 Sélectionner Menotté";
    private static final String SELECT_DURATION_TITLE = ChatColor.DARK_RED + "⏱ Durée Prison";

    // Contexte d'emprisonnement
    private final Map<UUID, ImprisonmentContext> pendingImprisonments;

    public ImprisonmentWorkflowGUI(RoleplayCity plugin, TownManager townManager,
                                  PrisonManager prisonManager, HandcuffedPlayerData handcuffedData) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.prisonManager = prisonManager;
        this.handcuffedData = handcuffedData;
        this.pendingImprisonments = new HashMap<>();
    }

    /**
     * Phase 1: Sélectionner un joueur menotté sur le COMMISSARIAT
     */
    public void openPrisonerSelectionMenu(Player policier) {
        String townName = townManager.getPlayerTown(policier.getUniqueId());
        if (townName == null) {
            policier.sendMessage(ChatColor.RED + "Vous devez être dans une ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            policier.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        // Trouver les joueurs menottés sur un COMMISSARIAT de la ville
        List<Player> handcuffedOnCommissariat = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!handcuffedData.isPlayerHandcuffed(target)) continue;

            // Vérifier si le joueur est sur un COMMISSARIAT de cette ville
            Chunk chunk = target.getLocation().getChunk();
            Plot plot = town.getPlot(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

            if (plot != null && plot.getMunicipalSubType() == MunicipalSubType.COMMISSARIAT) {
                handcuffedOnCommissariat.add(target);
            }
        }

        if (handcuffedOnCommissariat.isEmpty()) {
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§c✖ Aucun joueur menotté");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§7Les joueurs doivent être:");
            policier.sendMessage("§7  • Menottés");
            policier.sendMessage("§7  • Sur un COMMISSARIAT de votre ville");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, SELECT_PRISONER_TITLE);

        int slot = 0;
        for (Player target : handcuffedOnCommissariat) {
            if (slot >= 45) break;

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(target);
            skullMeta.setDisplayName(ChatColor.YELLOW + target.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "État: " + ChatColor.RED + "Menotté");

            // Trouver le plot COMMISSARIAT
            Chunk chunk = target.getLocation().getChunk();
            Plot plot = town.getPlot(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
            if (plot != null) {
                lore.add(ChatColor.GRAY + "Lieu: " + ChatColor.WHITE + plot.getPlotNumber());
                lore.add(ChatColor.GRAY + "Spawn défini: " +
                    (plot.hasPrisonSpawn() ? ChatColor.GREEN + "Oui" : ChatColor.RED + "Non"));
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "Cliquez pour emprisonner");

            skullMeta.setLore(lore);
            playerHead.setItemMeta(skullMeta);
            inv.setItem(slot++, playerHead);
        }

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);

        policier.openInventory(inv);
    }

    /**
     * Phase 2: Sélectionner la durée
     */
    private void openDurationSelectionMenu(Player policier) {
        Inventory inv = Bukkit.createInventory(null, 27, SELECT_DURATION_TITLE);

        // Durées prédéfinies: 5, 10, 15, 20, 30, 45, 60 minutes
        int[] durations = {5, 10, 15, 20, 30, 45, 60};
        int[] slots = {10, 11, 12, 13, 14, 15, 16};

        for (int i = 0; i < durations.length; i++) {
            int duration = durations[i];
            ItemStack durationItem = new ItemStack(Material.CLOCK);
            ItemMeta meta = durationItem.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + String.valueOf(duration) + " minutes");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Emprisonner pour");
            lore.add(ChatColor.GRAY + String.valueOf(duration) + " minutes");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Cliquez pour sélectionner");

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

        policier.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Phase 1: Sélection prisonnier
        if (title.equals(SELECT_PRISONER_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.PLAYER_HEAD) {
                String prisonerName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                Player prisoner = Bukkit.getPlayer(prisonerName);

                if (prisoner != null && handcuffedData.isPlayerHandcuffed(prisoner)) {
                    // Créer le contexte
                    ImprisonmentContext context = new ImprisonmentContext();
                    context.policier = player;
                    context.prisoner = prisoner;
                    pendingImprisonments.put(player.getUniqueId(), context);

                    // Passer à la phase 2
                    openDurationSelectionMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Ce joueur n'est plus menotté.");
                    player.closeInventory();
                }
            } else if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        }

        // Phase 2: Sélection durée
        else if (title.equals(SELECT_DURATION_TITLE)) {
            event.setCancelled(true);

            ImprisonmentContext context = pendingImprisonments.get(player.getUniqueId());
            if (context == null) {
                player.closeInventory();
                return;
            }

            if (clicked.getType() == Material.CLOCK) {
                String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                String[] parts = displayName.split(" ");

                if (parts.length > 0) {
                    try {
                        int minutes = Integer.parseInt(parts[0]);
                        context.durationMinutes = minutes;

                        // Passer à la phase 3 (raison dans le chat)
                        player.closeInventory();
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                        player.sendMessage("§e§l⛓️ EMPRISONNEMENT");
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                        player.sendMessage("§7Prisonnier: §e" + context.prisoner.getName());
                        player.sendMessage("§7Durée: §c" + minutes + " minutes");
                        player.sendMessage("");
                        player.sendMessage("§7Tapez la §craison §7de l'emprisonnement");
                        player.sendMessage("§7dans le chat §8(obligatoire)");
                        player.sendMessage("");
                        player.sendMessage("§7Tapez §cannuler §7pour annuler");
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Durée invalide.");
                    }
                }
            } else if (clicked.getType() == Material.ARROW) {
                pendingImprisonments.remove(player.getUniqueId());
                openPrisonerSelectionMenu(player);
            }
        }
    }

    /**
     * Phase 3: Raison dans le chat
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ImprisonmentContext context = pendingImprisonments.get(player.getUniqueId());

        if (context == null) return;

        event.setCancelled(true);

        String reason = event.getMessage().trim();

        if (reason.equalsIgnoreCase("annuler")) {
            pendingImprisonments.remove(player.getUniqueId());
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✖ Emprisonnement annulé");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return;
        }

        if (reason.isEmpty() || reason.length() < 3) {
            player.sendMessage("§c✖ La raison doit faire au moins 3 caractères.");
            return;
        }

        // Trouver le plot COMMISSARIAT
        String townName = townManager.getPlayerTown(player.getUniqueId());
        Town town = townManager.getTown(townName);

        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            pendingImprisonments.remove(player.getUniqueId());
            return;
        }

        Chunk chunk = context.prisoner.getLocation().getChunk();
        Plot plot = town.getPlot(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        if (plot == null || plot.getMunicipalSubType() != MunicipalSubType.COMMISSARIAT) {
            player.sendMessage(ChatColor.RED + "Le prisonnier n'est plus sur un COMMISSARIAT.");
            pendingImprisonments.remove(player.getUniqueId());
            return;
        }

        // Emprisonner (sync avec le main thread)
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = prisonManager.imprisonPlayer(
                context.prisoner,
                town,
                plot,
                context.durationMinutes,
                reason,
                player
            );

            if (success) {
                pendingImprisonments.remove(player.getUniqueId());
            }
        });
    }

    /**
     * Contexte d'emprisonnement en cours
     */
    private static class ImprisonmentContext {
        Player policier;
        Player prisoner;
        int durationMinutes;
    }
}
