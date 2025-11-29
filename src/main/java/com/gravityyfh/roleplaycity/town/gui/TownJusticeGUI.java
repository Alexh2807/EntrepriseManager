package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.contract.model.Contract;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
import com.gravityyfh.roleplaycity.town.manager.TownJusticeManager;
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

import java.util.*;

public class TownJusticeGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownPoliceManager policeManager;
    private final TownJusticeManager justiceManager;
    private TownMainGUI mainGUI;

    private static final String JUSTICE_TITLE = ChatColor.DARK_PURPLE + "⚖️ Justice Municipale";
    private static final String CASES_TITLE = ChatColor.DARK_PURPLE + "📋 Affaires (Amendes)";
    private static final String CONTRACT_CASES_TITLE = ChatColor.DARK_PURPLE + "📜 Litiges Contractuels";
    private static final String VERDICT_TITLE = ChatColor.DARK_PURPLE + "⚖ Rendre le Verdict";
    private static final String CONTRACT_VERDICT_TITLE = ChatColor.DARK_PURPLE + "⚖ Verdict Contrat";

    private final Map<UUID, JudgementContext> pendingJudgements;

    public TownJusticeGUI(RoleplayCity plugin, TownManager townManager,
            TownPoliceManager policeManager, TownJusticeManager justiceManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.policeManager = policeManager;
        this.justiceManager = justiceManager;
        this.pendingJudgements = new HashMap<>();
    }

    public void setMainGUI(TownMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    private boolean reVerifyJudgeAccess(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
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

        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role != TownRole.JUGE && role != TownRole.MAIRE) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Vous n'avez plus accès au menu Justice.");
            return false;
        }

        if (role == TownRole.JUGE) {
            ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
            if (serviceManager != null
                    && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.JUDGE)) {
                player.closeInventory();
                serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.JUDGE);
                return false;
            }
        }

        return true;
    }

    private boolean reVerifyJudgeAccessNoClose(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes plus dans une ville.");
            return false;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return false;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role != TownRole.JUGE && role != TownRole.MAIRE) {
            player.sendMessage(ChatColor.RED + "Vous n'avez plus accès aux fonctions de juge.");
            return false;
        }

        if (role == TownRole.JUGE) {
            ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
            if (serviceManager != null
                    && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.JUDGE)) {
                serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.JUDGE);
                return false;
            }
        }

        return true;
    }

    public void openJusticeMenu(Player player) {
        if (!reVerifyJudgeAccess(player))
            return;

        String townName = townManager.getPlayerTown(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 45, JUSTICE_TITLE);

        var stats = justiceManager.getTownStatistics(townName);
        int disputedContracts = plugin.getContractService().getDisputedContracts().size();

        // --- SECTION DOSSIERS (Ligne 2) ---
        ItemStack purplePane = createDecorativePane(Material.PURPLE_STAINED_GLASS_PANE, " ");
        inv.setItem(9, purplePane);
        inv.setItem(17, purplePane);

        // 1. Contestations Amendes (Slot 11)
        ItemStack casesItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta casesMeta = casesItem.getItemMeta();
        casesMeta.setDisplayName(ChatColor.YELLOW + "📋 Contestations Amendes");
        casesMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Gérer les amendes contestées",
                "",
                ChatColor.WHITE + "En attente: " + ChatColor.RED + stats.pendingContestations(),
                "",
                ChatColor.YELLOW + "▶ Cliquez pour traiter"));
        casesItem.setItemMeta(casesMeta);
        inv.setItem(11, casesItem);

        // 2. Litiges Contractuels (Slot 15)
        ItemStack contractsItem = new ItemStack(Material.MAP);
        ItemMeta contractsMeta = contractsItem.getItemMeta();
        contractsMeta.setDisplayName(ChatColor.GOLD + "📜 Litiges Contractuels");
        contractsMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Gérer les conflits commerciaux",
                "",
                ChatColor.WHITE + "En attente: " + ChatColor.RED + disputedContracts,
                "",
                ChatColor.YELLOW + "▶ Cliquez pour traiter"));
        contractsItem.setItemMeta(contractsMeta);
        inv.setItem(15, contractsItem);

        // --- SECTION STATISTIQUES (Ligne 4) ---
        ItemStack goldPane = createDecorativePane(Material.YELLOW_STAINED_GLASS_PANE, " ");
        inv.setItem(27, goldPane);
        inv.setItem(35, goldPane);

        // Statistiques (Slot 31)
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.WHITE + "📊 Registre Judiciaire");
        statsMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Total jugements: " + ChatColor.WHITE + stats.totalJudgements(),
                ChatColor.GRAY + "Confirmés: " + ChatColor.RED + stats.confirmedJudgements(),
                ChatColor.GRAY + "Annulés: " + ChatColor.GREEN + stats.cancelledJudgements(),
                "",
                ChatColor.GRAY + "Taux confirmation: " + ChatColor.AQUA
                        + String.format("%.1f%%", stats.getConfirmationRate())));
        statsItem.setItemMeta(statsMeta);
        inv.setItem(31, statsItem);

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

    // ... (Keep existing sub-menus and event handlers, updated to redirect to
    // openJusticeMenu)

    public void openCasesMenu(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null)
            return;
        List<Fine> contestedFines = policeManager.getContestedFines(townName);
        Inventory inv = Bukkit.createInventory(null, 54, CASES_TITLE);
        int slot = 0;
        for (Fine fine : contestedFines) {
            if (slot >= 45)
                break;
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + fine.getOffenderName());
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Motif: " + ChatColor.WHITE + fine.getReason(),
                    ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€",
                    ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName(), "",
                    ChatColor.GREEN + "Cliquez pour juger"));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        addBackAndCloseButtons(inv);
        player.openInventory(inv);
    }

    public void openContractCasesMenu(Player player) {
        List<Contract> disputes = plugin.getContractService().getDisputedContracts();
        Inventory inv = Bukkit.createInventory(null, 54, CONTRACT_CASES_TITLE);
        int slot = 0;
        for (Contract c : disputes) {
            if (slot >= 45)
                break;
            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + c.getTitle());
            meta.setLore(Arrays.asList(ChatColor.GRAY + "ID: " + c.getId(),
                    ChatColor.GRAY + "Client: "
                            + (c.getType().isB2B() ? c.getClientCompany()
                                    : Bukkit.getOfflinePlayer(c.getClientUuid()).getName()),
                    ChatColor.RED + "Motif: " + c.getDisputeReason()));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        addBackAndCloseButtons(inv);
        player.openInventory(inv);
    }

    public void openVerdictMenu(Player player, Fine fine) {
        Inventory inv = Bukkit.createInventory(null, 27, VERDICT_TITLE);
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Dossier #" + fine.getFineId().toString().substring(0, 8));
        infoMeta.setLore(Arrays.asList(ChatColor.GRAY + "Contrevenant: " + ChatColor.WHITE + fine.getOffenderName(),
                ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName(),
                ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€", "",
                ChatColor.DARK_RED + "Motif:", ChatColor.WHITE + fine.getReason(), "",
                ChatColor.YELLOW + "Contestation:", ChatColor.WHITE + fine.getContestReason()));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        ItemStack confirmItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ CONFIRMER L'AMENDE");
        confirmMeta.setLore(Arrays.asList("", ChatColor.GRAY + "L'amende sera maintenue",
                ChatColor.GREEN + "Cliquez pour confirmer"));
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(11, confirmItem);

        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ ANNULER L'AMENDE");
        cancelMeta.setLore(
                Arrays.asList("", ChatColor.GRAY + "L'amende sera annulée", ChatColor.RED + "Cliquez pour annuler"));
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(15, cancelItem);

        addBackAndCloseButtons(inv);
        player.openInventory(inv);
    }

    public void openContractVerdictMenu(Player player, Contract c) {
        Inventory inv = Bukkit.createInventory(null, 27, CONTRACT_VERDICT_TITLE);
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Litige: " + c.getTitle());
        infoMeta.setLore(Arrays.asList(ChatColor.GRAY + "ID: " + c.getId(),
                ChatColor.GRAY + "Fournisseur: " + c.getProviderCompany(),
                ChatColor.GRAY + "Client: "
                        + (c.getType().isB2B() ? c.getClientCompany()
                                : Bukkit.getOfflinePlayer(c.getClientUuid()).getName()),
                ChatColor.GRAY + "Montant: " + c.getAmount() + "€", ChatColor.RED + "Motif: " + c.getDisputeReason()));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        ItemStack clientWin = new ItemStack(Material.LIME_WOOL);
        ItemMeta cwMeta = clientWin.getItemMeta();
        cwMeta.setDisplayName(ChatColor.GREEN + "CLIENT GAGNANT");
        cwMeta.setLore(Arrays.asList(ChatColor.GRAY + "Rembourser le client", ChatColor.GRAY + "(Plaintif)"));
        clientWin.setItemMeta(cwMeta);
        inv.setItem(11, clientWin);

        ItemStack providerWin = new ItemStack(Material.RED_WOOL);
        ItemMeta pwMeta = providerWin.getItemMeta();
        pwMeta.setDisplayName(ChatColor.RED + "FOURNISSEUR GAGNANT");
        pwMeta.setLore(Arrays.asList(ChatColor.GRAY + "Payer le fournisseur", ChatColor.GRAY + "(Défendeur)"));
        providerWin.setItemMeta(pwMeta);
        inv.setItem(15, providerWin);

        addBackAndCloseButtons(inv);
        player.openInventory(inv);
    }

    private void addBackAndCloseButtons(Inventory inv) {
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(inv.getSize() - 5, backItem);

        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(inv.getSize() - 1, closeItem);
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

        if (title.equals(JUSTICE_TITLE)) {
            event.setCancelled(true);
            if (displayName.contains("Contestations Amendes")) {
                openCasesMenu(player);
            } else if (displayName.contains("Litiges Contractuels")) {
                openContractCasesMenu(player);
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
        } else if (title.equals(CASES_TITLE)) {
            event.setCancelled(true);
            if (displayName.contains("Retour")) {
                openJusticeMenu(player);
            } else if (clicked.getType() == Material.PAPER) {
                if (!reVerifyJudgeAccess(player))
                    return;
                String offenderName = displayName;
                String townName = townManager.getPlayerTown(player.getUniqueId());
                List<Fine> contestedFines = policeManager.getContestedFines(townName);
                for (Fine fine : contestedFines) {
                    if (fine.getOffenderName().equals(offenderName)) {
                        openVerdictMenu(player, fine);
                        return;
                    }
                }
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            }
        } else if (title.equals(CONTRACT_CASES_TITLE)) {
            event.setCancelled(true);
            if (displayName.contains("Retour")) {
                openJusticeMenu(player);
            } else if (clicked.getType() == Material.MAP) {
                if (!reVerifyJudgeAccess(player))
                    return;
                if (clicked.getItemMeta().hasLore()) {
                    for (String line : clicked.getItemMeta().getLore()) {
                        if (line.startsWith(ChatColor.GRAY + "ID: ")) {
                            String idStr = line.substring((ChatColor.GRAY + "ID: ").length());
                            Contract c = plugin.getContractService().getContract(UUID.fromString(idStr));
                            if (c != null)
                                openContractVerdictMenu(player, c);
                            break;
                        }
                    }
                }
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            }
        } else if (title.equals(VERDICT_TITLE)) {
            event.setCancelled(true);
            if (displayName.contains("Retour")) {
                openCasesMenu(player);
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            } else {
                if (!reVerifyJudgeAccess(player))
                    return;
                ItemStack dossierItem = event.getInventory().getItem(4);
                if (dossierItem == null)
                    return;
                String dossierName = ChatColor.stripColor(dossierItem.getItemMeta().getDisplayName());
                String fineIdPrefix = dossierName.replace("Dossier #", "");
                String townName = townManager.getPlayerTown(player.getUniqueId());
                List<Fine> contestedFines = policeManager.getContestedFines(townName);
                Fine selectedFine = null;
                for (Fine fine : contestedFines) {
                    if (fine.getFineId().toString().startsWith(fineIdPrefix)) {
                        selectedFine = fine;
                        break;
                    }
                }
                if (selectedFine == null)
                    return;
                if (displayName.contains("CONFIRMER")) {
                    handleJudgeFine(player, selectedFine, true);
                } else if (displayName.contains("ANNULER")) {
                    handleJudgeFine(player, selectedFine, false);
                }
            }
        } else if (title.equals(CONTRACT_VERDICT_TITLE)) {
            event.setCancelled(true);
            if (displayName.contains("Retour")) {
                openContractCasesMenu(player);
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            } else {
                if (!reVerifyJudgeAccess(player))
                    return;
                ItemStack infoItem = event.getInventory().getItem(4);
                if (infoItem == null || !infoItem.hasItemMeta() || !infoItem.getItemMeta().hasLore())
                    return;
                String idStr = null;
                for (String line : infoItem.getItemMeta().getLore()) {
                    if (line.startsWith(ChatColor.GRAY + "ID: ")) {
                        idStr = line.substring((ChatColor.GRAY + "ID: ").length());
                        break;
                    }
                }
                if (idStr == null)
                    return;
                Contract c = plugin.getContractService().getContract(UUID.fromString(idStr));
                if (c == null)
                    return;
                if (displayName.contains("CLIENT GAGNANT")) {
                    handleJudgeContract(player, c, true);
                } else if (displayName.contains("FOURNISSEUR GAGNANT")) {
                    handleJudgeContract(player, c, false);
                }
            }
        }
    }

    private void handleJudgeFine(Player player, Fine fine, boolean valid) {
        player.closeInventory();
        Plot currentPlot = plugin.getClaimManager().getPlotAt(player.getLocation().getChunk());
        if (currentPlot == null || currentPlot.getMunicipalSubType() != MunicipalSubType.TRIBUNAL) {
            player.sendMessage(ChatColor.RED + "Vous devez être au TRIBUNAL.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Entrez le verdict (raison) dans le chat:");
        JudgementContext context = new JudgementContext(fine);
        context.valid = valid;
        pendingJudgements.put(player.getUniqueId(), context);
    }

    private void handleJudgeContract(Player player, Contract c, boolean valid) {
        player.closeInventory();
        Plot currentPlot = plugin.getClaimManager().getPlotAt(player.getLocation().getChunk());
        if (currentPlot == null || currentPlot.getMunicipalSubType() != MunicipalSubType.TRIBUNAL) {
            player.sendMessage(ChatColor.RED + "Vous devez être au TRIBUNAL.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Entrez le verdict (raison) dans le chat:");
        JudgementContext context = new JudgementContext(c);
        context.valid = valid;
        pendingJudgements.put(player.getUniqueId(), context);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        JudgementContext context = pendingJudgements.get(player.getUniqueId());
        if (context == null)
            return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingJudgements.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Jugement annulé.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> processJudgement(player, context, input));
    }

    private void processJudgement(Player player, JudgementContext context, String input) {
        if (!reVerifyJudgeAccessNoClose(player)) {
            pendingJudgements.remove(player.getUniqueId());
            return;
        }
        if (input.length() < 5) {
            player.sendMessage(ChatColor.RED + "Verdict trop court (min 5 car.)");
            return;
        }
        boolean success = false;
        if (context.fine != null) {
            success = justiceManager.judgeFine(context.fine, player, context.valid, input);
        } else if (context.contract != null) {
            success = justiceManager.judgeContract(context.contract, player, context.valid, input);
        }
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Jugement rendu !");
        }
        pendingJudgements.remove(player.getUniqueId());
    }

    private static class JudgementContext {
        Fine fine;
        Contract contract;
        boolean valid;

        JudgementContext(Fine fine) {
            this.fine = fine;
        }

        JudgementContext(Contract contract) {
            this.contract = contract;
        }
    }
}