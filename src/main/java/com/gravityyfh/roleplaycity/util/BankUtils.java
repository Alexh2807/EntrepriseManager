package com.gravityyfh.roleplaycity.util;

import com.gravityyfh.roleplaycity.RoleplayCity;
import de.lightplugins.economy.inventories.BankMainMenu;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;

public class BankUtils {

    private static final NamespacedKey BANK_ACCOUNT_KEY = new NamespacedKey(RoleplayCity.getInstance(), "has_bank_account");
    private static final double ACCOUNT_CREATION_COST = 250.0;

    public static boolean hasAccount(Player player) {
        return player.getPersistentDataContainer().has(BANK_ACCOUNT_KEY, PersistentDataType.BYTE);
    }

    public static void setHasAccount(Player player) {
        player.getPersistentDataContainer().set(BANK_ACCOUNT_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static void openBankFlow(Player player) {
        if (hasAccount(player)) {
            openLoadingGUI(player);
        } else {
            openCreationGUI(player);
        }
    }

    private static void openCreationGUI(Player player) {
        SmartInventory.builder()
                .id("bankCreation")
                .provider(new BankCreationProvider())
                .size(3, 9)
                .title("§8Création de compte")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private static void openLoadingGUI(Player player) {
        SmartInventory.builder()
                .id("bankLoading")
                .provider(new BankLoadingProvider())
                .size(3, 9)
                .title("§8Connexion bancaire...")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private static class BankCreationProvider implements InventoryProvider {

        @Override
        public void init(Player player, InventoryContents contents) {
            // Fond gris
            contents.fill(ClickableItem.empty(createItem(Material.GRAY_STAINED_GLASS_PANE, " ")));

            // Item de création
            ItemStack createItem = createItem(Material.EMERALD_BLOCK, "§a§lOuvrir un compte bancaire",
                    "§7Coût: §c" + ACCOUNT_CREATION_COST + "€",
                    "",
                    "§ecliquez pour ouvrir votre compte");

            contents.set(1, 4, ClickableItem.of(createItem, e -> {
                Economy eco = RoleplayCity.getEconomy();
                if (eco.getBalance(player) >= ACCOUNT_CREATION_COST) {
                    eco.withdrawPlayer(player, ACCOUNT_CREATION_COST);
                    setHasAccount(player);
                    player.sendMessage("§aCompte bancaire créé avec succès ! §7(-" + ACCOUNT_CREATION_COST + "€)");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    openLoadingGUI(player);
                } else {
                    player.sendMessage("§cVous n'avez pas assez d'argent (250€ requis).");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.closeInventory();
                }
            }));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private static class BankLoadingProvider implements InventoryProvider {
        private int tick = 0;

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));
        }

        @Override
        public void update(Player player, InventoryContents contents) {
            tick++;
            
            // Animation simple de chargement (barre verte qui avance)
            if (tick % 5 == 0) { // Toutes les 5 ticks (250ms) pour une animation 2.5x plus longue
                int step = (tick / 5) - 1;
                if (step >= 0 && step <= 8) {
                    contents.set(1, step, ClickableItem.empty(createItem(Material.LIME_STAINED_GLASS_PANE, "§aChargement...")));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f + (step * 0.1f));
                } else if (step == 9) {
                    // Fin du chargement
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    
                    // Ouvrir le vrai menu via une task sync pour éviter les conflits d'inventaire
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            BankMainMenu.INVENTORY.open(player);
                        }
                    }.runTask(RoleplayCity.getInstance());
                }
            }
        }
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
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
