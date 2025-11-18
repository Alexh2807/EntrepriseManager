package com.gravityyfh.roleplaycity.town.gui.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builder pour construire des scoreboards de manière fluide et propre
 * Gère automatiquement l'ordre des lignes (de haut en bas)
 */
public class ScoreboardBuilder {

    private final Scoreboard scoreboard;
    private final String title;
    private final List<String> lines;

    /**
     * Crée un nouveau builder de scoreboard
     * @param title Le titre du scoreboard (sera formatté automatiquement)
     */
    public ScoreboardBuilder(String title) {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.title = title;
        this.lines = new ArrayList<>();
    }

    /**
     * Ajoute une ligne au scoreboard
     * @param line Le texte de la ligne
     */
    public ScoreboardBuilder addLine(String line) {
        lines.add(line);
        return this;
    }

    /**
     * Ajoute une ligne vide (pour espacer)
     */
    public ScoreboardBuilder addEmptyLine() {
        // Utiliser des espaces différents pour éviter les doublons
        int emptyCount = (int) lines.stream().filter(l -> l.trim().isEmpty()).count();
        String spaces = " ".repeat(emptyCount + 1);
        lines.add(spaces);
        return this;
    }

    /**
     * Ajoute plusieurs lignes d'un coup
     */
    public ScoreboardBuilder addLines(String... linesToAdd) {
        for (String line : linesToAdd) {
            addLine(line);
        }
        return this;
    }

    /**
     * Ajoute une ligne seulement si la condition est vraie
     */
    public ScoreboardBuilder addLineIf(boolean condition, String line) {
        if (condition) {
            addLine(line);
        }
        return this;
    }

    /**
     * Ajoute une section avec un titre
     * Exemple: addSection("Économie", "À vendre: 15,000€")
     */
    public ScoreboardBuilder addSection(String sectionTitle, String... sectionLines) {
        // Ligne vide avant la section
        addEmptyLine();

        // Titre de la section
        addLine(ScoreboardTheme.LABEL_COLOR + "" + org.bukkit.ChatColor.BOLD + sectionTitle);

        // Lignes de la section
        for (String line : sectionLines) {
            addLine(line);
        }

        return this;
    }

    /**
     * Construit le scoreboard final
     * @return Le scoreboard Bukkit prêt à être affiché
     */
    public Scoreboard build() {
        // Supprimer l'ancien objectif s'il existe
        Objective objective = scoreboard.getObjective("townInfo");
        if (objective != null) {
            objective.unregister();
        }

        // Créer le nouvel objectif avec le titre
        objective = scoreboard.registerNewObjective("townInfo", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Ajouter les lignes avec des scores décroissants (de haut en bas)
        // Score le plus élevé = ligne la plus haute
        int score = lines.size();
        for (String line : lines) {
            // Limiter la longueur pour éviter les erreurs
            String displayLine = line;
            if (displayLine.length() > 40) {
                displayLine = displayLine.substring(0, 40);
            }

            objective.getScore(displayLine).setScore(score--);
        }

        return scoreboard;
    }

    /**
     * Construit et applique le scoreboard à un joueur
     * @param player Le joueur qui recevra le scoreboard
     */
    public void buildAndApply(Player player) {
        player.setScoreboard(build());
    }

    /**
     * Créer rapidement un scoreboard simple
     * @param title Le titre
     * @param lines Les lignes à afficher
     */
    public static Scoreboard createSimple(String title, String... lines) {
        ScoreboardBuilder builder = new ScoreboardBuilder(title);
        for (String line : lines) {
            builder.addLine(line);
        }
        return builder.build();
    }

    /**
     * Créer et appliquer rapidement un scoreboard simple
     */
    public static void createAndApply(Player player, String title, String... lines) {
        ScoreboardBuilder builder = new ScoreboardBuilder(title);
        for (String line : lines) {
            builder.addLine(line);
        }
        builder.buildAndApply(player);
    }
}
