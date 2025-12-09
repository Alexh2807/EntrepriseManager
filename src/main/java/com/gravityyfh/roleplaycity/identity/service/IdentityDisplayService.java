package com.gravityyfh.roleplaycity.identity.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.identity.data.Identity;
import com.gravityyfh.roleplaycity.identity.manager.IdentityManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Service pour afficher le nom d'identité (Prénom Nom) à la place du pseudo Minecraft
 * - Change le displayName (chat)
 * - Change le playerListName (tab list)
 * - Change le nametag au-dessus de la tête (scoreboard teams)
 */
public class IdentityDisplayService {

    private final RoleplayCity plugin;
    private final IdentityManager identityManager;
    private static final String TEAM_PREFIX = "identity_";

    public IdentityDisplayService(RoleplayCity plugin, IdentityManager identityManager) {
        this.plugin = plugin;
        this.identityManager = identityManager;
    }

    /**
     * Applique le nom d'identité à un joueur (displayName, tabList, nametag)
     * NOTE: Désactivé - On utilise maintenant le nom Minecraft directement
     * @param player Le joueur
     */
    public void applyIdentityName(Player player) {
        // Désactivé: On utilise le nom Minecraft, pas de nom d'identité personnalisé
        // Ne rien faire
    }

    /**
     * Applique un nom personnalisé à un joueur
     */
    private void applyName(Player player, String customName) {
        // 1. DisplayName (visible dans le chat)
        player.setDisplayName(ChatColor.WHITE + customName + ChatColor.RESET);

        // 2. PlayerListName (visible dans la tab list)
        player.setPlayerListName(ChatColor.WHITE + customName);

        // 3. Nametag au-dessus de la tête (via scoreboard team)
        applyNametag(player, customName);

        plugin.getLogger().fine("Nom d'identité appliqué pour " + player.getName() + ": " + customName);
    }

    /**
     * Restaure le nom original du joueur
     */
    public void resetToOriginalName(Player player) {
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        removeFromIdentityTeam(player);
    }

    /**
     * Applique le nametag au-dessus de la tête via scoreboard team
     */
    private void applyNametag(Player player, String customName) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Nom de la team unique pour ce joueur
        String teamName = getTeamName(player.getUniqueId());

        // Supprimer l'ancienne team si elle existe
        Team oldTeam = scoreboard.getTeam(teamName);
        if (oldTeam != null) {
            oldTeam.unregister();
        }

        // Créer une nouvelle team
        Team team = scoreboard.registerNewTeam(teamName);

        // Le préfixe contient le nouveau nom, le suffixe est vide
        // On utilise le préfixe pour "cacher" le vrai nom
        // Limites: prefix max 64 chars, suffix max 64 chars (1.13+)

        // Technique: On met le nom custom en préfixe et on rend le vrai nom invisible
        // Mais Minecraft affiche toujours le vrai nom après le préfixe...

        // Solution alternative: Utiliser le préfixe seul et cacher le vrai nom avec des couleurs
        // Le nom du joueur sera affiché après le préfixe, donc on utilise une autre approche

        // Approche: Préfixe = nom custom + espace, Suffixe = vide, et le vrai nom sera grisé
        String prefix = customName;
        if (prefix.length() > 64) {
            prefix = prefix.substring(0, 64);
        }

        team.setPrefix(ChatColor.WHITE + prefix + " " + ChatColor.DARK_GRAY);
        team.setSuffix(""); // Le vrai nom sera grisé après

        // Option: Cacher complètement le vrai nom en mettant la même couleur que le fond
        // Mais ce n'est pas parfait car le nom reste visible

        // Meilleure approche: Mettre le nom custom comme préfixe, le vrai nom sera juste après
        // mais en couleur sombre pour être moins visible

        team.setColor(ChatColor.DARK_GRAY); // Le vrai nom du joueur sera gris foncé

        // Ajouter le joueur à la team
        team.addEntry(player.getName());
    }

    /**
     * Retire le joueur de sa team d'identité
     */
    private void removeFromIdentityTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = getTeamName(player.getUniqueId());

        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    /**
     * Génère un nom de team unique basé sur l'UUID (max 16 chars avant 1.13, illimité après)
     */
    private String getTeamName(UUID uuid) {
        // Utiliser les 8 premiers caractères de l'UUID pour rester sous la limite
        return TEAM_PREFIX + uuid.toString().substring(0, 8);
    }

    /**
     * Applique les noms d'identité à tous les joueurs en ligne
     */
    public void applyToAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyIdentityName(player);
        }
    }

    /**
     * Nettoie toutes les teams d'identité (appelé au shutdown)
     */
    public void cleanup() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }
}
