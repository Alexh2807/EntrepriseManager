package com.gravityyfh.roleplaycity.police.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData;
import com.gravityyfh.roleplaycity.service.InteractionRequestManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI de fouille corporelle
 * Permet aux policiers de voir et confisquer les items d'un joueur menotté
 */
public class FriskGUI implements Listener {

    private final RoleplayCity plugin;
    private final HandcuffedPlayerData handcuffedData;
    
    // Map: UUID Policier -> UUID Suspect fouillé
    private final Map<UUID, UUID> activeFrisks = new HashMap<>();
    
    private static final String TITLE_PREFIX = "Fouille : ";

    public FriskGUI(RoleplayCity plugin, HandcuffedPlayerData handcuffedData) {
        this.plugin = plugin;
        this.handcuffedData = handcuffedData;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le menu de sélection du joueur à fouiller
     * Affiche TOUS les joueurs à proximité (menottés ou non)
     * - Menottés: fouille directe
     * - Non-menottés: demande de consentement
     */
    public void openTargetSelection(Player policier) {
        // Vérifier si le policier est en service
        ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
        if (serviceManager != null && !serviceManager.isInService(policier.getUniqueId(), ProfessionalServiceType.POLICE)) {
            serviceManager.sendNotInServiceMessage(policier, ProfessionalServiceType.POLICE);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Fouille : Sélection");

        int slot = 0;
        boolean found = false;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(policier)) continue;

            // Vérifier la distance (5 blocs)
            if (!target.getWorld().equals(policier.getWorld()) ||
                target.getLocation().distance(policier.getLocation()) > 5) {
                continue;
            }

            found = true;
            boolean isHandcuffed = handcuffedData.isPlayerHandcuffed(target);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Distance: " + String.format("%.1fm", target.getLocation().distance(policier.getLocation())));
            lore.add("");

            if (isHandcuffed) {
                meta.setDisplayName(ChatColor.RED + "⛓ " + target.getName() + ChatColor.DARK_RED + " [MENOTTÉ]");
                lore.add(ChatColor.RED + "✗ Menotté - Fouille sans consentement");
                lore.add("");
                lore.add(ChatColor.GREEN + "► Cliquez pour fouiller directement");
            } else {
                meta.setDisplayName(ChatColor.YELLOW + target.getName());
                lore.add(ChatColor.GREEN + "✓ Libre - Consentement requis");
                lore.add("");
                lore.add(ChatColor.YELLOW + "► Cliquez pour demander la fouille");
            }

            meta.setLore(lore);
            head.setItemMeta(meta);
            inv.setItem(slot++, head);

            if (slot >= 27) break;
        }

        if (!found) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Aucun joueur à proximité");
            meta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Rapprochez-vous d'un joueur",
                ChatColor.GRAY + "pour pouvoir le fouiller."
            ));
            none.setItemMeta(meta);
            inv.setItem(13, none);
        }

        policier.openInventory(inv);
    }

    /**
     * Ouvre l'inventaire de fouille
     */
    public void openFriskInventory(Player policier, Player suspect) {
        // Créer un inventaire de 54 slots (9x6)
        // Lignes 1-4 : Inventaire principal
        // Ligne 5 : Armure + Main secondaire
        // Ligne 6 : Contrôles
        
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + suspect.getName());
        
        // Copier l'inventaire du suspect
        ItemStack[] contents = suspect.getInventory().getContents();
        
        // Inventaire principal (0-35)
        for (int i = 0; i < 36; i++) {
            if (contents[i] != null) {
                inv.setItem(i, contents[i].clone());
            }
        }
        
        // Armure (36-39) -> Slots GUI 45-48
        ItemStack[] armor = suspect.getInventory().getArmorContents();
        inv.setItem(45, armor[0]); // Bottes
        inv.setItem(46, armor[1]); // Jambières
        inv.setItem(47, armor[2]); // Plastron
        inv.setItem(48, armor[3]); // Casque
        
        // Main secondaire
        inv.setItem(49, suspect.getInventory().getItemInOffHand());
        
        // Séparateur
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        for (int i = 36; i < 45; i++) inv.setItem(i, glass);
        
        // Info
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Mode Confiscation");
        infoMeta.setLore(java.util.Arrays.asList(
            ChatColor.GRAY + "Cliquez sur un item pour le",
            ChatColor.GRAY + "confisquer (ajouter à votre inventaire)."
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(53, info);
        
        activeFrisks.put(policier.getUniqueId(), suspect.getUniqueId());
        policier.openInventory(inv);
        
        suspect.sendMessage(ChatColor.RED + "👮 L'officier " + policier.getName() + " vous fouille...");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player policier)) return;
        String title = event.getView().getTitle();
        
        // Gestion de la sélection
        if (title.equals(ChatColor.DARK_BLUE + "Fouille : Sélection")) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() != Material.PLAYER_HEAD) return;

            String displayName = current.getItemMeta().getDisplayName();
            // Extraire le nom du joueur (peut contenir des préfixes comme "⛓ ")
            String name = ChatColor.stripColor(displayName);
            // Nettoyer les suffixes comme " [MENOTTÉ]"
            if (name.contains(" ")) {
                name = name.split(" ")[0];
                // Si le nom commence par un caractère spécial, prendre le deuxième élément
                if (name.length() <= 2) {
                    String[] parts = ChatColor.stripColor(displayName).split(" ");
                    name = parts.length > 1 ? parts[1] : parts[0];
                }
            }

            Player suspect = Bukkit.getPlayer(name);

            if (suspect == null || !suspect.isOnline()) {
                policier.sendMessage(ChatColor.RED + "Joueur introuvable.");
                policier.closeInventory();
                return;
            }

            // Vérifier si menotté
            boolean isHandcuffed = handcuffedData.isPlayerHandcuffed(suspect);

            if (isHandcuffed) {
                // Fouille directe sans consentement
                openFriskInventory(policier, suspect);
            } else {
                // Demander le consentement
                InteractionRequestManager requestManager = plugin.getInteractionRequestManager();
                if (requestManager != null) {
                    policier.closeInventory();

                    // Vérifier s'il n'y a pas déjà une demande en cours
                    if (requestManager.hasPendingRequest(policier.getUniqueId(), suspect.getUniqueId(),
                            com.gravityyfh.roleplaycity.service.InteractionRequest.RequestType.FRISK)) {
                        policier.sendMessage(ChatColor.YELLOW + "Une demande de fouille est déjà en attente pour ce joueur.");
                        return;
                    }

                    var request = requestManager.createFriskRequest(policier, suspect);
                    if (request != null) {
                        policier.sendMessage(ChatColor.GREEN + "Demande de fouille envoyée à " + suspect.getName() + ".");
                        policier.sendMessage(ChatColor.GRAY + "En attente de sa réponse (30 secondes)...");
                    }
                } else {
                    policier.sendMessage(ChatColor.RED + "Système de consentement non disponible.");
                }
            }
            return;
        }
        
        // Gestion de la fouille
        if (title.startsWith(TITLE_PREFIX)) {
            event.setCancelled(true); // On gère manuellement le transfert
            
            // Si clic dans l'inventaire du policier (bas), on ignore ou on bloque
            if (event.getClickedInventory() == event.getView().getBottomInventory()) {
                return;
            }
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                return;
            }
            
            // Si c'est l'item d'info
            if (clickedItem.getType() == Material.PAPER) return;
            
            UUID suspectUuid = activeFrisks.get(policier.getUniqueId());
            if (suspectUuid == null) {
                policier.closeInventory();
                return;
            }
            
            Player suspect = Bukkit.getPlayer(suspectUuid);
            if (suspect == null) {
                policier.closeInventory();
                policier.sendMessage(ChatColor.RED + "Le suspect s'est déconnecté.");
                return;
            }
            
            // Confiscation !
            // 1. Ajouter au policier
            HashMap<Integer, ItemStack> leftOver = policier.getInventory().addItem(clickedItem);
            
            if (!leftOver.isEmpty()) {
                policier.sendMessage(ChatColor.RED + "Votre inventaire est plein !");
                return; // Annuler
            }
            
            // 2. Retirer du suspect (le vrai)
            // Il faut trouver l'item correspondant dans l'inventaire du suspect
            // L'index du clic correspond à notre mapping
            int slot = event.getSlot();
            
            if (slot < 36) {
                suspect.getInventory().setItem(slot, null);
            } else if (slot >= 45 && slot <= 48) {
                // Armure (mapping inverse 0=45, 1=46...)
                ItemStack[] armor = suspect.getInventory().getArmorContents();
                armor[slot - 45] = null;
                suspect.getInventory().setArmorContents(armor);
            } else if (slot == 49) {
                suspect.getInventory().setItemInOffHand(null);
            }
            
            // 3. Retirer du GUI visuel
            event.getInventory().setItem(slot, null);
            
            // 4. Notifications
            String itemName = clickedItem.getType().name();
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                itemName = clickedItem.getItemMeta().getDisplayName();
            }
            
            policier.sendMessage(ChatColor.GREEN + "Confisqué : " + clickedItem.getAmount() + "x " + itemName);
            suspect.sendMessage(ChatColor.RED + "👮 L'officier " + policier.getName() + " a confisqué : " + 
                ChatColor.YELLOW + clickedItem.getAmount() + "x " + itemName);
                
            // Log console
            plugin.getLogger().info("[Police] " + policier.getName() + " a confisqué " + itemName + " à " + suspect.getName());
        }
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        activeFrisks.remove(event.getPlayer().getUniqueId());
    }
}
