package com.gravityyfh.roleplaycity.mdt.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.*;
import com.gravityyfh.roleplaycity.mdt.gui.MDTBedSelectionGUI;
// Note: BlockTracker n'existe plus avec le système FAWE
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import java.util.Iterator;

public class MDTBedListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;

    public MDTBedListener(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Empêche de dormir dans un lit dans le monde MDT (toujours désactivé)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        String mdtWorld = manager.getConfig().getWorldName();

        // Bloquer le sommeil dans le monde MDT (même sans partie active)
        if (player.getWorld().getName().equalsIgnoreCase(mdtWorld)) {
            event.setCancelled(true);
            // Pas de message pour ne pas spammer
            return;
        }

        // Si le joueur est dans une partie MDT (mais dans un autre monde)
        if (manager.isPlayerInGame(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Tu ne peux pas dormir pendant le MDT!");
        }
    }

    /**
     * Empeche de definir le point de respawn sur un lit dans le monde MDT
     * Bloque le message "Respawn point set" sauf en mode setup
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!event.getClickedBlock().getType().name().endsWith("_BED")) return;

        Player player = event.getPlayer();
        String mdtWorld = manager.getConfig().getWorldName();

        // Si le joueur est en mode setup (a un type de lit en attente), appliquer directement
        if (MDTBedSelectionGUI.hasPendingType(player.getUniqueId())) {
            event.setCancelled(true);

            // Recuperer et appliquer le type de lit
            String type = MDTBedSelectionGUI.getPendingType(player.getUniqueId());
            if (type != null) {
                org.bukkit.Location bedLoc = event.getClickedBlock().getLocation();
                applyBedSetup(player, type, bedLoc);
            }
            return;
        }

        // Bloquer l'interaction avec les lits dans le monde MDT
        if (player.getWorld().getName().equalsIgnoreCase(mdtWorld)) {
            event.setCancelled(true);
            return;
        }

        // Bloquer aussi si le joueur est dans une partie MDT (peu importe le monde)
        if (manager.isPlayerInGame(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Applique la configuration du lit selon le type selectionne
     */
    private void applyBedSetup(Player player, String type, org.bukkit.Location bedLoc) {
        switch (type) {
            case "RED" -> {
                manager.getConfig().setBedLocation(MDTTeam.RED, bedLoc);
                manager.getConfig().setTeamSpawn(MDTTeam.RED, bedLoc.clone().add(0.5, 0.1, 0.5));
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "✓ Lit de l'équipe ROUGE défini !");
                player.sendMessage(ChatColor.RED + "✓ Spawn de l'équipe ROUGE défini !");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            case "BLUE" -> {
                manager.getConfig().setBedLocation(MDTTeam.BLUE, bedLoc);
                manager.getConfig().setTeamSpawn(MDTTeam.BLUE, bedLoc.clone().add(0.5, 0.1, 0.5));
                player.sendMessage("");
                player.sendMessage(ChatColor.BLUE + "✓ Lit de l'équipe BLEUE défini !");
                player.sendMessage(ChatColor.BLUE + "✓ Spawn de l'équipe BLEUE défini !");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            case "NEUTRAL" -> {
                manager.getConfig().addNeutralBedLocation(bedLoc);
                player.sendMessage("");
                player.sendMessage(ChatColor.WHITE + "✓ Lit Neutre ajouté !");
                player.sendMessage(ChatColor.GRAY + "  Bonus: " + ChatColor.RED + "+2 cœurs");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            default -> player.sendMessage(ChatColor.RED + "Type de lit inconnu: " + type);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!manager.hasActiveGame()) return;
        MDTGame game = manager.getCurrentGame();
        if (game.getState() != MDTGameState.PLAYING) return;
        
        Player sourcePlayer = null;
        if (event.getEntity() instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) event.getEntity();
            if (tnt.getSource() instanceof Player) {
                sourcePlayer = (Player) tnt.getSource();
            }
        }

        // Note: BlockTracker n'existe plus avec le système FAWE

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (block.getType().name().endsWith("_BED")) {
                MDTBed mdtBed = game.getBedAtLocation(block.getLocation());
                if (mdtBed != null && !mdtBed.isDestroyed()) {
                    // Tracker les deux parties du lit (tête et pied) AVANT destruction
                    trackBedBlock(block);

                    it.remove(); // On retire le lit de l'explosion vanilla pour gérer nous-même

                    // Détruire physiquement le lit (les deux parties)
                    destroyBedBlocks(block);

                    handleBedDestruction(mdtBed, sourcePlayer, game);
                }
            }
        }
    }

    /**
     * Tracke un bloc de lit (et trouve l'autre partie pour la tracker aussi)
     * Note: Avec FAWE, on ne track plus les blocs individuellement
     */
    private void trackBedBlock(Block block) {
        if (!(block.getBlockData() instanceof Bed)) return;

        Bed bedData = (Bed) block.getBlockData();

        // Note: Le tracking des blocs n'est plus nécessaire avec FAWE

        // Trouver et tracker l'autre partie du lit
        Block otherPart;
        if (bedData.getPart() == Bed.Part.HEAD) {
            otherPart = block.getRelative(bedData.getFacing().getOppositeFace());
        } else {
            otherPart = block.getRelative(bedData.getFacing());
        }

        if (otherPart.getType().name().endsWith("_BED")) {
            // Note: Le tracking n'est plus nécessaire avec FAWE
            // La restauration est gérée par schématique complète
        }
    }

    /**
     * Détruit physiquement les deux parties d'un lit - ANTI-DROPS RENFORCÉ
     */
    private void destroyBedBlocks(Block block) {
        if (!(block.getBlockData() instanceof Bed)) return;

        Bed bedData = (Bed) block.getBlockData();

        // Trouver l'autre partie du lit
        Block otherPart;
        if (bedData.getPart() == Bed.Part.HEAD) {
            otherPart = block.getRelative(bedData.getFacing().getOppositeFace());
        } else {
            otherPart = block.getRelative(bedData.getFacing());
        }

        // Détruire les deux parties avec ANTI-DROPS MAXIMAL
        // setType(Material.AIR, false) est la méthode correcte pour supprimer un bloc sans physique
        block.setType(Material.AIR, false);
        if (otherPart.getType().name().endsWith("_BED")) {
            otherPart.setType(Material.AIR, false);
        }

        // Double sécurité - s'assurer qu'il n'y a pas d'items au sol
        for (org.bukkit.entity.Entity entity : block.getWorld().getNearbyEntities(block.getLocation(), 2, 2, 2)) {
            if (entity instanceof org.bukkit.entity.Item) {
                org.bukkit.entity.Item item = (org.bukkit.entity.Item) entity;
                if (item.getItemStack().getType().name().endsWith("_BED")) {
                    item.remove(); // Supprimer les items de lit au sol
                }
            }
        }
    }

    /**
     * Empeche la destruction manuelle des lits dans le monde MDT
     * Seules les explosions (TNT/Dynamite) peuvent detruire les lits
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getBlock().getType().name().endsWith("_BED")) return;

        Player player = event.getPlayer();
        String mdtWorld = manager.getConfig().getWorldName();

        // Dans le monde MDT, TOUS les lits sont proteges sauf pour les admins en mode setup
        if (player.getWorld().getName().equalsIgnoreCase(mdtWorld)) {
            // Permettre aux admins avec permission de casser les lits (pour configuration)
            if (player.hasPermission("roleplaycity.mdt.setup") && !manager.hasActiveGame()) {
                return; // Autoriser
            }

            // Bloquer pour tout le monde pendant une partie
            event.setCancelled(true);

            // Message different selon le contexte
            if (manager.isPlayerInGame(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Les lits ne peuvent être détruits que par des TNT ou Dynamite!");
            }
            return;
        }

        // Hors du monde MDT, verifier si le joueur est dans une partie
        if (manager.isPlayerInGame(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Tu ne peux pas casser de lits pendant le MDT!");
        }
    }
    
    private void handleBedDestruction(MDTBed mdtBed, Player player, MDTGame game) {
        mdtBed.setDestroyed(true);
        
        // Déterminer l'équipe responsable (si TNT inconnue, on ignore ou on log)
        MDTTeam attackerTeam = null;
        String attackerName = "une explosion";
        
        if (player != null) {
            MDTPlayer p = game.getPlayer(player.getUniqueId());
            if (p != null) {
                attackerTeam = p.getTeam();
                attackerName = player.getName();
                p.addBedDestroyed();
            }
        }

        if (mdtBed.isTeamBed()) {
            MDTTeam victimTeam = mdtBed.getOwnerTeam();

            // Ajouter +10 points a l'equipe attaquante pour la destruction du lit
            if (attackerTeam != null && attackerTeam != victimTeam) {
                game.addTeamPoints(attackerTeam, 10);  // +10 points pour destruction de lit
            }

            // Annonces
            String message = manager.getConfig().getFormattedMessage("bed-break",
                    "%team%", victimTeam.getColoredName(), "%player%", attackerName);
            manager.broadcastToGame(message);

            for (MDTPlayer p : game.getAllPlayers()) {
                Player pl = p.getPlayer();
                if (pl != null) {
                    pl.playSound(pl.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                    if (p.getTeam() == victimTeam) {
                        pl.sendTitle(ChatColor.RED + "LIT DÉTRUIT!", ChatColor.GRAY + "Tu ne peux plus respawn!", 10, 70, 20);
                    } else if (p.getTeam() == attackerTeam) {
                        pl.sendMessage(ChatColor.GREEN + "+10 points pour la destruction du lit ennemi!");
                    }
                }
            }

        } else if (mdtBed.isNeutralBed()) {
            if (mdtBed.getClaimedBy() != null) return; // Déjà pris

            if (attackerTeam != null) {
                mdtBed.setClaimedBy(attackerTeam);
                int bonus = mdtBed.getBonusHearts();
                int maxBonus = manager.getConfig().getMaxBonusHearts();

                for (MDTPlayer teammate : game.getTeamPlayers(attackerTeam)) {
                    teammate.addBonusHearts(bonus, maxBonus);
                    teammate.applyBonusHearts();
                    if (teammate.getPlayer() != null) {
                        teammate.getPlayer().sendMessage(ChatColor.GREEN + "Lit neutre explosé ! +" + bonus + " cœurs permanents !");
                        teammate.getPlayer().playSound(teammate.getPlayer().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    }
                }
                manager.broadcastToGame(ChatColor.YELLOW + attackerName + " a explosé un lit neutre pour l'équipe " + attackerTeam.getColoredName() + " !");
            }
        }
    }

    /**
     * Empêche les items de lit de tomber au sol - PROTECTION UNIVERSELLE
     * Bloque les drops de lits dans TOUS les cas (MDT et joueurs en partie)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDropItem(BlockDropItemEvent event) {
        // Vérifier si c'était un lit (le BlockState contient le type original)
        if (!event.getBlockState().getType().name().endsWith("_BED")) {
            return;
        }

        String mdtWorld = manager.getConfig().getWorldName();
        boolean isInMDTWorld = event.getBlock().getWorld().getName().equalsIgnoreCase(mdtWorld);

        // Récupérer le joueur qui a cassé le bloc
        Player player = null;
        // Le BlockDropItemEvent n'a pas directement de joueur, on cherche le joueur le plus proche
        for (org.bukkit.entity.Entity nearby : event.getBlock().getWorld().getNearbyEntities(
                event.getBlock().getLocation(), 5, 5, 5)) {
            if (nearby instanceof Player) {
                player = (Player) nearby;
                break;
            }
        }

        // Bloquer les drops si:
        // 1. On est dans le monde MDT
        // 2. Le joueur est dans une partie MDT
        // 3. Pas de joueur identifié et on est dans le monde MDT (sécurité)
        if (isInMDTWorld ||
            (player != null && manager.isPlayerInGame(player.getUniqueId())) ||
            (!isInMDTWorld && player == null)) {

            // Annuler TOUS les drops de lit
            event.setCancelled(true);

            // Debug pour suivre les drops bloqués
            if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
                plugin.getLogger().fine("[MDT] Drop de lit bloqué - Monde: " +
                    (isInMDTWorld ? "MDT" : "Autre") +
                    (player != null ? " Joueur: " + player.getName() : " Source inconnue"));
            }
        }
    }
}