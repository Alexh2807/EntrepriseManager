package com.gravityyfh.roleplaycity.backpack.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackManager;
import com.gravityyfh.roleplaycity.backpack.model.BackpackData;
import com.gravityyfh.roleplaycity.backpack.util.BackpackUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire de l'interface graphique des backpacks
 */
public class BackpackGUI {
    private final RoleplayCity plugin;
    private final BackpackManager backpackManager;
    private final BackpackItemManager itemManager;
    private final BackpackUtil backpackUtil;

    // Map pour suivre quel joueur a quel backpack ouvert
    // Key: Player UUID, Value: Backpack ItemStack dans l'inventaire
    private final Map<UUID, BackpackContext> openBackpacks;

    public BackpackGUI(RoleplayCity plugin, BackpackManager backpackManager,
                       BackpackItemManager itemManager, BackpackUtil backpackUtil) {
        this.plugin = plugin;
        this.backpackManager = backpackManager;
        this.itemManager = itemManager;
        this.backpackUtil = backpackUtil;
        this.openBackpacks = new HashMap<>();
    }

    /**
     * Ouvre un backpack pour un joueur
     *
     * @param player Le joueur
     * @param backpackItem L'item backpack
     * @return true si réussi, false sinon
     */
    public boolean openBackpack(Player player, ItemStack backpackItem) {
        if (!itemManager.isBackpack(backpackItem)) {
            player.sendMessage(ChatColor.RED + "Cet item n'est pas un backpack valide!");
            return false;
        }

        // Vérifier si le joueur a déjà un backpack ouvert
        if (hasOpenBackpack(player)) {
            player.sendMessage(ChatColor.RED + "Vous avez déjà un backpack ouvert!");
            return false;
        }

        // Charger les données du backpack
        BackpackData data = backpackManager.loadBackpack(backpackItem);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Impossible de charger les données du backpack!");
            return false;
        }

        // Créer l'inventaire
        String title = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("backpack.name", "&6Backpack"));
        Inventory inv = Bukkit.createInventory(null, data.size(), title);

        // Remplir l'inventaire avec le contenu
        ItemStack[] contents = data.contents();
        for (int i = 0; i < contents.length && i < inv.getSize(); i++) {
            if (contents[i] != null) {
                inv.setItem(i, contents[i].clone());
            }
        }

        // Enregistrer le contexte
        openBackpacks.put(player.getUniqueId(), new BackpackContext(backpackItem, data));

        // Ouvrir l'inventaire
        player.openInventory(inv);

        // Jouer le son d'ouverture
        backpackUtil.playOpenSound(player);

        return true;
    }

    /**
     * Ferme le backpack d'un joueur et sauvegarde les changements de manière asynchrone
     *
     * @param player Le joueur
     * @param inventory L'inventaire à sauvegarder
     * @return true si réussi, false sinon
     */
    public boolean closeBackpack(Player player, Inventory inventory) {
        UUID playerUUID = player.getUniqueId();
        BackpackContext context = openBackpacks.get(playerUUID);

        if (context == null) {
            return false;
        }

        // Récupérer le contenu de l'inventaire (SYNC - rapide)
        ItemStack[] contents = new ItemStack[context.data.size()];
        for (int i = 0; i < contents.length && i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null) {
                contents[i] = item.clone();
            }
        }

        // Créer les nouvelles données
        BackpackData newData = new BackpackData(
            context.data.uniqueId(),
            contents,
            context.data.creationTime(),
            context.data.size()
        );

        // Retirer immédiatement du tracking pour éviter les doubles ouvertures
        openBackpacks.remove(playerUUID);

        // Jouer le son immédiatement (meilleure UX)
        backpackUtil.playCloseSound(player);

        // ⚡ OPTIMISATION: Sauvegarder de manière ASYNCHRONE
        final ItemStack backpackItem = context.backpackItem;
        final BackpackData finalData = newData;
        final Player finalPlayer = player;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long startTime = System.nanoTime();

            try {
                // Sauvegarder le contenu
                boolean success = backpackManager.saveBackpackAsync(backpackItem, finalData);

                if (success) {
                    // Mettre à jour la lore
                    backpackUtil.updateBackpackLore(backpackItem, finalData);

                    // Mesurer la performance
                    long duration = (System.nanoTime() - startTime) / 1_000_000; // en ms
                    if (duration > 50) {
                        plugin.getLogger().warning("[Backpack] Sauvegarde lente: " + duration + "ms pour " + finalData.uniqueId());
                    }
                } else {
                    // Notifier le joueur en cas d'erreur (sync)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalPlayer.sendMessage(ChatColor.RED + "Erreur lors de la sauvegarde du backpack!");
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[Backpack] Erreur critique lors de la sauvegarde: " + e.getMessage());
                e.printStackTrace();
            }
        });

        return true;
    }

    /**
     * Vérifie si un joueur a un backpack ouvert
     *
     * @param player Le joueur
     * @return true si le joueur a un backpack ouvert, false sinon
     */
    public boolean hasOpenBackpack(Player player) {
        return openBackpacks.containsKey(player.getUniqueId());
    }

    /**
     * Vérifie si un inventaire est un backpack ouvert
     *
     * @param inventory L'inventaire à vérifier
     * @param player Le joueur qui a l'inventaire ouvert
     * @return true si c'est un backpack, false sinon
     */
    public boolean isBackpackInventory(Inventory inventory, Player player) {
        // Vérifier que l'inventaire n'a pas de holder (backpack custom inventory)
        if (inventory == null || inventory.getHolder() != null) {
            return false;
        }

        // Vérifier que le joueur a bien un backpack ouvert
        // Cette vérification suffit car on track tous les backpacks ouverts
        return hasOpenBackpack(player);
    }

    /**
     * Ferme le backpack d'un joueur sans sauvegarder (en cas de déconnexion)
     *
     * @param player Le joueur
     */
    public void closeBackpackWithoutSaving(Player player) {
        openBackpacks.remove(player.getUniqueId());
    }

    /**
     * Récupère le contexte du backpack ouvert d'un joueur
     *
     * @param player Le joueur
     * @return Le contexte, ou null si aucun backpack ouvert
     */
    public BackpackContext getOpenBackpackContext(Player player) {
        return openBackpacks.get(player.getUniqueId());
    }

    /**
         * Classe interne pour stocker le contexte d'un backpack ouvert
         */
        public record BackpackContext(ItemStack backpackItem, BackpackData data) {
    }
}
