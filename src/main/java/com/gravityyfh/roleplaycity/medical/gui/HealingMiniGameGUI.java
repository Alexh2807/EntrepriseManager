package com.gravityyfh.roleplaycity.medical.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Mini-jeu de suture pour les soins médicaux
 * Le médecin doit cliquer sur les laines rouges pour les transformer en vertes
 */
public class HealingMiniGameGUI {

    private final Player medic;
    private final Inventory inventory;
    private final Set<Integer> woundSlots; // Slots des plaies à recoudre
    private final Set<Integer> suturedSlots; // Slots déjà recousus
    private final int totalWounds;
    private final Runnable onComplete;
    private final Runnable onFail;

    /**
     * Crée un mini-jeu de suture
     * @param medic Le médecin qui effectue les soins
     * @param difficulty Nombre de points de suture (1-9)
     * @param onComplete Action à exécuter quand tous les points sont faits
     * @param onFail Action à exécuter si le médecin ferme le GUI avant
     */
    public HealingMiniGameGUI(Player medic, int difficulty, Runnable onComplete, Runnable onFail) {
        this.medic = medic;
        this.onComplete = onComplete;
        this.onFail = onFail;
        this.woundSlots = new HashSet<>();
        this.suturedSlots = new HashSet<>();
        this.totalWounds = Math.min(Math.max(difficulty, 1), 9);

        // Créer l'inventaire
        this.inventory = Bukkit.createInventory(null, 27, ChatColor.RED + "❤ Recoudre les plaies");

        // Initialiser le GUI
        setupGUI();
    }

    /**
     * Configure le GUI avec les plaies à recoudre
     */
    private void setupGUI() {
        // Remplir avec du verre noir (fond)
        ItemStack background = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = background.getItemMeta();
        bgMeta.setDisplayName(" ");
        background.setItemMeta(bgMeta);

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }

        // Placer les plaies aléatoirement dans la zone centrale
        List<Integer> availableSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16);
        Collections.shuffle(availableSlots);

        for (int i = 0; i < totalWounds; i++) {
            int slot = availableSlots.get(i);
            woundSlots.add(slot);
            inventory.setItem(slot, createWoundItem());
        }

        // Ajouter un indicateur de progression
        updateProgressIndicator();
    }

    /**
     * Crée un item représentant une plaie
     */
    private ItemStack createWoundItem() {
        ItemStack wound = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = wound.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "❌ Plaie ouverte");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Cliquez pour recoudre",
            ChatColor.YELLOW + "➤ Point de suture requis"
        ));
        wound.setItemMeta(meta);
        return wound;
    }

    /**
     * Crée un item représentant une plaie recousue
     */
    private ItemStack createSuturedItem() {
        ItemStack sutured = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = sutured.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "✓ Plaie recousue");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Point de suture effectué",
            ChatColor.GREEN + "✓ Terminé"
        ));
        sutured.setItemMeta(meta);
        return sutured;
    }

    /**
     * Met à jour l'indicateur de progression
     */
    private void updateProgressIndicator() {
        int progress = suturedSlots.size();
        int total = totalWounds;

        ItemStack indicator = new ItemStack(Material.PAPER);
        ItemMeta meta = indicator.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Progression: " + progress + "/" + total);
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Points de suture effectués",
            "",
            progress == total
                ? ChatColor.GREEN + "✓ Tous les points sont faits !"
                : ChatColor.YELLOW + "➤ " + (total - progress) + " point(s) restant(s)"
        ));
        indicator.setItemMeta(meta);

        inventory.setItem(4, indicator);
    }

    /**
     * Gère le clic sur un slot
     * @param slot Le slot cliqué
     * @return true si c'était une plaie valide, false sinon
     */
    public boolean handleClick(int slot) {
        // Vérifier si c'est une plaie non recousue
        if (woundSlots.contains(slot) && !suturedSlots.contains(slot)) {
            // Marquer comme recousu
            suturedSlots.add(slot);

            // Changer le visuel
            inventory.setItem(slot, createSuturedItem());

            // Mettre à jour la progression
            updateProgressIndicator();

            // Son de succès
            medic.playSound(medic.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            // Message d'encouragement
            medic.sendMessage(ChatColor.GREEN + "✓ Point de suture effectué ! " +
                ChatColor.GRAY + "(" + suturedSlots.size() + "/" + totalWounds + ")");

            // Vérifier si tous les points sont faits
            if (suturedSlots.size() == totalWounds) {
                completeMinigame();
            }

            return true;
        }

        return false;
    }

    /**
     * Termine le mini-jeu avec succès
     */
    private void completeMinigame() {
        medic.sendMessage("");
        medic.sendMessage(ChatColor.GREEN + "✓ Toutes les plaies ont été recousues !");
        medic.sendMessage(ChatColor.GRAY + "Les soins peuvent continuer...");
        medic.sendMessage("");

        medic.playSound(medic.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Fermer le GUI après 1 seconde
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("RoleplayCity"),
            () -> {
                medic.closeInventory();
                if (onComplete != null) {
                    onComplete.run();
                }
            },
            20L
        );
    }

    /**
     * Appelé quand le médecin ferme le GUI avant de finir
     */
    public void onPrematureClose() {
        if (suturedSlots.size() < totalWounds) {
            medic.sendMessage(ChatColor.RED + "❌ Vous avez interrompu les soins !");
            medic.sendMessage(ChatColor.GRAY + "Il restait " + (totalWounds - suturedSlots.size()) + " point(s) à faire.");

            if (onFail != null) {
                onFail.run();
            }
        }
    }

    /**
     * Ouvre le GUI pour le médecin
     */
    public void open() {
        medic.openInventory(inventory);

        medic.sendMessage("");
        medic.sendMessage(ChatColor.YELLOW + "⚕ Mini-jeu de suture !");
        medic.sendMessage(ChatColor.GRAY + "Cliquez sur les " + ChatColor.RED + "plaies rouges" +
            ChatColor.GRAY + " pour les recoudre");
        medic.sendMessage(ChatColor.YELLOW + "➤ " + totalWounds + " point(s) de suture à effectuer");
        medic.sendMessage("");

        medic.playSound(medic.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
    }

    /**
     * Vérifie si cet inventaire appartient à ce mini-jeu
     */
    public boolean isThisInventory(Inventory inv) {
        return inv.equals(this.inventory);
    }

    /**
     * Vérifie si le mini-jeu est terminé
     */
    public boolean isCompleted() {
        return suturedSlots.size() == totalWounds;
    }

    public Player getMedic() {
        return medic;
    }
}
