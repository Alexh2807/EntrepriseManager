package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

/**
 * GUI pour gerer les autorisations parentales sur un terrain.
 * Permet aux proprietaires/locataires d'autoriser d'autres joueurs a utiliser leur terrain.
 * Les autorisations sont liees au parent : si le parent perd le terrain, les enfants perdent leurs autorisations.
 */
public class PlotAuthorizationGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;

    private static final String MENU_TITLE = ChatColor.DARK_AQUA + "Joueurs Autorises";

    // Joueurs en attente de saisie de nom
    private final Map<UUID, AuthorizationContext> pendingAdditions;

    public PlotAuthorizationGUI(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.pendingAdditions = new HashMap<>();
    }

    /**
     * Contexte pour l'ajout d'un joueur autorise
     */
    private static class AuthorizationContext {
        final String townName;
        final String chunkKey; // Format: "chunkX:chunkZ:worldName"
        final boolean isRenter; // true si locataire, false si proprietaire

        AuthorizationContext(String townName, String chunkKey, boolean isRenter) {
            this.townName = townName;
            this.chunkKey = chunkKey;
            this.isRenter = isRenter;
        }
    }

    /**
     * Ouvre le menu de gestion des autorisations pour un terrain
     * @param player Le joueur qui ouvre le menu
     * @param plot Le terrain a gerer
     * @param isRenter true si le joueur accede en tant que locataire, false en tant que proprietaire
     */
    public void openAuthorizationMenu(Player player, Plot plot, boolean isRenter) {
        Town town = townManager.getTown(plot.getTownName());
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        // SLOT 4: Information
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Joueurs Autorises");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "-------------------");
        infoLore.add(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE + plot.getDisplayInfo());
        infoLore.add("");
        if (isRenter) {
            infoLore.add(ChatColor.GRAY + "En tant que " + ChatColor.GOLD + "Locataire");
            infoLore.add(ChatColor.GRAY + "Les joueurs que vous autorisez");
            infoLore.add(ChatColor.GRAY + "perdront leur acces si vous");
            infoLore.add(ChatColor.GRAY + "quittez ce terrain.");
        } else {
            infoLore.add(ChatColor.GRAY + "En tant que " + ChatColor.GOLD + "Proprietaire");
            infoLore.add(ChatColor.GRAY + "Les joueurs que vous autorisez");
            infoLore.add(ChatColor.GRAY + "perdront leur acces si vous");
            infoLore.add(ChatColor.GRAY + "vendez ce terrain.");
        }
        infoLore.add(ChatColor.GRAY + "-------------------");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // SLOTS 10-14: Joueurs autorises (max 5)
        Set<UUID> authorizedPlayers = isRenter
            ? plot.getRenterAuthorizedPlayers()
            : plot.getOwnerAuthorizedPlayers();

        int slot = 10;
        for (UUID authorizedUuid : authorizedPlayers) {
            if (slot > 14) break; // Max 5 joueurs affiches

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(authorizedUuid);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Joueur inconnu";

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(offlinePlayer);
            skullMeta.setDisplayName(ChatColor.GREEN + playerName);
            List<String> playerLore = new ArrayList<>();
            playerLore.add(ChatColor.GRAY + "Joueur autorise");
            playerLore.add("");
            playerLore.add(ChatColor.YELLOW + "Droits: " + ChatColor.WHITE + "Construire, Interagir");
            playerLore.add("");
            playerLore.add(ChatColor.RED + "Cliquez pour retirer l'autorisation");
            skullMeta.setLore(playerLore);
            playerHead.setItemMeta(skullMeta);

            // Stocker l'UUID dans les metadonnees de l'item pour le retrouver au clic
            inv.setItem(slot, playerHead);
            slot++;
        }

        // Remplir les slots vides avec des vitres grises
        for (int i = slot; i <= 14; i++) {
            ItemStack emptySlot = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta emptyMeta = emptySlot.getItemMeta();
            emptyMeta.setDisplayName(ChatColor.GRAY + "Emplacement vide");
            emptySlot.setItemMeta(emptyMeta);
            inv.setItem(i, emptySlot);
        }

        // SLOT 20: Ajouter un joueur
        boolean canAddMore = isRenter ? plot.canRenterAddMore() : plot.canOwnerAddMore();
        int currentCount = authorizedPlayers.size();
        int maxPlayers = Plot.getMaxAuthorizedPlayers();

        ItemStack addItem;
        if (canAddMore) {
            addItem = new ItemStack(Material.EMERALD);
            ItemMeta addMeta = addItem.getItemMeta();
            addMeta.setDisplayName(ChatColor.GREEN + "Ajouter un joueur");
            List<String> addLore = new ArrayList<>();
            addLore.add(ChatColor.GRAY + "Autoriser un nouveau joueur");
            addLore.add(ChatColor.GRAY + "a utiliser ce terrain.");
            addLore.add("");
            addLore.add(ChatColor.YELLOW + "Places: " + ChatColor.WHITE + currentCount + "/" + maxPlayers);
            addLore.add("");
            addLore.add(ChatColor.GREEN + "Cliquez pour ajouter");
            addMeta.setLore(addLore);
            addItem.setItemMeta(addMeta);
        } else {
            addItem = new ItemStack(Material.BARRIER);
            ItemMeta addMeta = addItem.getItemMeta();
            addMeta.setDisplayName(ChatColor.RED + "Limite atteinte");
            List<String> addLore = new ArrayList<>();
            addLore.add(ChatColor.GRAY + "Vous avez atteint la limite");
            addLore.add(ChatColor.GRAY + "de " + maxPlayers + " joueurs autorises.");
            addLore.add("");
            addLore.add(ChatColor.YELLOW + "Retirez un joueur pour");
            addLore.add(ChatColor.YELLOW + "pouvoir en ajouter un autre.");
            addMeta.setLore(addLore);
            addItem.setItemMeta(addMeta);
        }
        inv.setItem(20, addItem);

        // SLOT 22: Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        // Sauvegarder le contexte dans les metadonnees du joueur
        String chunkKey = plot.getChunkX() + ":" + plot.getChunkZ() + ":" + plot.getWorldName();
        player.setMetadata("plotAuth_townName", new FixedMetadataValue(plugin, plot.getTownName()));
        player.setMetadata("plotAuth_chunkKey", new FixedMetadataValue(plugin, chunkKey));
        player.setMetadata("plotAuth_isRenter", new FixedMetadataValue(plugin, isRenter));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Recuperer le contexte
        if (!player.hasMetadata("plotAuth_townName")) return;
        String townName = player.getMetadata("plotAuth_townName").get(0).asString();
        String chunkKey = player.getMetadata("plotAuth_chunkKey").get(0).asString();
        boolean isRenter = player.getMetadata("plotAuth_isRenter").get(0).asBoolean();

        Town town = townManager.getTown(townName);
        if (town == null) return;

        String[] parts = chunkKey.split(":");
        Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        if (plot == null) return;

        // BOUTON: Retour
        if (clicked.getType() == Material.ARROW) {
            player.closeInventory();
            clearPlayerMetadata(player);
            // Retour au menu precedent (sera gere par le menu appelant)
            return;
        }

        // BOUTON: Ajouter un joueur
        if (clicked.getType() == Material.EMERALD) {
            player.closeInventory();
            startAddPlayerProcess(player, townName, chunkKey, isRenter);
            return;
        }

        // BOUTON: Tete de joueur (retirer autorisation)
        if (clicked.getType() == Material.PLAYER_HEAD) {
            if (clicked.getItemMeta() instanceof SkullMeta skullMeta) {
                OfflinePlayer targetPlayer = skullMeta.getOwningPlayer();
                if (targetPlayer != null) {
                    removeAuthorization(player, plot, targetPlayer.getUniqueId(), isRenter);
                    // Rafraichir le menu
                    openAuthorizationMenu(player, plot, isRenter);
                }
            }
            return;
        }
    }

    /**
     * Demarre le processus d'ajout d'un joueur autorise
     */
    private void startAddPlayerProcess(Player player, String townName, String chunkKey, boolean isRenter) {
        pendingAdditions.put(player.getUniqueId(), new AuthorizationContext(townName, chunkKey, isRenter));

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Entrez le nom du joueur a autoriser:");
        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour annuler)");
        player.sendMessage("");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        AuthorizationContext context = pendingAdditions.remove(player.getUniqueId());

        if (context == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        // Annulation
        if (input.equalsIgnoreCase("annuler") || input.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "Ajout annule.");
            // Rouvrir le menu
            Bukkit.getScheduler().runTask(plugin, () -> {
                Town town = townManager.getTown(context.townName);
                if (town != null) {
                    String[] parts = context.chunkKey.split(":");
                    Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    if (plot != null) {
                        openAuthorizationMenu(player, plot, context.isRenter);
                    }
                }
            });
            return;
        }

        // Rechercher le joueur
        @SuppressWarnings("deprecation")
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(input);

        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            player.sendMessage(ChatColor.RED + "Joueur '" + input + "' introuvable.");
            // Rouvrir le menu
            Bukkit.getScheduler().runTask(plugin, () -> {
                Town town = townManager.getTown(context.townName);
                if (town != null) {
                    String[] parts = context.chunkKey.split(":");
                    Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    if (plot != null) {
                        openAuthorizationMenu(player, plot, context.isRenter);
                    }
                }
            });
            return;
        }

        // Executer sur le thread principal
        Bukkit.getScheduler().runTask(plugin, () -> {
            Town town = townManager.getTown(context.townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
                return;
            }

            String[] parts = context.chunkKey.split(":");
            Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Terrain introuvable.");
                return;
            }

            // Verifications
            UUID targetUuid = targetPlayer.getUniqueId();

            // Ne pas s'autoriser soi-meme
            if (targetUuid.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous autoriser vous-meme!");
                openAuthorizationMenu(player, plot, context.isRenter);
                return;
            }

            // Ne pas autoriser le proprietaire ou locataire (ils ont deja acces)
            if (plot.isOwnedBy(targetUuid) || plot.isRentedBy(targetUuid)) {
                player.sendMessage(ChatColor.RED + "Ce joueur a deja acces au terrain!");
                openAuthorizationMenu(player, plot, context.isRenter);
                return;
            }

            // Verifier si deja autorise
            if (context.isRenter) {
                if (plot.isAuthorizedByRenter(targetUuid)) {
                    player.sendMessage(ChatColor.RED + "Ce joueur est deja autorise!");
                    openAuthorizationMenu(player, plot, context.isRenter);
                    return;
                }
            } else {
                if (plot.isAuthorizedByOwner(targetUuid)) {
                    player.sendMessage(ChatColor.RED + "Ce joueur est deja autorise!");
                    openAuthorizationMenu(player, plot, context.isRenter);
                    return;
                }
            }

            // Ajouter l'autorisation
            boolean success;
            if (context.isRenter) {
                success = plot.addRenterAuthorizedPlayer(targetUuid);
            } else {
                success = plot.addOwnerAuthorizedPlayer(targetUuid);
            }

            if (success) {
                townManager.saveTownsNow();
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "Joueur autorise avec succes!");
                player.sendMessage(ChatColor.YELLOW + "Joueur: " + ChatColor.WHITE + targetPlayer.getName());
                player.sendMessage(ChatColor.GRAY + "Ce joueur peut maintenant construire");
                player.sendMessage(ChatColor.GRAY + "et interagir sur ce terrain.");
                player.sendMessage("");
            } else {
                player.sendMessage(ChatColor.RED + "Limite de joueurs autorises atteinte!");
            }

            openAuthorizationMenu(player, plot, context.isRenter);
        });
    }

    /**
     * Retire une autorisation
     */
    private void removeAuthorization(Player player, Plot plot, UUID targetUuid, boolean isRenter) {
        boolean success;
        if (isRenter) {
            success = plot.removeRenterAuthorizedPlayer(targetUuid);
        } else {
            success = plot.removeOwnerAuthorizedPlayer(targetUuid);
        }

        if (success) {
            townManager.saveTownsNow();
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Joueur inconnu";

            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Autorisation retiree pour: " + ChatColor.WHITE + targetName);
            player.sendMessage(ChatColor.GRAY + "Ce joueur n'a plus acces au terrain.");
            player.sendMessage("");
        }
    }

    /**
     * Nettoie les metadonnees du joueur
     */
    private void clearPlayerMetadata(Player player) {
        player.removeMetadata("plotAuth_townName", plugin);
        player.removeMetadata("plotAuth_chunkKey", plugin);
        player.removeMetadata("plotAuth_isRenter", plugin);
    }
}
