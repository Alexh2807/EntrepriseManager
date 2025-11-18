package com.gravityyfh.roleplaycity.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Système de messages interactifs cliquables dans le chat
 * Remplace les commandes à taper par des boutons cliquables
 *
 * Exemple d'utilisation :
 * new InteractiveMessage()
 *     .text("Voulez-vous supprimer cette entreprise ?")
 *     .newLine()
 *     .button("✓ CONFIRMER", "/entreprise delete confirm", "Cliquez pour confirmer", ChatColor.GREEN)
 *     .space()
 *     .button("✗ ANNULER", "/entreprise delete cancel", "Cliquez pour annuler", ChatColor.RED)
 *     .send(player);
 */
public class InteractiveMessage {

    private final List<BaseComponent[]> lines;
    private final List<BaseComponent> currentLine;

    public InteractiveMessage() {
        this.lines = new ArrayList<>();
        this.currentLine = new ArrayList<>();
    }

    /**
     * Ajoute du texte simple
     */
    public InteractiveMessage text(String text) {
        TextComponent component = new TextComponent(text);
        currentLine.add(component);
        return this;
    }

    /**
     * Ajoute du texte avec couleur
     */
    public InteractiveMessage text(String text, ChatColor color) {
        TextComponent component = new TextComponent(text);
        component.setColor(color);
        currentLine.add(component);
        return this;
    }

    /**
     * Ajoute du texte avec couleur et style
     */
    public InteractiveMessage text(String text, ChatColor color, boolean bold, boolean italic) {
        TextComponent component = new TextComponent(text);
        component.setColor(color);
        component.setBold(bold);
        component.setItalic(italic);
        currentLine.add(component);
        return this;
    }

    /**
     * Ajoute un bouton cliquable qui exécute une commande
     */
    public InteractiveMessage button(String label, String command, String hover, ChatColor color) {
        TextComponent button = new TextComponent(label);
        button.setColor(color);
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hover).color(ChatColor.YELLOW).create()
        ));
        currentLine.add(button);
        return this;
    }

    /**
     * Ajoute un bouton qui suggère une commande (pré-remplit le chat)
     */
    public InteractiveMessage suggestButton(String label, String command, String hover, ChatColor color) {
        TextComponent button = new TextComponent(label);
        button.setColor(color);
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        button.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hover).color(ChatColor.YELLOW).create()
        ));
        currentLine.add(button);
        return this;
    }

    /**
     * Ajoute un lien cliquable
     */
    public InteractiveMessage link(String label, String url, String hover, ChatColor color) {
        TextComponent link = new TextComponent(label);
        link.setColor(color);
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        link.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hover).color(ChatColor.YELLOW).create()
        ));
        currentLine.add(link);
        return this;
    }

    /**
     * Ajoute un bouton avec hover personnalisé multi-lignes
     */
    public InteractiveMessage buttonWithHover(String label, String command, List<String> hoverLines, ChatColor color) {
        TextComponent button = new TextComponent(label);
        button.setColor(color);
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));

        ComponentBuilder hoverBuilder = new ComponentBuilder("");
        for (int i = 0; i < hoverLines.size(); i++) {
            if (i > 0) hoverBuilder.append("\n");
            hoverBuilder.append(hoverLines.get(i)).color(ChatColor.YELLOW);
        }
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBuilder.create()));

        currentLine.add(button);
        return this;
    }

    /**
     * Ajoute un espace
     */
    public InteractiveMessage space() {
        TextComponent space = new TextComponent(" ");
        currentLine.add(space);
        return this;
    }

    /**
     * Ajoute plusieurs espaces
     */
    public InteractiveMessage spaces(int count) {
        for (int i = 0; i < count; i++) {
            space();
        }
        return this;
    }

    /**
     * Passe à la ligne suivante
     */
    public InteractiveMessage newLine() {
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toArray(new BaseComponent[0]));
            currentLine.clear();
        }
        return this;
    }

    /**
     * Ajoute une ligne vide
     */
    public InteractiveMessage emptyLine() {
        newLine();
        text("");
        newLine();
        return this;
    }

    /**
     * Ajoute un séparateur
     */
    public InteractiveMessage separator(ChatColor color) {
        text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", color);
        newLine();
        return this;
    }

    /**
     * Ajoute un header avec séparateur
     */
    public InteractiveMessage header(String title, ChatColor color) {
        separator(color);
        text(title, color, true, false);
        newLine();
        separator(color);
        return this;
    }

    /**
     * Envoie le message au joueur
     */
    public void send(Player player) {
        // Ajouter la ligne courante si elle n'est pas vide
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toArray(new BaseComponent[0]));
            currentLine.clear();
        }

        // Envoyer toutes les lignes
        for (BaseComponent[] line : lines) {
            player.spigot().sendMessage(line);
        }
    }

    /**
     * Construit et retourne le message sans l'envoyer
     */
    public BaseComponent[][] build() {
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toArray(new BaseComponent[0]));
            currentLine.clear();
        }
        return lines.toArray(new BaseComponent[0][]);
    }

    // === MÉTHODES STATIQUES POUR CRÉATIONS RAPIDES ===

    /**
     * Crée un message de confirmation standard
     */
    public static InteractiveMessage confirmation(String question, String confirmCommand, String cancelCommand) {
        return new InteractiveMessage()
            .emptyLine()
            .separator(ChatColor.GOLD)
            .text("⚠ CONFIRMATION REQUISE", ChatColor.YELLOW, true, false)
            .newLine()
            .separator(ChatColor.GOLD)
            .emptyLine()
            .text(question, ChatColor.WHITE)
            .emptyLine()
            .button("✓ CONFIRMER", confirmCommand, "Cliquez pour confirmer", ChatColor.GREEN)
            .spaces(3)
            .button("✗ ANNULER", cancelCommand, "Cliquez pour annuler", ChatColor.RED)
            .emptyLine()
            .text("Cette confirmation expire dans 30 secondes", ChatColor.GRAY)
            .newLine()
            .separator(ChatColor.GOLD);
    }

    /**
     * Crée un message de succès
     */
    public static InteractiveMessage success(String message) {
        return new InteractiveMessage()
            .text("[✓] ", ChatColor.GREEN, true, false)
            .text(message, ChatColor.WHITE);
    }

    /**
     * Crée un message d'erreur
     */
    public static InteractiveMessage error(String message) {
        return new InteractiveMessage()
            .text("[✗] ", ChatColor.RED, true, false)
            .text(message, ChatColor.WHITE);
    }

    /**
     * Crée un message d'information
     */
    public static InteractiveMessage info(String message) {
        return new InteractiveMessage()
            .text("[ℹ] ", ChatColor.AQUA, true, false)
            .text(message, ChatColor.WHITE);
    }

    /**
     * Crée un message d'avertissement
     */
    public static InteractiveMessage warning(String message) {
        return new InteractiveMessage()
            .text("[⚠] ", ChatColor.YELLOW, true, false)
            .text(message, ChatColor.WHITE);
    }
}
