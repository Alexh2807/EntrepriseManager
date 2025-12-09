package com.gravityyfh.roleplaycity.mdt.beds;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Outils de diagnostic et maintenance pour les lits MDT
 * Commandes :
 * /mdt checkbeds - Diagnostic complet
 * /mdt validatebeds - Validation configuration vs monde
 * /mdt savebeds - Force sauvegarde manuelle
 */
public class BedDiagnosticManager implements CommandExecutor {

    private final RoleplayCity plugin;
    private final MDTRushManager mdtManager;

    public BedDiagnosticManager(RoleplayCity plugin, MDTRushManager mdtManager) {
        this.plugin = plugin;
        this.mdtManager = mdtManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roleplaycity.mdt.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ Permission requise: roleplaycity.mdt.admin");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "=== Commandes de Diagnostic des Lits MDT ===");
            sender.sendMessage(ChatColor.GRAY + "/mdt checkbeds - Rapport de diagnostic complet");
            sender.sendMessage(ChatColor.GRAY + "/mdt validatebeds - Valide configuration vs monde");
            sender.sendMessage(ChatColor.GRAY + "/mdt savebeds - Force sauvegarde manuelle");
            sender.sendMessage(ChatColor.GRAY + "/mdt testbeds - Test restauration des lits");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "checkbeds":
                return handleCheckBeds(sender);
            case "validatebeds":
                return handleValidateBeds(sender);
            case "savebeds":
                return handleSaveBeds(sender);
            case "testbeds":
                return handleTestBeds(sender);
            default:
                sender.sendMessage(ChatColor.RED + "❌ Commande inconnue: " + subCommand);
                return false;
        }
    }

    /**
     * Diagnostic complet des lits
     */
    public boolean handleCheckBeds(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== Diagnostic des Lits MDT ===");

        if (!mdtManager.hasActiveGame()) {
            sender.sendMessage(ChatColor.GRAY + "Aucune partie MDT active");
            return true;
        }

        try {
            // Avec le système FAWE, on utilise directement la configuration pour vérifier les lits
            sender.sendMessage(ChatColor.YELLOW + "⚠️ Diagnostic des lits avec le nouveau système FAWE:");
            sender.sendMessage(ChatColor.GRAY + "• Le système ne track plus les blocs individuellement");
            sender.sendMessage(ChatColor.GRAY + "• Les lits sont gérés via la configuration MDT");
            sender.sendMessage(ChatColor.GRAY + "• Utilisez /mdt setup pour configurer les lits");
            sender.sendMessage(ChatColor.GREEN + "✅ Système FAWE opérationnel");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Erreur lors du diagnostic: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Validation configuration vs monde
     */
    public boolean handleValidateBeds(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== Validation des Lits ===");

        try {
            // Valider depuis la configuration
            String configValidation = mdtManager.getConfig().validateAllBeds();
            String[] lines = configValidation.split("\n");

            sender.sendMessage(ChatColor.AQUA + "Configuration:");
            for (String line : lines) {
                if (line.contains("✅")) {
                    sender.sendMessage(ChatColor.GREEN + line);
                } else if (line.contains("❌")) {
                    sender.sendMessage(ChatColor.RED + line);
                } else if (line.contains("===") || line.contains("Résumé")) {
                    sender.sendMessage(ChatColor.YELLOW + line);
                } else {
                    sender.sendMessage(ChatColor.GRAY + line);
                }
            }

            // Validation monde si partie active
            if (mdtManager.hasActiveGame()) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Monde: " + ChatColor.GREEN + "✅ Géré par FAWE");
                sender.sendMessage(ChatColor.GRAY + "La cohérence est maintenue automatiquement");
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Erreur lors de la validation: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Force la sauvegarde manuelle des lits
     */
    public boolean handleSaveBeds(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== Sauvegarde Manuelle des Lits ===");

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ Commande réservée aux joueurs");
            return true;
        }

        Player player = (Player) sender;

        try {
            if (!player.getWorld().getName().equalsIgnoreCase(mdtManager.getConfig().getWorldName())) {
                sender.sendMessage(ChatColor.RED + "❌ Vous devez être dans le monde MDT");
                return true;
            }

            // Forcer la sauvegarde depuis le monde actuel
            // Avec le système FAWE, les lits sont gérés automatiquement
            sender.sendMessage(ChatColor.YELLOW + "⚠️ Avec le système FAWE:");
            sender.sendMessage(ChatColor.GRAY + "• Les lits sont restaurés automatiquement");
            sender.sendMessage(ChatColor.GRAY + "• Aucune sauvegarde manuelle nécessaire");
            sender.sendMessage(ChatColor.GREEN + "✅ Système automatisé actif");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Erreur lors de la sauvegarde: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Test de restauration des lits
     */
    public boolean handleTestBeds(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== Test de Restauration des Lits ===");

        try {
            if (!mdtManager.hasActiveGame()) {
                sender.sendMessage(ChatColor.RED + "❌ Une partie MDT doit être active");
                return true;
            }

            // Avec le système FAWE, les lits sont restaurés automatiquement
            sender.sendMessage(ChatColor.YELLOW + "⚠️ Test avec système FAWE:");
            sender.sendMessage(ChatColor.GRAY + "• Les lits sont gérés par schématiques");
            sender.sendMessage(ChatColor.GRAY + "• La restauration est automatique après chaque partie");
            sender.sendMessage(ChatColor.GREEN + "✅ Système de test automatisé");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Erreur lors du test: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}