package com.gravityyfh.roleplaycity.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Gestionnaire de navigation unifié avec fil d'Ariane et style cohérent
 * Thème : Fond sombre avec éléments colorés mis en avant
 */
public class NavigationManager {

    // Stack de navigation pour chaque joueur (fil d'Ariane)
    private static final Map<UUID, NavigationStack> playerNavigation = new HashMap<>();

    // Couleurs du thème
    public static final ChatColor BG_DARK = ChatColor.DARK_GRAY;      // Fond sombre
    public static final ChatColor BG_MEDIUM = ChatColor.GRAY;         // Fond moyen
    public static final ChatColor ACCENT = ChatColor.GOLD;            // Couleur d'accent
    public static final ChatColor HIGHLIGHT = ChatColor.YELLOW;       // Mise en évidence
    public static final ChatColor SUCCESS = ChatColor.GREEN;          // Succès
    public static final ChatColor ERROR = ChatColor.RED;              // Erreur
    public static final ChatColor INFO = ChatColor.AQUA;              // Information
    public static final ChatColor TEXT = ChatColor.WHITE;             // Texte normal

    // Caractères spéciaux pour la décoration
    public static final String SEPARATOR_THICK = "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    public static final String SEPARATOR_THIN = "────────────────────────────────";
    public static final String ARROW_RIGHT = "→";
    public static final String ARROW_LEFT = "←";
    public static final String DOT = "•";
    public static final String STAR = "✦";

    /**
     * Classe représentant un niveau de navigation
     */
    public static class NavigationLevel {
        private final String name;
        private final String displayName;
        private final Inventory inventory;
        private final NavigationType type;

        public NavigationLevel(String name, String displayName, Inventory inventory, NavigationType type) {
            this.name = name;
            this.displayName = displayName;
            this.inventory = inventory;
            this.type = type;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public Inventory getInventory() { return inventory; }
        public NavigationType getType() { return type; }
    }

    /**
     * Types de navigation
     */
    public enum NavigationType {
        MAIN_MENU("Menu Principal", Material.COMPASS),
        TOWN_MENU("Ville", Material.EMERALD_BLOCK),
        PLOT_MENU("Terrains", Material.GRASS_BLOCK),
        ECONOMY_MENU("Économie", Material.GOLD_INGOT),
        COMPANY_MENU("Entreprises", Material.CRAFTING_TABLE),
        ADMIN_MENU("Administration", Material.COMMAND_BLOCK);

        private final String displayName;
        private final Material icon;

        NavigationType(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
    }

    /**
     * Stack de navigation pour un joueur
     */
    private static class NavigationStack {
        private final Deque<NavigationLevel> stack = new ArrayDeque<>();

        void push(NavigationLevel level) {
            stack.push(level);
        }

        NavigationLevel pop() {
            return stack.isEmpty() ? null : stack.pop();
        }

        NavigationLevel peek() {
            return stack.isEmpty() ? null : stack.peek();
        }

        void clear() {
            stack.clear();
        }

        List<NavigationLevel> getPath() {
            List<NavigationLevel> path = new ArrayList<>(stack);
            Collections.reverse(path);
            return path;
        }

        String getBreadcrumb() {
            if (stack.isEmpty()) return "";

            StringBuilder breadcrumb = new StringBuilder();
            List<NavigationLevel> path = getPath();

            for (int i = 0; i < path.size(); i++) {
                if (i > 0) {
                    breadcrumb.append(ChatColor.DARK_GRAY).append(" > ");
                }
                breadcrumb.append(i == path.size() - 1 ? ACCENT : BG_MEDIUM);
                breadcrumb.append(path.get(i).getDisplayName());
            }

            return breadcrumb.toString();
        }
    }

    /**
     * Crée un inventaire avec le style unifié
     */
    public static Inventory createStyledInventory(String title, int size) {
        // Titre avec fond sombre et texte coloré
        String styledTitle = BG_DARK + "[" + ACCENT + ChatColor.BOLD + title + BG_DARK + "]";
        return Bukkit.createInventory(null, size, styledTitle);
    }

    /**
     * Navigue vers un nouveau menu
     */
    public static void navigateTo(Player player, String name, String displayName,
                                 Inventory inventory, NavigationType type) {
        UUID playerUuid = player.getUniqueId();
        NavigationStack stack = playerNavigation.computeIfAbsent(playerUuid, k -> new NavigationStack());

        NavigationLevel level = new NavigationLevel(name, displayName, inventory, type);
        stack.push(level);

        // Ajouter le fil d'Ariane en bas de l'inventaire
        addBreadcrumbToInventory(inventory, stack.getBreadcrumb());

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Retourne au menu précédent
     */
    public static void navigateBack(Player player) {
        UUID playerUuid = player.getUniqueId();
        NavigationStack stack = playerNavigation.get(playerUuid);

        if (stack == null || stack.stack.size() <= 1) {
            player.closeInventory();
            return;
        }

        stack.pop(); // Retirer le menu actuel
        NavigationLevel previous = stack.peek();

        if (previous != null) {
            player.openInventory(previous.getInventory());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
        } else {
            player.closeInventory();
        }
    }

    /**
     * Retourne au menu principal
     */
    public static void navigateHome(Player player) {
        UUID playerUuid = player.getUniqueId();
        NavigationStack stack = playerNavigation.get(playerUuid);

        if (stack != null) {
            stack.clear();
        }

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
    }

    /**
     * Ajoute le fil d'Ariane à l'inventaire
     */
    private static void addBreadcrumbToInventory(Inventory inventory, String breadcrumb) {
        int size = inventory.getSize();

        // Bouton Retour (slot bottom-left)
        ItemStack backButton = createNavigationButton(
            Material.ARROW,
            ERROR + "" + ChatColor.BOLD + ARROW_LEFT + " Retour",
            Arrays.asList(
                BG_MEDIUM + "Clic pour revenir",
                BG_MEDIUM + "au menu précédent"
            )
        );
        inventory.setItem(size - 9, backButton);

        // Fil d'Ariane (slot bottom-center)
        if (!breadcrumb.isEmpty()) {
            ItemStack breadcrumbItem = createNavigationButton(
                Material.PAPER,
                ACCENT + "Navigation",
                Arrays.asList(
                    BG_DARK + SEPARATOR_THIN,
                    breadcrumb,
                    BG_DARK + SEPARATOR_THIN
                )
            );
            inventory.setItem(size - 5, breadcrumbItem);
        }

        // Bouton Accueil (slot bottom-right)
        ItemStack homeButton = createNavigationButton(
            Material.COMPASS,
            SUCCESS + "" + ChatColor.BOLD + "⌂ Accueil",
            Arrays.asList(
                BG_MEDIUM + "Clic pour retourner",
                BG_MEDIUM + "au menu principal"
            )
        );
        inventory.setItem(size - 1, homeButton);
    }

    /**
     * Crée un bouton de navigation
     */
    private static ItemStack createNavigationButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item stylisé pour les menus
     */
    public static ItemStack createStyledItem(Material material, String name, List<String> description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Nom avec style
        meta.setDisplayName(ACCENT + "" + ChatColor.BOLD + name);

        // Description avec style
        List<String> styledLore = new ArrayList<>();
        styledLore.add(BG_DARK + SEPARATOR_THIN);

        for (String line : description) {
            if (line.contains(":")) {
                // Format label: valeur
                String[] parts = line.split(":", 2);
                styledLore.add(HIGHLIGHT + parts[0] + ":" + TEXT + parts[1]);
            } else if (line.startsWith("!")) {
                // Ligne d'alerte
                styledLore.add(ERROR + line.substring(1));
            } else if (line.startsWith("+")) {
                // Ligne positive
                styledLore.add(SUCCESS + line.substring(1));
            } else if (line.startsWith("-")) {
                // Ligne négative
                styledLore.add(ERROR + line.substring(1));
            } else if (line.startsWith("*")) {
                // Point important
                styledLore.add(INFO + DOT + " " + TEXT + line.substring(1));
            } else {
                // Ligne normale
                styledLore.add(BG_MEDIUM + line);
            }
        }

        styledLore.add(BG_DARK + SEPARATOR_THIN);
        meta.setLore(styledLore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Envoie un message stylisé dans le chat
     */
    public static void sendStyledMessage(Player player, String title, List<String> lines) {
        player.sendMessage("");
        player.sendMessage(BG_DARK + SEPARATOR_THICK);
        player.sendMessage(ACCENT + "" + ChatColor.BOLD + "     " + title);
        player.sendMessage(BG_DARK + SEPARATOR_THICK);

        for (String line : lines) {
            if (line.isEmpty()) {
                player.sendMessage("");
            } else if (line.startsWith("!")) {
                // Ligne d'alerte
                player.sendMessage(ERROR + " " + STAR + " " + line.substring(1));
            } else if (line.startsWith("+")) {
                // Ligne positive
                player.sendMessage(SUCCESS + " " + DOT + " " + line.substring(1));
            } else if (line.startsWith("-")) {
                // Ligne négative
                player.sendMessage(ERROR + " " + DOT + " " + line.substring(1));
            } else if (line.contains(":")) {
                // Format label: valeur
                String[] parts = line.split(":", 2);
                player.sendMessage(HIGHLIGHT + " " + parts[0] + ":" + TEXT + parts[1]);
            } else {
                // Ligne normale
                player.sendMessage(BG_MEDIUM + " " + line);
            }
        }

        player.sendMessage(BG_DARK + SEPARATOR_THICK);
        player.sendMessage("");
    }

    /**
     * Envoie un message de succès stylisé
     */
    public static void sendSuccess(Player player, String message) {
        player.sendMessage("");
        player.sendMessage(BG_DARK + SEPARATOR_THIN);
        player.sendMessage(SUCCESS + " " + ChatColor.BOLD + "SUCCÈS");
        player.sendMessage(TEXT + " " + message);
        player.sendMessage(BG_DARK + SEPARATOR_THIN);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
    }

    /**
     * Envoie un message d'erreur stylisé
     */
    public static void sendError(Player player, String message) {
        player.sendMessage("");
        player.sendMessage(BG_DARK + SEPARATOR_THIN);
        player.sendMessage(ERROR + " " + ChatColor.BOLD + "ERREUR");
        player.sendMessage(TEXT + " " + message);
        player.sendMessage(BG_DARK + SEPARATOR_THIN);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    /**
     * Envoie un message d'information stylisé
     */
    public static void sendInfo(Player player, String title, String message) {
        player.sendMessage("");
        player.sendMessage(BG_DARK + SEPARATOR_THIN);
        player.sendMessage(INFO + " " + ChatColor.BOLD + title);
        player.sendMessage(TEXT + " " + message);
        player.sendMessage(BG_DARK + SEPARATOR_THIN);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /**
     * Nettoie la navigation d'un joueur
     */
    public static void clearNavigation(UUID playerUuid) {
        playerNavigation.remove(playerUuid);
    }

    /**
     * Récupère le chemin de navigation actuel
     */
    public static String getCurrentPath(UUID playerUuid) {
        NavigationStack stack = playerNavigation.get(playerUuid);
        return stack != null ? stack.getBreadcrumb() : "";
    }
}