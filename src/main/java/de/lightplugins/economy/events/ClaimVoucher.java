/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.EconomyResponse
 *  org.bukkit.NamespacedKey
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerSwapHandItemsEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataContainer
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package de.lightplugins.economy.events;

import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PersistentDataPaths;
import de.lightplugins.economy.master.Main;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class ClaimVoucher
implements Listener {
    // Protection contre le double-clic rapide
    private final java.util.Set<java.util.UUID> processingPlayers = new java.util.HashSet<>();

    @EventHandler
    public void onRightClickItem(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem();

        if ((event.getAction().equals((Object)Action.RIGHT_CLICK_AIR) || event.getAction().equals((Object)Action.RIGHT_CLICK_BLOCK)) && itemStack != null) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta == null) {
                return;
            }
            if (event.getHand() == null) {
                return;
            }
            if (event.getHand().equals((Object)EquipmentSlot.OFF_HAND)) {
                return;
            }

            PersistentDataContainer data = itemMeta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(Main.getInstance.asPlugin(), PersistentDataPaths.MONEY_VALUE.getType());

            if (key.getKey().equalsIgnoreCase(PersistentDataPaths.MONEY_VALUE.getType())) {
                if (!data.has(key, PersistentDataType.DOUBLE)) {
                    return;
                }

                // Protection contre le double-clic
                if (processingPlayers.contains(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);

                if (!Main.voucher.getConfig().getBoolean("voucher.enable")) {
                    Main.util.sendMessage(player, MessagePath.VoucherDisabled.getPath());
                    return;
                }

                // Marquer le joueur comme en traitement
                processingPlayers.add(player.getUniqueId());

                double amount = (Double)data.get(key, PersistentDataType.DOUBLE);
                int currentInvSlot = player.getInventory().getHeldItemSlot();
                ItemStack originalItem = player.getInventory().getItem(currentInvSlot);

                // V\u00e9rifier que l'item est toujours le m\u00eame
                if (originalItem == null || !originalItem.equals(itemStack)) {
                    processingPlayers.remove(player.getUniqueId());
                    return;
                }

                // D\u00e9truire l'item AVANT de donner l'argent
                if (itemStack.getAmount() > 1) {
                    itemStack.setAmount(itemStack.getAmount() - 1);
                    player.getInventory().setItem(currentInvSlot, itemStack);
                } else {
                    player.getInventory().setItem(currentInvSlot, null);
                }

                // Donner l'argent APRES destruction
                EconomyResponse economyResponse = Main.economyImplementer.depositPlayer(player.getName(), amount);
                if (economyResponse.transactionSuccess()) {
                    Main.util.sendMessage(player, MessagePath.VoucherCollected.getPath().replace("#amount#", String.valueOf(amount)).replace("#currency#", Main.util.getCurrency(amount)));
                } else {
                    // ROLLBACK: Rendre l'item si la transaction \u00e9choue
                    Main.util.sendMessage(player, MessagePath.TransactionFailed.getPath().replace("#reason#", economyResponse.errorMessage));
                    if (itemStack.getAmount() > 1) {
                        itemStack.setAmount(itemStack.getAmount() + 1);
                    }
                    player.getInventory().setItem(currentInvSlot, itemStack);
                }

                // D\u00e9bloquer apr\u00e8s un court d\u00e9lai
                org.bukkit.Bukkit.getScheduler().runTaskLater(Main.getInstance.asPlugin(), () -> {
                    processingPlayers.remove(player.getUniqueId());
                }, 5L); // 5 ticks = 0.25 secondes
            }
        }
    }

    @EventHandler
    public void onSecondHand(InventoryClickEvent event) {
        if (event.getCurrentItem() != null) {
            NamespacedKey key;
            ItemStack itemStack = event.getCursor();
            assert (itemStack != null);
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta == null) {
                return;
            }
            PersistentDataContainer data = itemMeta.getPersistentDataContainer();
            if (!data.has(key = new NamespacedKey(Main.getInstance.asPlugin(), PersistentDataPaths.MONEY_VALUE.getType()), PersistentDataType.DOUBLE)) {
                return;
            }
            if (event.getSlot() == 40) {
                event.setCancelled(true);
                Main.util.sendMessage((Player)event.getWhoClicked(), MessagePath.VoucherOffHanad.getPath());
                event.getWhoClicked().closeInventory();
            }
        }
    }

    @EventHandler
    public void offHandSwap(PlayerSwapHandItemsEvent event) {
        NamespacedKey key;
        ItemStack itemStack = event.getOffHandItem();
        assert (itemStack != null);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        PersistentDataContainer data = itemMeta.getPersistentDataContainer();
        if (!data.has(key = new NamespacedKey(Main.getInstance.asPlugin(), PersistentDataPaths.MONEY_VALUE.getType()), PersistentDataType.DOUBLE)) {
            return;
        }
        event.setCancelled(true);
        Main.util.sendMessage(event.getPlayer(), MessagePath.VoucherOffHanad.getPath());
    }
}

