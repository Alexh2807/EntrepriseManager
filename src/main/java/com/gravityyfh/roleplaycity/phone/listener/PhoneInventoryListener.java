package com.gravityyfh.roleplaycity.phone.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.model.ActiveCall;
import com.gravityyfh.roleplaycity.phone.service.MusicService;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Listener pour les evenements d'inventaire lies aux telephones.
 * Gere la deconnexion automatique des appels et de la musique.
 */
public class PhoneInventoryListener implements Listener {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;
    private final MusicService musicService;

    public PhoneInventoryListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
        this.musicService = plugin.getMusicService();
    }

    /**
     * Gere la connexion d'un joueur.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Initialiser le compte telephone (si premiere connexion)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            phoneService.getOrCreateAccount(player);
        }, 20L); // Attendre 1 seconde pour s'assurer que le joueur est bien charge
    }

    /**
     * Gere la deconnexion d'un joueur.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Terminer tous les appels en cours
        phoneService.handlePlayerDisconnect(uuid);

        // Arreter la musique
        if (musicService != null) {
            musicService.handlePlayerDisconnect(uuid);
        }
    }

    /**
     * Gere la mort d'un joueur.
     * Deconnecte les appels et la musique.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        // Terminer l'appel en cours
        if (phoneService.isInCall(uuid)) {
            phoneService.hangUp(player);
            player.sendMessage(PhoneMessages.CALL_DISCONNECTED_DEATH);
        }

        // Arreter la musique
        if (musicService != null && musicService.isPlaying(player)) {
            musicService.stop(player);
        }
    }

    /**
     * Gere le changement de slot (hotbar).
     * Verifie si le joueur retire le telephone de sa main pendant un appel.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        // Verifier si le joueur est en appel
        if (!phoneService.isInCall(player.getUniqueId())) {
            return;
        }

        ActiveCall call = phoneService.getActiveCall(player.getUniqueId());
        if (call == null || call.getState() != ActiveCall.CallState.CONNECTED) {
            return;
        }

        // Verifier si le nouveau slot contient un telephone
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (newItem == null || !phoneManager.isPhone(newItem)) {
            // Le joueur n'a plus de telephone en main -> deconnecter l'appel
            // Ajouter un delai pour permettre au joueur de changer rapidement
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Verifier a nouveau apres le delai
                    if (!phoneManager.hasPhoneInHand(player) && phoneService.isInCall(player.getUniqueId())) {
                        phoneService.hangUp(player);
                        player.sendMessage(PhoneMessages.CALL_DISCONNECTED_NO_PHONE);
                    }
                }
            }.runTaskLater(plugin, 20L); // 1 seconde de tolerance
        }
    }

    /**
     * Gere l'echange d'items entre les mains.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // Verifier si le joueur est en appel
        if (!phoneService.isInCall(player.getUniqueId())) {
            return;
        }

        ActiveCall call = phoneService.getActiveCall(player.getUniqueId());
        if (call == null || call.getState() != ActiveCall.CallState.CONNECTED) {
            return;
        }

        // Verifier si l'item qui arrive en main principale est un telephone
        ItemStack mainHandItem = event.getMainHandItem();

        if (mainHandItem == null || !phoneManager.isPhone(mainHandItem)) {
            // Pas de telephone en main principale apres le swap
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!phoneManager.hasPhoneInHand(player) && phoneService.isInCall(player.getUniqueId())) {
                        phoneService.hangUp(player);
                        player.sendMessage(PhoneMessages.CALL_DISCONNECTED_NO_PHONE);
                    }
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    /**
     * Verification periodique que les joueurs en appel ont toujours un telephone.
     * Cette tache est lancee dans le enable du plugin.
     */
    public void startPhoneCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    // Verifier les appels en cours
                    if (phoneService.isInCall(uuid)) {
                        ActiveCall call = phoneService.getActiveCall(uuid);
                        if (call != null && call.getState() == ActiveCall.CallState.CONNECTED) {
                            // Verifier que le telephone est toujours en main
                            if (!phoneManager.hasPhoneInHand(player)) {
                                phoneService.hangUp(player);
                                player.sendMessage(PhoneMessages.CALL_DISCONNECTED_NO_PHONE);
                            }
                        }
                    }

                    // Verifier la musique
                    if (musicService != null && musicService.isPlaying(player)) {
                        // Verifier que le telephone est dans l'inventaire
                        if (phoneManager.findPhoneInInventory(player) == null) {
                            musicService.stop(player);
                            player.sendMessage(PhoneMessages.MUSIC_STOPPED_NO_PHONE);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Toutes les 5 secondes
    }

    /**
     * Gere l'utilisation d'un forfait sur un telephone.
     * Clic droit avec un forfait en main -> recharge le telephone dans l'inventaire.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlanUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack planItem = event.getItem();

        // Verifier si c'est un forfait
        if (planItem == null || !phoneManager.isPlan(planItem)) {
            return;
        }

        // Chercher un telephone dans l'inventaire
        ItemStack phoneItem = phoneManager.findPhoneInInventory(player);
        if (phoneItem == null) {
            player.sendMessage(PhoneMessages.NO_PHONE_FOR_PLAN);
            return;
        }

        // Annuler l'event
        event.setCancelled(true);

        // Recharger le telephone
        if (phoneService.rechargePlan(player, phoneItem, planItem)) {
            // Le message de succes est envoye par rechargePlan()
        } else {
            player.sendMessage(PhoneMessages.RECHARGE_FAILED);
        }
    }
}
