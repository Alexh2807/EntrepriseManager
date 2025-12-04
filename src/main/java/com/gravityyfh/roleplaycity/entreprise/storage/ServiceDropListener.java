package com.gravityyfh.roleplaycity.entreprise.storage;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
import com.gravityyfh.roleplaycity.service.ServiceModeManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener qui intercepte les drops en mode service pour les envoyer au coffre d'entreprise.
 */
public class ServiceDropListener implements Listener {

    private final RoleplayCity plugin;
    private final CompanyStorageManager storageManager;
    private final ServiceModeManager serviceManager;
    private final EntrepriseManagerLogic entrepriseLogic;

    public ServiceDropListener(RoleplayCity plugin, CompanyStorageManager storageManager, ServiceModeManager serviceManager, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.serviceManager = serviceManager;
        this.entrepriseLogic = entrepriseLogic;
    }

    /**
     * Vérifie si une action est une activité légitime de l'entreprise (listée dans action_restrictions)
     */
    private boolean isActionAllowedForStorage(String companyNameOrSiret, String actionType, String materialName) {
        // Essayer d'abord par nom, puis par SIRET (compatibilité avec anciennes sessions)
        Entreprise entreprise = entrepriseLogic.getEntreprise(companyNameOrSiret);
        if (entreprise == null) {
            // Peut-être que c'est un SIRET au lieu d'un nom
            entreprise = entrepriseLogic.getEntrepriseBySiret(companyNameOrSiret);
        }
        if (entreprise == null) return false;

        String type = entreprise.getType();
        String path = "types-entreprise." + type + ".action_restrictions." + actionType + "." + materialName;

        // Si la clé existe dans la config, c'est une activité de l'entreprise
        return plugin.getConfig().contains(path);
    }

    /**
     * Intercepte la casse de blocs (Minage, Agriculture, Bûcheronnage)
     * Remplace BlockDropItemEvent pour une meilleure fiabilité sur les cultures
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Vérifier si le joueur est en service
        if (!serviceManager.isInService(player.getUniqueId())) {
            return;
        }

        String companyName = serviceManager.getActiveEnterprise(player.getUniqueId());
        if (companyName == null) return;

        String blockTypeName = event.getBlock().getType().name();

        // Vérifier si ce BLOC fait partie des activités de l'entreprise
        if (!isActionAllowedForStorage(companyName, "BLOCK_BREAK", blockTypeName)) {
            return; // Pas une activité de l'entreprise -> Drop normal au sol
        }

        // NOTE: Les restrictions/quotas sont gérés par EventListener.java
        // On ne doit PAS les vérifier ici pour éviter le double comptage !

        // Pour les cultures, vérifier si mature (sinon on ignore)
        Material blockType = event.getBlock().getType();
        if (isCrop(blockType)) {
            if (event.getBlock().getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() != ageable.getMaximumAge()) {
                    return; // Culture pas mature, ignorer
                }
            }
        }

        // Si l'événement a été annulé par EventListener (quota dépassé), on ne fait rien
        if (event.isCancelled()) {
            return;
        }

        // Éviter de casser les conteneurs (coffres, etc.) et de voler le contenu
        if (event.getBlock().getState() instanceof org.bukkit.block.Container) {
            return;
        }

        // Récupérer les drops théoriques
        List<ItemStack> drops = new ArrayList<>(event.getBlock().getDrops(player.getInventory().getItemInMainHand()));
        
        if (drops.isEmpty()) return;

        // LOGIQUE D'AUTO-REPLANT POUR L'AGRICULTURE
        // Si on est en mode service, on prend en charge le replant pour ne pas casser la boucle
        // Note: blockType est déjà défini plus haut
        boolean replanted = false;

        if (isCrop(blockType)) {
            // Chercher la graine correspondante dans les drops
            Material seedType = getSeedForCrop(blockType);
            for (ItemStack drop : drops) {
                if (drop.getType() == seedType && drop.getAmount() > 0) {
                    // On utilise une graine pour replanter
                    drop.setAmount(drop.getAmount() - 1);
                    
                    // Planifier le replant (1 tick plus tard pour laisser le bloc se casser)
                    org.bukkit.block.Block block = event.getBlock();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        block.setType(blockType);
                        if (block.getBlockData() instanceof Ageable ageable) {
                            ageable.setAge(0);
                            block.setBlockData(ageable);
                        }
                    }, 2L); // 2 ticks pour être sûr
                    
                    replanted = true;
                    break; // On a trouvé et utilisé une graine, on arrête
                }
            }
        }

        boolean full = false;
        boolean atLeastOneStored = false;

        for (ItemStack item : drops) {
            if (item.getAmount() <= 0) continue; // Ignorer les items vides (ex: la graine utilisée)

            // Tenter d'ajouter au coffre virtuel
            boolean added = storageManager.addItemToStorage(companyName, item);
            
            if (added) {
                atLeastOneStored = true;
            } else {
                full = true;
                // Si le coffre est plein, on fait tomber l'item au sol manuellement
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
            }
        }
        
        // Annuler le drop vanilla car on a géré manuellement (soit stocké, soit dropé si plein)
        event.setDropItems(false);

        if (full) {
            player.sendMessage(ChatColor.RED + "⚠ Le coffre de l'entreprise est plein !");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.5f);
        } else if (atLeastOneStored) {
             // Feedback sonore discret
             player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }
    }

    private boolean isCrop(Material mat) {
        return mat == Material.WHEAT || mat == Material.CARROTS || 
               mat == Material.POTATOES || mat == Material.BEETROOTS || 
               mat == Material.NETHER_WART;
    }

    private Material getSeedForCrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    /**
     * Intercepte les drops d'entités (Chasse/Boucherie)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // Vérifier si le joueur est en service
        if (!serviceManager.isInService(killer.getUniqueId())) {
            return;
        }

        String companyName = serviceManager.getActiveEnterprise(killer.getUniqueId());
        if (companyName == null) return;

        String entityTypeName = event.getEntityType().name();

        // Vérifier si cette ENTITÉ fait partie des activités de l'entreprise
        if (!isActionAllowedForStorage(companyName, "ENTITY_KILL", entityTypeName)) {
            return; // Pas une activité de l'entreprise -> Drop normal au sol
        }

        // NOTE: Les restrictions/quotas sont gérés par EntityDamageListener.java
        // On ne doit PAS les vérifier ici pour éviter le double comptage !

        List<ItemStack> drops = event.getDrops();
        boolean full = false;

        // Itérer sur une copie
        for (ItemStack item : new ArrayList<>(drops)) {
            boolean added = storageManager.addItemToStorage(companyName, item);
            
            if (added) {
                drops.remove(item); // Retirer de la liste des drops qui tomberont au sol
            } else {
                full = true;
            }
        }

        if (full) {
            killer.sendMessage(ChatColor.RED + "⚠ Le coffre de l'entreprise est plein !");
        } else {
            killer.playSound(killer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }
    }


    /**
     * Sauvegarde l'inventaire quand le gérant le ferme
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith("Coffre: ")) {
            String companyName = title.substring(8);
            storageManager.saveInventory(companyName, event.getInventory());
        }
    }
    
    // Note: CraftItemEvent est géré différemment car le résultat va dans le curseur, pas au sol.
    // Pour l'instant, on se concentre sur les drops (récolte). 
    // Si on veut intercepter le craft, il faudrait annuler l'event et simuler le craft vers le stockage, 
    // ce qui est complexe et risque de dupliquer les items si mal fait.
    // L'employé peut crafter et jeter l'item au sol pour qu'il soit ramassé par le onEntityDrop (si c'est un item drop)
    // ou on peut ajouter un listener spécifique plus tard.
}
