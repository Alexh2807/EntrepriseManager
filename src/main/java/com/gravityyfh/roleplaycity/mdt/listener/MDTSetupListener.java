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

    // Gestionnaire de schématiques (nouveau système FAWE)
    private com.gravityyfh.roleplaycity.mdt.schematic.MDTSchematicManager schematicManager;

    public MDTSetupListener(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.neutralBedsGUI = new MDTNeutralBedsGUI(plugin, config);
        this.merchantsListGUI = new MDTMerchantsListGUI(plugin, config);

        // Initialiser le gestionnaire de schématiques
        this.schematicManager = new com.gravityyfh.roleplaycity.mdt.schematic.MDTSchematicManager(plugin, config);
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

        // Actions en fonction du slot cliqué (nouveau layout avec snapshots)
        switch (event.getRawSlot()) {
            // === SNAPSHOT & PROTECTION ===
            case 10: // Outils Sélection FAWE
                if (schematicManager.hasFAWE()) {
                    schematicManager.giveSelectionTools(player);
                } else {
                    player.sendMessage("§cFAWE n'est pas disponible sur ce serveur !");
                }
                playSound(player);
                break;

            case 11: // Sauvegarder Schématique
                player.closeInventory();
                player.sendMessage("§e§lSAUVEGARDE DE SCHÉMATIQUE");
                player.sendMessage("§7La sauvegarde va commencer...");
                schematicManager.saveMDTRegion().thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a✅ Schématique sauvegardée avec succès !");
                        player.sendMessage("§7Cette schématique sera utilisée pour la restauration automatique en fin de partie.");
                    } else {
                        player.sendMessage("§c❌ Erreur lors de la sauvegarde !");
                    }
                });
                playSound(player);
                break;

            case 12: // Restaurer Schématique
                player.closeInventory();
                schematicManager.restoreMDTRegion().thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a✅ Schématique restaurée avec succès !");
                    } else {
                        player.sendMessage("§c❌ Erreur lors de la restauration ou aucune schématique trouvée !");
                    }
                });
                playSound(player);
                break;

            case 13: // Protéger la Zone
                // Toggle protection
                boolean currentStatus = plugin.getMDTRushManager() != null &&
                    plugin.getMDTRushManager().getRegionManager() != null &&
                    plugin.getMDTRushManager().getRegionManager().isProtectionEnabled();

                boolean newStatus = !currentStatus;
                if (plugin.getMDTRushManager() != null && plugin.getMDTRushManager().getRegionManager() != null) {
                    if (newStatus) {
                        plugin.getMDTRushManager().getRegionManager().enableProtection("Protection activée manuellement depuis GUI");
                    } else {
                        plugin.getMDTRushManager().getRegionManager().disableProtection();
                    }
                    player.sendMessage("§d§lPROTECTION DE ZONE");
                    player.sendMessage(newStatus ? "§a✅ Protection activée" : "§c❌ Protection désactivée");
                } else {
                    player.sendMessage("§c❌ Gestionnaire de protection non disponible !");
                }
                playSound(player);
                break;

            case 14: // Lister Snapshots
                player.closeInventory();
                player.sendMessage("§e§lSNAPSHOTS DISPONIBLES");
                java.io.File[] schematics = schematicManager.listSchematics();
                if (schematics != null && schematics.length > 0) {
                    for (java.io.File file : schematics) {
                        player.sendMessage("§7- §f" + file.getName());
                    }
                } else {
                    player.sendMessage("§7Aucune schématique trouvée. Utilise §e💾§7 pour sauvegarder.");
                }
                playSound(player);
                break;

            // === LITS (active le mode de placement) ===
            case 28: // Lit Rouge - active le mode
                MDTBedSelectionGUI.setPendingType(player, "RED");
                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "➤ " + ChatColor.WHITE + "Mode Lit ROUGE activé!");
                player.sendMessage(ChatColor.GRAY + "  Fais " + ChatColor.YELLOW + "clic droit sur un lit" + ChatColor.GRAY + " dans le monde MDT");
                player.sendMessage(ChatColor.GRAY + "  pour le définir comme lit + spawn de l'équipe " + ChatColor.RED + "ROUGE");
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                break;

            case 29: // Lit Bleu - active le mode
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
            case 30: // Gérer Lits Neutres
                player.closeInventory();
                neutralBedsGUI.open(player);
                break;

            // === SPAWNS ===
            case 32: // Spawn Rouge
                config.setTeamSpawn(MDTTeam.RED, loc);
                player.sendMessage("§c✓ Spawn Équipe Rouge défini !");
                playSound(player);
                break;

            case 33: // Spawn Lobby
                config.setLobbySpawn(loc);
                player.sendMessage("§e✓ Spawn Lobby défini !");
                playSound(player);
                break;
            case 34: // Spawn Bleu
                config.setTeamSpawn(MDTTeam.BLUE, loc);
                player.sendMessage("§9✓ Spawn Équipe Bleue défini !");
                playSound(player);
                break;

            // === GÉNÉRATEUR ===
            case 40: // Générateur UNIVERSEL
                config.addGeneratorLocation(loc.getBlock().getLocation().add(0.5, 0, 0.5), "UNIVERSAL");
                player.sendMessage("§6✓ Générateur placé ! (Brique/Fer/Or/Diamant)");
                playSound(player);
                break;

            // === MARCHANDS ===
            case 45: // GLOBAL
                config.addMerchantLocation(loc, MerchantType.GLOBAL);
                player.sendMessage("§e✓ Petit Marchand (Global) ajouté !");
                playSound(player);
                break;
            case 46: // BLOCKS
                config.addMerchantLocation(loc, MerchantType.BLOCKS);
                player.sendMessage("§7✓ Grand Marchand (Blocs) ajouté !");
                playSound(player);
                break;
            case 47: // WEAPONS
                config.addMerchantLocation(loc, MerchantType.WEAPONS);
                player.sendMessage("§c✓ Grand Marchand (Armes) ajouté !");
                playSound(player);
                break;
            case 48: // ARMOR
                config.addMerchantLocation(loc, MerchantType.ARMOR);
                player.sendMessage("§9✓ Grand Marchand (Armures) ajouté !");
                playSound(player);
                break;
            case 49: // SPECIAL
                config.addMerchantLocation(loc, MerchantType.SPECIAL);
                player.sendMessage("§d✓ Grand Marchand (Spécial) ajouté !");
                playSound(player);
                break;

            // === GESTION MARCHANDS ===
            case 50: // Gérer Marchands
                player.closeInventory();
                merchantsListGUI.open(player);
                break;

            // === SUPPRESSION ===
            case 53: // Clear ALL
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
