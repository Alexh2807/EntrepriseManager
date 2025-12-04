package com.gravityyfh.roleplaycity.mdt.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTPlayer;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MDTTeamSelectionGUI implements InventoryProvider {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;

    public MDTTeamSelectionGUI(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public SmartInventory getInventory() {
        return SmartInventory.builder()
                .id("mdt_team_selection")
                .provider(this)
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .size(3, 9)
                .title(ChatColor.DARK_RED + "⚔ " + ChatColor.BOLD + "Choisis ton équipe" + ChatColor.DARK_RED + " ⚔")
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        MDTPlayer mdtPlayer = game.getPlayer(player.getUniqueId());
        if (mdtPlayer == null) return;

        int totalPlayers = game.getPlayerCount();
        int redCount = game.getTeamPlayerCount(MDTTeam.RED);
        int blueCount = game.getTeamPlayerCount(MDTTeam.BLUE);
        int maxPerTeam = Math.max(1, (totalPlayers + 1) / 2); // Équilibrage: max = ceil(total/2)

        // Équipe ROUGE (slot 2)
        ItemStack redItem = createTeamItem(
                Material.RED_WOOL,
                ChatColor.RED + "" + ChatColor.BOLD + "Équipe ROUGE",
                redCount, maxPerTeam,
                mdtPlayer.getTeam() == MDTTeam.RED
        );
        contents.set(1, 2, ClickableItem.of(redItem, e -> {
            if (redCount >= maxPerTeam && mdtPlayer.getTeam() != MDTTeam.RED) {
                player.sendMessage(ChatColor.RED + "L'équipe rouge est pleine!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            selectTeam(player, mdtPlayer, MDTTeam.RED, game);
        }));

        // ALÉATOIRE (slot 4 - centre)
        ItemStack randomItem = createRandomItem(mdtPlayer.getTeam() == null);
        contents.set(1, 4, ClickableItem.of(randomItem, e -> {
            selectTeam(player, mdtPlayer, null, game); // null = aléatoire
        }));

        // Équipe BLEUE (slot 6)
        ItemStack blueItem = createTeamItem(
                Material.BLUE_WOOL,
                ChatColor.BLUE + "" + ChatColor.BOLD + "Équipe BLEUE",
                blueCount, maxPerTeam,
                mdtPlayer.getTeam() == MDTTeam.BLUE
        );
        contents.set(1, 6, ClickableItem.of(blueItem, e -> {
            if (blueCount >= maxPerTeam && mdtPlayer.getTeam() != MDTTeam.BLUE) {
                player.sendMessage(ChatColor.RED + "L'équipe bleue est pleine!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            selectTeam(player, mdtPlayer, MDTTeam.BLUE, game);
        }));

        // Info en bas
        ItemStack infoItem = createInfoItem(totalPlayers, redCount, blueCount, maxPerTeam);
        contents.set(2, 4, ClickableItem.empty(infoItem));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // Rafraîchir le GUI toutes les secondes
        init(player, contents);
    }

    private ItemStack createTeamItem(Material material, String name, int currentCount, int maxCount, boolean isSelected) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (isSelected) {
                meta.setDisplayName(name + ChatColor.GREEN + " ✓ SÉLECTIONNÉ");
            } else {
                meta.setDisplayName(name);
            }

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Joueurs: " + ChatColor.WHITE + currentCount + "/" + maxCount);
            lore.add("");

            if (currentCount >= maxCount && !isSelected) {
                lore.add(ChatColor.RED + "✗ Équipe pleine!");
            } else if (isSelected) {
                lore.add(ChatColor.GREEN + "✓ Tu es dans cette équipe");
            } else {
                lore.add(ChatColor.YELLOW + "► Clic pour rejoindre");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRandomItem(boolean isSelected) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (isSelected) {
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "ALÉATOIRE" + ChatColor.GREEN + " ✓");
            } else {
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "ALÉATOIRE");
            }

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Le système te placera");
            lore.add(ChatColor.GRAY + "automatiquement dans");
            lore.add(ChatColor.GRAY + "l'équipe la moins remplie.");
            lore.add("");
            if (isSelected) {
                lore.add(ChatColor.GREEN + "✓ Mode sélectionné");
            } else {
                lore.add(ChatColor.YELLOW + "► Clic pour sélectionner");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(int totalPlayers, int redCount, int blueCount, int maxPerTeam) {
        ItemStack item = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Informations");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + maxPerTeam + "v" + maxPerTeam);
            lore.add(ChatColor.GRAY + "Joueurs total: " + ChatColor.WHITE + totalPlayers);
            lore.add("");
            lore.add(ChatColor.RED + "Rouge: " + ChatColor.WHITE + redCount + "/" + maxPerTeam);
            lore.add(ChatColor.BLUE + "Bleu: " + ChatColor.WHITE + blueCount + "/" + maxPerTeam);
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Les équipes seront");
            lore.add(ChatColor.DARK_GRAY + "équilibrées automatiquement");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void selectTeam(Player player, MDTPlayer mdtPlayer, MDTTeam team, MDTGame game) {
        MDTTeam oldTeam = mdtPlayer.getTeam();

        // Si on sélectionne la même équipe, ne rien faire
        if (team == oldTeam) {
            return;
        }

        if (team == null) {
            // Aléatoire - retirer de l'équipe actuelle
            mdtPlayer.setTeam(null);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Tu seras assigné automatiquement à une équipe!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else {
            mdtPlayer.setTeam(team);
            String teamMessage = manager.getConfig().getFormattedMessage("team-joined",
                    "%player%", player.getName(),
                    "%team%", team.getColoredName());
            manager.broadcastToGame(teamMessage);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }

        // Le GUI se rafraîchit automatiquement via update()
    }

    /**
     * Ouvre le GUI de sélection d'équipe pour un joueur
     */
    public static void open(RoleplayCity plugin, MDTRushManager manager, Player player) {
        new MDTTeamSelectionGUI(plugin, manager).getInventory().open(player);
    }
}
