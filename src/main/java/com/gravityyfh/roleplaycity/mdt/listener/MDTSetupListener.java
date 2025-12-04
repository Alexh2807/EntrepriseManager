package com.gravityyfh.roleplaycity.mdt.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig.MerchantType;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
import com.gravityyfh.roleplaycity.mdt.gui.MDTBedSelectionGUI;
import com.gravityyfh.roleplaycity.mdt.gui.MDTMerchantsListGUI;
import com.gravityyfh.roleplaycity.mdt.gui.MDTNeutralBedsGUI;
import com.gravityyfh.roleplaycity.mdt.gui.MDTSetupGUI;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MDTSetupListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTConfig config;
    private final MDTNeutralBedsGUI neutralBedsGUI;
    private final MDTMerchantsListGUI merchantsListGUI;

    public MDTSetupListener(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.neutralBedsGUI = new MDTNeutralBedsGUI(plugin, config);
        this.merchantsListGUI = new MDTMerchantsListGUI(plugin, config);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MDTSetupGUI)) {
            return;
        }

        event.setCancelled(true); // Empêcher de prendre les items

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Location loc = player.getLocation();

        // Actions en fonction du slot cliqué (nouveau layout)
        switch (event.getRawSlot()) {
            // === LITS (active le mode de placement) ===
            case 10: // Lit Rouge - active le mode
                MDTBedSelectionGUI.setPendingType(player, "RED");
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "➤ " + ChatColor.WHITE + "Mode Lit ROUGE activé!");
                player.sendMessage(ChatColor.GRAY + "  Fais " + ChatColor.YELLOW + "clic droit sur un lit" + ChatColor.GRAY + " dans le monde MDT");
                player.sendMessage(ChatColor.GRAY + "  pour le définir comme lit + spawn de l'équipe " + ChatColor.RED + "ROUGE");
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                break;
            case 11: // Lit Bleu - active le mode
                MDTBedSelectionGUI.setPendingType(player, "BLUE");
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(ChatColor.BLUE + "➤ " + ChatColor.WHITE + "Mode Lit BLEU activé!");
                player.sendMessage(ChatColor.GRAY + "  Fais " + ChatColor.YELLOW + "clic droit sur un lit" + ChatColor.GRAY + " dans le monde MDT");
                player.sendMessage(ChatColor.GRAY + "  pour le définir comme lit + spawn de l'équipe " + ChatColor.BLUE + "BLEUE");
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                break;

            // === GESTION LITS NEUTRES ===
            case 12: // Gérer Lits Neutres
                player.closeInventory();
                neutralBedsGUI.open(player);
                break;

            // === SPAWNS ===
            case 14: // Spawn Rouge
                config.setTeamSpawn(MDTTeam.RED, loc);
                player.sendMessage("§c✓ Spawn Équipe Rouge défini !");
                playSound(player);
                break;
            case 15: // Spawn Lobby
                config.setLobbySpawn(loc);
                player.sendMessage("§e✓ Spawn Lobby défini !");
                playSound(player);
                break;
            case 16: // Spawn Bleu
                config.setTeamSpawn(MDTTeam.BLUE, loc);
                player.sendMessage("§9✓ Spawn Équipe Bleue défini !");
                playSound(player);
                break;

            // === GÉNÉRATEUR ===
            case 22: // Générateur UNIVERSEL
                config.addGeneratorLocation(loc.getBlock().getLocation().add(0.5, 0, 0.5), "UNIVERSAL");
                player.sendMessage("§6✓ Générateur placé ! (Brique/Fer/Or/Diamant)");
                playSound(player);
                break;

            // === MARCHANDS ===
            case 27: // GLOBAL
                config.addMerchantLocation(loc, MerchantType.GLOBAL);
                player.sendMessage("§e✓ Petit Marchand (Global) ajouté !");
                playSound(player);
                break;
            case 28: // BLOCKS
                config.addMerchantLocation(loc, MerchantType.BLOCKS);
                player.sendMessage("§7✓ Grand Marchand (Blocs) ajouté !");
                playSound(player);
                break;
            case 29: // WEAPONS
                config.addMerchantLocation(loc, MerchantType.WEAPONS);
                player.sendMessage("§c✓ Grand Marchand (Armes) ajouté !");
                playSound(player);
                break;
            case 30: // ARMOR
                config.addMerchantLocation(loc, MerchantType.ARMOR);
                player.sendMessage("§9✓ Grand Marchand (Armures) ajouté !");
                playSound(player);
                break;
            case 31: // SPECIAL
                config.addMerchantLocation(loc, MerchantType.SPECIAL);
                player.sendMessage("§d✓ Grand Marchand (Spécial) ajouté !");
                playSound(player);
                break;

            // === GESTION MARCHANDS ===
            case 32: // Gérer Marchands
                player.closeInventory();
                merchantsListGUI.open(player);
                break;

            // === SUPPRESSION ===
            case 35: // Clear ALL
                config.clearGenerators();
                config.clearMerchants();
                player.sendMessage("§c✓ Tous les générateurs et marchands ont été supprimés.");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
                break;
        }
    }

    private void playSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }
}
