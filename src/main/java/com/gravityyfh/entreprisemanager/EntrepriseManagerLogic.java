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
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EntrepriseManagerLogic {
    static EntrepriseManager plugin;
    private static Map<String, Entreprise> entreprises;
    private static File entrepriseFile;
    private static FileConfiguration entrepriseConfig;

    private final Map<String, Double> activiteHoraireValeur = new ConcurrentHashMap<>();
    private Map<String, Set<Material>> blocsAutorisesParTypeEntreprise = new HashMap<>();
    private Map<String, String> invitations = new HashMap<>();
    private final Map<UUID, DemandeCreation> demandesEnAttente = new HashMap<>();
    private Map<UUID, Map<String, ActionInfo>> joueurActivites = new HashMap<>();
    private BukkitTask hourlyTask;

    public EntrepriseManagerLogic(EntrepriseManager plugin) {
        EntrepriseManagerLogic.plugin = plugin;
        entreprises = new HashMap<>();
        entrepriseFile = new File(plugin.getDataFolder(), "entreprise.yml");
        entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);
        loadEntreprises();
        chargerRestrictionsActions();
        planifierTachesHoraires();
    }

    // --- DEBUT: Idées 1 & 4 : Revenu d'Entreprise par Activité Physique ---
    public void enregistrerActionProductive(Player player, String actionType, Material material, int quantite) {
        if (player == null || material == null || quantite <= 0) return;
        String nomEntrepriseJoueur = getNomEntrepriseDuMembre(player.getName());
        if (nomEntrepriseJoueur == null) return;
        Entreprise entreprise = entreprises.get(nomEntrepriseJoueur);
        if (entreprise == null) {
            plugin.getLogger().warning("Aucune entreprise trouvée pour " + nomEntrepriseJoueur + " lors de l'enregistrement d'action.");
            return;
        }
        String typeEntreprise = entreprise.getType();
        String basePathConfig = "types-entreprise." + typeEntreprise + ".activites-payantes." + actionType.toUpperCase();
        String materialPathConfig = basePathConfig + "." + material.name();
        if (!plugin.getConfig().contains(materialPathConfig)) return;
        double valeurUnitaire = plugin.getConfig().getDouble(materialPathConfig, 0.0);
        if (valeurUnitaire <= 0) return;
        double valeurTotaleAction = valeurUnitaire * quantite;
        activiteHoraireValeur.merge(nomEntrepriseJoueur, valeurTotaleAction, Double::sum);
    }

    public String getNomEntrepriseDuMembre(String nomJoueur) {
        if (nomJoueur == null) return null;
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getGerant().equalsIgnoreCase(nomJoueur) || entreprise.getEmployes().contains(nomJoueur)) {
                return entreprise.getNom();
            }
        }
        return null;
    }
    // --- FIN: Idées 1 & 4 ---

    // --- DEBUT: Idées 1, 2 & 5 : Tâches Horaires (CA, Primes, Chômage) ---
    private void planifierTachesHoraires() {
        if (hourlyTask != null && !hourlyTask.isCancelled()) {
            hourlyTask.cancel();
        }
        long ticksParHeure = 20L * 60L * 60L;
        hourlyTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("[EntrepriseManager] Début du cycle horaire des activités économiques...");
                traiterChiffreAffairesHoraire();
                payerPrimesHorairesAuxEmployes();
                payerAllocationChomageHoraire();
                plugin.getLogger().info("[EntrepriseManager] Fin du cycle horaire des activités économiques.");
            }
        }.runTaskTimer(plugin, ticksParHeure, ticksParHeure);
        plugin.getLogger().info("[EntrepriseManager] Tâches horaires (CA, Primes, Chômage) planifiées.");
    }


    public void traiterChiffreAffairesHoraire() {
        this.plugin.getLogger().info("[EntrepriseManager] Traitement du chiffre d'affaires horaire...");
        if (this.entreprises.isEmpty()) {
            this.plugin.getLogger().info("[EntrepriseManager] Aucune entreprise, aucun CA à traiter.");
            this.activiteHoraireValeur.clear(); // S'assurer que c'est vide si aucune entreprise
            return;
        }

        double pourcentageTaxes = this.plugin.getConfig().getDouble("finance.pourcentage-taxes", 15.0);
        boolean caTraiteGlobal = false; // Pour savoir si au moins une entreprise a eu un CA traité

        // Itérer sur une copie pour éviter ConcurrentModificationException si la map est modifiée ailleurs
        // Bien que dans ce flux séquentiel, ce ne soit pas strictement nécessaire si rien d'autre ne touche à activiteHoraireValeur.
        for (Map.Entry<String, Double> entry : new HashMap<>(this.activiteHoraireValeur).entrySet()) {
            String nomEntreprise = entry.getKey();
            double caBrutHoraire = entry.getValue();

            if (caBrutHoraire <= 0) {
                this.activiteHoraireValeur.put(nomEntreprise, 0.0); // Réinitialiser même si 0 pour la propreté
                continue; // Pas de CA à traiter pour cette entreprise
            }

            Entreprise entreprise = this.entreprises.get(nomEntreprise);
            if (entreprise == null) {
                this.plugin.getLogger().warning("[EntrepriseManager] Entreprise '" + nomEntreprise + "' non trouvée lors du traitement du CA horaire, mais avait une activité enregistrée (valeur: " + caBrutHoraire + "). Cette valeur sera perdue.");
                this.activiteHoraireValeur.remove(nomEntreprise); // Nettoyer l'entrée pour éviter des problèmes futurs
                continue;
            }

            caTraiteGlobal = true; // Au moins une entreprise a du CA
            double ancienSolde = entreprise.getSolde();
            double taxesCalculees = caBrutHoraire * (pourcentageTaxes / 100.0);
            double caNetHoraire = caBrutHoraire - taxesCalculees;

            entreprise.setSolde(ancienSolde + caNetHoraire);
            entreprise.setChiffreAffairesTotal(entreprise.getChiffreAffairesTotal() + caBrutHoraire); // Ajouter au CA brut total de l'entreprise

            this.plugin.getLogger().info(String.format(
                    "[EntrepriseManager] Rapport Entreprise '%s': Ancien Solde: %.2f€ | CA Brut: +%.2f€ | Taxes (%.1f%%): -%.2f€ | CA Net: +%.2f€ | Nouveau Solde: %.2f€",
                    nomEntreprise, ancienSolde, caBrutHoraire, pourcentageTaxes, taxesCalculees, caNetHoraire, entreprise.getSolde()
            ));

            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            String messageTitreGerant = "&6&l[Rapport Horaire] &e" + entreprise.getNom();
            String messageDetailsGerant = String.format(
                    "&7-----------------------------------\n" +
                            "&bAncien Solde: &f%.2f€\n" +
                            "&bChiffre d'Affaires Brut (cette heure): &f+%.2f€\n" +
                            "&cTaxes Prélevées (%.1f%%): &f-%.2f€\n" +
                            "&aChiffre d'Affaires Net Ajouté: &f+%.2f€\n" +
                            "&bNouveau Solde de l'Entreprise: &f&l%.2f€\n" +
                            "&7-----------------------------------",
                    ancienSolde, caBrutHoraire, pourcentageTaxes, taxesCalculees, caNetHoraire, entreprise.getSolde()
            );

            if (gerantPlayer != null && gerantPlayer.isOnline()) {
                gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', messageTitreGerant));
                for(String line : messageDetailsGerant.split("\n")){
                    gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
                }
            } else {
                OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(entreprise.getGerant()); // Utilise le nom du gérant
                if (offlineGerant.hasPlayedBefore()) {
                    String resumePourHorsLigne = String.format("Rapport Horaire pour '%s': CA Net +%.2f€. Nouveau Solde: %.2f€.",
                            entreprise.getNom(), caNetHoraire, entreprise.getSolde());
                    ajouterMessageGerantDifferre(entreprise.getGerant(), ChatColor.GREEN + resumePourHorsLigne, nomEntreprise, caNetHoraire);
                }
            }
            // Réinitialiser le compteur d'activité pour la prochaine heure pour CETTE entreprise
            this.activiteHoraireValeur.put(nomEntreprise, 0.0);
        }

        if (!caTraiteGlobal && !this.activiteHoraireValeur.isEmpty()) {
            // Si aucune activité positive n'a été traitée mais que la map n'est pas vide (contient des 0 ou des entrées pour des ent. supprimées)
            // On s'assure de vider pour toutes les entreprises connues au cas où (celles qui existent encore)
            for(String nomEntExistant : this.entreprises.keySet()){
                this.activiteHoraireValeur.put(nomEntExistant, 0.0);
            }
            this.plugin.getLogger().info("[EntrepriseManager] Aucune activité positive à traiter, tous les compteurs d'activité horaire ont été réinitialisés.");
        } else if (this.activiteHoraireValeur.isEmpty() && !this.entreprises.isEmpty()){
            this.plugin.getLogger().info("[EntrepriseManager] Aucune activité enregistrée pour les entreprises cette heure (map vide).");
        }


        if (caTraiteGlobal) { // Sauvegarder seulement si des changements ont eu lieu
            saveEntreprises();
        }
        this.plugin.getLogger().info("[EntrepriseManager] Fin du traitement du chiffre d'affaires horaire.");
    }


    public void payerPrimesHorairesAuxEmployes() {
        this.plugin.getLogger().info("[EntrepriseManager] Début du cycle de paiement des primes horaires...");
        if (this.entreprises.isEmpty()) {
            this.plugin.getLogger().info("[EntrepriseManager] Aucune entreprise pour le paiement des primes.");
            return;
        }

        boolean primesOntEtePayeesGlobal = false;

        for (Entreprise entreprise : this.entreprises.values()) {
            if (entreprise.getEmployes().isEmpty()) {
                continue; // Pas d'employés, pas de primes pour cette entreprise
            }

            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant()); // Pour notification au gérant

            // Utiliser une copie pour éviter ConcurrentModificationException si un employé est kick/leave pendant ce processus
            for (String employeNom : new HashSet<>(entreprise.getEmployes())) {
                double primeConfigurée = entreprise.getPrimePourEmploye(employeNom);

                if (primeConfigurée > 0) {
                    OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(employeNom);

                    if (!employeOffline.hasPlayedBefore() && !employeOffline.isOnline()) {
                        this.plugin.getLogger().warning("[EntrepriseManager] Tentative de paiement de prime à un joueur '" + employeNom + "' qui n'a jamais joué ou n'est pas reconnu. Prime non versée.");
                        continue;
                    }

                    if (entreprise.getSolde() >= primeConfigurée) {
                        EconomyResponse er = EntrepriseManager.getEconomy().depositPlayer(employeOffline, primeConfigurée);
                        if (er.transactionSuccess()) {
                            double soldeEntrepriseAvantPrime = entreprise.getSolde();
                            entreprise.setSolde(soldeEntrepriseAvantPrime - primeConfigurée);
                            primesOntEtePayeesGlobal = true;

                            String msgSuccesEmploye = String.format("&aVous avez reçu votre prime horaire de &e%.2f€&a de l'entreprise '&6%s&a'.", primeConfigurée, entreprise.getNom());
                            String msgSuccesGerant = String.format("&bPrime horaire de &e%.2f€&b versée à &3%s&b (Ent: '&6%s&b'). Solde entreprise: &e%.2f€ &7-> &e%.2f€",
                                    primeConfigurée, employeNom, entreprise.getNom(), soldeEntrepriseAvantPrime, entreprise.getSolde());

                            if (employeOffline.isOnline()) {
                                employeOffline.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', msgSuccesEmploye));
                            } else {
                                ajouterMessageEmployeDifferre(employeNom, ChatColor.translateAlternateColorCodes('&', msgSuccesEmploye), entreprise.getNom(), primeConfigurée);
                            }

                            if (gerantPlayer != null && gerantPlayer.isOnline()) {
                                gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgSuccesGerant));
                            } else {
                                OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(entreprise.getGerant());
                                if (offlineGerant.hasPlayedBefore()) {
                                    ajouterMessageGerantDifferre(entreprise.getGerant(), ChatColor.translateAlternateColorCodes('&', msgSuccesGerant), entreprise.getNom(), primeConfigurée);
                                }
                            }
                            this.plugin.getLogger().info("[EntrepriseManager] Prime de " + primeConfigurée + "€ payée à " + employeNom + " (Entreprise: " + entreprise.getNom() + ").");
                        } else {
                            this.plugin.getLogger().severe("[EntrepriseManager] Échec du dépôt Vault pour la prime de " + employeNom + " (Ent: " + entreprise.getNom() + "): " + er.errorMessage);
                            if (gerantPlayer != null && gerantPlayer.isOnline()) {
                                gerantPlayer.sendMessage(ChatColor.RED + "Erreur lors du versement de la prime à " + employeNom + " pour '" + entreprise.getNom() + "': " + er.errorMessage);
                            }
                        }
                    } else {
                        // Solde insuffisant
                        String msgEchecSoldeEmploye = String.format("&cL'entreprise '&6%s&c' n'a pas pu vous verser votre prime de &e%.2f€&c (solde insuffisant).", entreprise.getNom(), primeConfigurée);
                        String msgEchecSoldeGerant = String.format("&cL'entreprise '&6%s&c' n'a pas pu verser la prime de &e%.2f€&c à &3%s&c (solde actuel: %.2f€).", entreprise.getNom(), primeConfigurée, employeNom, entreprise.getSolde());

                        if (employeOffline.isOnline()) {
                            employeOffline.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', msgEchecSoldeEmploye));
                        } else {
                            ajouterMessageEmployeDifferre(employeNom, ChatColor.translateAlternateColorCodes('&', msgEchecSoldeEmploye), entreprise.getNom(), 0); // 0 car non payé
                        }

                        if (gerantPlayer != null && gerantPlayer.isOnline()) {
                            gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEchecSoldeGerant));
                        } else {
                            OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(entreprise.getGerant());
                            if (offlineGerant.hasPlayedBefore()) {
                                ajouterMessageGerantDifferre(entreprise.getGerant(), ChatColor.translateAlternateColorCodes('&', msgEchecSoldeGerant), entreprise.getNom(), 0);
                            }
                        }
                        this.plugin.getLogger().warning("[EntrepriseManager] Solde insuffisant ("+entreprise.getSolde()+") pour payer la prime de " + primeConfigurée + " à " + employeNom + " (Entreprise: " + entreprise.getNom() + ").");
                    }
                }
            }
        }

        if (primesOntEtePayeesGlobal) { // Sauvegarder seulement si des changements de solde ont eu lieu
            saveEntreprises();
        }
        this.plugin.getLogger().info("[EntrepriseManager] Fin du cycle de paiement des primes horaires.");
    }


    public void payerAllocationChomageHoraire() {
        this.plugin.getLogger().info("[EntrepriseManager] Début du versement de l'allocation chômage horaire...");
        double montantAllocation = this.plugin.getConfig().getDouble("finance.allocation-chomage-horaire", 0);

        if (montantAllocation <= 0) {
            this.plugin.getLogger().info("[EntrepriseManager] Allocation chômage désactivée (montant <= 0).");
            return;
        }

        int joueursPayes = 0;
        for (Player joueurConnecte : Bukkit.getOnlinePlayers()) {
            if (getNomEntrepriseDuMembre(joueurConnecte.getName()) == null) { // Si ni gérant, ni employé
                double ancienSoldeJoueur = EntrepriseManager.getEconomy().getBalance(joueurConnecte);
                EconomyResponse er = EntrepriseManager.getEconomy().depositPlayer(joueurConnecte, montantAllocation);

                if (er.transactionSuccess()) {
                    double nouveauSoldeJoueur = EntrepriseManager.getEconomy().getBalance(joueurConnecte); // Récupérer après dépôt
                    joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&l[Allocation Chômage]"));
                    joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format("&bAncien solde: &f%.2f€", ancienSoldeJoueur)));
                    joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format("&aAllocation horaire reçue: &f+%.2f€", montantAllocation)));
                    joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format("&bNouveau solde: &f&l%.2f€", nouveauSoldeJoueur)));
                    joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7--------------------------"));
                    joueursPayes++;
                } else {
                    this.plugin.getLogger().warning("[EntrepriseManager] Impossible de verser l'allocation chômage à " + joueurConnecte.getName() + ": " + er.errorMessage);
                    joueurConnecte.sendMessage(ChatColor.RED + "Erreur lors du versement de votre allocation chômage. Veuillez contacter un administrateur.");
                }
            }
        }

        if (joueursPayes > 0) {
            this.plugin.getLogger().info("[EntrepriseManager] " + joueursPayes + " joueur(s) ont reçu l'allocation chômage ("+String.format("%,.2f", montantAllocation)+"€ chacun).");
        } else {
            this.plugin.getLogger().info("[EntrepriseManager] Aucun joueur en ligne éligible pour l'allocation chômage cette heure.");
        }
    }
    // --- FIN: Idées 1, 2 & 5 ---

    // --- DEBUT: Idée 3 : Restrictions d'Actions et Limites Horaires ---
    private void chargerRestrictionsActions() {
        blocsAutorisesParTypeEntreprise.clear();
        ConfigurationSection typesSection = plugin.getConfig().getConfigurationSection("types-entreprise");
        if (typesSection != null) {
            for (String typeKey : typesSection.getKeys(false)) {
                List<String> blocStrings = typesSection.getStringList(typeKey + ".blocs-autorisés");
                Set<Material> materials = new HashSet<>();
                for (String blocStr : blocStrings) {
                    Material mat = Material.matchMaterial(blocStr.toUpperCase());
                    if (mat != null) materials.add(mat);
                    else plugin.getLogger().warning("[EntrepriseManager] Matériel non reconnu dans config (blocs-autorisés) pour " + typeKey + ": " + blocStr);
                }
                blocsAutorisesParTypeEntreprise.put(typeKey, materials);
            }
        }
        plugin.getLogger().info("[EntrepriseManager] Restrictions d'actions (legacy blocs-autorisés) chargées.");
    }

    public boolean verifierEtGererRestrictionAction(Player player, String actionTypeString, Material material, int quantite) {
        String nomEntrepriseJoueur = getNomEntrepriseDuMembre(player.getName());
        ConfigurationSection typesEntreprisesConfig = plugin.getConfig().getConfigurationSection("types-entreprise");
        if (typesEntreprisesConfig == null) return false;

        for (String typeEntrepriseSpecialise : typesEntreprisesConfig.getKeys(false)) {
            String restrictionPath = "types-entreprise." + typeEntrepriseSpecialise + ".action_restrictions." + actionTypeString + "." + material.name();
            if (plugin.getConfig().contains(restrictionPath)) {
                boolean estMembreDuTypeSpecialise = false;
                if (nomEntrepriseJoueur != null) {
                    Entreprise entrepriseJoueurObj = entreprises.get(nomEntrepriseJoueur);
                    if (entrepriseJoueurObj != null && entrepriseJoueurObj.getType().equals(typeEntrepriseSpecialise)) {
                        estMembreDuTypeSpecialise = true;
                    }
                }
                if (estMembreDuTypeSpecialise) return false;
                else {
                    int limiteNonMembre = plugin.getConfig().getInt("types-entreprise." + typeEntrepriseSpecialise + ".limite-non-membre-par-heure", 0);
                    if (limiteNonMembre <= 0) return false;

                    String actionIdentifier = typeEntrepriseSpecialise + "_" + actionTypeString + "_" + material.name();
                    ActionInfo info = joueurActivites.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                            .computeIfAbsent(actionIdentifier, k -> new ActionInfo());
                    LocalDateTime maintenant = LocalDateTime.now();
                    if (info.getDernierActionHeure().getHour() != maintenant.getHour()) {
                        info.reinitialiserActions(maintenant);
                    }
                    if (info.getNombreActions() + quantite > limiteNonMembre) {
                        List<String> messagesErreur = plugin.getConfig().getStringList("types-entreprise." + typeEntrepriseSpecialise + ".message-erreur-restriction");
                        if (messagesErreur.isEmpty()) messagesErreur = plugin.getConfig().getStringList("types-entreprise." + typeEntrepriseSpecialise + ".message-erreur");
                        for (String msg : messagesErreur) player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                        player.sendMessage(ChatColor.GRAY + "(Limite: " + info.getNombreActions() + "/" + limiteNonMembre + " pour " + material.name() + ")");
                        return true;
                    } else {
                        info.incrementerActions(quantite);
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public boolean isActionAllowedForPlayer(Material blockType, UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return false;
        String categorieActivite = getCategorieActivitePourBlocAutorise(blockType);
        if (categorieActivite != null) {
            String nomEntrepriseJoueur = getNomEntrepriseDuMembre(player.getName());
            if (nomEntrepriseJoueur != null) {
                Entreprise entrepriseJoueurObj = entreprises.get(nomEntrepriseJoueur);
                if (entrepriseJoueurObj != null && entrepriseJoueurObj.getType().equals(categorieActivite)) {
                    return true;
                }
            }
            String actionIdentifier = categorieActivite + "_BLOCK_BREAK_LEGACY_" + blockType.name();
            ActionInfo info = joueurActivites.computeIfAbsent(playerUUID, k -> new HashMap<>())
                    .computeIfAbsent(actionIdentifier, k -> new ActionInfo());
            LocalDateTime maintenant = LocalDateTime.now();
            if (info.getDernierActionHeure().getHour() != maintenant.getHour()) {
                info.reinitialiserActions(maintenant);
            }
            int limite = plugin.getConfig().getInt("types-entreprise." + categorieActivite + ".limite-non-membre-par-heure", 3);
            if (info.getNombreActions() < limite) {
                info.incrementerActions(1); return true;
            } else {
                List<String> messagesErreur = plugin.getConfig().getStringList("types-entreprise." + categorieActivite + ".message-erreur");
                for (String message : messagesErreur) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
                return false;
            }
        }
        return true;
    }

    private String getCategorieActivitePourBlocAutorise(Material blockType) {
        if (blocsAutorisesParTypeEntreprise == null) return null;
        for (Map.Entry<String, Set<Material>> entry : blocsAutorisesParTypeEntreprise.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(blockType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String getCategorieActivite(Material blockType) {
        ConfigurationSection typesSection = plugin.getConfig().getConfigurationSection("types-entreprise");
        if (typesSection == null) return null;
        for (String categorie : typesSection.getKeys(false)) {
            List<String> blocsAutorises = plugin.getConfig().getStringList("types-entreprise." + categorie + ".blocs-autorisés");
            if (blocsAutorises.contains(blockType.name())) return categorie;
            if (plugin.getConfig().contains("types-entreprise." + categorie + ".action_restrictions.BLOCK_BREAK." + blockType.name())) return categorie;
        }
        return null;
    }
    // --- FIN: Idée 3 ---

    // --- DEBUT: Méthodes de gestion d'entreprise (CRUD, employés, etc.) ---
    public Entreprise getEntreprise(String nomEntreprise) {
        return entreprises.get(nomEntreprise);
    }

    public void handleEntrepriseRemoval(Entreprise entreprise, String reason) {
        if (entreprise == null) { plugin.getLogger().warning("[EntrepriseManager] Tentative de suppression d'une entreprise null. Raison: " + reason); return; }
        String nomEntreprise = entreprise.getNom();
        String gerantNom = entreprise.getGerant();
        plugin.getLogger().info("[EntrepriseManager] Suppression de '" + nomEntreprise + "' (Gérant: " + gerantNom + "). Raison: " + reason);
        checkAndRemoveShopsIfNeeded(gerantNom, nomEntreprise);
        entreprises.remove(nomEntreprise);
        activiteHoraireValeur.remove(nomEntreprise);
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection gerantSection = playersConfig.getConfigurationSection("players." + gerantNom);
        if (gerantSection != null) {
            gerantSection.set(nomEntreprise, null);
            if (gerantSection.getKeys(false).isEmpty()) playersConfig.set("players." + gerantNom, null);
        }
        for (String employeNom : new ArrayList<>(entreprise.getEmployes())) {
            ConfigurationSection employeSection = playersConfig.getConfigurationSection("players." + employeNom);
            if (employeSection != null) {
                employeSection.set(nomEntreprise, null);
                if (employeSection.getKeys(false).isEmpty()) playersConfig.set("players." + employeNom, null);
            }
            Player employePlayer = Bukkit.getPlayerExact(employeNom);
            if (employePlayer != null && employePlayer.isOnline()) employePlayer.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' a été dissoute. Raison: " + reason);
        }
        try { playersConfig.save(playersFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "[EntrepriseManager] Erreur sauvegarde players.yml après suppression '" + nomEntreprise + "'.", e); }
        saveEntreprises();
        Player gerantPlayer = Bukkit.getPlayerExact(gerantNom);
        if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.RED + "Votre entreprise '" + nomEntreprise + "' a été dissoute. Raison: " + reason);
        plugin.getLogger().info("[EntrepriseManager] Suppression de '" + nomEntreprise + "' terminée.");
    }

    public void supprimerEntreprise(Player initiator, String nomEntreprise) {
        Entreprise entrepriseASupprimer = entreprises.get(nomEntreprise);
        if (entrepriseASupprimer == null) {
            initiator.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'existe pas.");
            return;
        }
        if (!entrepriseASupprimer.getGerant().equalsIgnoreCase(initiator.getName()) && !initiator.hasPermission("entreprisemanager.admin.deleteany")) {
            initiator.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de supprimer cette entreprise.");
            return;
        }
        String reason = "Dissolution par " + initiator.getName();
        if(initiator.hasPermission("entreprisemanager.admin.deleteany") && !entrepriseASupprimer.getGerant().equalsIgnoreCase(initiator.getName())) {
            reason += " (Admin)";
        } else {
            reason += " (Gérant)";
        }
        handleEntrepriseRemoval(entrepriseASupprimer, reason);
        if(initiator.hasPermission("entreprisemanager.admin.deleteany") && !entrepriseASupprimer.getGerant().equalsIgnoreCase(initiator.getName())){
            initiator.sendMessage(ChatColor.GREEN + "L'entreprise '" + nomEntreprise + "' (gérant: "+entrepriseASupprimer.getGerant()+") a été dissoute par vos soins.");
        }
    }

    private void checkAndRemoveShopsIfNeeded(String gerantNom, String entrepriseEnCoursDeSuppression) {
        long autresEntreprisesGerees = entreprises.values().stream()
                .filter(e -> e.getGerant().equalsIgnoreCase(gerantNom) && !e.getNom().equalsIgnoreCase(entrepriseEnCoursDeSuppression))
                .count();
        if (autresEntreprisesGerees == 0) {
            String command = "quickshop removeall " + gerantNom;
            plugin.getLogger().info("[EntrepriseManager] Plus d'entreprise pour " + gerantNom + ". Exécution: " + command);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    public void declareEntreprise(Player maireCreateur, String ville, String nomEntreprise, String type, String nomGerant, String siret) {
        if (this.entreprises.containsKey(nomEntreprise)) { maireCreateur.sendMessage(ChatColor.RED + "Nom d'entreprise '" + nomEntreprise + "' déjà pris."); return; }
        OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(nomGerant);
        if (!offlineGerant.hasPlayedBefore() && !offlineGerant.isOnline()) { maireCreateur.sendMessage(ChatColor.RED + "Joueur '" + nomGerant + "' inconnu."); return; }

        Entreprise nouvelleEntreprise = new Entreprise(nomEntreprise, ville, type, nomGerant, new HashSet<>(), 0.0, siret);
        this.entreprises.put(nomEntreprise, nouvelleEntreprise);
        this.activiteHoraireValeur.put(nomEntreprise, 0.0);
        saveEntreprises();
        maireCreateur.sendMessage(ChatColor.GREEN + "Entreprise '" + nomEntreprise + "' (" + type + ") proposée pour " + nomGerant + ".");
    }

    public void inviterEmploye(Player gerant, String nomEntreprise, Player joueurInvite) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { gerant.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' introuvable."); return; }
        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName())) { gerant.sendMessage(ChatColor.RED + "Pas le gérant."); return; }
        if (gerant.getName().equalsIgnoreCase(joueurInvite.getName())) { gerant.sendMessage(ChatColor.RED + "Impossible de s'auto-inviter."); return; }
        if (getNomEntrepriseDuMembre(joueurInvite.getName()) != null) { gerant.sendMessage(ChatColor.RED + joueurInvite.getName() + " est déjà dans une entreprise."); return; }
        if (entreprise.getEmployes().size() >= plugin.getConfig().getInt("finance.max-employer-par-entreprise", 10)) { gerant.sendMessage(ChatColor.RED + "Limite d'employés atteinte."); return; }
        double distanceMax = plugin.getConfig().getDouble("invitation.distance-max", 10.0);
        if (!gerant.getWorld().equals(joueurInvite.getWorld()) || gerant.getLocation().distanceSquared(joueurInvite.getLocation()) > distanceMax * distanceMax) {
            gerant.sendMessage(ChatColor.RED + joueurInvite.getName() + " trop loin."); return;
        }
        invitations.put(joueurInvite.getName(), nomEntreprise);
        envoyerInvitationVisuelle(joueurInvite, nomEntreprise, gerant.getName(), entreprise.getType());
        gerant.sendMessage(ChatColor.GREEN + "Invitation envoyée à " + joueurInvite.getName() + " pour '" + nomEntreprise + "'.");
    }

    private void envoyerInvitationVisuelle(Player joueurInvite, String nomEntreprise, String nomGerant, String typeEntreprise) {
        joueurInvite.sendMessage(ChatColor.GOLD + "------------------------------------------");
        joueurInvite.sendMessage(ChatColor.YELLOW + nomGerant + " (Gérant de '" + nomEntreprise + "', type: " + typeEntreprise + ") vous invite à rejoindre son entreprise !");
        TextComponent accepterMsg = new TextComponent("        [ACCEPTER]");
        accepterMsg.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accepterMsg.setBold(true);
        accepterMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise accepter"));
        accepterMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour accepter").color(net.md_5.bungee.api.ChatColor.GRAY).create()));
        TextComponent refuserMsg = new TextComponent("   [REFUSER]");
        refuserMsg.setColor(net.md_5.bungee.api.ChatColor.RED);
        refuserMsg.setBold(true);
        refuserMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise refuser"));
        refuserMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour refuser").color(net.md_5.bungee.api.ChatColor.GRAY).create()));
        TextComponent ligneActions = new TextComponent("");
        ligneActions.addExtra(accepterMsg);
        ligneActions.addExtra(refuserMsg);
        joueurInvite.spigot().sendMessage(ligneActions);
        joueurInvite.sendMessage(ChatColor.GOLD + "------------------------------------------");
    }

    public void handleAccepterCommand(Player joueur) {
        String joueurNom = joueur.getName();
        if (!invitations.containsKey(joueurNom)) {
            joueur.sendMessage(ChatColor.RED + "Vous n'avez aucune invitation en attente ou elle a expiré.");
            return;
        }

        String nomEntreprise = invitations.remove(joueurNom);
        Entreprise entreprise = getEntreprise(nomEntreprise);

        if (entreprise == null) {
            joueur.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'existe plus. L'invitation est annulée.");
            return;
        }

        int maxEmployes = plugin.getConfig().getInt("finance.max-employer-par-entreprise", 10);
        if (entreprise.getEmployes().size() >= maxEmployes) { // getEmployes() ici est OK car c'est pour une vérification de taille
            joueur.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' a atteint sa limite d'employés pendant que vous considériez l'invitation.");
            Player gerantP = Bukkit.getPlayerExact(entreprise.getGerant());
            if (gerantP != null && gerantP.isOnline()) {
                gerantP.sendMessage(ChatColor.YELLOW + joueurNom + " a tenté de rejoindre '" + nomEntreprise + "', mais l'entreprise est pleine.");
            }
            return;
        }

        // Appel correct à addEmploye
        addEmploye(nomEntreprise, joueurNom);

        joueur.sendMessage(ChatColor.GREEN + "Vous avez rejoint l'entreprise '" + nomEntreprise + "' !");

        Player gerantP = Bukkit.getPlayerExact(entreprise.getGerant());
        if (gerantP != null && gerantP.isOnline()) {
            gerantP.sendMessage(ChatColor.GREEN + joueurNom + " a accepté votre invitation et a rejoint '" + nomEntreprise + "'.");
        }
    }

    public void handleRefuserCommand(Player joueur) {
        String joueurNom = joueur.getName();
        if (!invitations.containsKey(joueurNom)) { joueur.sendMessage(ChatColor.RED + "Aucune invitation valide."); return; }
        String nomEntreprise = invitations.remove(joueurNom);
        joueur.sendMessage(ChatColor.YELLOW + "Vous avez refusé l'invitation pour '" + nomEntreprise + "'.");
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise != null) {
            Player gerantP = Bukkit.getPlayerExact(entreprise.getGerant());
            if (gerantP != null) gerantP.sendMessage(ChatColor.YELLOW + joueurNom + " a refusé l'invitation pour '" + nomEntreprise + "'.");
        }
    }

    public void addEmploye(String nomEntreprise, String nomJoueur) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise != null) {
            // Utiliser la méthode interne pour modifier la collection d'employés
            Set<String> employesModifiables = entreprise.getEmployesInternal();
            if (employesModifiables.add(nomJoueur)) { // True si l'employé n'était pas déjà présent et a été ajouté
                entreprise.setPrimePourEmploye(nomJoueur, 0.0); // Prime par défaut à 0
                plugin.getLogger().info("[EntrepriseManager] " + nomJoueur + " ajouté comme employé à '" + nomEntreprise + "'.");
                saveEntreprises(); // Sauvegarder les changements
            } else {
                plugin.getLogger().warning("[EntrepriseManager] " + nomJoueur + " est déjà un employé de '" + nomEntreprise + "', ajout ignoré.");
                // Optionnel : informer le joueur/gérant si l'employé était déjà là
            }
        } else {
            plugin.getLogger().severe("[EntrepriseManager] Tentative d'ajout d'un employé à une entreprise non existante: " + nomEntreprise);
        }
    }

    public void kickEmploye(Player gerant, String nomEntreprise, String nomEmployeAKick) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { gerant.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' introuvable."); return; }
        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName())) { gerant.sendMessage(ChatColor.RED + "Pas le gérant."); return; }
        if (!entreprise.getEmployes().contains(nomEmployeAKick)) { gerant.sendMessage(ChatColor.RED + nomEmployeAKick + " n'est pas employé ici."); return; }

        Set<String> employesModifiables = entreprise.getEmployesInternal(); // Accéder à la collection modifiable
        if (employesModifiables.remove(nomEmployeAKick)) {
            entreprise.retirerPrimeEmploye(nomEmployeAKick);
            plugin.getLogger().info("[EntrepriseManager] " + nomEmployeAKick + " retiré de '" + nomEntreprise + "'.");
            saveEntreprises();
            gerant.sendMessage(ChatColor.GREEN + nomEmployeAKick + " viré de '" + nomEntreprise + "'.");
            Player employeKickeP = Bukkit.getPlayerExact(nomEmployeAKick);
            if (employeKickeP != null) employeKickeP.sendMessage(ChatColor.RED + "Viré de '" + nomEntreprise + "'.");
        } else {
            gerant.sendMessage(ChatColor.RED + "Erreur lors du licenciement de " + nomEmployeAKick + ".");
        }
    }

    public void leaveEntreprise(Player joueur, String nomEntreprise) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { joueur.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' introuvable."); return; }
        if (entreprise.getGerant().equalsIgnoreCase(joueur.getName())) { joueur.sendMessage(ChatColor.RED + "Gérant, utilisez /entreprise delete <NomEntreprise>."); return; }
        if (!entreprise.getEmployes().contains(joueur.getName())) { joueur.sendMessage(ChatColor.RED + "Pas employé ici."); return; }

        Set<String> employesModifiables = entreprise.getEmployesInternal(); // Accéder à la collection modifiable
        if (employesModifiables.remove(joueur.getName())) {
            entreprise.retirerPrimeEmploye(joueur.getName());
            plugin.getLogger().info("[EntrepriseManager] " + joueur.getName() + " a quitté '" + nomEntreprise + "'.");
            saveEntreprises();
            joueur.sendMessage(ChatColor.GREEN + "Quitté '" + nomEntreprise + "'.");
            Player gerantP = Bukkit.getPlayerExact(entreprise.getGerant());
            if (gerantP != null) gerantP.sendMessage(ChatColor.YELLOW + joueur.getName() + " a quitté '" + nomEntreprise + "'.");
        } else {
            joueur.sendMessage(ChatColor.RED + "Erreur en quittant l'entreprise.");
        }
    }
    // --- FIN: Méthodes de gestion d'entreprise ---

    // --- DEBUT: Commandes de création d'entreprise et validation ---
    public void proposerCreationEntreprise(Player maire, Player gerantCible, String type, String ville, String nomEntreprisePropose, String siret) {
        double coutCreation = plugin.getConfig().getDouble("types-entreprise." + type + ".cout-creation", 0.0);
        double distanceMax = plugin.getConfig().getDouble("invitation.distance-max", 10.0);
        if (!gerantCible.isOnline()) { maire.sendMessage(ChatColor.RED + gerantCible.getName() + " doit être en ligne."); return; }
        if (!maire.getWorld().equals(gerantCible.getWorld()) || maire.getLocation().distanceSquared(gerantCible.getLocation()) > distanceMax * distanceMax) {
            maire.sendMessage(ChatColor.RED + gerantCible.getName() + " est trop loin."); return;
        }
        if (getNomEntrepriseDuMembre(gerantCible.getName()) != null) { maire.sendMessage(ChatColor.RED + gerantCible.getName() + " est déjà dans une entreprise."); return; }
        int maxEnt = plugin.getConfig().getInt("finance.max-entreprises-par-gerant", 1);
        if (entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(gerantCible.getName())).count() >= maxEnt) {
            maire.sendMessage(ChatColor.RED + gerantCible.getName() + " a atteint la limite d'entreprises gérées (" + maxEnt + ")."); return;
        }

        DemandeCreation demande = new DemandeCreation(maire, gerantCible, type, ville, siret, nomEntreprisePropose, coutCreation, 60000L);
        demandesEnAttente.put(gerantCible.getUniqueId(), demande);
        maire.sendMessage(ChatColor.GREEN + "Proposition de création envoyée à " + gerantCible.getName() + " pour l'entreprise '" + nomEntreprisePropose + "'.");
        envoyerInvitationVisuelleContrat(gerantCible, demande);
    }

    private void envoyerInvitationVisuelleContrat(Player gerantCible, DemandeCreation demande) {
        gerantCible.sendMessage(ChatColor.GOLD + "---------------- Contrat de Gérance ----------------");
        gerantCible.sendMessage(ChatColor.AQUA + "Maire: " + ChatColor.WHITE + demande.maire.getName() + ChatColor.AQUA + " Ville: " + ChatColor.WHITE + demande.ville);
        gerantCible.sendMessage(ChatColor.AQUA + "Type: " + ChatColor.WHITE + demande.type + ChatColor.AQUA + " Nom: " + ChatColor.WHITE + demande.nomEntreprise);
        gerantCible.sendMessage(ChatColor.AQUA + "SIRET: " + ChatColor.WHITE + demande.siret);
        gerantCible.sendMessage(ChatColor.AQUA + "Coût de création à régler: " + ChatColor.GREEN + String.format("%,.2f€", demande.cout));
        gerantCible.sendMessage(ChatColor.YELLOW + "Vous avez 60 secondes pour accepter ou refuser.");

        TextComponent accepterMsg = new TextComponent("        [VALIDER LE CONTRAT]");
        accepterMsg.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accepterMsg.setBold(true);
        accepterMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise validercreation"));
        accepterMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour signer et payer " + String.format("%,.2f€", demande.cout)).create()));

        TextComponent refuserMsg = new TextComponent("   [REFUSER LE CONTRAT]");
        refuserMsg.setColor(net.md_5.bungee.api.ChatColor.RED);
        refuserMsg.setBold(true);
        refuserMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise annulercreation"));
        refuserMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour refuser l'offre de gérance").create()));

        TextComponent ligneActions = new TextComponent("");
        ligneActions.addExtra(accepterMsg);
        ligneActions.addExtra(refuserMsg);
        gerantCible.spigot().sendMessage(ligneActions);
        gerantCible.sendMessage(ChatColor.GOLD + "--------------------------------------------------");
        // Expiration automatique
        UUID gerantUUID = gerantCible.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (demandesEnAttente.containsKey(gerantUUID)) {
                DemandeCreation d = demandesEnAttente.remove(gerantUUID);
                d.gerant.sendMessage(ChatColor.RED + "Le contrat pour '" + d.nomEntreprise + "' a expiré.");
                d.maire.sendMessage(ChatColor.RED + "Le contrat pour '" + d.nomEntreprise + "' (gérant: " + d.gerant.getName() + ") a expiré.");
            }
        }, 20L * 60L); // 60 secondes
    }

    public void validerCreationEntreprise(Player gerantSignataire) {
        UUID uuid = gerantSignataire.getUniqueId();
        if (!demandesEnAttente.containsKey(uuid)) { gerantSignataire.sendMessage(ChatColor.RED + "Aucune demande de création en attente."); return; }
        DemandeCreation demande = demandesEnAttente.remove(uuid);
        if (demande.isExpired()) { gerantSignataire.sendMessage(ChatColor.RED + "La demande a expiré."); demande.maire.sendMessage(ChatColor.RED + "La demande pour " + gerantSignataire.getName() + " a expiré."); return; }
        if (!EntrepriseManager.getEconomy().has(gerantSignataire, demande.cout)) { gerantSignataire.sendMessage(ChatColor.RED + "Fonds insuffisants (" + demande.cout + "€ requis)."); demande.maire.sendMessage(ChatColor.RED + "Création échouée: fonds insuffisants pour " + gerantSignataire.getName()); return; }
        EconomyResponse er = EntrepriseManager.getEconomy().withdrawPlayer(gerantSignataire, demande.cout);
        if (!er.transactionSuccess()) { gerantSignataire.sendMessage(ChatColor.RED + "Erreur paiement: " + er.errorMessage); demande.maire.sendMessage(ChatColor.RED + "Échec paiement par " + gerantSignataire.getName()); return; }
        declareEntreprise(demande.maire, demande.ville, demande.nomEntreprise, demande.type, demande.gerant.getName(), demande.siret);
        gerantSignataire.sendMessage(ChatColor.GREEN + "Contrat accepté et payé (" + String.format("%,.2f€", demande.cout) + "). Entreprise '" + demande.nomEntreprise + "' créée !");
        demande.maire.sendMessage(ChatColor.GREEN + gerantSignataire.getName() + " a validé et payé la création de '" + demande.nomEntreprise + "'.");
    }

    public void refuserCreationEntreprise(Player gerantSignataire) {
        UUID uuid = gerantSignataire.getUniqueId();
        if (!demandesEnAttente.containsKey(uuid)) { gerantSignataire.sendMessage(ChatColor.RED + "Aucune demande en attente."); return; }
        DemandeCreation demande = demandesEnAttente.remove(uuid);
        gerantSignataire.sendMessage(ChatColor.RED + "Vous avez refusé le contrat de gérance pour '" + demande.nomEntreprise + "'.");
        demande.maire.sendMessage(ChatColor.RED + gerantSignataire.getName() + " a refusé le contrat de gérance pour '" + demande.nomEntreprise + "'.");
    }
    // --- FIN: Commandes de création ---

    // --- DEBUT: Getters et utilitaires divers ---
    public Collection<Entreprise> getEntreprises() {
        return Collections.unmodifiableCollection(entreprises.values());
    }

    public List<Entreprise> getEntreprisesByVille(String ville) {
        return entreprises.values().stream().filter(e -> e.getVille().equalsIgnoreCase(ville)).collect(Collectors.toList());
    }

    public String trouverNomEntrepriseParTypeEtGerant(String gerant, String type) {
        return entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(gerant) && e.getType().equalsIgnoreCase(type)).map(Entreprise::getNom).findFirst().orElse(null);
    }

    public Set<String> getGerantsAvecEntreprises() {
        return entreprises.values().stream().map(Entreprise::getGerant).collect(Collectors.toSet());
    }

    public Set<String> getTypesEntreprise() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("types-entreprise");
        return (section != null) ? section.getKeys(false) : Collections.emptySet();
    }

    public Entreprise getEntrepriseDuGerantEtType(String gerant, String type) {
        return entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(gerant) && e.getType().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public Collection<String> getPlayersInMayorTown(Player mayor) {
        if (!estMaire(mayor)) return Collections.emptyList();
        try {
            Town town = TownyAPI.getInstance().getResident(mayor.getName()).getTown();
            if (town != null) return town.getResidents().stream().map(Resident::getName).collect(Collectors.toList());
        } catch (NotRegisteredException e) { /* ignore */ }
        return Collections.emptyList();
    }

    public Collection<String> getAllOnlinePlayersNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    public Collection<String> getAllTownsNames() {
        if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return Collections.emptyList();
        return TownyAPI.getInstance().getTowns().stream().map(Town::getName).collect(Collectors.toList());
    }

    public List<Entreprise> getEntreprisesGereesPar(String nomGerant) {
        return entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(nomGerant)).collect(Collectors.toList());
    }

    public List<String> getNomDesEntreprisesGereesPar(String nomGerant) {
        return entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(nomGerant)).map(Entreprise::getNom).collect(Collectors.toList());
    }

    public boolean peutCreerEntreprise(Player gerantPotentiel) {
        long count = entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(gerantPotentiel.getName())).count();
        return count < plugin.getConfig().getInt("finance.max-entreprises-par-gerant", 1);
    }

    public boolean peutAjouterEmploye(String nomEntreprise) {
        Entreprise e = getEntreprise(nomEntreprise);
        if (e == null) return false;
        return e.getEmployes().size() < plugin.getConfig().getInt("finance.max-employer-par-entreprise", 10);
    }

    public boolean joueurPeutRejoindreAutreEntreprise(String nomJoueur) {
        long count = 0;
        for(Entreprise e : entreprises.values()){
            if(e.getGerant().equalsIgnoreCase(nomJoueur) || e.getEmployes().contains(nomJoueur)){
                count++;
            }
        }
        return count < plugin.getConfig().getInt("finance.max-travail-joueur", 1);
    }

    public void renameEntreprise(Player gerant, String ancienNom, String nouveauNom) {
        Entreprise entreprise = getEntreprise(ancienNom);
        if (entreprise == null) { gerant.sendMessage(ChatColor.RED + "L'entreprise '" + ancienNom + "' n'existe pas."); return; }
        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName()) && !gerant.hasPermission("entreprisemanager.admin.renameany")) {
            gerant.sendMessage(ChatColor.RED + "Vous n'êtes pas le gérant ou n'avez pas la permission."); return;
        }
        if (ancienNom.equalsIgnoreCase(nouveauNom)) { gerant.sendMessage(ChatColor.RED + "Nouveau nom identique à l'ancien."); return; }
        if (entreprises.containsKey(nouveauNom)) { gerant.sendMessage(ChatColor.RED + "Nom '" + nouveauNom + "' déjà pris."); return; }
        if (!nouveauNom.matches("^[a-zA-Z0-9_\\-]+$")) { gerant.sendMessage(ChatColor.RED + "Nom invalide (lettres, chiffres, _, -)."); return; }
        double coutRenommage = plugin.getConfig().getDouble("rename-cost", 2500);
        boolean adminRename = gerant.hasPermission("entreprisemanager.admin.renameany");
        if(!adminRename){
            if (!EntrepriseManager.getEconomy().has(gerant, coutRenommage)) { gerant.sendMessage(ChatColor.RED + "Pas assez d'argent (" + String.format("%,.2f", coutRenommage) + "€)."); return; }
            EconomyResponse er = EntrepriseManager.getEconomy().withdrawPlayer(gerant, coutRenommage);
            if (!er.transactionSuccess()) { gerant.sendMessage(ChatColor.RED + "Erreur paiement: " + er.errorMessage); return; }
        }
        entreprises.remove(ancienNom);
        Double caPotentiel = activiteHoraireValeur.remove(ancienNom);
        entreprise.setNom(nouveauNom);
        entreprises.put(nouveauNom, entreprise);
        if (caPotentiel != null) activiteHoraireValeur.put(nouveauNom, caPotentiel);
        else activiteHoraireValeur.put(nouveauNom, 0.0);
        saveEntreprises();
        String messageFinal = ChatColor.GREEN + "'" + ancienNom + "' renommée en '" + nouveauNom + "'";
        if(!adminRename) messageFinal += " pour " + String.format("%.2f€", coutRenommage);
        messageFinal += ".";
        gerant.sendMessage(messageFinal);
    }

    public void definirPrime(String nomEntreprise, String nomEmploye, double montantPrime) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) return;
        if (!entreprise.getEmployes().contains(nomEmploye)) {
            plugin.getLogger().warning("[EntrepriseManager] Tentative de définir prime pour '"+nomEmploye+"' non employé de '"+nomEntreprise+"'.");
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            if(gerantPlayer != null && gerantPlayer.isOnline()) {
                gerantPlayer.sendMessage(ChatColor.RED + "L'employé '" + nomEmploye + "' ne fait plus partie de l'entreprise '" + nomEntreprise + "'.");
            }
            return;
        }
        entreprise.setPrimePourEmploye(nomEmploye, montantPrime);
        saveEntreprises();
    }

    public String getTownNameFromPlayer(Player player) {
        if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return null;
        try {
            Resident resident = TownyAPI.getInstance().getResident(player.getName());
            if (resident != null && resident.hasTown()) return resident.getTown().getName();
        } catch (NotRegisteredException e) { /* ignore */ }
        return null;
    }

    public String generateSiret() {
        int longueurSiret = plugin.getConfig().getInt("siret.longueur", 14);
        return UUID.randomUUID().toString().replace("-", "").substring(0, Math.min(longueurSiret, 32));
    }

    public boolean estMaire(Player joueur) {
        if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return false;
        Resident resident = TownyAPI.getInstance().getResident(joueur.getName());
        return resident != null && resident.isMayor();
    }

    public boolean estMembreDeLaVille(String nomJoueur, String nomVille) {
        if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return false;
        try {
            Resident resident = TownyAPI.getInstance().getResident(nomJoueur);
            return resident != null && resident.hasTown() && resident.getTown().getName().equalsIgnoreCase(nomVille);
        } catch (NotRegisteredException e) { return false; }
    }

    public EntrepriseManager getPlugin() {
        return plugin;
    }

    public Collection<String> getTypesEntrepriseGereesPar(String nomGerant) {
        return entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(nomGerant)).map(Entreprise::getType).distinct().collect(Collectors.toList());
    }

    public Set<String> getEmployesDeLEntreprise(String nomEntreprise) {
        Entreprise e = getEntreprise(nomEntreprise);
        return (e != null) ? e.getEmployes() : Collections.emptySet();
    }

    public List<String> getTypesEntrepriseDuJoueur(String nomJoueur) {
        List<String> types = new ArrayList<>();
        for (Entreprise e : entreprises.values()) {
            if (e.getGerant().equalsIgnoreCase(nomJoueur) || e.getEmployes().contains(nomJoueur)) {
                types.add(e.getType());
            }
        }
        return types.stream().distinct().collect(Collectors.toList());
    }

    public void afficherSoldeEntreprise(Player player, String nomEntreprise){
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if(entreprise == null){ player.sendMessage(ChatColor.RED + "Entreprise introuvable."); return; }
        player.sendMessage(ChatColor.GREEN + "Solde de l'entreprise " + nomEntreprise + ": " + String.format("%,.2f", entreprise.getSolde()) + "€");
    }

    public void retirerArgent(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { player.sendMessage(ChatColor.RED + "Entreprise introuvable."); return; }
        if (!entreprise.getGerant().equalsIgnoreCase(player.getName())) { player.sendMessage(ChatColor.RED + "Vous n'êtes pas le gérant."); return; }
        if (montant <= 0) { player.sendMessage(ChatColor.RED + "Montant invalide."); return; }
        if (entreprise.getSolde() < montant) { player.sendMessage(ChatColor.RED + "Solde insuffisant (Solde: " + String.format("%,.2f", entreprise.getSolde()) + "€)."); return; }
        EconomyResponse er = EntrepriseManager.getEconomy().depositPlayer(player, montant);
        if (er.transactionSuccess()) {
            entreprise.setSolde(entreprise.getSolde() - montant);
            saveEntreprises();
            player.sendMessage(ChatColor.GREEN + "Retiré " + String.format("%,.2f", montant) + "€ de '" + nomEntreprise + "'. Nouveau solde ent.: " + String.format("%,.2f", entreprise.getSolde()) + "€.");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur dépôt sur votre compte: " + er.errorMessage);
        }
    }

    public void deposerArgent(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { player.sendMessage(ChatColor.RED + "Entreprise introuvable."); return; }
        if (!entreprise.getGerant().equalsIgnoreCase(player.getName())) { player.sendMessage(ChatColor.RED + "Seul le gérant peut déposer."); return; }
        if (montant <= 0) { player.sendMessage(ChatColor.RED + "Montant invalide."); return; }
        if (!EntrepriseManager.getEconomy().has(player, montant)) { player.sendMessage(ChatColor.RED + "Pas assez d'argent (Requis: " + String.format("%,.2f", montant) + "€)."); return; }
        EconomyResponse er = EntrepriseManager.getEconomy().withdrawPlayer(player, montant);
        if (er.transactionSuccess()) {
            entreprise.setSolde(entreprise.getSolde() + montant);
            saveEntreprises();
            player.sendMessage(ChatColor.GREEN + "Déposé " + String.format("%,.2f", montant) + "€ dans '" + nomEntreprise + "'. Nouveau solde ent.: " + String.format("%,.2f", entreprise.getSolde()) + "€.");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur retrait de votre compte: " + er.errorMessage);
        }
    }
    // --- FIN: Getters et utilitaires ---

    // --- DEBUT: Sauvegarde et Chargement ---
    private void loadEntreprises() {
        entreprises.clear();
        activiteHoraireValeur.clear();
        if (!entrepriseFile.exists()) { plugin.getLogger().info("[EntrepriseManager] entreprise.yml non trouvé. Aucune entreprise chargée."); return; }
        entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);
        ConfigurationSection entreprisesSection = entrepriseConfig.getConfigurationSection("entreprises");
        if (entreprisesSection == null) { plugin.getLogger().info("[EntrepriseManager] Section 'entreprises' vide ou manquante."); return; }
        for (String nomEntrepriseKey : entreprisesSection.getKeys(false)) {
            String path = "entreprises." + nomEntrepriseKey + ".";
            String ville = entrepriseConfig.getString(path + "ville");
            String type = entrepriseConfig.getString(path + "type");
            String gerant = entrepriseConfig.getString(path + "gerant");
            double solde = entrepriseConfig.getDouble(path + "solde", 0.0);
            String siret = entrepriseConfig.getString(path + "siret", generateSiret());
            double caTotal = entrepriseConfig.getDouble(path + "chiffreAffairesTotal", 0.0);
            double caHorairePotentiel = entrepriseConfig.getDouble(path + "activiteHoraireValeur", 0.0);
            Set<String> employesSet = new HashSet<>();
            Map<String, Double> primesMap = new HashMap<>();
            ConfigurationSection employesFileSection = entrepriseConfig.getConfigurationSection(path + "employes");
            if (employesFileSection != null) {
                for (String nomEmployeKey : employesFileSection.getKeys(false)) {
                    employesSet.add(nomEmployeKey);
                    primesMap.put(nomEmployeKey, employesFileSection.getDouble(nomEmployeKey + ".prime", 0.0));
                }
            }
            if (gerant == null || type == null || ville == null) {
                plugin.getLogger().severe("[EntrepriseManager] Données corrompues pour '" + nomEntrepriseKey + "'. Non chargée."); continue;
            }
            Entreprise entreprise = new Entreprise(nomEntrepriseKey, ville, type, gerant, employesSet, solde, siret);
            entreprise.setChiffreAffairesTotal(caTotal);
            entreprise.setPrimes(primesMap);
            entreprises.put(nomEntrepriseKey, entreprise);
            activiteHoraireValeur.put(nomEntrepriseKey, caHorairePotentiel);
        }
        plugin.getLogger().info("[EntrepriseManager] " + entreprises.size() + " entreprises chargées.");
    }

    // Rendue non-statique
    public void saveEntreprises() {
        if (entrepriseConfig == null || entrepriseFile == null || plugin == null) {
            plugin.getLogger().severe("[EntrepriseManager] ERREUR CRITIQUE: Config/Fichier/Plugin null. Sauvegarde annulée.");
            return;
        }
        entrepriseConfig.set("entreprises", null);
        for (Map.Entry<String, Entreprise> entry : entreprises.entrySet()) {
            String nomEnt = entry.getKey();
            Entreprise ent = entry.getValue();
            String path = "entreprises." + nomEnt + ".";
            entrepriseConfig.set(path + "ville", ent.getVille());
            entrepriseConfig.set(path + "type", ent.getType());
            entrepriseConfig.set(path + "gerant", ent.getGerant());
            entrepriseConfig.set(path + "solde", ent.getSolde());
            entrepriseConfig.set(path + "siret", ent.getSiret());
            entrepriseConfig.set(path + "chiffreAffairesTotal", ent.getChiffreAffairesTotal());
            if (this.activiteHoraireValeur.containsKey(nomEnt)) {
                entrepriseConfig.set(path + "activiteHoraireValeur", this.activiteHoraireValeur.get(nomEnt));
            }
            ConfigurationSection employesCfgSection = entrepriseConfig.createSection(path + "employes");
            // Correction : Vérifier si l'objet est null avant d'y accéder
            if (ent.getEmployes() != null && (!ent.getEmployes().isEmpty() || (ent.getPrimes() != null && !ent.getPrimes().isEmpty())) ) {
                for (String empNom : ent.getEmployes()) {
                    employesCfgSection.set(empNom + ".prime", ent.getPrimePourEmploye(empNom));
                }
            }
        }
        try { entrepriseConfig.save(entrepriseFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "[EntrepriseManager] Erreur sauvegarde entreprise.yml", e); }

        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        playersConfig.set("players", null);
        for (Entreprise ent : entreprises.values()) {
            String pGerantPath = "players." + ent.getGerant() + "." + ent.getNom();
            playersConfig.set(pGerantPath + ".role", "gerant");
            playersConfig.set(pGerantPath + ".type-entreprise", ent.getType());
            if (ent.getEmployes() != null) { // Vérifier si la liste d'employés n'est pas null
                for (String empNom : ent.getEmployes()) {
                    String pEmpPath = "players." + empNom + "." + ent.getNom();
                    playersConfig.set(pEmpPath + ".role", "employe");
                    playersConfig.set(pEmpPath + ".type-entreprise", ent.getType());
                }
            }
        }
        try { playersConfig.save(playersFile); } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "[EntrepriseManager] Erreur sauvegarde players.yml", e); }
    }

    public void reloadPluginData() {
        plugin.reloadConfig();
        loadEntreprises();
        chargerRestrictionsActions();
        planifierTachesHoraires();
        plugin.getLogger().info("[EntrepriseManager] Données du plugin rechargées.");
    }
    // --- FIN: Sauvegarde et Chargement ---

    // --- DEBUT: Méthodes pour les messages différés (primes hors-ligne) ---
    public void ajouterMessageEmployeDifferre(String joueurNom, String message, String entrepriseNom, double montantPrime) {
        File messagesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml");
        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        // Utiliser un chemin plus simple pour la liste, par ex. messages.<joueur>.<entreprise>
        String listPath = "messages." + joueurNom + "." + entrepriseNom + ".list";
        List<String> messagesActuels = messagesConfig.getStringList(listPath);
        if (messagesActuels == null) { // Initialiser si n'existe pas
            messagesActuels = new ArrayList<>();
        }
        messagesActuels.add(ChatColor.stripColor(LocalDateTime.now().toLocalDate().toString() + " " + LocalDateTime.now().toLocalTime().toString().substring(0,5) + ": " + message));
        messagesConfig.set(listPath, messagesActuels);

        // Stocker aussi le montant total si besoin de l'afficher séparément
        String totalPrimePath = "messages." + joueurNom + "." + entrepriseNom + ".totalPrime";
        double totalPrimeCumulee = messagesConfig.getDouble(totalPrimePath, 0.0);
        if (montantPrime > 0) {
            messagesConfig.set(totalPrimePath, totalPrimeCumulee + montantPrime);
        }

        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[EntrepriseManager] Erreur sauvegarde message différé employé " + joueurNom + ": " + e.getMessage());
        }
    }

    public void envoyerPrimesDifferreesEmployes(Player player) {
        String playerName = player.getName();
        File messagesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml");
        if (!messagesFile.exists()) return;
        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        String basePath = "messages." + playerName; // Chemin simplifié
        if (!messagesConfig.contains(basePath)) {
            player.sendMessage(ChatColor.YELLOW + "Vous n'avez aucun message de prime d'entreprise en attente.");
            return;
        }
        ConfigurationSection entreprisesMessagesSection = messagesConfig.getConfigurationSection(basePath);
        boolean aRecuMessage = false;
        if(entreprisesMessagesSection != null) {
            for (String nomEnt : entreprisesMessagesSection.getKeys(false)) {
                // Vérifier si l'entrée est bien une section (qui contient list et totalPrime)
                if(entreprisesMessagesSection.isConfigurationSection(nomEnt)){
                    List<String> messages = entreprisesMessagesSection.getStringList(nomEnt + ".list");
                    double totalPrime = entreprisesMessagesSection.getDouble(nomEnt + ".totalPrime", 0.0);
                    if (!messages.isEmpty()) {
                        player.sendMessage(ChatColor.GOLD + "--- Primes/Messages de '" + nomEnt + "' reçus hors-ligne ---");
                        for (String msg : messages) player.sendMessage(ChatColor.AQUA + "- " + msg);
                        if(totalPrime > 0) player.sendMessage(ChatColor.GREEN + "Un total de " + String.format("%,.2f",totalPrime) + "€ de primes de '"+nomEnt+"' a été crédité.");
                        player.sendMessage(ChatColor.GOLD + "--------------------------------------------------");
                        aRecuMessage = true;
                    }
                }
            }
        }
        if(aRecuMessage){
            messagesConfig.set(basePath, null);
            try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("[EntrepriseManager] Erreur suppression messages différés pour " + playerName + ": " + e.getMessage()); }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Vous n'avez aucun nouveau message de prime d'entreprise.");
        }
    }

    public void ajouterMessageGerantDifferre(String joueurNom, String message, String entrepriseNom, double montantConcerne) {
        File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml");
        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        String listPath = "messages." + joueurNom + "." + entrepriseNom + ".list"; // Chemin simplifié
        List<String> messagesActuels = messagesConfig.getStringList(listPath);
        if (messagesActuels == null) {
            messagesActuels = new ArrayList<>();
        }
        messagesActuels.add(ChatColor.stripColor(LocalDateTime.now().toLocalDate().toString() + " " + LocalDateTime.now().toLocalTime().toString().substring(0,5) + ": " + message));
        messagesConfig.set(listPath, messagesActuels);
        // Optionnel: stocker montantConcerne pour résumé
        // String montantPath = "messages." + joueurNom + "." + entrepriseNom + ".montantTotal";
        // double montantCumule = messagesConfig.getDouble(montantPath, 0.0);
        // messagesConfig.set(montantPath, montantCumule + montantConcerne);
        try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("[EntrepriseManager] Erreur sauvegarde message différé gérant " + joueurNom + ": " + e.getMessage()); }
    }

    public void envoyerPrimesDifferreesGerants(Player player) {
        String playerName = player.getName();
        File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml");
        if (!messagesFile.exists()) return;
        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        String basePath = "messages." + playerName; // Chemin simplifié
        if (!messagesConfig.contains(basePath)) {
            player.sendMessage(ChatColor.YELLOW + "Vous n'avez aucun message de gérance d'entreprise en attente.");
            return;
        }
        ConfigurationSection entreprisesMessagesSection = messagesConfig.getConfigurationSection(basePath);
        boolean aRecuMessage = false;
        if(entreprisesMessagesSection != null) {
            for (String nomEnt : entreprisesMessagesSection.getKeys(false)) {
                if(entreprisesMessagesSection.isConfigurationSection(nomEnt)){ // Vérifier que c'est une section
                    List<String> messages = entreprisesMessagesSection.getStringList(nomEnt + ".list");
                    if (!messages.isEmpty()) {
                        player.sendMessage(ChatColor.BLUE + "--- Notifications pour votre entreprise '" + nomEnt + "' ---");
                        for (String msg : messages) player.sendMessage(ChatColor.AQUA + "- " + msg);
                        player.sendMessage(ChatColor.BLUE + "----------------------------------------------------");
                        aRecuMessage = true;
                    }
                }
            }
        }
        if(aRecuMessage){
            messagesConfig.set(basePath, null);
            try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("[EntrepriseManager] Erreur suppression messages différés gérant " + playerName + ": " + e.getMessage()); }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Vous n'avez aucun nouveau message de gérance d'entreprise.");
        }
    }
    // --- FIN: Méthodes pour les messages différés ---

    // --- DEBUT: Getters et méthodes utilitaires ajoutés/corrigés ---
    public double getActiviteHoraireValeurPour(String nomEntreprise) {
        return this.activiteHoraireValeur.getOrDefault(nomEntreprise, 0.0);
    }

    public void listEntreprises(Player player, String ville) {
        TextComponent messageList = new TextComponent();
        messageList.addExtra(ChatColor.GOLD + "=== Entreprises à " + ChatColor.AQUA + ville + ChatColor.GOLD + " ===\n");
        boolean found = false;
        for (Entreprise e : getEntreprisesByVille(ville)) {
            TextComponent entComp = new TextComponent(ChatColor.AQUA + e.getNom() + ChatColor.GRAY + " (Type: " + e.getType() + ", Gérant: " + e.getGerant() + ")");
            entComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise info " + e.getNom()));
            entComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Voir infos pour " + e.getNom()).create()));
            messageList.addExtra(entComp);
            messageList.addExtra("\n");
            found = true;
        }
        if (!found) {
            messageList.addExtra(ChatColor.YELLOW + "Aucune entreprise trouvée dans cette ville.");
        }
        player.spigot().sendMessage(messageList);
    }

    public Collection<String> getNearbyPlayers(Player player, int distanceMax) {
        List<String> nearby = new ArrayList<>();
        if (player == null || player.getWorld() == null) return nearby;
        for (Player onlineP : Bukkit.getOnlinePlayers()) {
            if (onlineP.getWorld().equals(player.getWorld()) && onlineP.getLocation().distanceSquared(player.getLocation()) <= (long)distanceMax * distanceMax) {
                if (!onlineP.equals(player)) {
                    nearby.add(onlineP.getName());
                }
            }
        }
        return nearby;
    }

    public void getEntrepriseInfo(Player joueur, String nomEntreprise) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise != null) {
            joueur.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Infos: " + ChatColor.AQUA + entreprise.getNom() + ChatColor.GOLD + " ===");
            joueur.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + entreprise.getVille());
            joueur.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + entreprise.getType());
            joueur.sendMessage(ChatColor.YELLOW + "Gérant: " + ChatColor.WHITE + entreprise.getGerant());
            joueur.sendMessage(ChatColor.YELLOW + "Employés: " + ChatColor.WHITE + entreprise.getEmployes().size());
            joueur.sendMessage(ChatColor.YELLOW + "Solde: " + ChatColor.GREEN + String.format("%,.2f", entreprise.getSolde()) + "€");
            joueur.sendMessage(ChatColor.YELLOW + "CA Potentiel (heure): " + ChatColor.AQUA + String.format("%,.2f", getActiviteHoraireValeurPour(entreprise.getNom())) + "€");
            joueur.sendMessage(ChatColor.YELLOW + "CA Total (brut): " + ChatColor.DARK_GREEN + String.format("%,.2f", entreprise.getChiffreAffairesTotal()) + "€");
            joueur.sendMessage(ChatColor.YELLOW + "SIRET: " + ChatColor.WHITE + entreprise.getSiret());
            if (!entreprise.getPrimes().isEmpty() && (entreprise.getGerant().equalsIgnoreCase(joueur.getName()) || joueur.hasPermission("entreprisemanager.admin.info"))) {
                joueur.sendMessage(ChatColor.GOLD + "Primes Horaires:");
                entreprise.getPrimes().forEach((emp, prime) -> joueur.sendMessage(ChatColor.GRAY + "  - " + emp + ": " + ChatColor.YELLOW + String.format("%,.2f", prime) + "€/h"));
            }
            joueur.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=============================");
        } else {
            joueur.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'existe pas.");
        }
    }
    // --- FIN: Getters et méthodes utilitaires ajoutés/corrigés ---

    // --- Classe interne Entreprise ---
    public static class Entreprise {
        private String nom;
        private String ville;
        private String type;
        private String gerant;
        private Set<String> employes; // Utiliser Set<String> pour les noms d'employés
        private double solde;
        private String siret;
        private double chiffreAffairesTotal;
        private Map<String, Double> primes;

        public Entreprise(String nom, String ville, String type, String gerant, Set<String> employes, double solde, String siret) {
            this.nom = nom;
            this.ville = ville;
            this.type = type;
            this.gerant = gerant;
            this.employes = (employes != null) ? new HashSet<>(employes) : new HashSet<>(); // Assurer l'initialisation
            this.solde = solde;
            this.siret = siret;
            this.chiffreAffairesTotal = 0.0;
            this.primes = new HashMap<>();
        }

        // Getters
        public String getNom() { return nom; }
        public String getVille() { return ville; }
        public String getType() { return type; }
        public String getGerant() { return gerant; }
        public Set<String> getEmployes() { return Collections.unmodifiableSet(employes); } // Retourner une vue non modifiable
        // Méthode pour obtenir la collection modifiable (usage interne à EntrepriseManagerLogic)
        protected Set<String> getEmployesInternal() { return this.employes; }
        public double getSolde() { return solde; }
        public String getSiret() { return siret; }
        public double getChiffreAffairesTotal() { return chiffreAffairesTotal; }
        public Map<String, Double> getPrimes() { return Collections.unmodifiableMap(primes); } // Retourner une vue non modifiable

        // Setters
        public void setNom(String nom) { this.nom = nom; }
        public void setSolde(double solde) { this.solde = solde; }
        public void setChiffreAffairesTotal(double chiffreAffairesTotal) { this.chiffreAffairesTotal = chiffreAffairesTotal; }

        // Gestion des primes
        public double getPrimePourEmploye(String nomEmploye) { return this.primes.getOrDefault(nomEmploye, 0.0); }
        public void setPrimePourEmploye(String nomEmploye, double montantPrime) {
            if (montantPrime < 0) montantPrime = 0;
            this.primes.put(nomEmploye, montantPrime);
        }
        public void retirerPrimeEmploye(String nomEmploye) { this.primes.remove(nomEmploye); }
        public void setPrimes(Map<String, Double> nouvellesPrimes) { // Pour chargement
            this.primes.clear();
            if (nouvellesPrimes != null) this.primes.putAll(nouvellesPrimes);
        }

        // Méthode getRevenusBrutsJournaliers (Obsolète avec V2, mais gardée pour référence si utilisée ailleurs)
        public double getRevenusBrutsJournaliers() {
            // Si la config 'gain-par-employe' existe encore pour un calcul différent du CA
            if (plugin != null && plugin.getConfig().contains("finance.gain-par-employe")) {
                return getEmployes().size() * plugin.getConfig().getDouble("finance.gain-par-employe", 0);
            }
            return 0; // Retourner 0 si non pertinent
        }

        @Override
        public String toString() {
            return "Entreprise{nom='" + nom + "', type='" + type + "', gerant='" + gerant + "', solde=" + solde + "}";
        }
    }

    // --- Classe interne ActionInfo ---
    public static class ActionInfo {
        private int nombreActions;
        private LocalDateTime dernierActionHeure;
        public ActionInfo() {
            this.nombreActions = 0;
            this.dernierActionHeure = LocalDateTime.now();
        }
        public int getNombreActions() { return nombreActions; }
        public LocalDateTime getDernierActionHeure() { return dernierActionHeure; }
        public void incrementerActions(int quantite) { this.nombreActions += quantite; }
        public void reinitialiserActions(LocalDateTime heureActuelle) {
            this.nombreActions = 0;
            this.dernierActionHeure = heureActuelle;
        }
        @Override
        public String toString() {
            return "ActionInfo{actions=" + nombreActions + ", heureReset=" + dernierActionHeure.getHour() + "h}";
        }
    }
}