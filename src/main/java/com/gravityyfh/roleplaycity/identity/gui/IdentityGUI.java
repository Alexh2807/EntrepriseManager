package com.gravityyfh.roleplaycity.identity.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.identity.data.Identity;
import com.gravityyfh.roleplaycity.identity.manager.IdentityManager;
import com.gravityyfh.roleplaycity.service.InteractionRequest;
import com.gravityyfh.roleplaycity.service.InteractionRequestManager;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class IdentityGUI {

    private final RoleplayCity plugin;
    private final IdentityManager identityManager;

    public IdentityGUI(RoleplayCity plugin, IdentityManager identityManager) {
        this.plugin = plugin;
        this.identityManager = identityManager;
    }

    public void open(Player player) {
        openMainMenu(player);
    }

    public void openMainMenu(Player player) {
        if (identityManager.hasIdentity(player.getUniqueId())) {
            openIdentityCard(player);
        } else {
            // Rediriger vers la Mairie pour créer une identité
            player.sendMessage(ChatColor.RED + "Vous n'avez pas de carte d'identité.");
            player.sendMessage(ChatColor.YELLOW + "Rendez-vous à la Mairie pour en créer une !");
        }
    }

    private void openIdentityCard(Player player) {
        Identity id = identityManager.getIdentity(player.getUniqueId());
        if (id == null) return;

        // Vérification de sécurité : le manager doit être initialisé
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) {
            player.sendMessage("§cErreur: Le système d'interface n'est pas encore initialisé.");
            plugin.getLogger().severe("[IdentityGUI] bankMenuInventoryManager est null - LightEconomy non initialisé?");
            return;
        }

        SmartInventory.builder()
                .id("identityCard")
                .provider(new IdentityCardProvider(id))
                .size(3, 9)
                .title("§8Votre Carte d'Identité")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- PROVIDERS ---

    private class IdentityCardProvider implements InventoryProvider {
        private final Identity identity;

        public IdentityCardProvider(Identity identity) {
            this.identity = identity;
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.GRAY_STAINED_GLASS_PANE, " ")));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

            // Récupérer le nom Minecraft du joueur
            String playerName = player.getName();

            ItemStack card = createItem(Material.PAPER, "§6Carte d'Identité",
                    "§7Nom: §e" + playerName,
                    "§7Sexe: §f" + identity.getSex(),
                    "§7Âge: §f" + identity.getAge() + " ans",
                    "§7Taille: §f" + identity.getHeight() + " cm",
                    identity.hasResidenceCity() ? "§7Résidence: §f" + identity.getResidenceCity() : "§7Résidence: §cAucune",
                    "§7Délivrée le: §f" + sdf.format(new Date(identity.getCreationDate()))
            );

            contents.set(1, 4, ClickableItem.empty(card));

            // Bouton "Montrer à quelqu'un"
            ItemStack showItem = createItem(Material.ENDER_EYE, "§a📋 Montrer à quelqu'un",
                    "§7Montrer votre carte d'identité",
                    "§7à un joueur à proximité",
                    "",
                    "§eLe joueur doit accepter",
                    "§ede voir votre carte.",
                    "",
                    "§fCliquez pour choisir"
            );

            contents.set(1, 7, ClickableItem.of(showItem, e -> {
                openShowIdSelectionMenu(player);
            }));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    /**
     * Ouvre le menu de sélection de joueur pour montrer son ID
     * N'affiche que les joueurs à proximité (5 blocs)
     */
    public void openShowIdSelectionMenu(Player player) {
        if (de.lightplugins.economy.master.Main.bankMenuInventoryManager == null) {
            player.sendMessage("§cErreur: Le système d'interface n'est pas encore initialisé.");
            return;
        }

        SmartInventory.builder()
                .id("showIdSelection")
                .provider(new ShowIdSelectionProvider())
                .size(3, 9)
                .title("§8Montrer à qui ?")
                .manager(de.lightplugins.economy.master.Main.bankMenuInventoryManager)
                .build()
                .open(player);
    }

    private class ShowIdSelectionProvider implements InventoryProvider {

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(createItem(Material.GRAY_STAINED_GLASS_PANE, " ")));

            int slot = 0;
            boolean found = false;

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(player)) continue;
                if (slot >= 7) break;

                // Vérifier la distance (5 blocs)
                if (!target.getWorld().equals(player.getWorld()) ||
                    target.getLocation().distance(player.getLocation()) > 5) {
                    continue;
                }

                found = true;

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.YELLOW + target.getName());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Distance: " + String.format("%.1fm", target.getLocation().distance(player.getLocation())));
                lore.add("");
                lore.add(ChatColor.GREEN + "► Cliquez pour montrer votre ID");
                meta.setLore(lore);
                head.setItemMeta(meta);

                final Player finalTarget = target;
                contents.set(1, slot++, ClickableItem.of(head, e -> {
                    player.closeInventory();
                    sendShowIdRequest(player, finalTarget);
                }));
            }

            if (!found) {
                ItemStack none = createItem(Material.BARRIER, "§cAucun joueur à proximité",
                        "§7Rapprochez-vous d'un joueur",
                        "§7pour lui montrer votre carte."
                );
                contents.set(1, 4, ClickableItem.empty(none));
            }

            // Bouton retour
            ItemStack back = createItem(Material.ARROW, "§e← Retour");
            contents.set(2, 4, ClickableItem.of(back, e -> openIdentityCard(player)));
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }

    private void sendShowIdRequest(Player player, Player target) {
        InteractionRequestManager requestManager = plugin.getInteractionRequestManager();
        if (requestManager == null) {
            player.sendMessage(ChatColor.RED + "Système non disponible.");
            return;
        }

        // Vérifier s'il n'y a pas déjà une demande en cours
        if (requestManager.hasPendingRequest(player.getUniqueId(), target.getUniqueId(),
                InteractionRequest.RequestType.SHOW_ID)) {
            player.sendMessage(ChatColor.YELLOW + "Une demande est déjà en attente pour ce joueur.");
            return;
        }

        var request = requestManager.createShowIdRequest(player, target);
        if (request != null) {
            player.sendMessage(ChatColor.GREEN + "Demande envoyée à " + target.getName() + ".");
            player.sendMessage(ChatColor.GRAY + "En attente de sa réponse (30 secondes)...");
        }
    }
}