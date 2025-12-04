package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.model.ActiveCall;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import de.lightplugins.economy.master.Main;
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

import java.util.Arrays;

/**
 * GUI pour les appels entrants.
 * Interface compacte de 4 blocs de large centree.
 *
 * Layout (6 lignes x 9 colonnes):
 * Colonnes 0-1: Bordure noire (gauche)
 * Colonne 2: Bordure grise
 * Colonnes 3-6: Zone utile (4 blocs de large)
 * Colonne 7: Bordure grise
 * Colonne 8: Bordure noire (droite)
 */
public class PhoneIncomingCallGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneService phoneService;
    private final ActiveCall call;
    private final String callerNumber;
    private final String callerDisplayName;

    public PhoneIncomingCallGUI(RoleplayCity plugin, ActiveCall call) {
        this.plugin = plugin;
        this.phoneService = plugin.getPhoneService();
        this.call = call;
        this.callerNumber = call.getCallerNumber();

        // Chercher le nom du contact
        String contactName = phoneService.getContactDisplayName(call.getCalleeUuid(), callerNumber);
        this.callerDisplayName = contactName != null ? contactName : callerNumber;
    }

    // Ancien constructeur pour compatibilite avec PhoneCallListener
    public PhoneIncomingCallGUI(RoleplayCity plugin, String callerNumber, String callerName) {
        this.plugin = plugin;
        this.phoneService = plugin.getPhoneService();
        this.call = null;
        this.callerNumber = callerNumber;
        this.callerDisplayName = callerName != null ? callerName : callerNumber;
    }

    public void open(Player player) {
        SmartInventory inv = getInventory();
        if (inv != null) {
            inv.open(player);
        }
    }

    public SmartInventory getInventory() {
        if (Main.bankMenuInventoryManager == null) {
            return null;
        }

        return SmartInventory.builder()
            .id("phone_incoming_call")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.GREEN + "\u260E Appel Entrant")
            .manager(Main.bankMenuInventoryManager)
            .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        // === LIGNE 0: Barre de statut "APPEL ENTRANT" ===
        ItemStack statusItem = createItem(Material.LIME_STAINED_GLASS_PANE,
            ChatColor.GREEN + "" + ChatColor.BOLD + "\u260E APPEL ENTRANT"
        );
        for (int col = 0; col <= 3; col++) {
            contents.set(0, col, ClickableItem.empty(statusItem));
        }

        // === LIGNE 1: Icone telephone qui sonne ===
        ItemStack phoneIcon = createItem(Material.LIME_CONCRETE,
            ChatColor.GREEN + "" + ChatColor.BOLD + "\u260E",
            "",
            ChatColor.YELLOW + "Sonnerie..."
        );
        contents.set(1, 1, ClickableItem.empty(phoneIcon));
        contents.set(1, 2, ClickableItem.empty(phoneIcon));

        // Remplir le reste de la ligne 1
        contents.set(1, 0, ClickableItem.empty(createGlass(Material.LIME_STAINED_GLASS_PANE)));
        contents.set(1, 3, ClickableItem.empty(createGlass(Material.LIME_STAINED_GLASS_PANE)));

        // === LIGNE 2: Info appelant ===
        ItemStack callerItem = createItem(Material.PLAYER_HEAD,
            ChatColor.AQUA + "" + ChatColor.BOLD + callerDisplayName,
            "",
            ChatColor.GRAY + "Numero: " + ChatColor.WHITE + callerNumber,
            "",
            ChatColor.YELLOW + "Vous recevez un appel!"
        );
        contents.set(2, 1, ClickableItem.empty(callerItem));
        contents.set(2, 2, ClickableItem.empty(callerItem));

        // Remplir le reste de la ligne 2
        contents.set(2, 0, ClickableItem.empty(createGlass(Material.CYAN_STAINED_GLASS_PANE)));
        contents.set(2, 3, ClickableItem.empty(createGlass(Material.CYAN_STAINED_GLASS_PANE)));

        // === LIGNE 3: Animation sonnerie (panneau clignotant) ===
        ItemStack ringPane = createGlass(Material.YELLOW_STAINED_GLASS_PANE);
        for (int col = 0; col <= 3; col++) {
            contents.set(3, col, ClickableItem.empty(ringPane));
        }

        // === LIGNE 4: Boutons DECROCHER et REFUSER (separateur) ===
        // Bouton DECROCHER (vert)
        ItemStack acceptItem = createItem(Material.LIME_CONCRETE,
            ChatColor.GREEN + "" + ChatColor.BOLD + "\u2714 DECROCHER",
            "",
            ChatColor.GRAY + "Cliquez pour repondre"
        );
        contents.set(4, 0, ClickableItem.of(acceptItem, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            player.closeInventory();
            if (phoneService.acceptCall(player)) {
                // Ouvrir le GUI en appel
                ActiveCall activeCall = phoneService.getActiveCall(player.getUniqueId());
                if (activeCall != null) {
                    new PhoneInCallGUI(plugin, activeCall).open(player);
                }
            }
        }));
        contents.set(4, 1, ClickableItem.of(acceptItem, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            player.closeInventory();
            if (phoneService.acceptCall(player)) {
                ActiveCall activeCall = phoneService.getActiveCall(player.getUniqueId());
                if (activeCall != null) {
                    new PhoneInCallGUI(plugin, activeCall).open(player);
                }
            }
        }));

        // Bouton REFUSER (rouge)
        ItemStack rejectItem = createItem(Material.RED_CONCRETE,
            ChatColor.RED + "" + ChatColor.BOLD + "\u2716 REFUSER",
            "",
            ChatColor.GRAY + "Cliquez pour rejeter"
        );
        contents.set(4, 2, ClickableItem.of(rejectItem, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            player.closeInventory();
            phoneService.rejectCall(player);
        }));
        contents.set(4, 3, ClickableItem.of(rejectItem, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            player.closeInventory();
            phoneService.rejectCall(player);
        }));

        // === LIGNE 5: Dock ===
        for (int col = 0; col <= 3; col++) {
            contents.set(5, col, ClickableItem.empty(createGlass(Material.CYAN_STAINED_GLASS_PANE)));
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // Animation de sonnerie - alterner les couleurs
        long time = System.currentTimeMillis() / 500; // Change toutes les 500ms
        Material ringMaterial = (time % 2 == 0) ? Material.YELLOW_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
        ItemStack ringPane = createGlass(ringMaterial);
        for (int col = 0; col <= 3; col++) {
            contents.set(3, col, ClickableItem.empty(ringPane));
        }

        // Verifier si l'appel est toujours actif
        ActiveCall activeCall = phoneService.getActiveCall(player.getUniqueId());
        if (activeCall == null || activeCall.getState() != ActiveCall.CallState.RINGING) {
            // L'appel n'est plus en sonnerie, fermer le GUI
            player.closeInventory();
        }
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlass(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
