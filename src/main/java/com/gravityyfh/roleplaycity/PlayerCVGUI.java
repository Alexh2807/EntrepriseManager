package com.gravityyfh.roleplaycity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Level; // Pour les logs

public class PlayerCVGUI implements Listener {

    private final RoleplayCity plugin;
    private final EntrepriseManagerLogic entrepriseLogic; // Référence nécessaire pour récupérer l'historique/entreprise actuelle
    private final CVManager cvManager;                 // Référence nécessaire pour initier les partages de CV

    // FIX MOYENNE: Protection contre le double-click pour éviter exploits
    private final java.util.Map<UUID, Long> clickTimestamps = new java.util.HashMap<>();
    private static final long CLICK_DELAY_MS = 500;

    // Titres de menu
    private static final String TITLE_CV_VIEW_PREFIX = ChatColor.DARK_AQUA + "CV de ";
    private static final String TITLE_CV_MAIN_MENU = ChatColor.DARK_GREEN + "Gestion de CV";
    private static final String TITLE_SHOW_CV_TO_PLAYER_LIST = ChatColor.DARK_GREEN + "Montrer CV à un Joueur";
    private static final String TITLE_REQUEST_CV_FROM_PLAYER_LIST = ChatColor.YELLOW + "Demander CV à un Joueur"; // Si besoin d'un titre distinct

    // Constructeur mis à jour pour inclure EntrepriseManagerLogic et CVManager
    public PlayerCVGUI(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic, CVManager cvManager) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        this.cvManager = cvManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le menu principal de gestion des CV pour le joueur.
     * @param player Le joueur qui ouvre le menu.
     */
    public void openCVMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CV_MAIN_MENU); // 3 lignes

        // Option 1: Voir son propre CV
        inv.setItem(11, createMenuItem(Material.BOOK, ChatColor.AQUA + "Consulter mon CV",
                List.of(ChatColor.GRAY + "Affiche votre propre parcours professionnel.")));

        // Option 2: Montrer son CV à un joueur proche
        inv.setItem(13, createMenuItem(Material.PLAYER_HEAD, ChatColor.GREEN + "Montrer mon CV à un joueur",
                List.of(ChatColor.GRAY + "Sélectionnez un joueur à proximité", ChatColor.GRAY + "pour lui proposer de voir votre CV.")));

        // Option 3: Demander à voir le CV d'un autre joueur proche
        inv.setItem(15, createMenuItem(Material.SPYGLASS, ChatColor.YELLOW + "Demander le CV d'un joueur",
                List.of(ChatColor.GRAY + "Sélectionnez un joueur à proximité", ChatColor.GRAY + "pour lui demander de partager son CV.")));

        // Bouton de retour au menu principal d'EntrepriseGUI
        inv.setItem(22, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour au Menu Entreprise"));

        player.openInventory(inv);
    }

    /**
     * Ouvre l'inventaire de sélection des joueurs proches à qui montrer son CV.
     * @param requester Le joueur qui veut montrer son CV.
     */
    private void openShowCVToPlayerSelection(Player requester) {
        double distanceMax = plugin.getConfig().getDouble("invitation.distance-max", 15.0);
        String title = TITLE_SHOW_CV_TO_PLAYER_LIST;
        Inventory inv = Bukkit.createInventory(null, 54, title); // 6 lignes
        int slot = 0;
        boolean foundPlayers = false;

        Collection<Entity> nearbyEntities = requester.getNearbyEntities(distanceMax, distanceMax, distanceMax);
        plugin.getLogger().log(Level.INFO, "[PlayerCVGUI] Joueurs proches de " + requester.getName() + " (max " + distanceMax + " blocs) pour montrer CV:");

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player target && !entity.getUniqueId().equals(requester.getUniqueId())) {
                plugin.getLogger().log(Level.INFO, "[PlayerCVGUI]   - Trouvé: " + target.getName());
                if (slot < 45) { // Laisser la dernière ligne pour les contrôles (0-44 pour les têtes)
                    inv.setItem(slot++, createPlayerHead(target.getName(), ChatColor.AQUA + target.getName(),
                            List.of(ChatColor.GRAY + "Cliquez pour proposer de montrer", ChatColor.GRAY + "votre CV à " + ChatColor.YELLOW + target.getName())));
                    foundPlayers = true;
                } else {
                    plugin.getLogger().log(Level.WARNING, "[PlayerCVGUI] Trop de joueurs proches, GUI plein pour montrer CV.");
                    break;
                }
            }
        }

        if (!foundPlayers) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun joueur à proximité.",
                    List.of(ChatColor.GRAY + "Distance max: " + distanceMax + " blocs")));
        }

        addBackButton(inv, 49, "(Menu CV)"); // Slot 49 = milieu de la dernière ligne (index 4)
        requester.openInventory(inv);
    }

    /**
     * Ouvre l'inventaire de sélection des joueurs proches à qui demander de voir LEUR CV.
     * @param requester Le joueur qui veut demander un CV.
     */
    private void openRequestCVFromPlayerSelection(Player requester) {
        double distanceMax = plugin.getConfig().getDouble("invitation.distance-max", 15.0);
        String title = TITLE_REQUEST_CV_FROM_PLAYER_LIST;
        Inventory inv = Bukkit.createInventory(null, 54, title);
        int slot = 0;
        boolean foundPlayers = false;

        Collection<Entity> nearbyEntities = requester.getNearbyEntities(distanceMax, distanceMax, distanceMax);
        plugin.getLogger().log(Level.INFO, "[PlayerCVGUI] Joueurs proches de " + requester.getName() + " (max " + distanceMax + " blocs) pour demander CV:");

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player target && !entity.getUniqueId().equals(requester.getUniqueId())) {
                plugin.getLogger().log(Level.INFO, "[PlayerCVGUI]   - Trouvé: " + target.getName());
                if (slot < 45) {
                    inv.setItem(slot++, createPlayerHead(target.getName(), ChatColor.AQUA + target.getName(),
                            List.of(ChatColor.GRAY + "Cliquez pour demander à " + ChatColor.YELLOW + target.getName(), ChatColor.GRAY + "de vous montrer son CV.")));
                    foundPlayers = true;
                } else {
                    plugin.getLogger().log(Level.WARNING, "[PlayerCVGUI] Trop de joueurs proches, GUI plein pour demander CV.");
                    break;
                }
            }
        }
        if (!foundPlayers) {
            inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Aucun joueur à proximité.",
                    List.of(ChatColor.GRAY + "Distance max: " + distanceMax + " blocs")));
        }
        addBackButton(inv, 49, "(Menu CV)");
        requester.openInventory(inv);
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String topInventoryTitle = event.getView().getTitle(); // Titre de l'inventaire supérieur (celui du GUI)

        // FIX MOYENNE: Vérifier que c'est bien un menu CV avant de vérifier le délai
        if (isPluginCVMenu(topInventoryTitle)) {
            // FIX MOYENNE: Vérifier le délai entre clics pour éviter exploits
            long currentTime = System.currentTimeMillis();
            if (clickTimestamps.getOrDefault(player.getUniqueId(), 0L) + CLICK_DELAY_MS > currentTime) {
                event.setCancelled(true);
                return;
            }
            clickTimestamps.put(player.getUniqueId(), currentTime);
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || clickedItem.getItemMeta().getDisplayName() == null) {
            if (isPluginCVMenu(topInventoryTitle)) event.setCancelled(true); // Annuler si c'est notre GUI même pour un clic dans le vide
            return;
        }
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());


        if (topInventoryTitle.startsWith(TITLE_CV_VIEW_PREFIX)) { // Gérer le clic dans un CV affiché
            event.setCancelled(true);
            if (itemName.equalsIgnoreCase("Fermer le CV")) {
                player.closeInventory();
            }
            return;
        }

        if (topInventoryTitle.equals(TITLE_CV_MAIN_MENU)) {
            event.setCancelled(true);
            switch (itemName) {
                case "Consulter mon CV":
                    openCV(player, player, entrepriseLogic);
                    break;
                case "Montrer mon CV à un joueur":
                    openShowCVToPlayerSelection(player);
                    break;
                case "Demander le CV d'un joueur":
                    openRequestCVFromPlayerSelection(player);
                    break;
                case "Retour au Menu Entreprise":
                    // Assurez-vous que RoleplayCity a un getter pour EntrepriseGUI
                    if (plugin.getEntrepriseGUI() != null) {
                        plugin.getEntrepriseGUI().openMainMenu(player);
                    } else {
                        player.closeInventory();
                        player.sendMessage(ChatColor.RED + "Erreur: Impossible de retourner au menu principal.");
                    }
                    break;
            }
        } else if (topInventoryTitle.equals(TITLE_SHOW_CV_TO_PLAYER_LIST)) {
            event.setCancelled(true);
            if (clickedItem.getType() == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) clickedItem.getItemMeta();
                if (skullMeta.hasOwner() && skullMeta.getOwningPlayer() != null) {
                    OfflinePlayer targetOfflinePlayer = skullMeta.getOwningPlayer();
                    Player targetOnlinePlayer = targetOfflinePlayer.getPlayer(); // Peut être null si hors ligne

                    if (targetOnlinePlayer != null && targetOnlinePlayer.isOnline()) {
                        cvManager.requestShareOwnCV(player, targetOnlinePlayer); // Joueur (this) veut montrer SON CV à targetOnlinePlayer
                        // Le message de confirmation est géré dans CVManager ou après l'appel
                        player.closeInventory(); // Fermer le GUI après l'action
                    } else {
                        player.sendMessage(ChatColor.RED + "Le joueur " + targetOfflinePlayer.getName() + " n'est plus en ligne.");
                        openShowCVToPlayerSelection(player); // Ré-ouvrir pour choisir un autre joueur
                    }
                }
            } else if (itemName.startsWith("Retour")) {
                openCVMainMenu(player);
            }
        } else if (topInventoryTitle.equals(TITLE_REQUEST_CV_FROM_PLAYER_LIST)) {
            event.setCancelled(true);
            if (clickedItem.getType() == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) clickedItem.getItemMeta();
                if (skullMeta.hasOwner() && skullMeta.getOwningPlayer() != null) {
                    OfflinePlayer targetOfflinePlayer = skullMeta.getOwningPlayer();
                    Player targetOnlinePlayer = targetOfflinePlayer.getPlayer();

                    if (targetOnlinePlayer != null && targetOnlinePlayer.isOnline()) {
                        cvManager.requestShareCV(player, targetOnlinePlayer); // Joueur (this) demande à target de montrer SON CV
                        player.closeInventory();
                    } else {
                        player.sendMessage(ChatColor.RED + "Le joueur " + targetOfflinePlayer.getName() + " n'est plus en ligne.");
                        openRequestCVFromPlayerSelection(player);
                    }
                }
            } else if (itemName.startsWith("Retour")) {
                openCVMainMenu(player);
            }
        }
    }

    /**
     * Méthode pour afficher le CV d'un joueur (cvOwner) à un autre joueur (viewer).
     * C'est la méthode qui construit l'inventaire visuel du CV.
     */
    public void openCV(Player viewer, Player cvOwner, EntrepriseManagerLogic logic) {
        // FIX MULTI-ENTREPRISES: Récupérer toutes les entreprises actuelles
        java.util.List<EntrepriseManagerLogic.Entreprise> entreprisesActuelles = logic.getEntreprisesDuJoueur(cvOwner);
        List<PastExperience> historique = logic.getPlayerHistory(cvOwner.getUniqueId());

        String cvTitle = TITLE_CV_VIEW_PREFIX + cvOwner.getName();
        Inventory inv = Bukkit.createInventory(null, 54, cvTitle);

        // Ligne 1: Informations personnelles
        inv.setItem(4, createPlayerHead(cvOwner.getName(), ChatColor.GOLD + ChatColor.BOLD.toString() + cvOwner.getName(), List.of(ChatColor.GRAY + "Profil Professionnel")));

        // Ligne 2: Expériences actuelles (support multi-entreprises)
        if (!entreprisesActuelles.isEmpty()) {
            if (entreprisesActuelles.size() == 1) {
                // Une seule entreprise : affichage classique
                EntrepriseManagerLogic.Entreprise entrepriseActuelle = entreprisesActuelles.get(0);
                EntrepriseManagerLogic.EmployeeActivityRecord record = entrepriseActuelle.getEmployeeActivityRecord(cvOwner.getUniqueId());
                String role = entrepriseActuelle.getGerant().equalsIgnoreCase(cvOwner.getName()) ? "Gérant" : "Employé";
                inv.setItem(10, createMenuItem(Material.EMERALD_BLOCK, ChatColor.AQUA + "Emploi Actuel", List.of(
                        ChatColor.WHITE + entrepriseActuelle.getNom(),
                        ChatColor.GRAY + "Type: " + entrepriseActuelle.getType()
                )));
                List<String> roleLore = new ArrayList<>();
                roleLore.add(ChatColor.YELLOW + "Poste: " + ChatColor.WHITE + role);
                if (record != null && record.joinDate != null) {
                    roleLore.add(ChatColor.YELLOW + "Membre depuis: " + ChatColor.WHITE + record.joinDate.format(DateTimeFormatter.ofPattern("dd/MM/yy")));
                    roleLore.add(ChatColor.YELLOW + "Ancienneté: " + ChatColor.WHITE + formatDuration(record.joinDate, LocalDateTime.now()));
                    roleLore.add(ChatColor.YELLOW + "CA Rapporté: " + ChatColor.GREEN + String.format("%,.2f", record.totalValueGenerated) + "€");
                } else {
                    roleLore.add(ChatColor.GRAY + "Données d'activité indisponibles.");
                }
                inv.setItem(12, createMenuItem(Material.CLOCK, ChatColor.AQUA + "Détails Actuels", roleLore));
            } else {
                // Plusieurs entreprises : affichage multi-entreprises
                List<String> entreprisesLore = new ArrayList<>();
                entreprisesLore.add(ChatColor.GRAY + "Possède " + ChatColor.AQUA + entreprisesActuelles.size() + ChatColor.GRAY + " entreprises");
                entreprisesLore.add("");
                for (EntrepriseManagerLogic.Entreprise ent : entreprisesActuelles) {
                    String role = ent.getGerant().equalsIgnoreCase(cvOwner.getName()) ? "Gérant" : "Employé";
                    entreprisesLore.add(ChatColor.YELLOW + "• " + ChatColor.WHITE + ent.getNom());
                    entreprisesLore.add(ChatColor.GRAY + "  Type: " + ent.getType() + " | Poste: " + role);
                }
                inv.setItem(11, createMenuItem(Material.EMERALD_BLOCK, ChatColor.AQUA + "Emplois Actuels", entreprisesLore));
            }
        } else {
            inv.setItem(13, createMenuItem(Material.BARRIER, ChatColor.YELLOW + "Actuellement sans entreprise.",
                    List.of("" + ChatColor.ITALIC + ChatColor.GRAY + "À la recherche d'opportunités?")));
        }

        // Lignes 3, 4, 5 : Historique Professionnel
        inv.setItem(22, createMenuItem(Material.BOOKSHELF, ChatColor.GOLD + ChatColor.BOLD.toString() + "Historique Professionnel", Collections.emptyList()));
        int nextAvailableSlot = 27; // Début de la ligne 4
        if (historique.isEmpty()) {
            inv.setItem(nextAvailableSlot, createMenuItem(Material.PAPER, ChatColor.GRAY + "Aucune expérience passée enregistrée."));
        } else {
            int historyLimit = Math.min(historique.size(), 15); // Limiter à 15 entrées (3 lignes de 5)
            for (int i = 0; i < historyLimit; i++) {
                if (nextAvailableSlot >= 45) break; // S'arrêter avant la dernière ligne réservée aux contrôles
                PastExperience exp = historique.get(i);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
                String dateEntreeStr = exp.dateEntree() != null ? exp.dateEntree().format(formatter) : "N/A";
                String dateSortieStr = exp.dateSortie() != null ? exp.dateSortie().format(formatter) : "N/A";
                List<String> expLore = List.of(
                        ChatColor.YELLOW + "Type: " + ChatColor.WHITE + exp.entrepriseType(),
                        ChatColor.YELLOW + "Rôle: " + ChatColor.WHITE + exp.role(),
                        ChatColor.YELLOW + "Période: " + ChatColor.WHITE + dateEntreeStr + " - " + dateSortieStr,
                        ChatColor.YELLOW + "CA Généré: " + ChatColor.GREEN + String.format("%,.2f", exp.caGenere()) + "€"
                );
                inv.setItem(nextAvailableSlot, createMenuItem(Material.BOOK, ChatColor.AQUA + exp.entrepriseNom(), expLore));
                nextAvailableSlot++;
                if (nextAvailableSlot % 9 == 0) { // Si on arrive à la fin d'une ligne
                    nextAvailableSlot = (nextAvailableSlot / 9) * 9; // S'assurer qu'on est bien au début de la ligne
                }
            }
            if (historique.size() > historyLimit && nextAvailableSlot < 45) {
                inv.setItem(nextAvailableSlot, createMenuItem(Material.PAPER, ChatColor.GRAY+"(... et " + (historique.size() - historyLimit) + " autres)"));
            }
        }

        // Ligne 6: Bouton Fermer
        inv.setItem(49, createMenuItem(Material.RED_WOOL, ChatColor.RED.toString() + ChatColor.BOLD + "Fermer le CV"));

        // Remplir les bords avec des vitres (esthétique)
        ItemStack filler = createMenuItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                if (i < 9 || i >= 45 || i % 9 == 0 || (i + 1) % 9 == 0) {
                    inv.setItem(i, filler);
                }
            }
        }
        viewer.openInventory(inv);
    }

    private void addBackButton(Inventory inv, int slot, String contextHint) {
        inv.setItem(slot, createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour " + ChatColor.GRAY + contextHint));
    }

    // Vérifie si le titre de l'inventaire correspond à un menu géré par ce GUI
    public boolean isPluginCVMenu(String inventoryTitle) {
        return inventoryTitle != null && (
                inventoryTitle.equals(TITLE_CV_MAIN_MENU) ||
                        inventoryTitle.equals(TITLE_SHOW_CV_TO_PLAYER_LIST) ||
                        inventoryTitle.equals(TITLE_REQUEST_CV_FROM_PLAYER_LIST) || // Si vous l'implémentez
                        inventoryTitle.startsWith(TITLE_CV_VIEW_PREFIX)
        );
    }


    // --- Méthodes Utilitaires (inchangées de votre version, juste copiées ici pour la complétude) ---
// Dans PlayerCVGUI.java
    private ItemStack createMenuItem(Material material, String name) {
        return createMenuItem(material, name, null); // Appelle la version complète avec lore à null
    }
    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(l -> ChatColor.RESET + l).collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayerHead(String playerName, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName); // Attention: cet appel peut être bloquant
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(l -> ChatColor.RESET + l).collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || start.isAfter(end)) { return "N/A"; }
        long totalSeconds = ChronoUnit.SECONDS.between(start, end);
        if (totalSeconds < 60) return "Moins d'une minute";
        long days = ChronoUnit.DAYS.between(start, end); long hours = ChronoUnit.HOURS.between(start, end) % 24; long minutes = ChronoUnit.MINUTES.between(start, end) % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 365) { long years = days / 365; sb.append(years).append(years > 1 ? " ans" : " an"); days %= 365; }
        if (days > 30 && days <=365) { long months = days / 30; if(sb.length() > 0) sb.append(", "); sb.append(months).append(" mois"); days %= 30; }
        if (days > 0 && days <=30) { if(sb.length() > 0) sb.append(", "); sb.append(days).append(days > 1 ? " jours" : " jour"); }
        if (sb.length() == 0 || (days > 0 && days <= 7)) { if (hours > 0) { if(sb.length() > 0 && days <=0) sb.append(", "); else if(sb.length() > 0 && days > 0) sb.append(", "); sb.append(hours).append(hours > 1 ? " heures" : " heure"); } if (minutes > 0 && hours == 0 && days == 0) { if(sb.length() > 0) sb.append(" et "); sb.append(minutes).append(minutes > 1 ? " minutes" : " minute"); } }
        return sb.length() > 0 ? sb.toString() : "Quelques instants";
    }
}