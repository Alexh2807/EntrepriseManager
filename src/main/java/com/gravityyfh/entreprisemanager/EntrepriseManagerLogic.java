package com.gravityyfh.entreprisemanager;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;

public class EntrepriseManagerLogic {
    private static EntrepriseManager plugin;
    private static Map<String, Entreprise> entreprises;
    private static File entrepriseFile;
    private static FileConfiguration entrepriseConfig;


    public EntrepriseManagerLogic(EntrepriseManager plugin) {
        EntrepriseManagerLogic.plugin = plugin;
        entreprises = new HashMap<>();
        entrepriseFile = new File(plugin.getDataFolder(), "entreprise.yml");
        entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);
        loadEntreprises();
        planifierPaiements();
    }
    private Map<UUID, Map<String, Integer>> activitesJoueurs = new HashMap<>();
    private Map<String, Set<Material>> blocsAutorisesParTypeEntreprise = new HashMap<>();
    private Map<UUID, String> joueursEntreprises = new HashMap<>();
    private Map<String, String> invitations = new HashMap<>();

    public Entreprise getEntreprise(String nomEntreprise) {
        return entreprises.get(nomEntreprise);
    }

    public boolean isActionAllowedForPlayer(Material blockType, UUID playerUUID) {
        // La méthode getTypeEntrepriseDuJoueur doit être mise à jour pour utiliser players.yml
        String typeEntreprise = getTypeEntrepriseDuJoueur(playerUUID.toString());

        String categorieActivite = getCategorieActivite(blockType);

        if (categorieActivite == null) {
            // Si la catégorie d'activité n'est pas déterminée, autoriser par défaut
            return true;
        }

        if (typeEntreprise != null && categorieActivite.equals(typeEntreprise)) {
            // Le joueur peut effectuer l'action car son entreprise autorise cette catégorie d'activité
            return true;
        }

        // Pour les joueurs sans entreprise ou lorsque l'entreprise n'autorise pas la catégorie,
        // vérifie la limite quotidienne pour les non-membres.
        return checkDailyLimitForNonMembers(playerUUID, categorieActivite);
    }


    public String getCategorieActivite(Material blockType) {
        // Exemple de logique pour déterminer la catégorie en fonction du type de bloc
        // Cela suppose que vous avez défini les catégories et les types de blocs autorisés dans votre fichier de configuration
        for (String categorie : plugin.getConfig().getConfigurationSection("types-entreprise").getKeys(false)) {
            List<String> blocsAutorises = plugin.getConfig().getStringList("types-entreprise." + categorie + ".blocs-autorisés");
            if (blocsAutorises.contains(blockType.name())) {
                return categorie;
            }
        }
        return null; // Retourne null si le bloc ne correspond à aucune catégorie
    }

    public boolean checkDailyLimitForNonMembers(UUID joueurUUID, String categorie) {
        LocalDateTime maintenant = LocalDateTime.now();
        Map<String, ActionInfo> activites = joueurActivites.computeIfAbsent(joueurUUID, k -> new HashMap<>());
        ActionInfo info = activites.computeIfAbsent(categorie, k -> new ActionInfo());

        if (info.getDernierActionHeure().getHour() != maintenant.getHour()) {
            // Réinitialise le compteur si nous sommes dans une nouvelle heure
            info.setNombreActions(0);
            info.setDernierActionHeure(maintenant);
        }

        int limite = plugin.getConfig().getInt("types-entreprise." + categorie + ".limite-non-membre-par-heure");
        if (info.getNombreActions() < limite) {
            // Incrémente le compteur et autorise l'action
            info.setNombreActions(info.getNombreActions() + 1);
            return true;
        } else {
            // Limite atteinte, action non autorisée
            return false;
        }
    }

    public String getTypeEntrepriseDuJoueur(String joueurUUIDString) {
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        String entrepriseNom = playersConfig.getString("players." + joueurUUIDString + ".entreprise");
        if (entrepriseNom != null) {
            Entreprise entreprise = entreprises.get(entrepriseNom);
            return entreprise != null ? entreprise.getType() : null;
        }
        return null; // Le joueur n'appartient à aucune entreprise
    }

    public void chargerJoueurs() {
        File fichierJoueurs = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration configJoueurs = YamlConfiguration.loadConfiguration(fichierJoueurs);

        if (configJoueurs.contains("players")) {
            for (String uuid : configJoueurs.getConfigurationSection("players").getKeys(false)) {
                String entreprise = configJoueurs.getString("players." + uuid + ".entreprise");
                joueursEntreprises.put(UUID.fromString(uuid), entreprise);
            }
        }
    }

    public void sauvegarderJoueurs() {
        File fichierJoueurs = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration configJoueurs = YamlConfiguration.loadConfiguration(fichierJoueurs);

        for (Map.Entry<UUID, String> entry : joueursEntreprises.entrySet()) {
            configJoueurs.set("players." + entry.getKey().toString() + ".entreprise", entry.getValue());
        }

        try {
            configJoueurs.save(fichierJoueurs);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void kickEmploye(Player gerant, String nomEntreprise, String nomEmploye) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "L'entreprise " + nomEntreprise + " n'existe pas.");
            return;
        }

        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName())) {
            gerant.sendMessage(ChatColor.RED + "Vous n'êtes pas le gérant de l'entreprise " + nomEntreprise + ".");
            return;
        }

        if (!entreprise.getEmployes().contains(nomEmploye)) {
            gerant.sendMessage(ChatColor.RED + "L'employé " + nomEmploye + " ne fait pas partie de l'entreprise " + nomEntreprise + ".");
            return;
        }

        // Retirer l'employé de l'entreprise
        entreprise.getEmployes().remove(nomEmploye);

        // Mettre à jour players.yml
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        playersConfig.set("players." + nomEmploye + ".entreprise", null); // Supprimer l'entreprise de l'employé
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        saveEntreprises(); // Sauvegardez les modifications

        gerant.sendMessage(ChatColor.GREEN + "L'employé " + nomEmploye + " a été viré de l'entreprise " + nomEntreprise + ".");
        Player employePlayer = Bukkit.getPlayerExact(nomEmploye);
        if (employePlayer != null) {
            employePlayer.sendMessage(ChatColor.RED + "Vous avez été viré de l'entreprise " + nomEntreprise + ".");
        }
    }



    public void chargerBlocsAutorises() {
        FileConfiguration config = plugin.getConfig();
        for (String typeEntreprise : config.getConfigurationSection("types-entreprise").getKeys(false)) {
            Set<Material> blocsAutorises = config.getStringList("types-entreprise." + typeEntreprise + ".blocs-autorisés").stream()
                    .map(Material::matchMaterial)
                    .collect(Collectors.toSet());
            blocsAutorisesParTypeEntreprise.put(typeEntreprise, blocsAutorises);
        }
    }

    public List<Map<String, String>> getEntreprisesDuJoueurInfo(String joueurNom) {
        List<Map<String, String>> entreprisesInfo = new ArrayList<>();
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getEmployes().contains(joueurNom) || entreprise.getGerant().equalsIgnoreCase(joueurNom)) {
                Map<String, String> entrepriseInfo = new HashMap<>();
                entrepriseInfo.put("nom", entreprise.getNom());
                entrepriseInfo.put("gerant", entreprise.getGerant());
                entrepriseInfo.put("type", entreprise.getType());
                entreprisesInfo.add(entrepriseInfo);
            }
        }
        return entreprisesInfo;
    }

    public List<Entreprise> getEntrepriseDuGerant(String gerantNom) {
        // Créez une liste pour stocker les entreprises du gérant
        List<Entreprise> entreprisesDuGerant = new ArrayList<>();

        // Parcourez toutes les entreprises et ajoutez celles gérées par le gérant spécifié
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getGerant().equalsIgnoreCase(gerantNom)) {
                entreprisesDuGerant.add(entreprise);
            }
        }

        return entreprisesDuGerant;
    }


    public void retirerArgent(Player player, String entrepriseNom, double montant) {
        Entreprise entreprise = getEntreprise(entrepriseNom);
        if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "Entreprise introuvable ou vous n'êtes pas le gérant.");
            return;
        }
        if (montant <= 0 || montant > entreprise.getSolde()) {
            player.sendMessage(ChatColor.RED + "Montant invalide ou solde insuffisant.");
            return;
        }

        // Retirer l'argent de l'entreprise
        entreprise.setSolde(entreprise.getSolde() - montant);
        saveEntreprises();

        // Utilisation de Vault pour ajouter de l'argent au joueur
        EconomyResponse response = EntrepriseManager.getEconomy().depositPlayer(player, montant);
        if (response.transactionSuccess()) {
            player.sendMessage(ChatColor.GREEN + "Vous avez retiré " + montant + "€ de " + entrepriseNom + ".");
        } else {
            // Si la transaction échoue, remettre l'argent à l'entreprise
            entreprise.setSolde(entreprise.getSolde() + montant);
            saveEntreprises();
            player.sendMessage(ChatColor.RED + "Erreur lors de la transaction : " + response.errorMessage);
        }
    }

    public void deposerArgent(Player player, String entrepriseNom, double montant) {
        Entreprise entreprise = getEntreprise(entrepriseNom);
        if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "Entreprise introuvable ou vous n'êtes pas le gérant.");
            return;
        }
        if (montant <= 0) {
            player.sendMessage(ChatColor.RED + "Le montant doit être positif.");
            return;
        }

        // Utilisation de Vault pour retirer de l'argent au joueur
        EconomyResponse response = EntrepriseManager.getEconomy().withdrawPlayer(player, montant);
        if (response.transactionSuccess()) {
            // Ajouter l'argent à l'entreprise
            entreprise.setSolde(entreprise.getSolde() + montant);
            saveEntreprises();
            player.sendMessage(ChatColor.GREEN + "Vous avez déposé " + montant + "€ dans " + entrepriseNom + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors de la transaction : " + response.errorMessage);
        }
    }

    public Collection<Entreprise> getEntreprises() {
        return entreprises.values();
    }

    public List<Entreprise> getEntreprisesByVille(String ville) {
        List<Entreprise> entreprisesDansVille = new ArrayList<>();
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getVille().equalsIgnoreCase(ville)) {
                entreprisesDansVille.add(entreprise);
            }
        }
        return entreprisesDansVille;
    }


    public void leaveEntreprise(Player joueur, String nomEntreprise) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise == null) {
            joueur.sendMessage(ChatColor.RED + "L'entreprise spécifiée n'existe pas.");
            return;
        }

        String joueurUUIDString = joueur.getUniqueId().toString();
        if (!entreprise.getEmployes().contains(joueurUUIDString)) {
            joueur.sendMessage(ChatColor.RED + "Vous n'êtes pas un employé de cette entreprise.");
            return;
        }

        // Vérifier si le joueur est le gérant (si c'est le cas, ne pas permettre de quitter de cette manière)
        if (entreprise.getGerant().equalsIgnoreCase(joueur.getName())) {
            joueur.sendMessage(ChatColor.RED + "En tant que gérant, vous ne pouvez pas quitter l'entreprise. Utilisez '/entreprise delete' pour supprimer l'entreprise.");
            return;
        }

        // Supprimer le joueur de la liste des employés de l'entreprise
        entreprise.getEmployes().remove(joueurUUIDString);

        // Mettre à jour la liste des joueurs dans le fichier players.yml
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        playersConfig.set("players." + joueurUUIDString + ".entreprise", null); // Supprime l'entreprise associée au joueur
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        saveEntreprises(); // Sauvegardez les modifications des entreprises
        joueur.sendMessage(ChatColor.GREEN + "Vous avez quitté l'entreprise " + nomEntreprise + ".");
    }

    public String trouverNomEntrepriseParTypeEtGerant(String gerant, String type) {
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getGerant().equalsIgnoreCase(gerant) && entreprise.getType().equalsIgnoreCase(type)) {
                return entreprise.getNom();
            }
        }
        return null; // Retourne null si aucune entreprise correspondante n'est trouvée
    }

    public Set<String> getGerantsAvecEntreprises() {
        Set<String> gerants = new HashSet<>();
        for (Entreprise entreprise : entreprises.values()) {
            gerants.add(entreprise.getGerant());
        }
        return gerants;
    }
    public void reloadEntreprises() {
        // Étape 1: Recharger le fichier entreprise.yml
        if (entrepriseFile == null) {
            entrepriseFile = new File(plugin.getDataFolder(), "entreprise.yml");
        }
        entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);

        // Étape 2: Réinitialiser l'état actuel
        entreprises.clear(); // Assurez-vous que 'entreprises' est votre Map ou List stockant les objets Entreprise
        loadEntreprises();

        // Log pour indiquer le succès du rechargement
        plugin.getLogger().info("Entreprises rechargées avec succès depuis le fichier.");
    }


    public void inviterEmploye(Player gerant, String entrepriseNom, Player joueurInvite) {
        // Assurez-vous que l'entreprise existe
        Entreprise entreprise = getEntreprise(entrepriseNom);
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "L'entreprise n'existe pas.");
            return;
        }

        // Vérifier que le gérant ne tente pas de s'inviter lui-même
        if (gerant.getName().equals(joueurInvite.getName())) {
            gerant.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous inviter dans votre propre entreprise.");
            return;
        }

        // Le reste du code pour inviter un employé
        if (!estGerant(gerant.getName(), entrepriseNom)) {
            gerant.sendMessage(ChatColor.RED + "Vous n'êtes pas le gérant de cette entreprise.");
            return;
        }

        if (gerant.getLocation().distance(joueurInvite.getLocation()) > plugin.getConfig().getDouble("invitation.distance-max")) {
            gerant.sendMessage(ChatColor.RED + "Le joueur est trop loin pour être invité.");
            return;
        }

        invitations.put(joueurInvite.getName(), entrepriseNom);
        envoyerInvitationDansChat(joueurInvite, entrepriseNom, gerant.getName(), entreprise.getType());
        gerant.sendMessage(ChatColor.GREEN + "Une invitation a été envoyée à " + joueurInvite.getName() + " pour rejoindre l'entreprise " + entrepriseNom + ".");
    }







    void handleAccepterCommand(Player joueur) {
        String joueurNom = joueur.getName();

        if (invitations.containsKey(joueurNom)) {
            String entrepriseNom = invitations.get(joueurNom);

            // Ajouter l'employé à l'entreprise
            this.addEmploye(entrepriseNom, joueurNom);
            joueur.sendMessage(ChatColor.GREEN + "Vous avez accepté l'invitation de l'entreprise " + entrepriseNom + ".");

            // Notification au gérant
            Entreprise entreprise = this.getEntreprise(entrepriseNom);
            if (entreprise != null) {
                Player gerant = Bukkit.getServer().getPlayerExact(entreprise.getGerant());
                if (gerant != null) {
                    gerant.sendMessage(ChatColor.GREEN + joueurNom + " a accepté l'invitation à rejoindre l'entreprise " + entrepriseNom + ".");
                }
            }

            invitations.remove(joueurNom);

        } else {
            joueur.sendMessage(ChatColor.RED + "Aucune invitation trouvée ou elle a expiré.");
        }
    }



    void handleRefuserCommand(Player joueur) {
        String joueurNom = joueur.getName();

        if (invitations.containsKey(joueurNom)) {
            String entrepriseNom = invitations.get(joueurNom);

            // Notification au gérant
            Entreprise entreprise = this.getEntreprise(entrepriseNom);
            if (entreprise != null) {
                Player gerant = Bukkit.getServer().getPlayerExact(entreprise.getGerant());
                if (gerant != null) {
                    gerant.sendMessage(ChatColor.RED + joueurNom + " a refusé l'invitation à rejoindre l'entreprise " + entrepriseNom + ".");
                }
            }

            invitations.remove(joueurNom);
            joueur.sendMessage(ChatColor.RED + "Vous avez refusé l'invitation de l'entreprise.");

        } else {
            joueur.sendMessage(ChatColor.RED + "Aucune invitation trouvée ou elle a expiré.");
        }
    }

    public void listEntreprises(Player player, String ville) {
        // Création du composant principal qui contiendra tout le message
        TextComponent message = new TextComponent();

        // Ajout de l'en-tête
        message.addExtra(createMessageComponent(ChatColor.GOLD + "====================================================\n"));
        message.addExtra(createMessageComponent(ChatColor.YELLOW + "Entreprises présentes dans la ville de " + ChatColor.AQUA + ville + ChatColor.YELLOW + " :\n"));

        boolean entrepriseTrouvee = false;

        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getVille().equalsIgnoreCase(ville)) {
                // Création d'un composant cliquable pour chaque entreprise
                TextComponent entrepriseInfo = new TextComponent(ChatColor.GREEN + entreprise.getNom());
                entrepriseInfo.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise info " + entreprise.getGerant() + " " + entreprise.getType()));

                message.addExtra(entrepriseInfo);
                message.addExtra(createMessageComponent(ChatColor.GRAY + " -> Type : " + ChatColor.WHITE + entreprise.getType() + ChatColor.GRAY + " --- Gérant : " + ChatColor.WHITE + entreprise.getGerant() + ChatColor.GRAY + " --- Chiffre affaires : " + ChatColor.GREEN + String.format("%.2f€", entreprise.getChiffreAffairesTotal()) + "\n"));

                entrepriseTrouvee = true;
            }
        }

        // Ajout du pied de page
        message.addExtra(createMessageComponent(ChatColor.GOLD + "===================================================="));

        if (!entrepriseTrouvee) {
            player.sendMessage(ChatColor.RED + "Aucune entreprise trouvée dans la ville " + ville + ".");
        } else {
            player.spigot().sendMessage(message);
        }
    }

    private TextComponent createMessageComponent(String text) {
        return new TextComponent(TextComponent.fromLegacyText(text));
    }

    public void getEntrepriseInfo(Player joueur, String nomEntreprise) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise != null) {
            joueur.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Informations sur l'Entreprise ===");
            joueur.sendMessage(ChatColor.YELLOW + "Nom: " + ChatColor.WHITE + entreprise.getNom());
            joueur.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + entreprise.getVille());
            joueur.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + entreprise.getType());
            joueur.sendMessage(ChatColor.YELLOW + "Gérant: " + ChatColor.WHITE + entreprise.getGerant());
            joueur.sendMessage(ChatColor.YELLOW + "Nombre d'employés: " + ChatColor.WHITE + entreprise.getEmployes().size());
            joueur.sendMessage(ChatColor.YELLOW + "Revenus BRUT/jour: " + ChatColor.GREEN + entreprise.getRevenusBrutsJournaliers() + "€");
            joueur.sendMessage(ChatColor.YELLOW + "Montant d'argent dans la société: " + ChatColor.GREEN + entreprise.getSolde() + "€");
            joueur.sendMessage(ChatColor.YELLOW + "Chiffre d'affaires total: " + ChatColor.GREEN + entreprise.getChiffreAffairesTotal() + "€");
            joueur.sendMessage(ChatColor.YELLOW + "SIRET: " + ChatColor.WHITE + entreprise.getSiret());
            joueur.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "====================================");
        } else {
            joueur.sendMessage(ChatColor.RED + "L'entreprise spécifiée n'existe pas.");
        }
    }
    public void afficherSoldeEntreprise(Player player, String nomEntreprise) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Entreprise introuvable.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Solde de l'entreprise " + nomEntreprise + ": " + entreprise.getSolde());
    }
    public void retirerArgentAdmin(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Entreprise introuvable.");
            return;
        }

        if (montant <= 0 || montant > entreprise.getSolde()) {
            player.sendMessage(ChatColor.RED + "Montant invalide ou insuffisant dans le solde de l'entreprise.");
            return;
        }

        entreprise.setSolde(entreprise.getSolde() - montant);
        player.sendMessage(ChatColor.GREEN + "Retiré " + montant + " de l'entreprise " + nomEntreprise + ".");
        this.saveEntreprises();
    }
    public void deposerArgentAdmin(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Entreprise introuvable.");
            return;
        }

        if (montant <= 0) {
            player.sendMessage(ChatColor.RED + "Le montant doit être positif.");
            return;
        }

        entreprise.setSolde(entreprise.getSolde() + montant);
        player.sendMessage(ChatColor.GREEN + "Déposé " + montant + " dans l'entreprise " + nomEntreprise + ".");
        this.saveEntreprises();
    }

    public boolean estGerant(String nomJoueur, String nomEntreprise) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        return entreprise != null && entreprise.getGerant().equalsIgnoreCase(nomJoueur);
    }
    public EntrepriseManager getPlugin() {
        return plugin;
    }

    void planifierPaiements() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();

        // Obtenir l'heure de paiement à partir de la configuration
        int heurePaiement = plugin.getConfig().getInt("finance.heure-paiement");

        // Calculer le délai avant le prochain paiement
        long delay = calculerDelaiAvantPaiement(heurePaiement);

        // Planifier la tâche pour se répéter toutes les 24 heures (20 ticks * 60 secondes * 60 minutes * 24 heures)
        long period = 20L * 60L * 60L * 24L;

        scheduler.scheduleSyncRepeatingTask(plugin, this::traiterPaiementsJournaliers, delay, period);
    }

    private long calculerDelaiAvantPaiement(int heurePaiement) {
        // Créer une instance de Calendar pour maintenant et pour l'heure de paiement aujourd'hui
        Calendar maintenant = Calendar.getInstance();
        Calendar prochainPaiement = (Calendar) maintenant.clone();
        prochainPaiement.set(Calendar.HOUR_OF_DAY, heurePaiement);
        prochainPaiement.set(Calendar.MINUTE, 0);
        prochainPaiement.set(Calendar.SECOND, 0);

        // Si l'heure de paiement est passée aujourd'hui, planifier pour demain
        if (maintenant.after(prochainPaiement)) {
            prochainPaiement.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Calculer le délai en millisecondes et le convertir en ticks (1 seconde = 20 ticks)
        long delayMillis = prochainPaiement.getTimeInMillis() - maintenant.getTimeInMillis();
        return delayMillis / 50L; // Convertir en ticks
    }

    public void traiterPaiementsJournaliers() {
        for (Entreprise entreprise : entreprises.values()) {
            entreprise.calculerPaiementJournalier();
            // Affichage des logs pour le suivi
            plugin.getLogger().info("Paiements journaliers traités pour l'entreprise: " + entreprise.getNom());
        }
    }

    private Entreprise trouverEntrepriseParGerantEtType(String gerant, String type) {
        return entreprises.values().stream()
                .filter(e -> e.getGerant().equals(gerant) && e.getType().equals(type))
                .findFirst()
                .orElse(null);
    }

    private void loadEntreprises() {
        if (entrepriseConfig.contains("entreprises")) {
            for (String key : entrepriseConfig.getConfigurationSection("entreprises").getKeys(false)) {
                String path = "entreprises." + key + ".";
                String ville = entrepriseConfig.getString(path + "ville");
                String type = entrepriseConfig.getString(path + "type");
                String gerant = entrepriseConfig.getString(path + "gerant");
                List<String> employesList = entrepriseConfig.getStringList(path + "employes");
                Set<String> employes = new HashSet<>(employesList);
                double solde = entrepriseConfig.getDouble(path + "solde", 0.0);
                String siret = entrepriseConfig.getString(path + "siret", "Inconnu"); // Charge le SIRET
                double chiffreAffairesTotal = entrepriseConfig.getDouble(path + "chiffreAffairesTotal", 0.0); // Charge le chiffre d'affaires total

                Entreprise entreprise = new Entreprise(key, ville, type, gerant, employes, solde, siret);
                entreprise.setChiffreAffairesTotal(chiffreAffairesTotal); // Assurez-vous d'ajouter cette méthode dans votre classe Entreprise si elle n'existe pas déjà

                entreprises.put(key, entreprise);
            }
        }
    }

    public Set<String> getTypesEntreprise() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("types-entreprise");
        if (section != null) {
            return section.getKeys(false);
        }
        return new HashSet<>();
    }

    public boolean estMaire(Player joueur) {
        try {
            Resident resident = TownyAPI.getInstance().getResident(joueur.getName());
            return resident != null && resident.isMayor();
        } catch (Exception e) {
            joueur.sendMessage(ChatColor.RED + "Erreur de vérification du statut de maire.");
            return false;
        }
    }

    public boolean estMembreDeLaVille(String gerantNom, String villeNom) {
        try {
            Resident gerant = TownyAPI.getInstance().getResident(gerantNom);
            return gerant != null && gerant.hasTown() && gerant.getTown().getName().equalsIgnoreCase(villeNom);
        } catch (NotRegisteredException e) {
            return false;
        }
    }

    public boolean estVilleSupprimee(String ville) {
        // Vérifier si la ville existe dans Towny
        // Cette logique dépend de comment Towny gère les villes supprimées.
        // Vous devez adapter cette méthode en fonction de votre implémentation spécifique.
        return ville == null || TownyAPI.getInstance().getTown(ville) == null;
    }


    public void saveEntreprises() {
        // Suppression des données existantes pour éviter les doublons.
        if (entrepriseConfig.contains("entreprises")) {
            entrepriseConfig.set("entreprises", null);
        }

        // Sauvegarde des entreprises.
        for (Map.Entry<String, Entreprise> entry : entreprises.entrySet()) {
            String key = entry.getKey();
            Entreprise entreprise = entry.getValue();
            String path = "entreprises." + key + ".";
            entrepriseConfig.set(path + "nom", entreprise.getNom());
            entrepriseConfig.set(path + "ville", entreprise.getVille());
            entrepriseConfig.set(path + "type", entreprise.getType());
            entrepriseConfig.set(path + "gerant", entreprise.getGerant());
            // Enregistrer les noms des employés au lieu de leurs UUIDs
            entrepriseConfig.set(path + "employes", new ArrayList<>(entreprise.getEmployes()));
            entrepriseConfig.set(path + "solde", entreprise.getSolde());
            entrepriseConfig.set(path + "siret", entreprise.getSiret());
            entrepriseConfig.set(path + "chiffreAffairesTotal", entreprise.getChiffreAffairesTotal());
        }

        try {
            entrepriseConfig.save(entrepriseFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder les entreprises: " + e.getMessage());
        }

        // Sauvegarde des appartenances des joueurs dans un fichier séparé.
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        // Suppression des anciennes données des joueurs.
        playersConfig.set("players", null);

        // Ajout des données des joueurs.
        for (Map.Entry<String, Entreprise> entry : entreprises.entrySet()) {
            String entrepriseNom = entry.getKey();
            Entreprise entreprise = entry.getValue();

            for (String employeName : entreprise.getEmployes()) {
                // Enregistrement de l'entreprise à laquelle appartient chaque joueur.
                playersConfig.set("players." + employeName + ".entreprise", entrepriseNom);
            }
        }

        // Sauvegarde du fichier players.yml.
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder le fichier des joueurs: " + e.getMessage());
        }
    }


    public String getTownNameFromPlayer(Player player) {
        try {
            Resident resident = TownyAPI.getInstance().getResident(player.getName());
            if (resident != null && resident.hasTown()) {
                return resident.getTown().getName();
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Erreur lors de la récupération des informations de ville.");
        }
        return null;
    }
    public String generateSiret() {
        return UUID.randomUUID().toString().substring(0, 14);
    }
    public boolean aDejaEntrepriseDuType(String gerant, String type) {
        // Parcourir la liste des entreprises et vérifier si le gérant a déjà une entreprise de ce type.
        // Retourner true si trouvé, false sinon.
        return entreprises.values().stream()
                .anyMatch(entreprise -> entreprise.getGerant().equals(gerant) && entreprise.getType().equals(type));
    }
    public String trouverNomEntrepriseParType(String gerant, String type) {
        // Parcourir les entreprises pour trouver une correspondance de type pour le gérant donné.
        return entreprises.values().stream()
                .filter(entreprise -> entreprise.getGerant().equals(gerant) && entreprise.getType().equals(type))
                .map(Entreprise::getNom)
                .findFirst()
                .orElse(null);
    }
    public int compterEntreprisesDuGerant(String gerant) {
        return (int) entreprises.values().stream()
                .filter(entreprise -> entreprise.getGerant().equals(gerant))
                .count();
    }
    public String trouverNomEntrepriseParTypeEtVille(String gerant, String type, String ville) {
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getGerant().equalsIgnoreCase(gerant) &&
                    entreprise.getType().equalsIgnoreCase(type) &&
                    entreprise.getVille().equalsIgnoreCase(ville)) {
                return entreprise.getNom();
            }
        }
        return null; // Aucune entreprise trouvée correspondant aux critères
    }
    public void closeEntreprise(Player joueur, String ville, String nomEntreprise) {
        // Vérifie si l'entreprise existe et si elle appartient à la ville indiquée
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise != null && entreprise.getVille().equalsIgnoreCase(ville)) {
            entreprises.remove(nomEntreprise);
            this.saveEntreprises();
        } else {
            joueur.sendMessage(ChatColor.RED + "L'entreprise n'existe pas ou n'est pas dans cette ville.");
        }
    }

    public void addEmploye(String entrepriseNom, String joueurNom) {
        Entreprise entreprise = entreprises.get(entrepriseNom);
        if (entreprise != null) {
            // Vérifiez si l'employé n'est pas déjà dans l'entreprise
            if (!entreprise.getEmployes().contains(joueurNom)) {
                // Ajouter le nom du joueur à la liste des employés
                entreprise.getEmployes().add(joueurNom);
                System.out.println("Employé " + joueurNom + " ajouté à l'entreprise " + entrepriseNom);

                // Mettre à jour la liste des joueurs dans le fichier players.yml
                File playersFile = new File(plugin.getDataFolder(), "players.yml");
                FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
                playersConfig.set("players." + joueurNom + ".entreprise", entrepriseNom);
                try {
                    playersConfig.save(playersFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Sauvegarder les entreprises si nécessaire
                saveEntreprises();
            } else {
                System.out.println("L'employé " + joueurNom + " est déjà dans l'entreprise " + entrepriseNom);
            }
        } else {
            System.out.println("Entreprise " + entrepriseNom + " non trouvée.");
        }
    }

    public void envoyerInvitationDansChat(Player joueurInvite, String entrepriseNom, String gerantNom, String typeEntreprise) {
        TextComponent message = new TextComponent("L'entreprise de " + typeEntreprise + " de " + gerantNom + " vous invite à rejoindre son entreprise : ");

        TextComponent accepter = new TextComponent("[Accepter]");
        accepter.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accepter.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise accepter"));
        accepter.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour accepter").create()));

        TextComponent refuser = new TextComponent("[Refuser]");
        refuser.setColor(net.md_5.bungee.api.ChatColor.RED);
        refuser.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise refuser"));
        refuser.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour refuser").create()));

        message.addExtra(accepter);
        message.addExtra(" ");
        message.addExtra(refuser);

        joueurInvite.spigot().sendMessage(message);
    }

    public void declareEntreprise(Player joueur, String ville, String nomEntreprise, String type, String gerantNom, String siret) {
        if (this.entreprises.containsKey(nomEntreprise)) {
            joueur.sendMessage(ChatColor.RED + "Une entreprise avec ce nom existe déjà.");
            return;
        }

        Set<String> employes = new HashSet<>(); // Commencez avec une liste vide d'employés
        double soldeInitial = 0.0; // Le solde initial de l'entreprise

        Player gerant = Bukkit.getPlayerExact(gerantNom);
        if (gerant == null) {
            joueur.sendMessage(ChatColor.RED + "Le gérant spécifié n'est pas en ligne ou n'existe pas.");
            return;
        }

        Entreprise nouvelleEntreprise = new Entreprise(nomEntreprise, ville, type, gerantNom, employes, soldeInitial, siret);
        this.entreprises.put(nomEntreprise, nouvelleEntreprise);

        // Enregistrez les entreprises dans le fichier de configuration
        this.saveEntreprises();

        // Ajouter le gérant dans players.yml
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        playersConfig.set("players." + gerant.getUniqueId().toString() + ".entreprise", nomEntreprise);
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        joueur.sendMessage(ChatColor.GREEN + "Entreprise '" + nomEntreprise + "' de type '" + type + "' a été créée avec succès pour le gérant '" + gerantNom + "' avec le SIRET: " + siret);
    }


    public Collection<String> getTypesEntrepriseDuGerant(String gerant) {
        return entreprises.values().stream()
                .filter(entreprise -> entreprise.getGerant().equalsIgnoreCase(gerant))
                .map(Entreprise::getType)
                .distinct()
                .collect(Collectors.toList());
    }

    public Entreprise getEntrepriseDuGerantEtType(String gerant, String type) {
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getGerant().equalsIgnoreCase(gerant) && entreprise.getType().equalsIgnoreCase(type)) {
                return entreprise;
            }
        }
        return null; // Aucune entreprise correspondante n'a été trouvée
    }

    public Collection<String> getPlayersInMayorTown(Player player) {
        try {
            Resident mayor = TownyAPI.getInstance().getResident(player.getName());
            if (mayor != null && mayor.isMayor()) {
                Town town = mayor.getTown();
                return town.getResidents().stream()
                        .map(Resident::getName)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Erreur lors de la récupération des résidents de la ville.");
        }
        return List.of();
    }

    public Collection<String> getAllPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    public Collection<String> getAllTowns() {
        return TownyAPI.getInstance().getTowns().stream()
                .map(Town::getName)
                .collect(Collectors.toList());
    }

    public Collection<String> getPlayersInGerantEnterprise(Player player) {
        Collection<Entreprise> entreprisesDuGerant = getEntreprisesDuGerant(player.getName());

        // Vérifiez si le gérant a des entreprises
        if (entreprisesDuGerant.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous ne gérez aucune entreprise.");
            return Collections.emptyList(); // Utilisez Collections.emptyList pour retourner une collection immuable vide
        } else {
            // Créez un ensemble pour éviter les doublons si un employé travaille pour plusieurs entreprises du même gérant
            Set<String> employes = new HashSet<>();
            for (Entreprise entreprise : entreprisesDuGerant) {
                employes.addAll(entreprise.getEmployes());
            }
            return employes;
        }
    }


    private Collection<Entreprise> getEntreprisesDuGerant(String gerantNom) {
        // Utilisez un flux pour filtrer les entreprises et collecter celles qui correspondent au gérant
        return entreprises.values().stream()
                .filter(entreprise -> entreprise.getGerant().equalsIgnoreCase(gerantNom))
                .collect(Collectors.toList());
    }

    // Exemple de vérification du nombre maximum d'entreprises par gérant
    public boolean peutCreerEntreprise(Player gerant) {
        int nombreEntreprises = compterEntreprisesDuGerant(gerant.getName());
        int maxEntreprisesParGerant = plugin.getConfig().getInt("finance.max-entreprises-par-gerant");
        return nombreEntreprises < maxEntreprisesParGerant;
    }

    private int compterEntreprisesOuEmployeTravaille(String name) {
        int count = 0;
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getEmployes().contains(name)) {
                count++;
            }
        }
        return count;
    }

    // Exemple de vérification du nombre d'entreprises où un joueur travaille déjà
    public boolean peutTravaillerPourUneAutreEntreprise(String employe) {
        int nombreEntreprisesOuEmployeTravaille = compterEntreprisesOuEmployeTravaille(employe);
        int maxTravailJoueur = plugin.getConfig().getInt("finance.max-travail-joueur");
        return nombreEntreprisesOuEmployeTravaille < maxTravailJoueur;
    }

    // Exemple de vérification du nombre maximum d'employés dans une entreprise
    public boolean peutAjouterEmploye(String nomEntreprise) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) {
            // Gérer le cas où aucune entreprise correspondante n'est trouvée
            return false;
        }
        int nombreEmployes = entreprise.getEmployes().size();
        int maxEmployerParEntreprise = plugin.getConfig().getInt("finance.max-employer-par-entreprise");
        return nombreEmployes < maxEmployerParEntreprise;
    }

    public void changerNomEntreprise(Player player, String gerant, String type, String nouveauNom) {
        Entreprise entreprise = trouverEntrepriseParGerantEtType(gerant, type);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "L'entreprise spécifiée n'existe pas.");
            return;
        }

        if (!entreprise.getGerant().equals(player.getName())) {
            player.sendMessage(ChatColor.RED + "Vous devez être le gérant de l'entreprise pour changer son nom.");
            return;
        }

        if (entreprises.containsKey(nouveauNom)) {
            player.sendMessage(ChatColor.RED + "Une entreprise avec ce nom existe déjà.");
            return;
        }

        // Changer le nom de l'entreprise
        entreprises.remove(entreprise.getNom());
        entreprise.setNom(nouveauNom);
        entreprises.put(nouveauNom, entreprise);

        player.sendMessage(ChatColor.GREEN + "Le nom de l'entreprise a été changé en " + nouveauNom + ".");
    }

    public void renameEntreprise(Player player, String entrepriseNom, String nouveauNom) {
        Entreprise entreprise = entreprises.get(entrepriseNom);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Aucune entreprise trouvée avec ce nom.");
            return;
        }

        // Vérifier si le nouveau nom est déjà pris
        if (entreprises.containsKey(nouveauNom)) {
            player.sendMessage(ChatColor.RED + "Une entreprise avec ce nom existe déjà.");
            return;
        }

        // Vérification et application du nom
        if (nouveauNom.equalsIgnoreCase(entrepriseNom)) {
            player.sendMessage(ChatColor.RED + "Le nouveau nom doit être différent de l'ancien.");
            return;
        }

        // Renommer l'entreprise
        entreprises.remove(entreprise.getNom());
        entreprise.setNom(nouveauNom);
        entreprises.put(nouveauNom, entreprise);

        player.sendMessage(ChatColor.GREEN + "L'entreprise a été renommée avec succès de '" + entrepriseNom + "' à '" + nouveauNom + "'.");
    }


    public Collection<String> getNearbyPlayers(Player player, int distanceMax) {
        List<String> nearbyPlayers = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getWorld().equals(player.getWorld()) && onlinePlayer.getLocation().distance(player.getLocation()) <= distanceMax) {
                nearbyPlayers.add(onlinePlayer.getName());
            }
        }
        return nearbyPlayers;
    }

    public void supprimerEntreprise(Player player, String nomEntreprise) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "Entreprise introuvable ou vous n'êtes pas le gérant.");
            return;
        }
        entreprises.remove(nomEntreprise);
        saveEntreprises();
        player.sendMessage(ChatColor.GREEN + "L'entreprise " + nomEntreprise + " a été supprimée avec succès.");
    }
    public Set<String> getEmployes(String entrepriseNom) {
        Entreprise entreprise = entreprises.get(entrepriseNom);
        if (entreprise != null) {
            return entreprise.getEmployes();  // Ceci retourne les noms des joueurs
        }
        return Collections.emptySet();
    }



    public static class Entreprise {
        private double chiffreAffairesTotal = 0;
        private String nom;
        private String ville;
        private String type;
        private String gerant;
        private Set<String> employes;
        private double solde;

        public Entreprise(String nom, String ville, String type, String gerant, Set<String> employes, double solde, String siret) {
            this.nom = nom;
            this.ville = ville;
            this.type = type;
            this.gerant = gerant;
            this.employes = employes;
            this.solde = solde;
            this.chiffreAffairesTotal = 0.0;
            this.siret = siret;
        }
        public Set<String> getGerantsAvecEntreprises() {
            Set<String> gerants = new HashSet<>();
            for (Entreprise entreprise : entreprises.values()) {
                gerants.add(entreprise.getGerant());
            }
            return gerants;
        }

        public List<String> getTypesEntrepriseDuGerant(String gerantNom) {
            return entreprises.values().stream()
                    .filter(entreprise -> entreprise.getGerant().equalsIgnoreCase(gerantNom))
                    .map(Entreprise::getType)
                    .distinct()
                    .collect(Collectors.toList());
        }

        public List<String> getTypesEntreprise() {
            // Assurez-vous que votre plugin a accès à son fichier config.yml
            // Retourner la liste des types d'entreprise depuis le fichier config.yml
            return plugin.getConfig().getStringList("types-entreprise");
        }

        public String getSiret() {
            return siret;
        }

        // Méthode pour calculer le paiement journalier des employés et appliquer les taxes
        public double getRevenusBrutsJournaliers() {
            return getEmployes().size() * plugin.getConfig().getDouble("finance.gain-par-employe");
        }


        public void calculerPaiementJournalier() {
            double gainParEmploye = plugin.getConfig().getDouble("finance.gain-par-employe");
            double gainJournalier = employes.size() * gainParEmploye;

            // Mise à jour du chiffre d'affaires brut total
            chiffreAffairesTotal += gainJournalier;

            // Calcul et application des taxes
            double pourcentageTaxes = plugin.getConfig().getDouble("finance.pourcentage-taxes");
            double taxes = gainJournalier * (pourcentageTaxes / 100.0);
            double gainNet = gainJournalier - taxes;

            // Mise à jour du solde
            solde += gainNet;
            // Assurez-vous que les modifications du solde ET du chiffre d'affaires sont sauvegardées
            saveSolde();
            // Notez que saveSolde() devrait idéalement appeler saveEntreprises() ou une logique similaire pour assurer la persistance de toutes les données
        }


        // Méthode pour enregistrer le solde de l'entreprise dans le fichier de configuration
        private void saveSolde() {
            // Sauvegarde du solde
            entrepriseConfig.set("entreprises." + nom + ".solde", solde);
            // Sauvegarde du chiffre d'affaires total
            entrepriseConfig.set("entreprises." + nom + ".chiffreAffairesTotal", chiffreAffairesTotal);

            try {
                entrepriseConfig.save(entrepriseFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde pour l'entreprise " + nom, e);
            }
        }
        // Getters et Setters

        public double getChiffreAffairesTotal() {
            return this.chiffreAffairesTotal;
        }
        public String getNom() { return nom; }

        public void setNom(String nom) {
            this.nom = nom;
        }
        public String getVille() { return ville; }
        public String getType() { return type; }
        public String getGerant() { return gerant; }
        public Set<String> getEmployes() { return employes; }
        public double getSolde() { return solde; }
        private String siret; // Attribut pour stocker le SIRET

        public void setSolde(double solde) { this.solde = solde; }
        public void setChiffreAffairesTotal(double chiffreAffairesTotal) {
            this.chiffreAffairesTotal = chiffreAffairesTotal;
        }
    }

    private Map<UUID, Map<String, ActionInfo>> joueurActivites = new HashMap<>();

    public static class ActionInfo {
        private int nombreActions;
        private LocalDateTime dernierActionHeure;

        public ActionInfo() {
            this.nombreActions = 0;
            this.dernierActionHeure = LocalDateTime.now();
        }

        // Getters et Setters
        public int getNombreActions() { return nombreActions; }
        public void setNombreActions(int nombreActions) { this.nombreActions = nombreActions; }
        public LocalDateTime getDernierActionHeure() { return dernierActionHeure; }
        public void setDernierActionHeure(LocalDateTime dernierActionHeure) { this.dernierActionHeure = dernierActionHeure; }
    }


}
