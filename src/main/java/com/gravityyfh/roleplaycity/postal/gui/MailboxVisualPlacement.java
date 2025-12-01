package com.gravityyfh.roleplaycity.postal.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.data.MailboxType;
import com.gravityyfh.roleplaycity.postal.manager.MailboxManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.*;

/**
 * Système de placement visuel des boîtes aux lettres
 * Le joueur voit les 3 types de boîtes dans sa hotbar + un bouton annuler
 */
public class MailboxVisualPlacement implements Listener {
    private final RoleplayCity plugin;
    private final MailboxManager mailboxManager;

    // Stockage des inventaires sauvegardés : UUID -> Inventaire original
    private final Map<UUID, ItemStack[]> savedInventories;

    // Stockage des plots en cours de sélection : UUID -> Plot
    private final Map<UUID, Plot> activePlacements;

    // Stockage des types sélectionnés : UUID -> MailboxType
    private final Map<UUID, MailboxType> selectedTypes;

    public MailboxVisualPlacement(RoleplayCity plugin, MailboxManager mailboxManager) {
        this.plugin = plugin;
        this.mailboxManager = mailboxManager;
        this.savedInventories = new HashMap<>();
        this.activePlacements = new HashMap<>();
        this.selectedTypes = new HashMap<>();
    }

    /**
     * Active le mode de placement visuel pour un joueur
     */
    public void startVisualPlacement(Player player, Plot plot) {
        UUID playerId = player.getUniqueId();

        // Sauvegarder l'inventaire actuel
        ItemStack[] inventory = player.getInventory().getContents();
        savedInventories.put(playerId, inventory);

        // Vider l'inventaire
        player.getInventory().clear();

        // Créer les items de sélection dans la hotbar
        setupHotbar(player);

        // Stocker le plot
        activePlacements.put(playerId, plot);

        // Messages d'instruction
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.AQUA + "   📬 PLACEMENT DE BOÎTE AUX LETTRES");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "1. " + ChatColor.WHITE + "Choisissez une couleur dans votre barre");
        player.sendMessage(ChatColor.YELLOW + "2. " + ChatColor.WHITE + "Faites clic droit sur un bloc pour placer");
        player.sendMessage(ChatColor.YELLOW + "3. " + ChatColor.WHITE + "Utilisez " + ChatColor.RED + "❌ Annuler" + ChatColor.WHITE + " pour sortir");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "La boîte doit être placée sur votre terrain");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    /**
     * Configure la hotbar avec les items de sélection
     */
    private void setupHotbar(Player player) {
        // Slot 0 : Boîte Gris Clair
        ItemStack lightGray = createMailboxItem(MailboxType.LIGHT_GRAY);
        player.getInventory().setItem(0, lightGray);

        // Slot 1 : Boîte Bleu Clair
        ItemStack lightBlue = createMailboxItem(MailboxType.LIGHT_BLUE);
        player.getInventory().setItem(1, lightBlue);

        // Slot 2 : Boîte Orange
        ItemStack orange = createMailboxItem(MailboxType.ORANGE);
        player.getInventory().setItem(2, orange);

        // Slot 3 : Annuler
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "❌ Annuler");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("");
        cancelLore.add(ChatColor.GRAY + "Cliquez pour annuler");
        cancelLore.add(ChatColor.GRAY + "le placement de la boîte");
        cancelMeta.setLore(cancelLore);
        cancel.setItemMeta(cancelMeta);
        player.getInventory().setItem(3, cancel);

        // Mettre à jour l'inventaire
        player.updateInventory();
    }

    /**
     * Crée un item représentant une boîte aux lettres avec la vraie texture
     */
    private ItemStack createMailboxItem(MailboxType type) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // Appliquer la texture de la boîte aux lettres
        try {
            // Créer un profil de joueur avec l'UUID de la texture
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.fromString(type.getTextureId()), "mailbox");

            // Obtenir les textures du profil
            PlayerTextures textures = profile.getTextures();

            // Décoder la valeur base64 pour obtenir le JSON
            String textureValue = type.getTextureValue();
            byte[] decoded = Base64.getDecoder().decode(textureValue);
            String json = new String(decoded);

            // Parser le JSON pour extraire l'URL de la texture
            int urlStart = json.indexOf("\"url\":\"") + 7;
            if (urlStart > 6) {
                int urlEnd = json.indexOf("\"", urlStart);
                if (urlEnd > urlStart) {
                    String skinUrl = json.substring(urlStart, urlEnd);

                    // Définir l'URL de la skin
                    textures.setSkin(new URL(skinUrl));
                    profile.setTextures(textures);

                    // Appliquer le profil à l'item
                    meta.setOwnerProfile(profile);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'application de la texture pour " + type.name() + ": " + e.getMessage());
        }

        meta.setDisplayName(ChatColor.GOLD + "📬 " + type.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Une jolie boîte aux lettres");
        lore.add(ChatColor.GRAY + "pour recevoir du courrier");
        lore.add("");
        lore.add(ChatColor.YELLOW + "➜ Clic droit sur un bloc pour placer");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Écoute les interactions pour placer la boîte ou annuler
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Vérifier si le joueur est en mode placement
        if (!activePlacements.containsKey(playerId)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        // Annuler l'événement pour éviter les placements normaux
        event.setCancelled(true);

        // Vérifier si c'est le bouton Annuler
        if (item.getType() == Material.BARRIER) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                cancelPlacement(player);
                player.sendMessage(ChatColor.YELLOW + "Placement annulé.");
            }
            return;
        }

        // Vérifier si c'est un des items de boîte aux lettres (tête de joueur)
        MailboxType selectedType = getTypeFromItem(item);
        if (selectedType == null) return;

        // Stocker le type sélectionné
        selectedTypes.put(playerId, selectedType);

        // Vérifier que c'est un clic droit sur un bloc
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            player.sendMessage(ChatColor.RED + "Cliquez sur un bloc pour placer la boîte !");
            return;
        }

        // Récupérer la position du bloc cliqué
        org.bukkit.block.Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Position où la tête sera placée (au-dessus du bloc cliqué)
        org.bukkit.Location headLocation = clickedBlock.getLocation().add(0, 1, 0);

        // Récupérer le plot
        Plot plot = activePlacements.get(playerId);
        if (plot == null) {
            cancelPlacement(player);
            return;
        }

        // Tenter de placer la mailbox (ownerUuid retiré, géré par le manager via le plot)
        // Le joueur est passé pour orienter la boîte face à lui
        boolean success = mailboxManager.placeMailbox(plot, headLocation, selectedType, player);

        if (success) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Boîte aux lettres placée avec succès !");
            player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.AQUA + selectedType.getDisplayName());
            player.sendMessage(ChatColor.GRAY + "Vous pouvez maintenant recevoir du courrier.");
            player.sendMessage("");
        } else {
            player.sendMessage(ChatColor.RED + "✗ Impossible de placer la boîte ici !");
            player.sendMessage(ChatColor.YELLOW + "Assurez-vous de la placer dans les limites de votre terrain.");
            return;
        }

        // Nettoyer et restaurer l'inventaire
        endPlacement(player);
    }

    /**
     * Empêcher le joueur de jeter les items pendant le mode placement
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (activePlacements.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas jeter ces items !");
        }
    }

    /**
     * Nettoyer si le joueur se déconnecte pendant le placement
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (activePlacements.containsKey(playerId)) {
            restoreInventory(player);
            activePlacements.remove(playerId);
            selectedTypes.remove(playerId);
        }
    }

    /**
     * Détermine le type de boîte à partir de l'item (via son nom d'affichage)
     */
    private MailboxType getTypeFromItem(ItemStack item) {
        if (item.getType() != Material.PLAYER_HEAD) return null;
        if (!item.hasItemMeta()) return null;

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        // Vérifier chaque type
        for (MailboxType type : MailboxType.values()) {
            if (displayName.contains(type.getDisplayName())) {
                return type;
            }
        }

        return null;
    }

    /**
     * Annule le placement et restaure l'inventaire
     */
    public void cancelPlacement(Player player) {
        endPlacement(player);
    }

    /**
     * Termine le mode placement et restaure l'inventaire
     */
    private void endPlacement(Player player) {
        UUID playerId = player.getUniqueId();

        // Restaurer l'inventaire
        restoreInventory(player);

        // Nettoyer les données
        activePlacements.remove(playerId);
        selectedTypes.remove(playerId);
    }

    /**
     * Restaure l'inventaire sauvegardé du joueur
     */
    private void restoreInventory(Player player) {
        UUID playerId = player.getUniqueId();
        ItemStack[] savedInventory = savedInventories.remove(playerId);

        if (savedInventory != null) {
            player.getInventory().clear();
            player.getInventory().setContents(savedInventory);
            player.updateInventory();
        }
    }

    /**
     * Vérifie si un joueur est en mode placement
     */
    public boolean isInPlacementMode(UUID playerId) {
        return activePlacements.containsKey(playerId);
    }
}
