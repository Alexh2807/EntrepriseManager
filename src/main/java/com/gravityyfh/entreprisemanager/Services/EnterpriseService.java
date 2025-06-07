package com.gravityyfh.entreprisemanager.Services;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import com.gravityyfh.entreprisemanager.Models.*;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

public class EnterpriseService {

    private final EntrepriseManagerLogic logic;
    private final EntrepriseManager plugin;
    private final NotificationService notificationService;
    private final FinanceService financeService;

    public EnterpriseService(EntrepriseManagerLogic logic, NotificationService notificationService, FinanceService financeService) {
        this.logic = logic;
        this.plugin = logic.plugin;
        this.notificationService = notificationService;
        this.financeService = financeService;
    }

    public void proposeEnterpriseCreation(Player maire, Player gerantCible, String type, String ville, String nomPropose, String siret) {
        double coutCreation = plugin.getConfig().getDouble("types-entreprise." + type + ".cout-creation", 0.0);
        double distanceMax = plugin.getConfig().getDouble("creation.distance-max-maire-gerant", 15.0);

        if (!gerantCible.isOnline() || !maire.getWorld().equals(gerantCible.getWorld()) || maire.getLocation().distanceSquared(gerantCible.getLocation()) > distanceMax * distanceMax) {
            maire.sendMessage(ChatColor.RED + gerantCible.getName() + " est trop loin ou hors ligne.");
            return;
        }

        int maxManaged = plugin.getConfig().getInt("finance.max-entreprises-par-gerant", 1);
        long currentManagedCount = logic.getEntreprisesMap().values().stream().filter(e -> e.getGerant().equalsIgnoreCase(gerantCible.getName())).count();

        if (currentManagedCount >= maxManaged) {
            maire.sendMessage(ChatColor.RED + gerantCible.getName() + " gère déjà le maximum de " + maxManaged + " entreprise(s).");
            return;
        }
        if (logic.getEntreprise(nomPropose) != null) {
            maire.sendMessage(ChatColor.RED + "Le nom d'entreprise '" + nomPropose + "' est déjà pris.");
            return;
        }

        long delaiValidation = plugin.getConfig().getLong("creation.delai-validation-ms", 60000L);
        DemandeCreation demande = new DemandeCreation(maire, gerantCible, type, ville, siret, nomPropose, coutCreation, delaiValidation);
        logic.getDemandesEnAttente().put(gerantCible.getUniqueId(), demande);

        maire.sendMessage(ChatColor.GREEN + "Proposition de création envoyée à " + gerantCible.getName() + ".");
        envoyerInvitationVisuelleContrat(gerantCible, demande);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (logic.getDemandesEnAttente().get(gerantCible.getUniqueId()) == demande) {
                logic.getDemandesEnAttente().remove(gerantCible.getUniqueId());
                gerantCible.sendMessage(ChatColor.RED + "L'offre pour créer '" + demande.nomEntreprise + "' a expiré.");
                maire.sendMessage(ChatColor.RED + "L'offre de création pour " + gerantCible.getName() + " a expiré.");
            }
        }, delaiValidation / 50);
    }

    public void validateEnterpriseCreation(Player gerantSignataire) {
        DemandeCreation demande = logic.getDemandesEnAttente().remove(gerantSignataire.getUniqueId());
        if (demande == null || demande.isExpired()) {
            gerantSignataire.sendMessage(ChatColor.RED + "Aucune demande valide ou celle-ci a expiré.");
            return;
        }

        if (!EntrepriseManager.getEconomy().has(gerantSignataire, demande.cout)) {
            gerantSignataire.sendMessage(ChatColor.RED + "Fonds insuffisants pour payer les " + String.format("%,.2f€", demande.cout) + " de frais.");
            demande.maire.sendMessage(ChatColor.RED + "Création échouée: " + gerantSignataire.getName() + " n'a pas les fonds nécessaires.");
            return;
        }

        if (logic.getEntreprise(demande.nomEntreprise) != null) {
            gerantSignataire.sendMessage(ChatColor.RED + "Le nom d'entreprise '" + demande.nomEntreprise + "' a été pris entre-temps. Action annulée.");
            demande.maire.sendMessage(ChatColor.RED + "Création échouée: Le nom '" + demande.nomEntreprise + "' a été pris.");
            return;
        }

        financeService.withdrawMoney(gerantSignataire, demande.cout);

        Entreprise nouvelleEntreprise = new Entreprise(demande.nomEntreprise, demande.ville, demande.type, gerantSignataire.getName(), gerantSignataire.getUniqueId().toString(), new HashSet<>(), 0.0, demande.siret);
        nouvelleEntreprise.addTransaction(new Transaction(Transaction.TransactionType.CREATION_COST, demande.cout, "Frais de création", gerantSignataire.getName()));
        logic.getEntreprisesMap().put(demande.nomEntreprise, nouvelleEntreprise);
        logic.getActiviteHoraireValeur().put(demande.nomEntreprise, 0.0);
        logic.saveData();

        demande.maire.sendMessage(ChatColor.GREEN + "Entreprise '" + demande.nomEntreprise + "' créée avec succès pour " + gerantSignataire.getName() + ".");
        gerantSignataire.sendMessage(ChatColor.GREEN + "Félicitations ! Vous êtes maintenant gérant de '" + demande.nomEntreprise + "'.");
    }

    public void refuseCreation(Player gerant) {
        DemandeCreation demande = logic.getDemandesEnAttente().remove(gerant.getUniqueId());
        if (demande == null) {
            gerant.sendMessage(ChatColor.RED + "Aucune demande à refuser.");
            return;
        }
        gerant.sendMessage(ChatColor.YELLOW + "Vous avez refusé le contrat pour '" + demande.nomEntreprise + "'.");
        if(demande.maire.isOnline()) {
            demande.maire.sendMessage(ChatColor.RED + gerant.getName() + " a refusé votre proposition de contrat.");
        }
    }

    // ... Mettre ici TOUTES les autres méthodes de gestion d'entreprise :
    // inviterEmploye, handleAccepter/RefuserInvitation, kickEmploye, leaveEntreprise,
    // renameEnterprise, handleEntrepriseRemoval, tenterAmeliorationNiveauMaxEmployes/Solde,
    // etc. en les adaptant pour utiliser `logic.get...` et les autres services.

    public int getLimiteMaxEmployesActuelle(Entreprise entreprise) {
        if (entreprise == null) return 0;
        return plugin.getConfig().getInt("finance.max-employer-par-entreprise." + entreprise.getNiveauMaxEmployes(), 0);
    }

    public double getLimiteMaxSoldeActuelle(Entreprise entreprise) {
        if (entreprise == null) return 0.0;
        return plugin.getConfig().getDouble("finance.max-solde-par-niveau." + entreprise.getNiveauMaxSolde(), 0.0);
    }

    private void envoyerInvitationVisuelleContrat(Player gerantCible, DemandeCreation demande) {
        gerantCible.sendMessage(ChatColor.GOLD + "---------------- Contrat de Gérance ----------------");
        gerantCible.sendMessage(ChatColor.AQUA + "Maire: " + ChatColor.WHITE + demande.maire.getName());
        gerantCible.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + demande.ville);
        gerantCible.sendMessage(ChatColor.AQUA + "Type: " + ChatColor.WHITE + demande.type);
        gerantCible.sendMessage(ChatColor.AQUA + "Nom: " + ChatColor.WHITE + demande.nomEntreprise);
        gerantCible.sendMessage(ChatColor.AQUA + "SIRET: " + ChatColor.WHITE + demande.siret);
        gerantCible.sendMessage(ChatColor.YELLOW + "Coût: " + ChatColor.GREEN + String.format("%,.2f€", demande.cout));

        TextComponent accepterMsg = new TextComponent("        [VALIDER CONTRAT]");
        accepterMsg.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accepterMsg.setBold(true);
        accepterMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise validercreation"));
        accepterMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Accepter (coût: " + String.format("%,.2f€", demande.cout) + ")").create()));

        TextComponent refuserMsg = new TextComponent("   [REFUSER CONTRAT]");
        refuserMsg.setColor(net.md_5.bungee.api.ChatColor.RED);
        refuserMsg.setBold(true);
        refuserMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise annulercreation"));
        refuserMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Refuser").create()));

        gerantCible.spigot().sendMessage(new TextComponent(accepterMsg, refuserMsg));
        gerantCible.sendMessage(ChatColor.GOLD + "--------------------------------------------------");
    }
}