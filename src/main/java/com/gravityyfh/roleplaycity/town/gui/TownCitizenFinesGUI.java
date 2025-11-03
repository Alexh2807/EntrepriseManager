package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
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

import java.util.*;

public class TownCitizenFinesGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownPoliceManager policeManager;

    private static final String FINES_TITLE = ChatColor.RED + "📋 Mes Amendes";

    private final Map<UUID, ContestContext> pendingContests;

    public TownCitizenFinesGUI(RoleplayCity plugin, TownPoliceManager policeManager) {
        this.plugin = plugin;
        this.policeManager = policeManager;
        this.pendingContests = new HashMap<>();
    }

    public void openFinesMenu(Player player) {
        List<Fine> fines = policeManager.getPlayerFines(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, FINES_TITLE);

        // Informations globales
        double totalUnpaid = policeManager.getTotalUnpaidFines(player.getUniqueId());

        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Résumé");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Total amendes: " + ChatColor.WHITE + fines.size());
        infoLore.add(ChatColor.GRAY + "À payer: " + ChatColor.RED +
            String.format("%.2f€", totalUnpaid));
        infoLore.add("");
        infoLore.add(ChatColor.YELLOW + "Cliquez sur une amende");
        infoLore.add(ChatColor.YELLOW + "pour la payer ou contester");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Liste des amendes
        int slot = 9;
        for (Fine fine : fines) {
            if (slot >= 45) break;

            Material mat = switch (fine.getStatus()) {
                case PENDING, JUDGED_VALID -> Material.RED_STAINED_GLASS_PANE;
                case PAID -> Material.GREEN_STAINED_GLASS_PANE;
                case CONTESTED -> Material.YELLOW_STAINED_GLASS_PANE;
                case CANCELLED, JUDGED_INVALID -> Material.GRAY_STAINED_GLASS_PANE;
            };

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + fine.getReason());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€");
            lore.add(ChatColor.GRAY + "Ville: " + ChatColor.WHITE + fine.getTownName());
            lore.add(ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName());
            lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + fine.getIssueDate().toLocalDate());
            lore.add(ChatColor.GRAY + "Statut: " + ChatColor.AQUA + fine.getStatus().getDisplayName());
            lore.add("");

            if (fine.isPending()) {
                lore.add(ChatColor.GREEN + "Clic gauche: Payer");
                if (fine.canBeContested()) {
                    lore.add(ChatColor.YELLOW + "Clic droit: Contester");
                }
            } else if (fine.getStatus() == Fine.FineStatus.CONTESTED) {
                lore.add(ChatColor.YELLOW + "En attente de jugement");
            } else if (fine.getStatus() == Fine.FineStatus.PAID) {
                lore.add(ChatColor.GREEN + "✔ Payée");
            } else if (fine.getStatus() == Fine.FineStatus.JUDGED_VALID) {
                lore.add(ChatColor.RED + "Confirmée par un juge");
                lore.add(ChatColor.GREEN + "Clic gauche: Payer");
            } else if (fine.getStatus() == Fine.FineStatus.JUDGED_INVALID ||
                      fine.getStatus() == Fine.FineStatus.CANCELLED) {
                lore.add(ChatColor.GREEN + "✔ Annulée");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(49, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(FINES_TITLE)) {
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

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Fermer")) {
            player.closeInventory();
            return;
        }

        // Récupérer l'amende correspondante
        List<Fine> fines = policeManager.getPlayerFines(player.getUniqueId());
        Fine selectedFine = null;

        for (Fine fine : fines) {
            if (fine.getReason().equals(displayName)) {
                selectedFine = fine;
                break;
            }
        }

        if (selectedFine == null) {
            return;
        }

        // Clic gauche = Payer, Clic droit = Contester
        if (event.isLeftClick()) {
            handlePayFine(player, selectedFine);
        } else if (event.isRightClick()) {
            handleContestFine(player, selectedFine);
        }
    }

    private void handlePayFine(Player player, Fine fine) {
        player.closeInventory();

        if (policeManager.payFine(fine, player)) {
            player.sendMessage(ChatColor.GREEN + "✔ Amende payée avec succès !");
        }
    }

    private void handleContestFine(Player player, Fine fine) {
        if (!fine.canBeContested()) {
            player.sendMessage(ChatColor.RED + "Cette amende ne peut plus être contestée.");
            return;
        }

        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "=== CONTESTER UNE AMENDE ===");
        player.sendMessage(ChatColor.GRAY + "Amende: " + fine.getReason());
        player.sendMessage(ChatColor.GRAY + "Montant: " + fine.getAmount() + "€");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Entrez la raison de votre contestation:");
        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");

        pendingContests.put(player.getUniqueId(), new ContestContext(fine));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ContestContext context = pendingContests.get(player.getUniqueId());

        if (context == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingContests.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Contestation annulée.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.length() < 10) {
                player.sendMessage(ChatColor.RED + "La raison doit contenir au moins 10 caractères.");
                return;
            }

            if (policeManager.contestFine(context.fine, player, input)) {
                player.sendMessage(ChatColor.GREEN + "✔ Contestation enregistrée !");
                player.sendMessage(ChatColor.YELLOW + "Un juge examinera votre dossier.");
            }

            pendingContests.remove(player.getUniqueId());
        });
    }

    private static class ContestContext {
        final Fine fine;

        ContestContext(Fine fine) {
            this.fine = fine;
        }
    }
}
