package com.gravityyfh.roleplaycity.contract.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.contract.model.Contract;
import com.gravityyfh.roleplaycity.contract.model.ContractType;
import com.gravityyfh.roleplaycity.contract.service.ContractService;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Wizard de création de contrats
 * Étapes: Type (B2B/B2C) -> Sélection cible -> Saisie infos (via chat) -> Confirmation
 */
public class ContractCreationGUI implements Listener {

    private final RoleplayCity plugin;
    private final ContractService contractService;

    // Contexte de création par joueur
    private final Map<UUID, CreationContext> contexts = new HashMap<>();
    private final Map<UUID, Long> clickTimestamps = new HashMap<>();
    private static final long CLICK_DELAY_MS = 300;

    public enum CreationStep {
        SELECT_TYPE,        // Choisir B2B ou B2C
        SELECT_TARGET,      // Choisir l'entreprise ou le joueur
        INPUT_DETAILS,      // Saisie via chat (géré par ChatListener)
        CONFIRM             // Confirmation finale
    }

    /**
     * Contexte de création d'un contrat
     */
    public static class CreationContext {
        public String providerCompany;
        public UUID providerOwnerUuid;
        public CreationStep currentStep = CreationStep.SELECT_TYPE;
        public ContractType type;
        public String targetName; // Nom de l'entreprise (B2B) ou du joueur (B2C)
        public UUID targetUuid;   // UUID du gérant (B2B) ou du joueur (B2C)
        public String title;
        public String description;
        public double amount;
        public int validityDays = 7; // Par défaut 7 jours

        CreationContext(String providerCompany, UUID providerOwnerUuid) {
            this.providerCompany = providerCompany;
            this.providerOwnerUuid = providerOwnerUuid;
        }
    }

    public ContractCreationGUI(RoleplayCity plugin, ContractService contractService) {
        this.plugin = plugin;
        this.contractService = contractService;
    }

    /**
     * Ouvre le wizard de création pour une entreprise
     */
    public void openCreationWizard(Player player, String companyName) {
        CreationContext ctx = new CreationContext(companyName, player.getUniqueId());
        contexts.put(player.getUniqueId(), ctx);
        openStepSelectType(player, ctx);
    }

    /**
     * Récupère le contexte de création d'un joueur
     */
    public CreationContext getContext(UUID playerUuid) {
        return contexts.get(playerUuid);
    }

    /**
     * Supprime le contexte de création d'un joueur
     */
    public void removeContext(UUID playerUuid) {
        contexts.remove(playerUuid);
    }

    /**
     * Étape 1: Sélection du type de contrat
     */
    private void openStepSelectType(Player player, CreationContext ctx) {
        ctx.currentStep = CreationStep.SELECT_TYPE;

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Type de Contrat");

        // B2C
        ItemStack b2cItem = createItem(Material.PLAYER_HEAD,
                ChatColor.GREEN + "Contrat Particulier (B2C)",
                ChatColor.GRAY + "Proposer un contrat",
                ChatColor.GRAY + "à un joueur individuel");
        inv.setItem(11, b2cItem);

        // B2B
        ItemStack b2bItem = createItem(Material.WRITABLE_BOOK,
                ChatColor.BLUE + "Contrat Entreprise (B2B)",
                ChatColor.GRAY + "Proposer un contrat",
                ChatColor.GRAY + "à une autre entreprise");
        inv.setItem(15, b2bItem);

        // Annuler
        ItemStack cancelItem = createItem(Material.BARRIER,
                ChatColor.RED + "Annuler",
                ChatColor.GRAY + "Fermer sans créer");
        inv.setItem(22, cancelItem);

        player.openInventory(inv);
    }

    /**
     * Étape 2: Sélection de la cible
     */
    private void openStepSelectTarget(Player player, CreationContext ctx) {
        ctx.currentStep = CreationStep.SELECT_TARGET;

        String title = ctx.type == ContractType.B2B
                ? ChatColor.DARK_BLUE + "Sélectionner Entreprise"
                : ChatColor.DARK_BLUE + "Sélectionner Joueur";

        Inventory inv = Bukkit.createInventory(null, 54, title);

        if (ctx.type == ContractType.B2B) {
            // Lister toutes les entreprises sauf la sienne
            Collection<Entreprise> allCompanies = plugin.getEntrepriseManagerLogic().getEntreprises();
            List<Entreprise> targetCompanies = new ArrayList<>();

            for (Entreprise ent : allCompanies) {
                if (!ent.getNom().equalsIgnoreCase(ctx.providerCompany)) {
                    targetCompanies.add(ent);
                }
            }

            for (int i = 0; i < targetCompanies.size() && i < 45; i++) {
                Entreprise ent = targetCompanies.get(i);
                ItemStack item = createItem(Material.EMERALD,
                        ChatColor.GREEN + ent.getNom(),
                        ChatColor.GRAY + "Ville: " + ent.getVille(),
                        ChatColor.GRAY + "Gérant: " + ent.getGerant(),
                        "",
                        ChatColor.YELLOW + "Cliquez pour sélectionner");
                inv.setItem(i, item);
            }
        } else {
            // Lister tous les joueurs connectés (ou récents)
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            int slot = 0;

            for (Player target : players) {
                if (slot >= 45) break;
                if (target.getUniqueId().equals(player.getUniqueId())) continue; // Pas soi-même

                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GREEN + target.getName());
                    meta.setOwningPlayer(target);
                    List<String> lore = Arrays.asList(
                            "",
                            ChatColor.YELLOW + "Cliquez pour sélectionner"
                    );
                    meta.setLore(lore);
                    skull.setItemMeta(meta);
                }
                inv.setItem(slot++, skull);
            }
        }

        // Bouton retour
        ItemStack backItem = createItem(Material.ARROW,
                ChatColor.YELLOW + "◀ Retour",
                ChatColor.GRAY + "Changer le type");
        inv.setItem(49, backItem);

        player.openInventory(inv);
    }

    /**
     * Étape 3: Demande de saisie des détails (via chat)
     */
    public void startInputDetails(Player player) {
        CreationContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        ctx.currentStep = CreationStep.INPUT_DETAILS;
        player.closeInventory();

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "═══ Création de Contrat ═══");
        player.sendMessage(ChatColor.YELLOW + "Entrez le titre du contrat:");
        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");
    }

    /**
     * Étape 4: Confirmation finale
     */
    public void openConfirmation(Player player) {
        CreationContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        ctx.currentStep = CreationStep.CONFIRM;

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Confirmer Contrat");

        // Récapitulatif
        ItemStack recap = createItem(Material.PAPER,
                ChatColor.GOLD + "Récapitulatif",
                ChatColor.GRAY + "Type: " + ChatColor.WHITE + (ctx.type == ContractType.B2B ? "B2B" : "B2C"),
                ChatColor.GRAY + "Cible: " + ChatColor.WHITE + ctx.targetName,
                ChatColor.GRAY + "Titre: " + ChatColor.WHITE + ctx.title,
                ChatColor.GRAY + "Montant: " + ChatColor.GOLD + ctx.amount + "€",
                ChatColor.GRAY + "Validité: " + ChatColor.WHITE + ctx.validityDays + " jours");
        inv.setItem(13, recap);

        // Confirmer
        ItemStack confirmItem = createItem(Material.LIME_DYE,
                ChatColor.GREEN + "✔ Confirmer",
                ChatColor.GRAY + "Envoyer la proposition");
        inv.setItem(11, confirmItem);

        // Annuler
        ItemStack cancelItem = createItem(Material.RED_DYE,
                ChatColor.RED + "✖ Annuler",
                ChatColor.GRAY + "Abandonner la création");
        inv.setItem(15, cancelItem);

        player.openInventory(inv);
    }

    /**
     * Finalise la création du contrat
     */
    private void finalizeContract(Player player) {
        CreationContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        Contract contract;

        if (ctx.type == ContractType.B2B) {
            contract = contractService.createContractB2B(
                    ctx.providerCompany,
                    ctx.providerOwnerUuid,
                    ctx.targetName,
                    ctx.targetUuid,
                    ctx.title,
                    ctx.description,
                    ctx.amount,
                    ctx.validityDays
            );
        } else {
            contract = contractService.createContractB2C(
                    ctx.providerCompany,
                    ctx.providerOwnerUuid,
                    ctx.targetUuid,
                    ctx.title,
                    ctx.description,
                    ctx.amount,
                    ctx.validityDays
            );
        }

        if (contract != null) {
            player.sendMessage(ChatColor.GREEN + "✔ Contrat créé avec succès!");
            player.sendMessage(ChatColor.GRAY + "ID: " + contract.getId());
        } else {
            player.sendMessage(ChatColor.RED + "✖ Erreur lors de la création du contrat.");
        }

        contexts.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Bloque le drag d'items dans les menus de creation de contrat
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title.contains("Type de Contrat") || title.contains("Sélectionner") || title.contains("Confirmer Contrat")) {
            event.setCancelled(true);
        }
    }

    /**
     * Gère les clics dans les inventaires du wizard
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!title.contains("Type de Contrat") && !title.contains("Sélectionner") && !title.contains("Confirmer Contrat")) {
            return;
        }

        event.setCancelled(true);

        // Anti double-clic
        long now = System.currentTimeMillis();
        Long lastClick = clickTimestamps.get(player.getUniqueId());
        if (lastClick != null && (now - lastClick) < CLICK_DELAY_MS) return;
        clickTimestamps.put(player.getUniqueId(), now);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        CreationContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return;

        // Étape SELECT_TYPE
        if (ctx.currentStep == CreationStep.SELECT_TYPE) {
            if (event.getSlot() == 11) { // B2C
                ctx.type = ContractType.B2C;
                openStepSelectTarget(player, ctx);
            } else if (event.getSlot() == 15) { // B2B
                ctx.type = ContractType.B2B;
                openStepSelectTarget(player, ctx);
            } else if (event.getSlot() == 22) { // Annuler
                contexts.remove(player.getUniqueId());
                player.closeInventory();
            }
        }
        // Étape SELECT_TARGET
        else if (ctx.currentStep == CreationStep.SELECT_TARGET) {
            if (event.getSlot() == 49) { // Retour
                openStepSelectType(player, ctx);
            } else if (event.getSlot() < 45) {
                if (ctx.type == ContractType.B2B) {
                    Collection<Entreprise> allCompanies = plugin.getEntrepriseManagerLogic().getEntreprises();
                    List<Entreprise> targetCompanies = new ArrayList<>();
                    for (Entreprise ent : allCompanies) {
                        if (!ent.getNom().equalsIgnoreCase(ctx.providerCompany)) {
                            targetCompanies.add(ent);
                        }
                    }

                    if (event.getSlot() < targetCompanies.size()) {
                        Entreprise selected = targetCompanies.get(event.getSlot());
                        ctx.targetName = selected.getNom();
                        ctx.targetUuid = UUID.fromString(selected.getGerantUUID());
                        startInputDetails(player);
                    }
                } else {
                    // B2C: récupérer le joueur sélectionné
                    if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                        String playerName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                        Player target = Bukkit.getPlayer(playerName);
                        if (target != null) {
                            ctx.targetName = target.getName();
                            ctx.targetUuid = target.getUniqueId();
                            startInputDetails(player);
                        }
                    }
                }
            }
        }
        // Étape CONFIRM
        else if (ctx.currentStep == CreationStep.CONFIRM) {
            if (event.getSlot() == 11) { // Confirmer
                finalizeContract(player);
            } else if (event.getSlot() == 15) { // Annuler
                contexts.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Création de contrat annulée.");
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
