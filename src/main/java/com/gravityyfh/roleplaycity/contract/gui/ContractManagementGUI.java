package com.gravityyfh.roleplaycity.contract.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.contract.model.Contract;
import com.gravityyfh.roleplaycity.contract.model.ContractStatus;
import com.gravityyfh.roleplaycity.contract.model.ContractType;
import com.gravityyfh.roleplaycity.contract.service.ContractService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Menu principal de gestion des contrats d'une entreprise
 * Affiche les contrats avec onglets Reçus/Envoyés/Historique et filtres par statut
 */
public class ContractManagementGUI implements Listener {

    private final RoleplayCity plugin;
    private final ContractService contractService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Contexte par joueur
    private final Map<UUID, GUIContext> contexts = new HashMap<>();

    // Anti double-clic
    private final Map<UUID, Long> clickTimestamps = new HashMap<>();
    private static final long CLICK_DELAY_MS = 300;

    public enum TabType {
        RECEIVED("Reçus"),
        SENT("Envoyés"),
        HISTORY("Historique");

        private final String display;

        TabType(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    public enum FilterStatus {
        ALL("Tous"),
        PROPOSE("Proposés"),
        ACCEPTE("Acceptés"),
        LITIGE("Litiges");

        private final String display;

        FilterStatus(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    /**
     * Contexte de navigation pour un joueur
     */
    private static class GUIContext {
        UUID playerUuid; // UUID du joueur
        String companyName; // Entreprise en cours de gestion (null = contrats personnels)
        TabType currentTab = TabType.RECEIVED;
        FilterStatus currentFilter = FilterStatus.ALL;
        int currentPage = 0;

        GUIContext(UUID playerUuid, String companyName) {
            this.playerUuid = playerUuid;
            this.companyName = companyName;
        }
    }

    public ContractManagementGUI(RoleplayCity plugin, ContractService contractService) {
        this.plugin = plugin;
        this.contractService = contractService;
    }

    /**
     * Ouvre le menu de gestion des contrats pour une entreprise ou un joueur
     * @param player Le joueur
     * @param companyName Le nom de l'entreprise, ou null pour les contrats personnels
     */
    public void openContractMenu(Player player, String companyName) {
        GUIContext ctx = contexts.computeIfAbsent(player.getUniqueId(),
            k -> new GUIContext(player.getUniqueId(), companyName));
        ctx.playerUuid = player.getUniqueId();
        ctx.companyName = companyName;
        ctx.currentPage = 0;

        Inventory inv = createInventory(ctx);
        player.openInventory(inv);
    }

    /**
     * Crée l'inventaire du menu
     */
    private Inventory createInventory(GUIContext ctx) {
        String title = ctx.companyName == null ?
                ChatColor.DARK_BLUE + "Mes Contrats Personnels" :
                ChatColor.DARK_BLUE + "Contrats: " + ctx.companyName;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Bordures et décoration
        fillBorders(inv);

        // Onglets (slots 10-12)
        createTabButtons(inv, ctx);

        // Filtres (slots 14-16)
        createFilterButtons(inv, ctx);

        // Bouton "Créer Contrat" (slot 22) - uniquement pour les entreprises
        if (ctx.companyName != null) {
            ItemStack createBtn = createItem(Material.WRITABLE_BOOK,
                    ChatColor.GREEN + "Créer un Contrat",
                    ChatColor.GRAY + "Proposer un nouveau contrat");
            inv.setItem(22, createBtn);
        }

        // Liste des contrats (slots 28-43, pagination 16 contrats)
        displayContracts(inv, ctx);

        // Navigation pagination (slots 48-50)
        createPaginationButtons(inv, ctx);

        return inv;
    }

    /**
     * Crée les boutons d'onglets
     */
    private void createTabButtons(Inventory inv, GUIContext ctx) {
        int[] slots = {10, 11, 12};
        TabType[] tabs = TabType.values();

        for (int i = 0; i < tabs.length; i++) {
            TabType tab = tabs[i];
            Material mat = ctx.currentTab == tab ? Material.LIME_DYE : Material.LIGHT_GRAY_DYE;
            String name = (ctx.currentTab == tab ? ChatColor.GREEN + "➤ " : ChatColor.GRAY) + tab.getDisplay();

            ItemStack item = createItem(mat, name,
                    ChatColor.GRAY + "Cliquez pour afficher");
            inv.setItem(slots[i], item);
        }
    }

    /**
     * Crée les boutons de filtres
     */
    private void createFilterButtons(Inventory inv, GUIContext ctx) {
        int[] slots = {14, 15, 16};
        FilterStatus[] filters = {FilterStatus.ALL, FilterStatus.PROPOSE, FilterStatus.ACCEPTE};

        // Pour l'historique, on ne montre que "Tous"
        if (ctx.currentTab == TabType.HISTORY) {
            ItemStack item = createItem(Material.BOOK,
                    ChatColor.YELLOW + "Filtre: Tous",
                    ChatColor.GRAY + "Affichage de tout l'historique");
            inv.setItem(14, item);
            return;
        }

        for (int i = 0; i < filters.length; i++) {
            FilterStatus filter = filters[i];
            Material mat = ctx.currentFilter == filter ? Material.GOLD_INGOT : Material.IRON_INGOT;
            String name = (ctx.currentFilter == filter ? ChatColor.YELLOW : ChatColor.GRAY) + filter.getDisplay();

            ItemStack item = createItem(mat, name,
                    ChatColor.GRAY + "Cliquez pour filtrer");
            inv.setItem(slots[i], item);
        }

        // Filtre Litige seulement si onglet Envoyés ou Reçus
        ItemStack litigeItem = createItem(
                ctx.currentFilter == FilterStatus.LITIGE ? Material.GOLD_INGOT : Material.IRON_INGOT,
                (ctx.currentFilter == FilterStatus.LITIGE ? ChatColor.YELLOW : ChatColor.GRAY) + FilterStatus.LITIGE.getDisplay(),
                ChatColor.GRAY + "Contrats en litige");
        inv.setItem(16, litigeItem);
    }

    /**
     * Affiche la liste des contrats selon l'onglet et le filtre
     */
    private void displayContracts(Inventory inv, GUIContext ctx) {
        List<Contract> contracts = getFilteredContracts(ctx);

        int startIndex = ctx.currentPage * 16;
        int[] slots = {28, 29, 30, 31, 32, 33, 34,
                       37, 38, 39, 40, 41, 42, 43};

        for (int i = 0; i < slots.length && (startIndex + i) < contracts.size(); i++) {
            Contract contract = contracts.get(startIndex + i);
            ItemStack item = createContractItem(contract, ctx);
            inv.setItem(slots[i], item);
        }
    }

    /**
     * Récupère les contrats filtrés selon l'onglet et le filtre
     */
    private List<Contract> getFilteredContracts(GUIContext ctx) {
        List<Contract> contracts;

        // Si companyName est null, afficher les contrats personnels du joueur (B2C)
        if (ctx.companyName == null) {
            // Récupérer tous les contrats du joueur
            List<Contract> allPlayerContracts = contractService.getContractsByPlayer(ctx.playerUuid);

            // Filtrer selon l'onglet
            switch (ctx.currentTab) {
                case RECEIVED:
                    // Contrats B2C où le joueur est le client
                    contracts = allPlayerContracts.stream()
                            .filter(c -> c.getType() == ContractType.B2C &&
                                    c.getClientUuid() != null &&
                                    c.getClientUuid().equals(ctx.playerUuid))
                            .collect(Collectors.toList());
                    break;
                case SENT:
                    // Aucun contrat envoyé pour un particulier (il faut une entreprise)
                    contracts = new ArrayList<>();
                    break;
                case HISTORY:
                    // Historique des contrats B2C du joueur
                    contracts = allPlayerContracts.stream()
                            .filter(c -> c.getType() == ContractType.B2C &&
                                    c.getStatus().isHistorical())
                            .collect(Collectors.toList());
                    return contracts;
                default:
                    contracts = new ArrayList<>();
            }
        } else {
            // Filtre par entreprise (logique existante)
            switch (ctx.currentTab) {
                case RECEIVED:
                    contracts = contractService.getReceivedContractsByCompany(ctx.companyName);
                    break;
                case SENT:
                    contracts = contractService.getSentContractsByCompany(ctx.companyName);
                    break;
                case HISTORY:
                    contracts = contractService.getHistoryContractsByCompany(ctx.companyName);
                    return contracts; // Pas de filtre sur l'historique
                default:
                    contracts = new ArrayList<>();
            }
        }

        // Filtre par statut
        if (ctx.currentFilter != FilterStatus.ALL) {
            ContractStatus status = mapFilterToStatus(ctx.currentFilter);
            contracts = contracts.stream()
                    .filter(c -> c.getStatus() == status)
                    .collect(Collectors.toList());
        } else {
            // Filtre "Tous" exclut l'historique
            contracts = contracts.stream()
                    .filter(c -> c.getStatus().isActive())
                    .collect(Collectors.toList());
        }

        // Tri par date (plus récents en premier)
        contracts.sort((c1, c2) -> c2.getProposalDate().compareTo(c1.getProposalDate()));

        return contracts;
    }

    /**
     * Convertit un filtre en statut de contrat
     */
    private ContractStatus mapFilterToStatus(FilterStatus filter) {
        switch (filter) {
            case PROPOSE: return ContractStatus.PROPOSE;
            case ACCEPTE: return ContractStatus.ACCEPTE;
            case LITIGE: return ContractStatus.LITIGE;
            default: return null;
        }
    }

    /**
     * Crée un ItemStack représentant un contrat
     */
    private ItemStack createContractItem(Contract contract, GUIContext ctx) {
        Material mat;
        ChatColor color;

        switch (contract.getStatus()) {
            case PROPOSE:
                mat = Material.PAPER;
                color = ChatColor.YELLOW;
                break;
            case ACCEPTE:
                mat = Material.WRITABLE_BOOK;
                color = ChatColor.GREEN;
                break;
            case LITIGE:
                mat = Material.BARRIER;
                color = ChatColor.RED;
                break;
            case TERMINE:
                mat = Material.BOOK;
                color = ChatColor.DARK_GREEN;
                break;
            case REJETE:
                mat = Material.BARRIER;
                color = ChatColor.DARK_RED;
                break;
            case EXPIRE:
                mat = Material.GRAY_DYE;
                color = ChatColor.DARK_GRAY;
                break;
            default:
                mat = Material.PAPER;
                color = ChatColor.WHITE;
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Statut: " + color + contract.getStatus().name());
        lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + contract.getAmount() + "€");

        // Afficher le client ou le fournisseur selon l'onglet
        if (ctx.currentTab == TabType.SENT) {
            String client = contract.getType().isB2B()
                    ? contract.getClientCompany()
                    : contract.getClientDisplayName(Bukkit.getServer());
            lore.add(ChatColor.GRAY + "Client: " + ChatColor.WHITE + client);
        } else {
            lore.add(ChatColor.GRAY + "Fournisseur: " + ChatColor.WHITE + contract.getProviderCompany());
        }

        lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + contract.getProposalDate().format(formatter));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Clic pour voir les détails");

        return createItem(mat, color + contract.getTitle(), lore.toArray(new String[0]));
    }

    /**
     * Crée les boutons de pagination
     */
    private void createPaginationButtons(Inventory inv, GUIContext ctx) {
        int totalContracts = getFilteredContracts(ctx).size();
        int maxPage = (totalContracts - 1) / 16;

        // Page précédente
        if (ctx.currentPage > 0) {
            ItemStack prev = createItem(Material.ARROW,
                    ChatColor.YELLOW + "◀ Page Précédente",
                    ChatColor.GRAY + "Page " + ctx.currentPage + "/" + (maxPage + 1));
            inv.setItem(48, prev);
        }

        // Indicateur de page
        ItemStack pageInfo = createItem(Material.BOOK,
                ChatColor.WHITE + "Page " + (ctx.currentPage + 1) + "/" + (maxPage + 1),
                ChatColor.GRAY + String.valueOf(totalContracts) + " contrat(s)");
        inv.setItem(49, pageInfo);

        // Page suivante
        if (ctx.currentPage < maxPage) {
            ItemStack next = createItem(Material.ARROW,
                    ChatColor.YELLOW + "Page Suivante ▶",
                    ChatColor.GRAY + "Page " + (ctx.currentPage + 2) + "/" + (maxPage + 1));
            inv.setItem(50, next);
        }
    }

    /**
     * Remplit les bordures de l'inventaire
     */
    private void fillBorders(Inventory inv) {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");

        int[] borders = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 51, 52, 53};
        for (int slot : borders) {
            inv.setItem(slot, glass);
        }
    }

    /**
     * Bloque le drag d'items dans le menu contrats
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title.startsWith(ChatColor.DARK_BLUE + "Contrats:")) {
            event.setCancelled(true);
        }
    }

    /**
     * Gère les clics dans l'inventaire
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.startsWith(ChatColor.DARK_BLUE + "Contrats:")) return;

        event.setCancelled(true);

        // Anti double-clic
        long now = System.currentTimeMillis();
        Long lastClick = clickTimestamps.get(player.getUniqueId());
        if (lastClick != null && (now - lastClick) < CLICK_DELAY_MS) {
            return;
        }
        clickTimestamps.put(player.getUniqueId(), now);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        GUIContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        int slot = event.getSlot();

        // Onglets
        if (slot == 10) {
            ctx.currentTab = TabType.RECEIVED;
            ctx.currentPage = 0;
            refreshInventory(player, ctx);
        } else if (slot == 11) {
            ctx.currentTab = TabType.SENT;
            ctx.currentPage = 0;
            refreshInventory(player, ctx);
        } else if (slot == 12) {
            ctx.currentTab = TabType.HISTORY;
            ctx.currentFilter = FilterStatus.ALL;
            ctx.currentPage = 0;
            refreshInventory(player, ctx);
        }
        // Filtres
        else if (slot == 14 && ctx.currentTab != TabType.HISTORY) {
            ctx.currentFilter = FilterStatus.ALL;
            ctx.currentPage = 0;
            refreshInventory(player, ctx);
        } else if (slot == 15 && ctx.currentTab != TabType.HISTORY) {
            ctx.currentFilter = FilterStatus.PROPOSE;
            ctx.currentPage = 0;
            refreshInventory(player, ctx);
        } else if (slot == 16 && ctx.currentTab != TabType.HISTORY) {
            ctx.currentFilter = slot == 16 ? FilterStatus.LITIGE : FilterStatus.ACCEPTE;
            ctx.currentPage = 0;
            refreshInventory(player, ctx);
        }
        // Bouton "Créer Contrat"
        else if (slot == 22) {
            player.closeInventory();
            if (plugin.getContractCreationGUI() != null) {
                plugin.getContractCreationGUI().openCreationWizard(player, ctx.companyName);
            }
        }
        // Pagination
        else if (slot == 48 && ctx.currentPage > 0) {
            ctx.currentPage--;
            refreshInventory(player, ctx);
        } else if (slot == 50) {
            int totalContracts = getFilteredContracts(ctx).size();
            int maxPage = (totalContracts - 1) / 16;
            if (ctx.currentPage < maxPage) {
                ctx.currentPage++;
                refreshInventory(player, ctx);
            }
        }
        // Clic sur un contrat
        else {
            int[] contractSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
            for (int i = 0; i < contractSlots.length; i++) {
                if (slot == contractSlots[i]) {
                    handleContractClick(player, ctx, i);
                    break;
                }
            }
        }
    }

    /**
     * Gère le clic sur un contrat spécifique
     */
    private void handleContractClick(Player player, GUIContext ctx, int index) {
        List<Contract> contracts = getFilteredContracts(ctx);
        int contractIndex = (ctx.currentPage * 16) + index;

        if (contractIndex >= contracts.size()) return;

        Contract contract = contracts.get(contractIndex);
        player.closeInventory();

        if (plugin.getContractDetailsGUI() != null) {
            plugin.getContractDetailsGUI().openDetailsMenu(player, contract, ctx.companyName);
        }
    }

    /**
     * Rafraîchit l'inventaire
     */
    private void refreshInventory(Player player, GUIContext ctx) {
        Inventory inv = createInventory(ctx);
        player.openInventory(inv);
    }

    /**
     * Crée un ItemStack avec nom et lore
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
