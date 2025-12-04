package com.gravityyfh.roleplaycity.contract.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.contract.model.Contract;
import com.gravityyfh.roleplaycity.contract.model.ContractStatus;
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

/**
 * GUI de détails d'un contrat avec actions disponibles
 */
public class ContractDetailsGUI implements Listener {

    private final RoleplayCity plugin;
    private final ContractService contractService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Contexte: qui regarde quel contrat
    private final Map<UUID, DetailContext> contexts = new HashMap<>();
    private final Map<UUID, Long> clickTimestamps = new HashMap<>();
    private static final long CLICK_DELAY_MS = 300;

    private static class DetailContext {
        UUID contractId;
        String companyContext; // Entreprise depuis laquelle on consulte

        DetailContext(UUID contractId, String companyContext) {
            this.contractId = contractId;
            this.companyContext = companyContext;
        }
    }

    public ContractDetailsGUI(RoleplayCity plugin, ContractService contractService) {
        this.plugin = plugin;
        this.contractService = contractService;
    }

    /**
     * Ouvre le menu de détails d'un contrat
     */
    public void openDetailsMenu(Player player, Contract contract, String companyContext) {
        contexts.put(player.getUniqueId(), new DetailContext(contract.getId(), companyContext));

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Détails Contrat");

        // Bordures
        fillBorders(inv);

        // Informations du contrat
        displayContractInfo(inv, contract);

        // Actions disponibles
        displayActions(inv, contract, player);

        player.openInventory(inv);
    }

    /**
     * Affiche les informations du contrat
     */
    private void displayContractInfo(Inventory inv, Contract contract) {
        // Titre et statut
        ChatColor statusColor = getStatusColor(contract.getStatus());
        ItemStack titleItem = createItem(Material.PAPER,
                ChatColor.GOLD + contract.getTitle(),
                ChatColor.GRAY + "Statut: " + statusColor + contract.getStatus().name());
        inv.setItem(13, titleItem);

        // Fournisseur
        ItemStack providerItem = createItem(Material.EMERALD,
                ChatColor.GREEN + "Fournisseur",
                ChatColor.GRAY + contract.getProviderCompany());
        inv.setItem(11, providerItem);

        // Client
        String clientName = contract.getType().isB2B()
                ? contract.getClientCompany()
                : contract.getClientDisplayName(Bukkit.getServer());
        ItemStack clientItem = createItem(Material.PLAYER_HEAD,
                ChatColor.BLUE + "Client",
                ChatColor.GRAY + clientName,
                ChatColor.GRAY + "Type: " + (contract.getType().isB2B() ? "B2B" : "B2C"));
        inv.setItem(15, clientItem);

        // Montant
        ItemStack amountItem = createItem(Material.GOLD_INGOT,
                ChatColor.GOLD + "Montant",
                ChatColor.YELLOW + String.format("%.2f€", contract.getAmount()),
                contract.isFundsEscrowed()
                        ? ChatColor.GREEN + "✔ Fonds sécurisés"
                        : ChatColor.GRAY + "En attente de paiement");
        inv.setItem(22, amountItem);

        // Description
        List<String> descLines = new ArrayList<>();
        descLines.add(ChatColor.GRAY + "Description:");
        if (contract.getDescription() != null && !contract.getDescription().isEmpty()) {
            // Découper la description en lignes de 40 caractères max
            String desc = contract.getDescription();
            int maxLen = 40;
            for (int i = 0; i < desc.length(); i += maxLen) {
                descLines.add(ChatColor.WHITE + desc.substring(i, Math.min(i + maxLen, desc.length())));
            }
        } else {
            descLines.add(ChatColor.GRAY + "(Aucune description)");
        }

        ItemStack descItem = createItem(Material.BOOK,
                ChatColor.YELLOW + "Description",
                descLines.toArray(new String[0]));
        inv.setItem(31, descItem);

        // Dates
        List<String> dateLore = new ArrayList<>();
        dateLore.add(ChatColor.GRAY + "Proposition: " + ChatColor.WHITE + contract.getProposalDate().format(formatter));
        dateLore.add(ChatColor.GRAY + "Expiration: " + ChatColor.WHITE + contract.getExpirationDate().format(formatter));

        if (contract.getResponseDate() != null) {
            dateLore.add(ChatColor.GRAY + "Réponse: " + ChatColor.WHITE + contract.getResponseDate().format(formatter));
        }
        if (contract.getEndDate() != null) {
            dateLore.add(ChatColor.GRAY + "Fin: " + ChatColor.WHITE + contract.getEndDate().format(formatter));
        }

        ItemStack dateItem = createItem(Material.CLOCK,
                ChatColor.YELLOW + "Dates",
                dateLore.toArray(new String[0]));
        inv.setItem(29, dateItem);

        // Litige (si applicable)
        if (contract.getStatus() == ContractStatus.LITIGE || contract.getStatus() == ContractStatus.RESOLU) {
            List<String> litigeLore = new ArrayList<>();
            if (contract.getDisputeReason() != null) {
                litigeLore.add(ChatColor.GRAY + "Raison:");
                litigeLore.add(ChatColor.WHITE + contract.getDisputeReason());
            }
            if (contract.getDisputeVerdict() != null) {
                litigeLore.add(ChatColor.GRAY + "Verdict:");
                litigeLore.add(ChatColor.WHITE + contract.getDisputeVerdict());
            }

            ItemStack litigeItem = createItem(Material.BARRIER,
                    ChatColor.RED + "Litige",
                    litigeLore.toArray(new String[0]));
            inv.setItem(33, litigeItem);
        }
    }

    /**
     * Affiche les actions disponibles selon le contexte
     */
    private void displayActions(Inventory inv, Contract contract, Player player) {
        UUID playerUuid = player.getUniqueId();
        DetailContext ctx = contexts.get(playerUuid);

        // Bouton Retour (toujours présent)
        ItemStack backItem = createItem(Material.ARROW,
                ChatColor.YELLOW + "◀ Retour",
                ChatColor.GRAY + "Retour à la liste");
        inv.setItem(49, backItem);

        // Actions selon le statut et le rôle
        if (contract.getStatus() == ContractStatus.PROPOSE) {
            // Si le joueur est le client, il peut accepter/refuser
            if (canPlayerAccept(contract, player)) {
                ItemStack acceptItem = createItem(Material.LIME_DYE,
                        ChatColor.GREEN + "✔ Accepter",
                        ChatColor.GRAY + "Accepter ce contrat",
                        ChatColor.GRAY + "Les fonds seront sécurisés");
                inv.setItem(45, acceptItem);

                ItemStack rejectItem = createItem(Material.RED_DYE,
                        ChatColor.RED + "✖ Refuser",
                        ChatColor.GRAY + "Refuser ce contrat");
                inv.setItem(53, rejectItem);
            }
        } else if (contract.getStatus() == ContractStatus.ACCEPTE) {
            // Si le joueur est le fournisseur, il peut terminer
            if (contract.isProvider(playerUuid)) {
                ItemStack completeItem = createItem(Material.EMERALD,
                        ChatColor.GREEN + "✔ Marquer Terminé",
                        ChatColor.GRAY + "Service effectué",
                        ChatColor.GRAY + "Les fonds seront libérés");
                inv.setItem(45, completeItem);
            }

            // Si le joueur est le client, il peut ouvrir un litige
            if (canPlayerAccept(contract, player)) {
                ItemStack disputeItem = createItem(Material.BARRIER,
                        ChatColor.RED + "⚠ Ouvrir un Litige",
                        ChatColor.GRAY + "Signaler un problème",
                        ChatColor.GRAY + "Nécessitera un juge");
                inv.setItem(53, disputeItem);
            }
        }
    }

    /**
     * Vérifie si le joueur peut accepter/refuser ce contrat
     */
    private boolean canPlayerAccept(Contract contract, Player player) {
        UUID playerUuid = player.getUniqueId();

        if (contract.getType().isB2B()) {
            // Pour B2B, le joueur doit être le gérant de l'entreprise cliente
            return contract.getClientOwnerUuid() != null
                    && contract.getClientOwnerUuid().equals(playerUuid);
        } else {
            // Pour B2C, le joueur doit être le client
            return contract.getClientUuid() != null
                    && contract.getClientUuid().equals(playerUuid);
        }
    }

    /**
     * Bloque le drag d'items dans le menu details contrat
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title.equals(ChatColor.DARK_BLUE + "Détails Contrat")) {
            event.setCancelled(true);
        }
    }

    /**
     * Gère les clics dans le menu de détails
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!title.equals(ChatColor.DARK_BLUE + "Détails Contrat")) return;

        event.setCancelled(true);

        // Anti double-clic
        long now = System.currentTimeMillis();
        Long lastClick = clickTimestamps.get(player.getUniqueId());
        if (lastClick != null && (now - lastClick) < CLICK_DELAY_MS) return;
        clickTimestamps.put(player.getUniqueId(), now);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        DetailContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        Contract contract = contractService.getContract(ctx.contractId);
        if (contract == null) {
            player.sendMessage(ChatColor.RED + "Contrat introuvable.");
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();

        // Retour
        if (slot == 49) {
            player.closeInventory();
            if (plugin.getContractManagementGUI() != null) {
                plugin.getContractManagementGUI().openContractMenu(player, ctx.companyContext);
            }
        }
        // Accepter
        else if (slot == 45 && contract.getStatus() == ContractStatus.PROPOSE) {
            if (contractService.acceptContract(contract.getId(), player.getUniqueId())) {
                player.sendMessage(ChatColor.GREEN + "✔ Contrat accepté!");
                player.closeInventory();
            } else {
                player.sendMessage(ChatColor.RED + "Impossible d'accepter ce contrat.");
            }
        }
        // Refuser
        else if (slot == 53 && contract.getStatus() == ContractStatus.PROPOSE) {
            if (contractService.rejectContract(contract.getId(), player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "Contrat refusé.");
                player.closeInventory();
            }
        }
        // Terminer
        else if (slot == 45 && contract.getStatus() == ContractStatus.ACCEPTE && contract.isProvider(player.getUniqueId())) {
            if (contractService.completeContract(contract.getId(), player.getUniqueId())) {
                player.sendMessage(ChatColor.GREEN + "✔ Contrat terminé! Fonds libérés.");
                player.closeInventory();
            }
        }
        // Litige
        else if (slot == 53 && contract.getStatus() == ContractStatus.ACCEPTE && canPlayerAccept(contract, player)) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Entrez la raison du litige:");
            player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");
            // Le ChatListener gérera la saisie
            if (plugin.getContractChatListener() != null) {
                plugin.getContractChatListener().startDisputeInput(player, contract.getId());
            }
        }
    }

    /**
     * Récupère la couleur selon le statut
     */
    private ChatColor getStatusColor(ContractStatus status) {
        switch (status) {
            case PROPOSE: return ChatColor.YELLOW;
            case ACCEPTE: return ChatColor.GREEN;
            case TERMINE: return ChatColor.DARK_GREEN;
            case LITIGE: return ChatColor.RED;
            case RESOLU: return ChatColor.BLUE;
            case EXPIRE: return ChatColor.DARK_GRAY;
            case REJETE: return ChatColor.DARK_RED;
            default: return ChatColor.WHITE;
        }
    }

    /**
     * Remplit les bordures
     */
    private void fillBorders(Inventory inv) {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        int[] borders = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 53};
        for (int slot : borders) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, glass);
            }
        }
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
