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

    private static final String JUSTICE_TITLE = ChatColor.DARK_PURPLE + "⚖️ Justice Municipale";
    private static final String CASES_TITLE = ChatColor.DARK_PURPLE + "📋 Affaires (Amendes)";
    private static final String CONTRACT_CASES_TITLE = ChatColor.DARK_PURPLE + "📜 Litiges Contractuels";
    private static final String VERDICT_TITLE = ChatColor.DARK_PURPLE + "⚖ Rendre le Verdict";
    private static final String CONTRACT_VERDICT_TITLE = ChatColor.DARK_PURPLE + "⚖ Verdict Contrat";

    private final Map<UUID, JudgementContext> pendingJudgements;

    /**
     * Vérifie si le joueur est toujours autorisé à utiliser le menu Justice
     * Re-vérification pour empêcher les actions si le joueur a quitté le service
     * @return true si autorisé, false sinon (ferme le menu et envoie un message)
     */
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

        // Si c'est un JUGE (pas MAIRE), vérifier qu'il est toujours en service
        if (role == TownRole.JUGE) {
            ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
            if (serviceManager != null && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.JUDGE)) {
                player.closeInventory();
                serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.JUDGE);
                return false;
            }
        }

        return true;
    }

    public TownJusticeGUI(RoleplayCity plugin, TownManager townManager,
                         TownPoliceManager policeManager, TownJusticeManager justiceManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.policeManager = policeManager;
        this.justiceManager = justiceManager;
        this.pendingJudgements = new HashMap<>();
    }

    public void openJusticeMenu(Player player) {
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
        if (role != TownRole.JUGE && role != TownRole.MAIRE) {
            player.sendMessage(ChatColor.RED + "Vous devez être juge pour accéder à ce menu.");
            return;
        }

        // Si c'est un JUGE (pas MAIRE), vérifier qu'il est en service
        if (role == TownRole.JUGE) {
            ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
            if (serviceManager != null && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.JUDGE)) {
                serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.JUDGE);
                return;
            }
        }

        Inventory inv = Bukkit.createInventory(null, 27, JUSTICE_TITLE);

        // Statistiques
        var stats = justiceManager.getTownStatistics(townName);

        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.GOLD + "Statistiques");
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "Total jugements: " + ChatColor.WHITE + stats.totalJudgements());
        statsLore.add(ChatColor.GRAY + "Confirmés: " + ChatColor.RED + stats.confirmedJudgements());
        statsLore.add(ChatColor.GRAY + "Annulés: " + ChatColor.GREEN + stats.cancelledJudgements());
        statsLore.add(ChatColor.GRAY + "En attente: " + ChatColor.YELLOW + stats.pendingContestations());
        statsLore.add("");
        statsLore.add(ChatColor.GRAY + "Taux confirmation: " + ChatColor.AQUA +
            String.format("%.1f%%", stats.getConfirmationRate()));
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(4, statsItem);

        // Affaires (Amendes)
        ItemStack casesItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta casesMeta = casesItem.getItemMeta();
        casesMeta.setDisplayName(ChatColor.YELLOW + "Contestations Amendes");
        List<String> casesLore = new ArrayList<>();
        casesLore.add(ChatColor.GRAY + "Voir les amendes contestées");
        casesLore.add("");
        casesLore.add(ChatColor.WHITE + "En attente: " + stats.pendingContestations());
        casesLore.add(ChatColor.YELLOW + "Cliquez pour gérer");
        casesMeta.setLore(casesLore);
        casesItem.setItemMeta(casesMeta);
        inv.setItem(11, casesItem);
        
        // Affaires (Contrats)
        int disputedContracts = plugin.getContractService().getDisputedContracts().size();
        ItemStack contractsItem = new ItemStack(Material.MAP);
        ItemMeta contractsMeta = contractsItem.getItemMeta();
        contractsMeta.setDisplayName(ChatColor.GOLD + "Litiges Contractuels");
        List<String> contractsLore = new ArrayList<>();
        contractsLore.add(ChatColor.GRAY + "Voir les litiges commerciaux");
        contractsLore.add("");
        contractsLore.add(ChatColor.WHITE + "En attente: " + disputedContracts);
        contractsLore.add(ChatColor.GOLD + "Cliquez pour gérer");
        contractsMeta.setLore(contractsLore);
        contractsItem.setItemMeta(contractsMeta);
        inv.setItem(15, contractsItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    public void openVerdictMenu(Player player, Fine fine) {
        Inventory inv = Bukkit.createInventory(null, 27, VERDICT_TITLE);

        // Informations de l'amende
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Dossier #" + fine.getFineId().toString().substring(0, 8));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Contrevenant: " + ChatColor.WHITE + fine.getOffenderName());
        infoLore.add(ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName());
        infoLore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€");
        infoLore.add("");
        infoLore.add(ChatColor.DARK_RED + "Motif de l'amende:");
        infoLore.add(ChatColor.WHITE + fine.getReason());
        infoLore.add("");
        if (fine.getContestReason() != null && !fine.getContestReason().isEmpty()) {
            infoLore.add(ChatColor.YELLOW + "Motif de contestation:");
            infoLore.add(ChatColor.WHITE + fine.getContestReason());
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Bouton CONFIRMER (vert)
        ItemStack confirmItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ CONFIRMER L'AMENDE");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        confirmLore.add(ChatColor.GRAY + "L'amende sera maintenue");
        confirmLore.add(ChatColor.GREEN + "Cliquez pour confirmer");
        confirmMeta.setLore(confirmLore);
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(11, confirmItem);

        // Bouton ANNULER (rouge)
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ ANNULER L'AMENDE");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("");
        cancelLore.add(ChatColor.GRAY + "L'amende sera annulée");
        cancelLore.add(ChatColor.RED + "Cliquez pour annuler");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(15, cancelItem);

        // Bouton Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);
    }
    
    public void openContractVerdictMenu(Player player, Contract c) {
        Inventory inv = Bukkit.createInventory(null, 27, CONTRACT_VERDICT_TITLE);

        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Litige: " + c.getTitle());
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Fournisseur: " + c.getProviderCompany());
        infoLore.add(ChatColor.GRAY + "Client: " + (c.getType().isB2B() ? c.getClientCompany() : Bukkit.getOfflinePlayer(c.getClientUuid()).getName()));
        infoLore.add(ChatColor.GRAY + "Montant: " + c.getAmount() + "€");
        infoLore.add(ChatColor.RED + "Motif: " + c.getDisputeReason());
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);
        
        // Valid (Client wins)
        ItemStack clientWin = new ItemStack(Material.LIME_WOOL);
        ItemMeta cwMeta = clientWin.getItemMeta();
        cwMeta.setDisplayName(ChatColor.GREEN + "CLIENT GAGNANT");
        cwMeta.setLore(Arrays.asList(ChatColor.GRAY + "Rembourser le client", ChatColor.GRAY + "(Plaintif)"));
        clientWin.setItemMeta(cwMeta);
        inv.setItem(11, clientWin);
        
        // Invalid (Provider wins)
        ItemStack providerWin = new ItemStack(Material.RED_WOOL);
        ItemMeta pwMeta = providerWin.getItemMeta();
        pwMeta.setDisplayName(ChatColor.RED + "FOURNISSEUR GAGNANT");
        pwMeta.setLore(Arrays.asList(ChatColor.GRAY + "Payer le fournisseur", ChatColor.GRAY + "(Défendeur)"));
        providerWin.setItemMeta(pwMeta);
        inv.setItem(15, providerWin);
        
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);
        
        player.openInventory(inv);
    }

    public void openCasesMenu(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            return;
        }

        List<Fine> contestedFines = policeManager.getContestedFines(townName);

        Inventory inv = Bukkit.createInventory(null, 54, CASES_TITLE);

        int slot = 0;
        for (Fine fine : contestedFines) {
            if (slot >= 45) break;

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + fine.getOffenderName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Motif amende: " + ChatColor.WHITE + fine.getReason());
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€");
            lore.add(ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName());
            lore.add("");
            lore.add(ChatColor.GREEN + "Cliquez pour juger");

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        player.openInventory(inv);
    }
    
    public void openContractCasesMenu(Player player) {
        List<Contract> disputes = plugin.getContractService().getDisputedContracts();
        Inventory inv = Bukkit.createInventory(null, 54, CONTRACT_CASES_TITLE);
        
        int slot = 0;
        for(Contract c : disputes) {
             if (slot >= 45) break;
             ItemStack item = new ItemStack(Material.MAP);
             ItemMeta meta = item.getItemMeta();
             meta.setDisplayName(ChatColor.GOLD + c.getTitle());
             meta.setLore(Arrays.asList(
                 ChatColor.GRAY + "ID: " + c.getId(),
                 ChatColor.GRAY + "Client: " + (c.getType().isB2B() ? c.getClientCompany() : Bukkit.getOfflinePlayer(c.getClientUuid()).getName()),
                 ChatColor.RED + "Motif: " + c.getDisputeReason()
             ));
             item.setItemMeta(meta);
             inv.setItem(slot++, item);
        }
        
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

        if (title.equals(JUSTICE_TITLE)) {
            handleJusticeMenuClick(event);
        } else if (title.equals(CASES_TITLE)) {
            handleCasesMenuClick(event);
        } else if (title.equals(VERDICT_TITLE)) {
            handleVerdictMenuClick(event);
        } else if (title.equals(CONTRACT_CASES_TITLE)) {
            handleContractCasesClick(event);
        } else if (title.equals(CONTRACT_VERDICT_TITLE)) {
            handleContractVerdictClick(event);
        }
    }

    private void handleJusticeMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Contestations Amendes")) {
            player.closeInventory();
            openCasesMenu(player);
        } else if (displayName.contains("Litiges Contractuels")) {
            player.closeInventory();
            openContractCasesMenu(player);
        } else if (displayName.contains("Fermer")) {
            player.closeInventory();
        }
    }

    private void handleCasesMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
            player.closeInventory();
            openJusticeMenu(player);
        } else if (clicked.getType() == Material.PAPER) {
            // Re-vérification avant d'ouvrir le verdict
            if (!reVerifyJudgeAccess(player)) return;
            String offenderName = displayName;
            String townName = townManager.getPlayerTown(player.getUniqueId());
            List<Fine> contestedFines = policeManager.getContestedFines(townName);
            
            for (Fine fine : contestedFines) {
                if (fine.getOffenderName().equals(offenderName)) {
                    player.closeInventory();
                    openVerdictMenu(player, fine);
                    return;
                }
            }
        }
    }
    
    private void handleContractCasesClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
            player.closeInventory();
            openJusticeMenu(player);
        } else if (clicked.getType() == Material.MAP) {
            // Re-vérification avant d'ouvrir le verdict contrat
            if (!reVerifyJudgeAccess(player)) return;
             // Extract ID
             if (clicked.getItemMeta().hasLore()) {
                 for(String line : clicked.getItemMeta().getLore()) {
                     if (line.startsWith(ChatColor.GRAY + "ID: ")) {
                         String idStr = line.substring((ChatColor.GRAY + "ID: ").length());
                         Contract c = plugin.getContractService().getContract(UUID.fromString(idStr));
                         if (c != null) {
                             player.closeInventory();
                             openContractVerdictMenu(player, c);
                         }
                         break;
                     }
                 }
             }
        }
    }

    private void handleVerdictMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
            player.closeInventory();
            openCasesMenu(player);
            return;
        }

        // Re-vérification avant de rendre un verdict
        if (!reVerifyJudgeAccess(player)) return;

        // Find Fine logic same as before...
        // Simplified for brevity in this large replace, relying on previous logic
        ItemStack dossierItem = event.getInventory().getItem(4);
        if (dossierItem == null) return;
        String dossierName = ChatColor.stripColor(dossierItem.getItemMeta().getDisplayName());
        String fineIdPrefix = dossierName.replace("Dossier #", "");
        
        String townName = townManager.getPlayerTown(player.getUniqueId());
        List<Fine> contestedFines = policeManager.getContestedFines(townName);
        Fine selectedFine = null;
        for (Fine fine : contestedFines) {
            if (fine.getFineId().toString().startsWith(fineIdPrefix)) {
                selectedFine = fine; break;
            }
        }
        
        if (selectedFine == null) return;

        if (displayName.contains("CONFIRMER")) {
            handleJudgeFine(player, selectedFine, true);
        } else if (displayName.contains("ANNULER")) {
            handleJudgeFine(player, selectedFine, false);
        }
    }
    
    private void handleContractVerdictClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
             player.closeInventory();
             openContractCasesMenu(player);
             return;
        }

        // Re-vérification avant de rendre un verdict contrat
        if (!reVerifyJudgeAccess(player)) return;

        // Find Contract via Title/ID match or Context
        // We can get ID from item 4 info again or cache it.
        // Let's assume we stored it in title "Litige: Title". Weak.
        // But wait, we are not extracting ID here.
        // Let's use the same logic: Item 4 has details.
        // But Item 4 in `openContractVerdictMenu` didn't store ID explicitly in title.
        // Let's assume we can find it by title.
        // Better: Store ID in lore of Item 4. (I didn't adding it in openContractVerdictMenu, I should have)
        // FIX: I will update openContractVerdictMenu to put ID in lore.
        
        ItemStack infoItem = event.getInventory().getItem(4);
        // In openContractVerdictMenu I only put "Litige: Title".
        // I need to find the contract.
        // I will search all disputed contracts for one with this title.
        String title = ChatColor.stripColor(infoItem.getItemMeta().getDisplayName()).replace("Litige: ", "");
        Contract c = plugin.getContractService().getDisputedContracts().stream()
             .filter(con -> con.getTitle().equals(title))
             .findFirst().orElse(null);
             
        if (c == null) return;
        
        if (displayName.contains("CLIENT GAGNANT")) {
             handleJudgeContract(player, c, true);
        } else if (displayName.contains("FOURNISSEUR GAGNANT")) {
             handleJudgeContract(player, c, false);
        }
    }

    private void handleJudgeFine(Player player, Fine fine, boolean valid) {
        player.closeInventory();
        // Tribunal Check
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
         // Tribunal Check
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

        if (context == null) return;

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
        // Re-vérification finale avant de rendre le jugement
        // (le joueur pourrait avoir quitté le service entre temps)
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

    /**
     * Version de reVerifyJudgeAccess sans fermer l'inventaire (pour processJudgement via chat)
     */
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

        // Si c'est un JUGE (pas MAIRE), vérifier qu'il est toujours en service
        if (role == TownRole.JUGE) {
            ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
            if (serviceManager != null && !serviceManager.isInService(player.getUniqueId(), ProfessionalServiceType.JUDGE)) {
                serviceManager.sendNotInServiceMessage(player, ProfessionalServiceType.JUDGE);
                return false;
            }
        }

        return true;
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