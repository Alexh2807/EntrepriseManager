package com.gravityyfh.roleplaycity.police.visual;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Gère l'affichage d'un faux joueur (NPC) pour les joueurs menottés
 * Le vrai joueur devient invisible et un NPC avec pose "mains derrière le dos" le remplace
 *
 * Utilise ProtocolLib pour créer des packets sans dépendances externes
 */
public class HandcuffedPlayerModel {

    private final RoleplayCity plugin;
    private final ProtocolManager protocolManager;

    // Map: UUID du joueur menotté -> Entity ID du NPC
    private final Map<UUID, Integer> npcEntityIds = new HashMap<>();

    // Map: UUID du joueur menotté -> UUID du NPC (pour le GameProfile)
    private final Map<UUID, UUID> npcUUIDs = new HashMap<>();

    // Task de synchronisation de position
    private final Map<UUID, BukkitTask> syncTasks = new HashMap<>();

    // Compteur d'entity ID (commence haut pour éviter les conflits)
    private int entityIdCounter = 100000;

    public HandcuffedPlayerModel(RoleplayCity plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    /**
     * Crée le modèle NPC pour un joueur menotté
     * @param player Le joueur à remplacer par un NPC
     */
    public void createModel(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Si déjà un modèle, le supprimer d'abord
        if (npcEntityIds.containsKey(playerUUID)) {
            removeModel(player);
        }

        // Générer un Entity ID unique pour le NPC
        int npcEntityId = entityIdCounter++;
        UUID npcUUID = UUID.randomUUID();

        npcEntityIds.put(playerUUID, npcEntityId);
        npcUUIDs.put(playerUUID, npcUUID);

        // Rendre le vrai joueur invisible
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.INVISIBILITY,
            Integer.MAX_VALUE,
            0,
            false,
            false,
            false
        ));

        // Envoyer les packets à tous les autres joueurs
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (!observer.equals(player)) {
                spawnNPCForObserver(player, observer, npcEntityId, npcUUID);
            }
        }

        // Démarrer la synchronisation de position
        startPositionSync(player, npcEntityId);

        plugin.getLogger().info("Modèle menotté créé pour " + player.getName() + " (NPC ID: " + npcEntityId + ")");
    }

    /**
     * Spawn le NPC pour un observateur spécifique
     */
    private void spawnNPCForObserver(Player handcuffedPlayer, Player observer, int npcEntityId, UUID npcUUID) {
        try {
            Location loc = handcuffedPlayer.getLocation();

            // 1. Packet PlayerInfo ADD - Ajouter le joueur à la tablist (nécessaire pour spawn)
            PacketContainer playerInfoAdd = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);

            WrappedGameProfile gameProfile = WrappedGameProfile.fromPlayer(handcuffedPlayer);
            // Créer un nouveau profil avec UUID différent mais même skin
            WrappedGameProfile npcProfile = new WrappedGameProfile(npcUUID, handcuffedPlayer.getName() + "_cuff");
            // Copier les propriétés (skin)
            npcProfile.getProperties().putAll(gameProfile.getProperties());

            EnumWrappers.PlayerInfoAction action = EnumWrappers.PlayerInfoAction.ADD_PLAYER;
            PlayerInfoData playerInfoData = new PlayerInfoData(
                npcProfile,
                0,
                EnumWrappers.NativeGameMode.SURVIVAL,
                WrappedChatComponent.fromText(handcuffedPlayer.getName())
            );

            playerInfoAdd.getPlayerInfoActions().write(0, EnumSet.of(action));
            playerInfoAdd.getPlayerInfoDataLists().write(1, Collections.singletonList(playerInfoData));

            protocolManager.sendServerPacket(observer, playerInfoAdd);

            // 2. Packet Named Entity Spawn - Spawner le NPC
            PacketContainer spawnPlayer = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
            spawnPlayer.getIntegers().write(0, npcEntityId);
            spawnPlayer.getUUIDs().write(0, npcUUID);
            spawnPlayer.getDoubles()
                .write(0, loc.getX())
                .write(1, loc.getY())
                .write(2, loc.getZ());
            spawnPlayer.getBytes()
                .write(0, (byte) (loc.getYaw() * 256.0F / 360.0F))
                .write(1, (byte) (loc.getPitch() * 256.0F / 360.0F));

            protocolManager.sendServerPacket(observer, spawnPlayer);

            // 3. Appliquer la pose "sneaking" (accroupi) - la plus proche de "mains liées"
            applyHandcuffedPose(observer, npcEntityId);

            // 4. Copier l'équipement du joueur
            sendEquipment(handcuffedPlayer, observer, npcEntityId);

            // 5. Retirer le NPC de la tablist après un délai (pour éviter qu'il apparaisse dans TAB)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removeFromTablist(observer, npcUUID, npcProfile);
            }, 5L);

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du spawn NPC pour " + observer.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Applique la pose "menottée" au NPC (sneaking + metadata)
     */
    private void applyHandcuffedPose(Player observer, int npcEntityId) {
        try {
            PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            metadataPacket.getIntegers().write(0, npcEntityId);

            // Créer la liste de DataWatcher values
            List<WrappedDataValue> dataValues = new ArrayList<>();

            // Index 0: Entity flags (byte) - Bit 1 = Sneaking (0x02)
            // On met sneaking pour avoir une pose "soumise"
            byte entityFlags = 0x02; // Sneaking
            dataValues.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), entityFlags));

            // Index 6: Pose - 5 = CROUCHING (1.14+)
            // Utiliser l'enum Pose si disponible
            try {
                // Pour 1.20+, on utilise directement le registre de Pose
                WrappedDataWatcher.Serializer poseSerializer = WrappedDataWatcher.Registry.get(
                    EnumWrappers.getEntityPoseClass()
                );
                dataValues.add(new WrappedDataValue(6, poseSerializer, EnumWrappers.EntityPose.CROUCHING));
            } catch (Exception e) {
                // Fallback: juste le flag sneaking suffit
            }

            metadataPacket.getDataValueCollectionModifier().write(0, dataValues);

            protocolManager.sendServerPacket(observer, metadataPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'application de la pose: " + e.getMessage());
        }
    }

    /**
     * Envoie l'équipement du joueur au NPC
     */
    private void sendEquipment(Player handcuffedPlayer, Player observer, int npcEntityId) {
        try {
            PacketContainer equipmentPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipmentPacket.getIntegers().write(0, npcEntityId);

            List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, org.bukkit.inventory.ItemStack>> equipmentList = new ArrayList<>();

            // Ajouter tout l'équipement
            equipmentList.add(new com.comphenix.protocol.wrappers.Pair<>(
                EnumWrappers.ItemSlot.HEAD,
                handcuffedPlayer.getInventory().getHelmet()
            ));
            equipmentList.add(new com.comphenix.protocol.wrappers.Pair<>(
                EnumWrappers.ItemSlot.CHEST,
                handcuffedPlayer.getInventory().getChestplate()
            ));
            equipmentList.add(new com.comphenix.protocol.wrappers.Pair<>(
                EnumWrappers.ItemSlot.LEGS,
                handcuffedPlayer.getInventory().getLeggings()
            ));
            equipmentList.add(new com.comphenix.protocol.wrappers.Pair<>(
                EnumWrappers.ItemSlot.FEET,
                handcuffedPlayer.getInventory().getBoots()
            ));
            // Mains vides (menotté = pas d'items en main)
            equipmentList.add(new com.comphenix.protocol.wrappers.Pair<>(
                EnumWrappers.ItemSlot.MAINHAND,
                null
            ));
            equipmentList.add(new com.comphenix.protocol.wrappers.Pair<>(
                EnumWrappers.ItemSlot.OFFHAND,
                null
            ));

            equipmentPacket.getSlotStackPairLists().write(0, equipmentList);

            protocolManager.sendServerPacket(observer, equipmentPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'envoi de l'équipement: " + e.getMessage());
        }
    }

    /**
     * Retire le NPC de la tablist pour l'observateur
     */
    private void removeFromTablist(Player observer, UUID npcUUID, WrappedGameProfile npcProfile) {
        try {
            PacketContainer playerInfoRemove = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            playerInfoRemove.getUUIDLists().write(0, Collections.singletonList(npcUUID));
            protocolManager.sendServerPacket(observer, playerInfoRemove);
        } catch (Exception e) {
            // Ignore - pas critique
        }
    }

    /**
     * Démarre la synchronisation de position du NPC avec le joueur
     */
    private void startPositionSync(Player player, int npcEntityId) {
        UUID playerUUID = player.getUniqueId();

        // Annuler l'ancienne task si elle existe
        BukkitTask oldTask = syncTasks.remove(playerUUID);
        if (oldTask != null) {
            oldTask.cancel();
        }

        // Nouvelle task de synchronisation (toutes les 1 tick = 50ms)
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(playerUUID);
            if (p == null || !p.isOnline()) {
                removeModel(playerUUID);
                return;
            }

            // Envoyer le packet de téléportation à tous les observateurs
            updateNPCPosition(p, npcEntityId);

        }, 1L, 1L);

        syncTasks.put(playerUUID, task);
    }

    /**
     * Met à jour la position du NPC
     */
    private void updateNPCPosition(Player handcuffedPlayer, int npcEntityId) {
        Location loc = handcuffedPlayer.getLocation();

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(handcuffedPlayer)) continue;
            if (!observer.getWorld().equals(handcuffedPlayer.getWorld())) continue;

            try {
                // Packet Entity Teleport
                PacketContainer teleportPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
                teleportPacket.getIntegers().write(0, npcEntityId);
                teleportPacket.getDoubles()
                    .write(0, loc.getX())
                    .write(1, loc.getY())
                    .write(2, loc.getZ());
                teleportPacket.getBytes()
                    .write(0, (byte) (loc.getYaw() * 256.0F / 360.0F))
                    .write(1, (byte) (loc.getPitch() * 256.0F / 360.0F));
                teleportPacket.getBooleans().write(0, false); // onGround

                protocolManager.sendServerPacket(observer, teleportPacket);

                // Packet Head Rotation
                PacketContainer headRotation = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                headRotation.getIntegers().write(0, npcEntityId);
                headRotation.getBytes().write(0, (byte) (loc.getYaw() * 256.0F / 360.0F));

                protocolManager.sendServerPacket(observer, headRotation);

            } catch (Exception e) {
                // Ignore les erreurs de packet
            }
        }
    }

    /**
     * Supprime le modèle NPC d'un joueur
     */
    public void removeModel(Player player) {
        removeModel(player.getUniqueId());

        // Retirer l'invisibilité
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    /**
     * Supprime le modèle NPC par UUID
     */
    public void removeModel(UUID playerUUID) {
        Integer npcEntityId = npcEntityIds.remove(playerUUID);
        npcUUIDs.remove(playerUUID);

        // Annuler la task de synchronisation
        BukkitTask task = syncTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        if (npcEntityId == null) return;

        // Envoyer le packet de destruction à tous les joueurs
        for (Player observer : Bukkit.getOnlinePlayers()) {
            try {
                PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroyPacket.getIntLists().write(0, Collections.singletonList(npcEntityId));
                protocolManager.sendServerPacket(observer, destroyPacket);
            } catch (Exception e) {
                // Ignore
            }
        }

        // Retirer l'invisibilité si le joueur est en ligne
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        plugin.getLogger().info("Modèle menotté supprimé pour UUID: " + playerUUID);
    }

    /**
     * Vérifie si un joueur a un modèle actif
     */
    public boolean hasModel(UUID playerUUID) {
        return npcEntityIds.containsKey(playerUUID);
    }

    /**
     * Appelé quand un nouveau joueur rejoint le serveur
     * Doit spawner les NPCs existants pour ce nouvel observateur
     */
    public void onPlayerJoin(Player newPlayer) {
        for (Map.Entry<UUID, Integer> entry : npcEntityIds.entrySet()) {
            UUID handcuffedUUID = entry.getKey();
            int npcEntityId = entry.getValue();
            UUID npcUUID = npcUUIDs.get(handcuffedUUID);

            Player handcuffedPlayer = Bukkit.getPlayer(handcuffedUUID);
            if (handcuffedPlayer != null && handcuffedPlayer.isOnline() && !handcuffedPlayer.equals(newPlayer)) {
                spawnNPCForObserver(handcuffedPlayer, newPlayer, npcEntityId, npcUUID);
            }
        }
    }

    /**
     * Nettoie toutes les ressources
     */
    public void cleanup() {
        // Copier les clés pour éviter ConcurrentModificationException
        Set<UUID> players = new HashSet<>(npcEntityIds.keySet());
        for (UUID uuid : players) {
            removeModel(uuid);
        }

        npcEntityIds.clear();
        npcUUIDs.clear();
        syncTasks.clear();
    }
}
