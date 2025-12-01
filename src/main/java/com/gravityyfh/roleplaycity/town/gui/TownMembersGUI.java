package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI pour gérer les membres et rôles d'une ville
 */
public class TownMembersGUI implements Listener {
    private final RoleplayCity plugin;
    private final TownManager townManager;
    private TownMainGUI mainGUI;

    public TownMembersGUI(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
    }

    public void setMainGUI(TownMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    public void openMembersMenu(Player player) {
        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        // Admin override = accès maire
        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        TownRole playerRole = isAdminOverride ? TownRole.MAIRE : town.getMemberRole(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.AQUA + "👥 Membres de " + townName);

        // Liste des membres
        int slot = 0;
        for (TownMember member : town.getMembers().values()) {
            if (slot >= 45) break; // Réserver les dernières lignes pour les actions

            ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) memberItem.getItemMeta();

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getPlayerUuid());
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(ChatColor.YELLOW + member.getPlayerName());

            List<String> lore = new ArrayList<>();

            // Afficher le rôle unique du joueur
            TownRole memberRole = member.getRole();
            lore.add(ChatColor.GRAY + "Rôle: " + ChatColor.AQUA + memberRole.getDisplayName());

            if (member.getPlayerUuid().equals(town.getMayorUuid())) {
                lore.add(ChatColor.GOLD + "★ Maire de la ville ★");
            }

            boolean isOnline = offlinePlayer.isOnline();
            lore.add(ChatColor.GRAY + "Statut: " + (isOnline ? ChatColor.GREEN + "En ligne" : ChatColor.RED + "Hors ligne"));
            lore.add("");

            // Actions disponibles selon le rôle
            if (playerRole == TownRole.MAIRE && !member.getPlayerUuid().equals(player.getUniqueId())) {
                lore.add(ChatColor.YELLOW + "Clic gauche: Changer le rôle");
                lore.add(ChatColor.RED + "Clic droit: Exclure");
            }

            meta.setLore(lore);
            memberItem.setItemMeta(meta);

            inv.setItem(slot++, memberItem);
        }

        // Boutons d'action en bas
        if (playerRole == TownRole.MAIRE || playerRole == TownRole.ADJOINT) {
            ItemStack inviteItem = new ItemStack(Material.EMERALD);
            ItemMeta inviteMeta = inviteItem.getItemMeta();
            inviteMeta.setDisplayName(ChatColor.GREEN + "Inviter un joueur");
            List<String> inviteLore = new ArrayList<>();
            inviteLore.add(ChatColor.GRAY + "Invitez un joueur proche");
            inviteLore.add(ChatColor.GRAY + "dans votre ville");
            inviteLore.add("");
            inviteLore.add(ChatColor.YELLOW + "Cliquez pour voir les joueurs proches");
            inviteMeta.setLore(inviteLore);
            inviteItem.setItemMeta(inviteMeta);
            inv.setItem(48, inviteItem);
        }

        // Bouton retour (haut gauche)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(0, backItem);

        // Bouton fermer (haut droite)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(8, closeItem);

        player.openInventory(inv);
    }

    public void openRoleSelectionMenu(Player player, UUID targetUuid, String targetName) {
        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return;
        }

        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        if (!isAdminOverride && !town.isMayor(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Seul le maire peut changer les rôles.");
            return;
        }

        TownMember targetMember = town.getMember(targetUuid);
        if (targetMember == null) {
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "Rôle de " + targetName);

        // UN SEUL RÔLE : Récupérer le rôle actuel du joueur
        TownRole currentRole = targetMember.getRole();

        int slot = 10;
        for (TownRole role : TownRole.values()) {
            if (role == TownRole.MAIRE) continue; // Le maire ne peut pas être assigné

            boolean hasRole = (currentRole == role);

            ItemStack roleItem = new ItemStack(getRoleIcon(role));
            ItemMeta meta = roleItem.getItemMeta();

            // Vérifier si le rôle peut être attribué selon le niveau de ville et les limites
            var levelManager = plugin.getTownLevelManager();
            var assignmentResult = levelManager.canAssignRole(town, role);
            boolean canAssign = assignmentResult.canAssign() || hasRole; // Peut toujours retirer son rôle actuel

            // Afficher avec état actif/inactif
            if (hasRole) {
                meta.setDisplayName(ChatColor.GREEN + "✓ " + role.getDisplayName() + " (Actuel)");
            } else if (!canAssign) {
                meta.setDisplayName(ChatColor.RED + "✗ " + role.getDisplayName() + " (Indisponible)");
            } else {
                meta.setDisplayName(ChatColor.YELLOW + "○ " + role.getDisplayName());
            }

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Permissions:");
            if (role.canManageTown()) lore.add(ChatColor.GREEN + "  ✓ Gérer la ville");
            if (role.canManageClaims()) lore.add(ChatColor.GREEN + "  ✓ Gérer les claims");
            lore.add("");

            if (hasRole) {
                lore.add(ChatColor.DARK_GRAY + "Rôle actuel du joueur");
                lore.add(ChatColor.YELLOW + "Cliquez pour changer");
            } else if (!canAssign) {
                // Afficher pourquoi le rôle n'est pas disponible
                var config = levelManager.getConfig(town.getLevel());
                if (!config.isRoleAvailable(role)) {
                    lore.add(ChatColor.RED + "Niveau de ville insuffisant");
                    lore.add(ChatColor.GRAY + "Nécessite: " + ChatColor.WHITE + "Village ou plus");
                } else {
                    int current = town.getMembersByRole(role).size();
                    int max = config.getRoleLimit(role);
                    lore.add(ChatColor.RED + "Limite atteinte: " + current + "/" + max);
                }
            } else {
                lore.add(ChatColor.YELLOW + "Cliquez pour attribuer ce rôle");
            }

            meta.setLore(lore);
            roleItem.setItemMeta(meta);

            inv.setItem(slot++, roleItem);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);

        // Stocker l'UUID cible dans les métadonnées temporaires
        player.setMetadata("town_role_target", new org.bukkit.metadata.FixedMetadataValue(plugin, targetUuid.toString()));
    }

    private Material getRoleIcon(TownRole role) {
        return switch (role) {
            case MAIRE -> Material.DIAMOND_BLOCK;
            case ADJOINT -> Material.GOLD_BLOCK;
            case POLICIER -> Material.IRON_CHESTPLATE;
            case JUGE -> Material.GOLDEN_SWORD;
            case MEDECIN -> Material.GOLDEN_APPLE;
            case ARCHITECTE -> Material.BRICK;
            case CITOYEN -> Material.PLAYER_HEAD;
            default -> Material.PLAYER_HEAD;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        // FIX: "Rôles:" corrigé en "Rôle de" pour correspondre au titre du menu de sélection de rôle
        if (!title.contains("Membres de") && !title.contains("Rôle de") && !title.contains("Inviter un joueur")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String displayName = clicked.getItemMeta().getDisplayName();
        String strippedName = ChatColor.stripColor(displayName);

        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return;
        }

        // Admin override = accès maire
        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        TownRole playerRole = isAdminOverride ? TownRole.MAIRE : town.getMemberRole(player.getUniqueId());

        if (title.contains("Membres de")) {
            if (strippedName.contains("Retour")) {
                player.closeInventory();
                if (mainGUI != null) {
                    mainGUI.openMainMenu(player);
                }
            } else if (strippedName.contains("Fermer")) {
                player.closeInventory();
            } else if (strippedName.contains("Inviter")) {
                player.closeInventory();
                openInvitePlayerMenu(player);
            } else if (clicked.getType() == Material.PLAYER_HEAD) {
                // Clic sur un membre
                if (playerRole != TownRole.MAIRE) {
                    return;
                }

                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                OfflinePlayer target = meta.getOwningPlayer();
                if (target == null) {
                    return;
                }

                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Vous ne pouvez pas modifier votre propre rôle.");
                    return;
                }

                if (town.isMayor(target.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Vous ne pouvez pas modifier le rôle du maire.");
                    return;
                }

                if (event.isLeftClick()) {
                    // Changer le rôle
                    player.closeInventory();
                    String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
                    openRoleSelectionMenu(player, target.getUniqueId(), targetName);
                } else if (event.isRightClick()) {
                    // Exclure le membre
                    player.closeInventory();
                    if (townManager.kickMember(townName, player, target.getUniqueId())) {
                        String kickedName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
                        player.sendMessage(ChatColor.GREEN + kickedName + " a été exclu de la ville.");

                        Player targetPlayer = target.getPlayer();
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            targetPlayer.sendMessage(ChatColor.RED + "Vous avez été exclu de la ville " + townName + ".");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Impossible d'exclure ce membre.");
                    }
                }
            }
        } else if (title.contains("Rôle de")) {
            if (strippedName.contains("Retour")) {
                player.closeInventory();
                openMembersMenu(player);
            } else if (strippedName.contains("Indisponible")) {
                // Rôle indisponible - ne rien faire
                player.sendMessage(ChatColor.RED + "Ce rôle n'est pas disponible pour votre ville.");
            } else {
                // Changer le rôle (UN SEUL RÔLE À LA FOIS)
                TownRole selectedRole = null;
                for (TownRole role : TownRole.values()) {
                    if (strippedName.contains(role.getDisplayName())) {
                        selectedRole = role;
                        break;
                    }
                }

                if (selectedRole != null && player.hasMetadata("town_role_target")) {
                    String targetUuidStr = player.getMetadata("town_role_target").get(0).asString();
                    UUID targetUuid = UUID.fromString(targetUuidStr);

                    TownMember targetMember = town.getMember(targetUuid);
                    if (targetMember == null) {
                        return;
                    }

                    TownRole currentRole = targetMember.getRole();
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
                    String targetRoleDisplayName = target.getName() != null ? target.getName() : targetUuid.toString().substring(0, 8);

                    // Vérifier si le rôle cliqué est déjà le rôle actuel
                    if (currentRole == selectedRole) {
                        player.sendMessage(ChatColor.YELLOW + targetRoleDisplayName + " a déjà le rôle " + selectedRole.getDisplayName());
                        return;
                    }

                    // Vérifier si le rôle peut être attribué (niveau de ville + limites)
                    var levelManager = plugin.getTownLevelManager();
                    var assignmentResult = levelManager.canAssignRole(town, selectedRole);

                    if (!assignmentResult.canAssign()) {
                        player.closeInventory();
                        player.sendMessage("");
                        player.sendMessage(assignmentResult.message());
                        player.sendMessage("");
                        return;
                    }

                    // Attribuer le nouveau rôle (remplace automatiquement l'ancien)
                    targetMember.addRole(selectedRole);
                    player.sendMessage(ChatColor.GREEN + "✓ Rôle de " + targetRoleDisplayName + " changé:");
                    player.sendMessage(ChatColor.GRAY + "  Ancien: " + ChatColor.YELLOW + currentRole.getDisplayName());
                    player.sendMessage(ChatColor.GRAY + "  Nouveau: " + ChatColor.GREEN + selectedRole.getDisplayName());

                    // ========================================
                    // DÉSACTIVER LE SERVICE SI RÔLE INCOMPATIBLE
                    // ========================================
                    checkAndDeactivateServiceOnRoleChange(targetUuid, currentRole, selectedRole);

                    Player targetPlayer = target.getPlayer();
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        targetPlayer.sendMessage("");
                        targetPlayer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        targetPlayer.sendMessage(ChatColor.AQUA + "   👔 CHANGEMENT DE RÔLE");
                        targetPlayer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        targetPlayer.sendMessage("");
                        targetPlayer.sendMessage(ChatColor.YELLOW + "Votre rôle dans " + ChatColor.AQUA + townName + ChatColor.YELLOW + " a changé:");
                        targetPlayer.sendMessage(ChatColor.GRAY + "  Ancien: " + ChatColor.RED + currentRole.getDisplayName());
                        targetPlayer.sendMessage(ChatColor.GRAY + "  Nouveau: " + ChatColor.GREEN + selectedRole.getDisplayName());
                        targetPlayer.sendMessage("");
                        targetPlayer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        targetPlayer.sendMessage("");
                    }

                    // Rafraîchir le menu
                    openRoleSelectionMenu(player, targetUuid, targetRoleDisplayName);
                }
            }
        } else if (title.contains("Inviter un joueur")) {
            if (strippedName.contains("Retour")) {
                player.closeInventory();
                openMembersMenu(player);
            } else if (clicked.getType() == Material.PLAYER_HEAD) {
                // Clic sur un joueur pour l'inviter
                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                OfflinePlayer target = meta.getOwningPlayer();
                if (target == null) {
                    return;
                }

                Player targetPlayer = target.getPlayer();
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Ce joueur n'est plus en ligne.");
                    player.closeInventory();
                    return;
                }

                if (townManager.invitePlayer(townName, player, targetPlayer)) {
                    player.sendMessage(ChatColor.GREEN + "Vous avez invité " + targetPlayer.getName() + " dans votre ville!");

                    // Envoyer invitation avec boutons interactifs
                    sendInvitationWithButtons(targetPlayer, townName, player.getName());
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible d'inviter ce joueur.");
                }
                player.closeInventory();
                openMembersMenu(player);
            }
        }
    }

    private void openInvitePlayerMenu(Player inviter) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GREEN + "Inviter un joueur");

        // Liste des joueurs en ligne dans un rayon de 50 blocs
        // FIX BASSE #16: Renamed 'p' → 'nearbyPlayer' for clarity
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Player nearbyPlayer : Bukkit.getOnlinePlayers()) {
            if (!nearbyPlayer.equals(inviter) &&
                nearbyPlayer.getWorld().equals(inviter.getWorld()) &&
                nearbyPlayer.getLocation().distance(inviter.getLocation()) <= 50) {

                // Vérifier qu'il n'est pas déjà dans une ville
                if (townManager.getPlayerTown(nearbyPlayer.getUniqueId()) == null) {
                    nearbyPlayers.add(nearbyPlayer);
                }
            }
        }

        int slot = 0;
        for (Player target : nearbyPlayers) {
            if (slot >= 45) break;

            ItemStack playerItem = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerItem.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GREEN + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Distance: " + ChatColor.WHITE +
                     String.format("%.1f", inviter.getLocation().distance(target.getLocation())) + " blocs");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Cliquez pour inviter");
            meta.setLore(lore);
            playerItem.setItemMeta(meta);
            inv.setItem(slot, playerItem);
            slot++;
        }

        if (nearbyPlayers.isEmpty()) {
            ItemStack noPlayerItem = new ItemStack(Material.BARRIER);
            ItemMeta noPlayerMeta = noPlayerItem.getItemMeta();
            noPlayerMeta.setDisplayName(ChatColor.RED + "Aucun joueur disponible");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Aucun joueur sans ville");
            lore.add(ChatColor.GRAY + "dans un rayon de 50 blocs");
            noPlayerMeta.setLore(lore);
            noPlayerItem.setItemMeta(noPlayerMeta);
            inv.setItem(22, noPlayerItem);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        inviter.openInventory(inv);
    }

    private void sendInvitationWithButtons(Player invitedPlayer, String townName, String inviterName) {
        invitedPlayer.sendMessage("");
        invitedPlayer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        invitedPlayer.sendMessage(ChatColor.GOLD + "    🏙️ INVITATION DE VILLE");
        invitedPlayer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        invitedPlayer.sendMessage("");
        invitedPlayer.sendMessage(ChatColor.YELLOW + inviterName + ChatColor.GRAY + " vous invite à rejoindre");
        invitedPlayer.sendMessage(ChatColor.AQUA + "        ➤ " + townName);
        invitedPlayer.sendMessage("");

        // Créer les boutons interactifs
        net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent("");

        // Bouton ACCEPTER (vert)
        net.md_5.bungee.api.chat.TextComponent acceptButton = new net.md_5.bungee.api.chat.TextComponent(
            ChatColor.GREEN + "" + ChatColor.BOLD + "[✓ ACCEPTER]"
        );
        acceptButton.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            "/ville accept " + townName
        ));
        acceptButton.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(
                ChatColor.GREEN + "Cliquez pour accepter l'invitation"
            ).create()
        ));

        // Espace entre les boutons
        net.md_5.bungee.api.chat.TextComponent space = new net.md_5.bungee.api.chat.TextComponent("  ");

        // Bouton REFUSER (rouge)
        net.md_5.bungee.api.chat.TextComponent refuseButton = new net.md_5.bungee.api.chat.TextComponent(
            ChatColor.RED + "" + ChatColor.BOLD + "[✗ REFUSER]"
        );
        refuseButton.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            "/ville refuse " + townName
        ));
        refuseButton.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(
                ChatColor.RED + "Cliquez pour refuser l'invitation"
            ).create()
        ));

        // Assembler les composants
        message.addExtra("        "); // Centrage
        message.addExtra(acceptButton);
        message.addExtra(space);
        message.addExtra(refuseButton);

        invitedPlayer.spigot().sendMessage(message);
        invitedPlayer.sendMessage("");
        invitedPlayer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        invitedPlayer.sendMessage("");
    }

    /**
     * Vérifie et désactive le service professionnel si le nouveau rôle n'est plus compatible
     * Appelé après un changement de rôle par le maire
     */
    private void checkAndDeactivateServiceOnRoleChange(UUID targetUuid, TownRole oldRole, TownRole newRole) {
        ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
        if (serviceManager == null) {
            return;
        }

        // Vérifier si le joueur a un service actif
        if (!serviceManager.isInAnyService(targetUuid)) {
            return;
        }

        // Récupérer le type de service actif
        ProfessionalServiceType activeService = serviceManager.getActiveServiceType(targetUuid);
        if (activeService == null || activeService == ProfessionalServiceType.ENTERPRISE) {
            return; // Les services entreprise ne dépendent pas des rôles de ville
        }

        // Vérifier si le nouveau rôle est compatible avec le service actif
        boolean isCompatible = switch (activeService) {
            case POLICE -> newRole == TownRole.POLICIER;
            case MEDICAL -> newRole == TownRole.MEDECIN;
            case JUDGE -> newRole == TownRole.JUGE;
            default -> true;
        };

        // Si le rôle n'est plus compatible, désactiver le service
        if (!isCompatible) {
            serviceManager.forceDeactivateService(targetUuid, "Votre rôle a été modifié par le maire");
            plugin.getLogger().info("[TownMembersGUI] Service " + activeService.getDisplayName() +
                " désactivé pour " + targetUuid + " suite au changement de rôle: " +
                oldRole.getDisplayName() + " -> " + newRole.getDisplayName());
        }
    }
}
