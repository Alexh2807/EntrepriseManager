package com.gravityyfh.roleplaycity.contract.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.contract.gui.ContractCreationGUI;
import com.gravityyfh.roleplaycity.contract.service.ContractService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener pour gérer les saisies via chat lors de la création de contrats et ouverture de litiges
 */
public class ContractChatListener implements Listener {

    private final RoleplayCity plugin;
    private final ContractService contractService;

    // Mode de saisie par joueur
    private final Map<UUID, InputMode> inputModes = new HashMap<>();

    // Contexte pour litiges
    private final Map<UUID, UUID> disputeContexts = new HashMap<>(); // PlayerUUID -> ContractID

    private enum InputMode {
        TITLE,          // Saisie du titre
        DESCRIPTION,    // Saisie de la description
        AMOUNT,         // Saisie du montant
        DISPUTE_REASON  // Saisie de la raison du litige
    }

    public ContractChatListener(RoleplayCity plugin, ContractService contractService) {
        this.plugin = plugin;
        this.contractService = contractService;
    }

    /**
     * Démarre la saisie d'un litige
     */
    public void startDisputeInput(Player player, UUID contractId) {
        inputModes.put(player.getUniqueId(), InputMode.DISPUTE_REASON);
        disputeContexts.put(player.getUniqueId(), contractId);
    }

    /**
     * Gère les messages de chat pour la saisie
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Vérifier si le joueur est en train de créer un contrat
        ContractCreationGUI creationGUI = plugin.getContractCreationGUI();
        if (creationGUI != null) {
            ContractCreationGUI.CreationContext ctx = creationGUI.getContext(playerUuid);

            if (ctx != null && ctx.currentStep == ContractCreationGUI.CreationStep.INPUT_DETAILS) {
                event.setCancelled(true);
                String message = event.getMessage();

                // Annulation
                if (message.equalsIgnoreCase("annuler")) {
                    creationGUI.removeContext(playerUuid);
                    inputModes.remove(playerUuid);
                    player.sendMessage(ChatColor.RED + "Création de contrat annulée.");
                    return;
                }

                // Déterminer quelle information on saisit
                InputMode mode = inputModes.getOrDefault(playerUuid, InputMode.TITLE);

                switch (mode) {
                    case TITLE:
                        if (message.length() > 50) {
                            player.sendMessage(ChatColor.RED + "Le titre est trop long (max 50 caractères).");
                            return;
                        }
                        ctx.title = message;
                        inputModes.put(playerUuid, InputMode.DESCRIPTION);
                        player.sendMessage(ChatColor.GREEN + "✔ Titre enregistré: " + message);
                        player.sendMessage("");
                        player.sendMessage(ChatColor.YELLOW + "Entrez la description du contrat:");
                        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");
                        break;

                    case DESCRIPTION:
                        if (message.length() > 200) {
                            player.sendMessage(ChatColor.RED + "La description est trop longue (max 200 caractères).");
                            return;
                        }
                        ctx.description = message;
                        inputModes.put(playerUuid, InputMode.AMOUNT);
                        player.sendMessage(ChatColor.GREEN + "✔ Description enregistrée.");
                        player.sendMessage("");
                        player.sendMessage(ChatColor.YELLOW + "Entrez le montant du contrat (en €):");
                        player.sendMessage(ChatColor.GRAY + "Exemple: 1000");
                        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");
                        break;

                    case AMOUNT:
                        try {
                            double amount = Double.parseDouble(message);
                            if (amount <= 0) {
                                player.sendMessage(ChatColor.RED + "Le montant doit être positif.");
                                return;
                            }
                            if (amount > 1000000) {
                                player.sendMessage(ChatColor.RED + "Le montant est trop élevé (max 1 000 000€).");
                                return;
                            }

                            ctx.amount = amount;
                            inputModes.remove(playerUuid);

                            player.sendMessage(ChatColor.GREEN + "✔ Montant enregistré: " + String.format("%.2f€", amount));
                            player.sendMessage("");
                            player.sendMessage(ChatColor.GREEN + "═══ Récapitulatif ═══");
                            player.sendMessage(ChatColor.GRAY + "Un menu de confirmation va s'ouvrir...");

                            // Ouvrir la confirmation (en sync)
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                creationGUI.openConfirmation(player);
                            });

                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "Montant invalide. Entrez un nombre valide.");
                            player.sendMessage(ChatColor.GRAY + "Exemple: 1000 ou 1000.50");
                        }
                        break;
                }
                return;
            }
        }

        // Vérifier si le joueur est en train d'ouvrir un litige
        if (inputModes.get(playerUuid) == InputMode.DISPUTE_REASON) {
            event.setCancelled(true);
            String message = event.getMessage();

            if (message.equalsIgnoreCase("annuler")) {
                inputModes.remove(playerUuid);
                disputeContexts.remove(playerUuid);
                player.sendMessage(ChatColor.RED + "Ouverture de litige annulée.");
                return;
            }

            if (message.length() > 200) {
                player.sendMessage(ChatColor.RED + "La raison est trop longue (max 200 caractères).");
                return;
            }

            UUID contractId = disputeContexts.get(playerUuid);
            if (contractId == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Contrat introuvable.");
                inputModes.remove(playerUuid);
                return;
            }

            // Ouvrir le litige (en sync)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (contractService.disputeContract(contractId, playerUuid, message)) {
                    player.sendMessage(ChatColor.GREEN + "✔ Litige ouvert avec succès.");
                    player.sendMessage(ChatColor.GRAY + "Un juge sera notifié pour résoudre le litige.");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible d'ouvrir un litige sur ce contrat.");
                }
            });

            inputModes.remove(playerUuid);
            disputeContexts.remove(playerUuid);
        }
    }
}
