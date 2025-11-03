package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.PlotTransaction;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownEconomyManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
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

public class TownBankGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownEconomyManager economyManager;

    private static final String BANK_TITLE = ChatColor.DARK_GREEN + "🏦 Banque Municipale";
    private static final String TRANSACTIONS_TITLE = ChatColor.DARK_GREEN + "📜 Historique";

    private final Map<UUID, BankActionContext> pendingActions;

    public TownBankGUI(RoleplayCity plugin, TownManager townManager, TownEconomyManager economyManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.economyManager = economyManager;
        this.pendingActions = new HashMap<>();
    }

    public void openBankMenu(Player player) {
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

        Inventory inv = Bukkit.createInventory(null, 27, BANK_TITLE);

        // Solde de la banque
        ItemStack balanceItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta balanceMeta = balanceItem.getItemMeta();
        balanceMeta.setDisplayName(ChatColor.GOLD + "Solde de la Banque");
        List<String> balanceLore = new ArrayList<>();
        balanceLore.add(ChatColor.GRAY + "Solde actuel:");
        balanceLore.add(ChatColor.GOLD + "" + ChatColor.BOLD + String.format("%.2f€", town.getBankBalance()));
        balanceLore.add("");
        balanceLore.add(ChatColor.GRAY + "Cette banque finance:");
        balanceLore.add(ChatColor.GRAY + "• Claims de territoires");
        balanceLore.add(ChatColor.GRAY + "• Services municipaux");
        balanceLore.add(ChatColor.GRAY + "• Développement de la ville");
        balanceMeta.setLore(balanceLore);
        balanceItem.setItemMeta(balanceMeta);
        inv.setItem(4, balanceItem);

        TownRole role = town.getMemberRole(player.getUniqueId());

        // Déposer
        ItemStack depositItem = new ItemStack(Material.EMERALD);
        ItemMeta depositMeta = depositItem.getItemMeta();
        depositMeta.setDisplayName(ChatColor.GREEN + "Déposer de l'Argent");
        List<String> depositLore = new ArrayList<>();
        depositLore.add(ChatColor.GRAY + "Contribuer au développement");
        depositLore.add(ChatColor.GRAY + "de votre ville");
        depositLore.add("");
        depositLore.add(ChatColor.YELLOW + "Cliquez pour déposer");
        depositMeta.setLore(depositLore);
        depositItem.setItemMeta(depositMeta);
        inv.setItem(11, depositItem);

        // Retirer (maire/adjoint uniquement)
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            ItemStack withdrawItem = new ItemStack(Material.GOLD_INGOT);
            ItemMeta withdrawMeta = withdrawItem.getItemMeta();
            withdrawMeta.setDisplayName(ChatColor.YELLOW + "Retirer de l'Argent");
            List<String> withdrawLore = new ArrayList<>();
            withdrawLore.add(ChatColor.GRAY + "Retirer des fonds");
            withdrawLore.add(ChatColor.GRAY + "de la banque municipale");
            withdrawLore.add("");
            withdrawLore.add(ChatColor.RED + "Réservé: Maire/Adjoint");
            withdrawLore.add(ChatColor.YELLOW + "Cliquez pour retirer");
            withdrawMeta.setLore(withdrawLore);
            withdrawItem.setItemMeta(withdrawMeta);
            inv.setItem(13, withdrawItem);
        }

        // Historique des transactions
        ItemStack historyItem = new ItemStack(Material.BOOK);
        ItemMeta historyMeta = historyItem.getItemMeta();
        historyMeta.setDisplayName(ChatColor.AQUA + "Historique des Transactions");
        List<String> historyLore = new ArrayList<>();
        historyLore.add(ChatColor.GRAY + "Voir les dernières");
        historyLore.add(ChatColor.GRAY + "opérations financières");
        historyLore.add("");
        historyLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        historyMeta.setLore(historyLore);
        historyItem.setItemMeta(historyMeta);
        inv.setItem(15, historyItem);

        // Collecter les taxes (maire/adjoint uniquement)
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            ItemStack taxItem = new ItemStack(Material.DIAMOND);
            ItemMeta taxMeta = taxItem.getItemMeta();
            taxMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Collecter les Taxes");
            List<String> taxLore = new ArrayList<>();
            taxLore.add(ChatColor.GRAY + "Collecter les taxes");
            taxLore.add(ChatColor.GRAY + "de toutes les parcelles");
            taxLore.add("");
            taxLore.add(ChatColor.GRAY + "Dernière collecte:");
            taxLore.add(ChatColor.WHITE + town.getLastTaxCollection().toLocalDate().toString());
            taxLore.add("");
            taxLore.add(ChatColor.YELLOW + "Cliquez pour collecter");
            taxMeta.setLore(taxLore);
            taxItem.setItemMeta(taxMeta);
            inv.setItem(22, taxItem);
        }

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    public void openTransactionsMenu(Player player, String townName) {
        Inventory inv = Bukkit.createInventory(null, 54, TRANSACTIONS_TITLE);

        List<PlotTransaction> recent = economyManager.getRecentTransactions(townName, 45);

        int slot = 0;
        for (PlotTransaction transaction : recent) {
            if (slot >= 45) break;

            Material mat = switch (transaction.getType()) {
                case SALE -> Material.EMERALD;
                case RENT -> Material.GOLD_INGOT;
                case TAX -> Material.DIAMOND;
                case FINE -> Material.REDSTONE;
                case DEPOSIT -> Material.LIME_DYE;
                case WITHDRAWAL -> Material.RED_DYE;
            };

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + transaction.getType().getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Joueur: " + ChatColor.WHITE + transaction.getPlayerName());
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + String.format("%.2f€", transaction.getAmount()));
            lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + transaction.getTimestamp().toLocalDate());
            lore.add(ChatColor.GRAY + "Détails: " + ChatColor.WHITE + transaction.getDescription());

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.equals(BANK_TITLE)) {
            handleBankClick(event);
        } else if (title.equals(TRANSACTIONS_TITLE)) {
            handleTransactionsClick(event);
        }
    }

    private void handleBankClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.closeInventory();
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Déposer")) {
            handleDeposit(player, townName);
        } else if (displayName.contains("Retirer")) {
            handleWithdraw(player, townName);
        } else if (displayName.contains("Historique")) {
            player.closeInventory();
            openTransactionsMenu(player, townName);
        } else if (displayName.contains("Collecter les Taxes")) {
            handleCollectTaxes(player, townName);
        } else if (displayName.contains("Fermer")) {
            player.closeInventory();
        }
    }

    private void handleTransactionsClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
            player.closeInventory();
            openBankMenu(player);
        }
    }

    private void handleDeposit(Player player, String townName) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Combien voulez-vous déposer dans la banque ?");
        player.sendMessage(ChatColor.YELLOW + "(Tapez 'annuler' pour abandonner)");

        pendingActions.put(player.getUniqueId(),
            new BankActionContext(ActionType.DEPOSIT, townName));
    }

    private void handleWithdraw(Player player, String townName) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Combien voulez-vous retirer de la banque ?");
        player.sendMessage(ChatColor.YELLOW + "(Tapez 'annuler' pour abandonner)");

        pendingActions.put(player.getUniqueId(),
            new BankActionContext(ActionType.WITHDRAW, townName));
    }

    private void handleCollectTaxes(Player player, String townName) {
        player.closeInventory();

        TownEconomyManager.TaxCollectionResult result = economyManager.collectTaxes(townName);

        player.sendMessage(ChatColor.GREEN + "=== Collecte des Taxes ===");
        player.sendMessage(ChatColor.GOLD + "Total collecté: " + String.format("%.2f€", result.totalCollected));
        player.sendMessage(ChatColor.GRAY + "Parcelles: " + result.parcelsCollected);

        if (result.unpaidCount > 0) {
            player.sendMessage(ChatColor.RED + "Impayés: " + result.unpaidCount);
            player.sendMessage(ChatColor.GRAY + "Joueurs: " + String.join(", ", result.unpaidPlayers));
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BankActionContext context = pendingActions.get(player.getUniqueId());

        if (context == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingActions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Action annulée.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            processPlayerInput(player, context, input);
            pendingActions.remove(player.getUniqueId());
        });
    }

    private void processPlayerInput(Player player, BankActionContext context, String input) {
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "Le montant doit être positif.");
                return;
            }

            Town town = townManager.getTown(context.townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
                return;
            }

            if (context.actionType == ActionType.DEPOSIT) {
                if (!RoleplayCity.getEconomy().has(player, amount)) {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent.");
                    return;
                }

                RoleplayCity.getEconomy().withdrawPlayer(player, amount);
                town.deposit(amount);

                player.sendMessage(ChatColor.GREEN + "Vous avez déposé " + amount + "€ dans la banque !");

            } else if (context.actionType == ActionType.WITHDRAW) {
                TownRole role = town.getMemberRole(player.getUniqueId());
                if (role != TownRole.MAIRE && role != TownRole.ADJOINT) {
                    player.sendMessage(ChatColor.RED + "Seul le maire et les adjoints peuvent retirer de l'argent.");
                    return;
                }

                if (town.getBankBalance() < amount) {
                    player.sendMessage(ChatColor.RED + "La banque n'a pas assez de fonds.");
                    return;
                }

                town.withdraw(amount);
                RoleplayCity.getEconomy().depositPlayer(player, amount);

                player.sendMessage(ChatColor.YELLOW + "Vous avez retiré " + amount + "€ de la banque.");
            }

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Veuillez entrer un nombre valide.");
        }
    }

    private enum ActionType {
        DEPOSIT,
        WITHDRAW
    }

    private static class BankActionContext {
        final ActionType actionType;
        final String townName;

        BankActionContext(ActionType actionType, String townName) {
            this.actionType = actionType;
            this.townName = townName;
        }
    }
}
