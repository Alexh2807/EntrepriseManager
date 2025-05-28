package com.gravityyfh.entreprisemanager; // Assurez-vous que le package est correct

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable; // Pour les opérations synchronisées si nécessaire

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level; // Pour les logs

public class ChatListener implements Listener {

    private final EntrepriseManager plugin;
    private final EntrepriseGUI entrepriseGUI; // Si le ChatListener en a besoin

    // Enum pour définir le type d'entrée attendue
    private enum InputType {
        DEPOSIT_AMOUNT,
        WITHDRAW_AMOUNT,
        RENAME_ENTREPRISE_NEW_NAME
        // Ajoutez d'autres types si nécessaire
    }

    // Classe interne pour stocker le contexte de l'entrée attendue
    private static class PlayerInputContext {
        final InputType inputType;
        final String entrepriseNom; // Pour stocker le nom de l'entreprise concernée
        // Ajoutez d'autres données contextuelles si besoin

        public PlayerInputContext(InputType inputType, String entrepriseNom) {
            this.inputType = inputType;
            this.entrepriseNom = entrepriseNom;
        }
    }

    // Map pour suivre les joueurs en attente d'une saisie et le contexte
    private final Map<UUID, PlayerInputContext> playersWaitingForInput = new HashMap<>();

    public ChatListener(EntrepriseManager plugin, EntrepriseGUI entrepriseGUI) {
        this.plugin = plugin;
        this.entrepriseGUI = entrepriseGUI;
        // Enregistrez ce listener si ce n'est pas déjà fait dans EntrepriseManager
        // plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Vérifie si un joueur est actuellement en attente d'une saisie dans le chat.
     * C'est la méthode que EntrepriseGUI utilisera.
     *
     * @param playerUUID L'UUID du joueur à vérifier.
     * @return true si le joueur est en attente, false sinon.
     */
    public boolean isPlayerWaitingForInput(UUID playerUUID) {
        return playersWaitingForInput.containsKey(playerUUID);
    }

    // Méthodes pour mettre un joueur en état d'attente (appelées par EntrepriseGUI ou EntrepriseCommandHandler)

    public void attendreMontantDepot(Player player, String nomEntreprise) {
        playersWaitingForInput.put(player.getUniqueId(), new PlayerInputContext(InputType.DEPOSIT_AMOUNT, nomEntreprise));
        player.sendMessage("§eVeuillez entrer le montant à déposer dans le chat. Tapez 'annuler' pour abandonner.");
        // Vous pourriez ajouter un timeout ici si vous le souhaitez
    }

    public void attendreMontantRetrait(Player player, String nomEntreprise) {
        playersWaitingForInput.put(player.getUniqueId(), new PlayerInputContext(InputType.WITHDRAW_AMOUNT, nomEntreprise));
        player.sendMessage("§eVeuillez entrer le montant à retirer dans le chat. Tapez 'annuler' pour abandonner.");
    }

    public void attendreNouveauNomEntreprise(Player player, String ancienNomEntreprise) {
        playersWaitingForInput.put(player.getUniqueId(), new PlayerInputContext(InputType.RENAME_ENTREPRISE_NEW_NAME, ancienNomEntreprise));
        player.sendMessage("§eVeuillez entrer le nouveau nom pour l'entreprise '" + ancienNomEntreprise + "' dans le chat. Tapez 'annuler' pour abandonner.");
    }

    // Gestionnaire d'événement pour intercepter les messages du chat
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Vérifier si ce joueur est dans notre map d'attente
        if (playersWaitingForInput.containsKey(playerUUID)) {
            event.setCancelled(true); // Annuler l'envoi du message au chat public

            String message = event.getMessage();
            PlayerInputContext context = playersWaitingForInput.get(playerUUID);

            // Important : Retirer le joueur de la map d'attente APRÈS avoir récupéré le contexte
            // mais AVANT de traiter pour éviter des conditions de concurrence ou des traitements multiples.
            // Cependant, si le traitement est asynchrone et peut échouer, vous pourriez vouloir
            // le retirer seulement après succès. Pour une saisie simple, le retirer tôt est souvent OK.

            if (message.equalsIgnoreCase("annuler")) {
                playersWaitingForInput.remove(playerUUID); // Retirer car l'action est annulée
                player.sendMessage("§cAction annulée.");
                // Optionnel : ré-ouvrir le GUI précédent si possible/nécessaire
                // Cela peut être complexe sans plus de contexte sur le flux de l'GUI.
                // Souvent, on informe juste le joueur et il doit rouvrir l'GUI manuellement.
                // Exemple : if (entrepriseGUI != null) entrepriseGUI.openManageSpecificEntrepriseMenu(player, plugin.getEntrepriseLogic().getEntreprise(context.entrepriseNom));
                return;
            }

            // Le traitement du message doit se faire dans le thread principal de Bukkit
            // car il interagit probablement avec l'API Bukkit (économie, logique d'entreprise).
            new BukkitRunnable() {
                @Override
                public void run() {
                    // On retire le joueur de la liste d'attente ici, dans le thread principal,
                    // juste avant de traiter sa réponse.
                    PlayerInputContext currentContext = playersWaitingForInput.remove(playerUUID);
                    if (currentContext == null) { // Vérification au cas où il aurait été retiré entre-temps
                        plugin.getLogger().warning("Le joueur " + player.getName() + " n'était plus en attente de saisie au moment du traitement synchrone.");
                        return;
                    }

                    switch (currentContext.inputType) {
                        case DEPOSIT_AMOUNT:
                            try {
                                double montant = Double.parseDouble(message);
                                plugin.getEntrepriseLogic().deposerArgent(player, currentContext.entrepriseNom, montant);
                            } catch (NumberFormatException e) {
                                player.sendMessage("§cMontant invalide. Veuillez entrer un nombre. Réessayez via l'interface.");
                                // Optionnel: remettre le joueur en attente ou le guider.
                            }
                            break;
                        case WITHDRAW_AMOUNT:
                            try {
                                double montant = Double.parseDouble(message);
                                plugin.getEntrepriseLogic().retirerArgent(player, currentContext.entrepriseNom, montant);
                            } catch (NumberFormatException e) {
                                player.sendMessage("§cMontant invalide. Veuillez entrer un nombre. Réessayez via l'interface.");
                            }
                            break;
                        case RENAME_ENTREPRISE_NEW_NAME:
                            plugin.getEntrepriseLogic().renameEntreprise(player, currentContext.entrepriseNom, message);
                            break;
                        // Ajoutez d'autres cas ici
                    }
                }
            }.runTask(plugin); // Exécuter sur le thread principal du serveur
        }
        // Si le joueur n'est pas dans la map, le message est traité normalement par le serveur.
    }
}