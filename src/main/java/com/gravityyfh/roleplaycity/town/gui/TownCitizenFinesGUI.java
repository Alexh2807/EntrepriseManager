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
    private TownMainGUI mainGUI;

    private static final String FINES_TITLE = ChatColor.RED + "📋 Mes Amendes";

    private final Map<UUID, ContestContext> pendingContests;

    public TownCitizenFinesGUI(RoleplayCity plugin, TownPoliceManager policeManager) {
        this.plugin = plugin;
        this.policeManager = policeManager;
        this.pendingContests = new HashMap<>();
    }

    public void setMainGUI(TownMainGUI mainGUI) {
        this.mainGUI = mainGUI;
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
            lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + fine.getFineId().toString().substring(0, 8));
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

        // Retour au menu principal (haut gauche)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + "Retour au menu ville");
        backMeta.setLore(backLore);
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

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
            player.closeInventory();
            // Retour au menu principal de la ville
            if (mainGUI != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    mainGUI.openMainMenu(player);
                }, 1L);
            }
            return;
        }

        if (displayName.contains("Fermer")) {
            player.closeInventory();
            return;
        }

        // Récupérer l'amende correspondante par son ID dans le lore
        List<Fine> fines = policeManager.getPlayerFines(player.getUniqueId());
        Fine selectedFine = null;

        if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
            List<String> lore = clicked.getItemMeta().getLore();
            if (!lore.isEmpty()) {
                String firstLine = ChatColor.stripColor(lore.get(0));
                if (firstLine.startsWith("ID: ")) {
                    String fineIdPrefix = firstLine.substring(4); // Récupérer les 8 premiers caractères de l'ID

                    for (Fine fine : fines) {
                        if (fine.getFineId().toString().startsWith(fineIdPrefix)) {
                            selectedFine = fine;
                            break;
                        }
                    }
                }
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
        policeManager.payFine(fine, player);
    }

    private void handleContestFine(Player player, Fine fine) {
        if (!fine.canBeContested()) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✖ Contestation impossible");
            player.sendMessage("§7Cette amende ne peut plus être contestée");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return;
        }

        player.closeInventory();
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§e⚖ §lCONTESTER UNE AMENDE");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7Motif: §f" + fine.getReason());
        player.sendMessage("§7Montant: §6" + fine.getAmount() + "€");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§eEntrez la raison de votre contestation:");
        player.sendMessage("§7(Minimum 10 caractères)");
        player.sendMessage("§7(Tapez 'annuler' pour abandonner)");

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
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§e✖ Contestation annulée");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.length() < 10) {
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§c✖ Raison trop courte");
                player.sendMessage("§7Minimum: §f10 caractères");
                player.sendMessage("§7Actuel: §f" + input.length() + " caractères");
                player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                return;
            }

            if (policeManager.contestFine(context.fine, player, input)) {
                // Le message est déjà affiché dans contestFine()
            }

            pendingContests.remove(player.getUniqueId());
        });
    }

    private record ContestContext(Fine fine) {
    }
}
