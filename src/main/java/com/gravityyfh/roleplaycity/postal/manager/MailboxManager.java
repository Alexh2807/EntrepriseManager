package com.gravityyfh.roleplaycity.postal.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.data.Mailbox;
import com.gravityyfh.roleplaycity.postal.data.MailboxType;
import com.gravityyfh.roleplaycity.town.data.Plot;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.*;
import java.util.logging.Level;

/**
 * Gestionnaire simplifié des boîtes aux lettres
 * REFONTE: Les données de mailbox sont maintenant intégrées dans Plot
 * Ce manager gère uniquement les opérations de placement/suppression/interaction
 */
public class MailboxManager {
    private final RoleplayCity plugin;

    public MailboxManager(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    /**
     * Place une nouvelle boîte aux lettres sur un terrain
     * Remplace automatiquement l'ancienne si elle existe (conserve le courrier)
     * Note: Les terrains PUBLIC ne peuvent pas avoir de boîte aux lettres
     */
    public boolean placeMailbox(Plot plot, Location headLocation, MailboxType type, Player placer) {
        // Vérifier que le terrain n'est pas PUBLIC (routes, places publiques)
        if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PUBLIC) {
            if (placer != null) {
                placer.sendMessage(org.bukkit.ChatColor.RED + "Impossible de placer une boîte aux lettres sur un terrain public.");
            }
            return false;
        }

        // Vérifier que le terrain a un numéro (sécurité supplémentaire)
        if (plot.getPlotNumber() == null) {
            plugin.getLogger().warning("Tentative de placement de mailbox sur un terrain sans numéro");
            return false;
        }

        // Vérifier que la position de la tête est dans le terrain
        if (!isLocationInPlot(headLocation, plot)) {
            plugin.getLogger().warning("Tentative de placement de mailbox hors du terrain");
            return false;
        }

        // Sauvegarder le contenu de l'ancienne mailbox si elle existe
        Map<Integer, ItemStack> savedItems = null;
        if (plot.hasMailbox()) {
            Mailbox oldMailbox = plot.getMailbox();
            savedItems = oldMailbox.items();

            // Supprimer l'ancien bloc de tête
            oldMailbox.headLocation().getBlock().setType(Material.AIR);

            plugin.getLogger().info("Déplacement de mailbox - " + savedItems.size() + " items sauvegardés");
        }

        // Placer la tête (player head) à la nouvelle position
        Block headBlock = headLocation.getBlock();
        headBlock.setType(Material.PLAYER_HEAD);
        org.bukkit.block.Skull skull = (org.bukkit.block.Skull) headBlock.getState();

        // Orienter la boîte aux lettres face au joueur qui la place
        if (placer != null) {
            org.bukkit.block.BlockFace facing = getPlayerFacing(placer);
            skull.setRotation(facing);
        }

        // Appliquer la texture custom
        applySkullTexture(skull, type);
        skull.update();

        // Créer l'objet Mailbox avec inventaire virtuel
        Mailbox mailbox;
        if (savedItems != null && !savedItems.isEmpty()) {
            // Restaurer le contenu sauvegardé
            mailbox = new Mailbox(type, headLocation, savedItems);
            plugin.getLogger().info("Contenu restauré - " + savedItems.size() + " items");
        } else {
            // Nouvelle mailbox vide
            mailbox = new Mailbox(type, headLocation);
        }

        // Enregistrer la mailbox dans le plot
        plot.setMailbox(mailbox);

        // Sauvegarder les données du plot (qui contient maintenant la mailbox)
        plugin.getTownManager().saveTownsNow();

        return true;
    }

    /**
     * Supprime la boîte aux lettres d'un terrain
     * ATTENTION: Le courrier sera perdu !
     */
    public void removeMailbox(Plot plot) {
        if (!plot.hasMailbox()) {
            return;
        }

        Mailbox mailbox = plot.getMailbox();

        // Supprimer le bloc physique de la tête
        mailbox.headLocation().getBlock().setType(Material.AIR);

        // Retirer la mailbox du plot
        plot.removeMailbox();

        // Sauvegarder
        plugin.getTownManager().saveTownsNow();

        plugin.getLogger().info("Mailbox supprimée du plot " + plot.getIdentifier());
    }

    /**
     * Ouvre l'inventaire de la boîte aux lettres pour un joueur
     */
    public void openMailbox(Player player, Plot plot) {
        if (!plot.hasMailbox()) {
            player.sendMessage(ChatColor.RED + "Ce terrain n'a pas de boîte aux lettres.");
            return;
        }

        Mailbox mailbox = plot.getMailbox();

        // Créer un inventaire Bukkit à partir des items stockés
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "📬 Boîte aux Lettres");

        // Charger les items depuis la Map
        Map<Integer, ItemStack> items = mailbox.items();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();
            if (slot >= 0 && slot < 27 && item != null) {
                inv.setItem(slot, item.clone());
            }
        }

        // Marquer le joueur comme étant en train de consulter cette mailbox
        // (utilisé par le listener pour sauvegarder les changements)
        player.setMetadata("viewing_mailbox_plot", new org.bukkit.metadata.FixedMetadataValue(plugin, plot));

        player.openInventory(inv);
    }

    /**
     * Sauvegarde le contenu d'un inventaire de mailbox dans le Plot
     * Appelé par le listener quand l'inventaire est fermé
     */
    public void saveMailboxInventory(Plot plot, Inventory inventory) {
        if (!plot.hasMailbox()) {
            return;
        }

        Mailbox mailbox = plot.getMailbox();

        // Sauvegarder le contenu de l'inventaire dans la Map
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            mailbox.setItem(i, item);
        }

        // Sauvegarder les données du plot
        plugin.getTownManager().saveTownsNow();
    }

    /**
     * Récupère la mailbox à une location donnée (en cherchant dans tous les plots)
     */
    public Mailbox getMailboxAt(Location location) {
        String locationKey = Mailbox.locationToKey(location);

        // Chercher dans tous les towns et plots
        for (var town : plugin.getTownManager().getAllTowns()) {
            for (var plot : town.getPlots().values()) {
                if (plot.hasMailbox()) {
                    Mailbox mailbox = plot.getMailbox();
                    if (mailbox.getLocationKey().equals(locationKey)) {
                        return mailbox;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Récupère le plot qui contient une mailbox à une location donnée
     */
    public Plot getPlotByMailboxLocation(Location location) {
        String locationKey = Mailbox.locationToKey(location);

        // Chercher dans tous les towns et plots
        for (var town : plugin.getTownManager().getAllTowns()) {
            for (var plot : town.getPlots().values()) {
                if (plot.hasMailbox()) {
                    Mailbox mailbox = plot.getMailbox();
                    if (mailbox.getLocationKey().equals(locationKey)) {
                        return plot;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Vérifie si un terrain a une mailbox
     */
    public boolean hasMailbox(Plot plot) {
        return plot.hasMailbox();
    }

    /**
     * Vérifie si une location est dans les limites d'un terrain
     */
    private boolean isLocationInPlot(Location location, Plot plot) {
        Chunk chunk = location.getChunk();
        return plot.containsChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    /**
     * Applique une texture de skin à un crâne de joueur en utilisant l'API Bukkit PlayerProfile
     */
    private void applySkullTexture(org.bukkit.block.Skull skull, MailboxType type) {
        try {
            // Créer un profil de joueur avec l'UUID de la texture
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.fromString(type.getTextureId()), "mailbox");

            // Obtenir les textures du profil
            PlayerTextures textures = profile.getTextures();

            // Décoder la valeur base64 pour obtenir le JSON
            String textureValue = type.getTextureValue();
            byte[] decoded = java.util.Base64.getDecoder().decode(textureValue);
            String json = new String(decoded);

            // Parser le JSON pour extraire l'URL de la texture
            // Format: {"textures":{"SKIN":{"url":"http://..."}}}
            String urlPattern = "\"url\":\"";
            int urlStart = json.indexOf(urlPattern);
            if (urlStart != -1) {
                urlStart += urlPattern.length();
                int urlEnd = json.indexOf("\"", urlStart);
                if (urlEnd != -1) {
                    String skinUrl = json.substring(urlStart, urlEnd);

                    // Définir l'URL de la skin
                    textures.setSkin(new URL(skinUrl));
                    profile.setTextures(textures);

                    // Appliquer le profil au skull
                    skull.setOwnerProfile(profile);
                    skull.update();
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossible d'appliquer la texture custom à la boîte aux lettres", e);
        }
    }

    /**
     * Détermine la direction cardinale vers laquelle regarde un joueur (N/S/E/W)
     */
    private org.bukkit.block.BlockFace getPlayerFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        // Normaliser le yaw entre 0 et 360
        yaw = (yaw % 360 + 360) % 360;

        if (yaw >= 315 || yaw < 45) {
            return org.bukkit.block.BlockFace.SOUTH;
        } else if (yaw >= 45 && yaw < 135) {
            return org.bukkit.block.BlockFace.WEST;
        } else if (yaw >= 135 && yaw < 225) {
            return org.bukkit.block.BlockFace.NORTH;
        } else {
            return org.bukkit.block.BlockFace.EAST;
        }
    }

    /**
     * Supprime toutes les mailboxes d'une ville
     * Utilisé quand une ville est supprimée
     */
    public void removeAllMailboxesFromTown(String townName) {
        var town = plugin.getTownManager().getTown(townName);
        if (town == null) {
            return;
        }

        int count = 0;
        for (var plot : town.getPlots().values()) {
            if (plot.hasMailbox()) {
                Mailbox mailbox = plot.getMailbox();
                mailbox.headLocation().getBlock().setType(Material.AIR);
                plot.removeMailbox();
                count++;
            }
        }

        if (count > 0) {
            plugin.getTownManager().saveTownsNow();
            plugin.getLogger().info("Supprimé " + count + " boîte(s) aux lettres de la ville " + townName);
        }
    }
}
