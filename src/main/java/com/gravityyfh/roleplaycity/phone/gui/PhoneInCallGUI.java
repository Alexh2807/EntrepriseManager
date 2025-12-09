package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.model.ActiveCall;
import com.gravityyfh.roleplaycity.phone.service.CallService;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import de.lightplugins.economy.master.Main;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.UUID;

/**
 * GUI pour les appels en cours.
 * Interface compacte de 4 blocs de large centree.
 *
 * Layout (6 lignes x 9 colonnes):
 * Colonnes 0-1: Bordure noire (gauche)
 * Colonne 2: Bordure grise
 * Colonnes 3-6: Zone utile (4 blocs de large)
 * Colonne 7: Bordure grise
 * Colonne 8: Bordure noire (droite)
 */
public class PhoneInCallGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneService phoneService;
    private final CallService callService;
    private final ActiveCall call;

    public PhoneInCallGUI(RoleplayCity plugin, ActiveCall call) {
        this.plugin = plugin;
        this.phoneService = plugin.getPhoneService();
        this.callService = plugin.getCallService();
        this.call = call;
    }

    public void open(Player player) {
        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        SmartInventory.builder()
            .id("phone_in_call")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.GREEN + "\u260E En Appel")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        UUID uuid = player.getUniqueId();

        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        // Determiner le correspondant
        String otherNumber;
        UUID otherUuid;
        if (call.isCaller(uuid)) {
            otherNumber = call.getCalleeNumber();
            otherUuid = call.getCalleeUuid();
        } else {
            otherNumber = call.getCallerNumber();
            otherUuid = call.getCallerUuid();
        }

        String displayName = phoneService.getContactDisplayName(uuid, otherNumber);
        if (displayName == null) {
            displayName = otherNumber;
        }

        // Nom du joueur (si en ligne)
        String playerName = "";
        Player otherPlayer = Bukkit.getPlayer(otherUuid);
        if (otherPlayer != null) {
            playerName = otherPlayer.getName();
        }

        // === LIGNE 0: Barre de statut "EN APPEL" ===
        ItemStack statusItem = createItem(Material.LIME_STAINED_GLASS_PANE,
            ChatColor.GREEN + "" + ChatColor.BOLD + "\u260E EN APPEL"
        );
        for (int col = 0; col <= 3; col++) {
            contents.set(0, col, ClickableItem.empty(statusItem));
        }

        // === LIGNE 1: Icone appel connecte ===
        ItemStack phoneIcon = createItem(Material.LIME_CONCRETE,
            ChatColor.GREEN + "" + ChatColor.BOLD + "\u260E",
            "",
            ChatColor.GREEN + "Appel connecte"
        );
        contents.set(1, 1, ClickableItem.empty(phoneIcon));
        contents.set(1, 2, ClickableItem.empty(phoneIcon));

        // Remplir le reste de la ligne 1
        contents.set(1, 0, ClickableItem.empty(createGlass(Material.GREEN_STAINED_GLASS_PANE)));
        contents.set(1, 3, ClickableItem.empty(createGlass(Material.GREEN_STAINED_GLASS_PANE)));

        // === LIGNE 2: Info correspondant ===
        ItemStack contactItem = createItem(Material.PLAYER_HEAD,
            ChatColor.AQUA + "" + ChatColor.BOLD + displayName,
            "",
            ChatColor.GRAY + "Numero: " + ChatColor.WHITE + otherNumber,
            playerName.isEmpty() ? "" : ChatColor.GRAY + "Joueur: " + ChatColor.WHITE + playerName
        );
        contents.set(2, 1, ClickableItem.empty(contactItem));
        contents.set(2, 2, ClickableItem.empty(contactItem));

        // Remplir le reste de la ligne 2
        contents.set(2, 0, ClickableItem.empty(createGlass(Material.CYAN_STAINED_GLASS_PANE)));
        contents.set(2, 3, ClickableItem.empty(createGlass(Material.CYAN_STAINED_GLASS_PANE)));

        // === LIGNE 3: Duree de l'appel (mise a jour dynamique) ===
        updateDuration(contents);

        // === LIGNE 4: Statut vocal + Bouton RACCROCHER (separateur) ===
        boolean voiceConnected = callService != null && callService.isAvailable()
            && callService.isPlayerInVoiceChat(player);

        ItemStack voiceStatus;
        if (voiceConnected) {
            voiceStatus = createItem(Material.NOTE_BLOCK,
                ChatColor.GREEN + "" + ChatColor.BOLD + "VOCAL ACTIF",
                "",
                ChatColor.GREEN + "Chat vocal connecte",
                ChatColor.GRAY + "Parlez normalement!"
            );
        } else {
            voiceStatus = createItem(Material.BARRIER,
                ChatColor.YELLOW + "" + ChatColor.BOLD + "MODE TEXTE",
                "",
                ChatColor.YELLOW + "Chat vocal non connecte",
                ChatColor.GRAY + "Utilisez /audio pour activer"
            );
        }
        contents.set(4, 0, ClickableItem.empty(voiceStatus));
        contents.set(4, 1, ClickableItem.empty(voiceStatus));

        // Bouton RACCROCHER
        ItemStack hangupItem = createItem(Material.RED_CONCRETE,
            ChatColor.RED + "" + ChatColor.BOLD + "\u260E RACCROCHER",
            "",
            ChatColor.GRAY + "Cliquez pour terminer"
        );
        contents.set(4, 2, ClickableItem.of(hangupItem, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            phoneService.hangUp(player);
            player.sendMessage(PhoneMessages.CALL_ENDED);
            player.closeInventory();
        }));
        contents.set(4, 3, ClickableItem.of(hangupItem, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            phoneService.hangUp(player);
            player.sendMessage(PhoneMessages.CALL_ENDED);
            player.closeInventory();
        }));

        // === LIGNE 5: Dock ===
        for (int col = 0; col <= 3; col++) {
            contents.set(5, col, ClickableItem.empty(createGlass(Material.CYAN_STAINED_GLASS_PANE)));
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // Verifier si l'appel est toujours actif
        ActiveCall activeCall = phoneService.getActiveCall(player.getUniqueId());
        if (activeCall == null || activeCall.getState() != ActiveCall.CallState.CONNECTED) {
            // L'appel est termine, fermer le GUI
            player.closeInventory();
            return;
        }

        // Mettre a jour la duree
        updateDuration(contents);
    }

    private void updateDuration(InventoryContents contents) {
        String duration = call.getFormattedDuration();
        int durationSeconds = call.getDurationSeconds();

        // Icone qui change selon la duree
        Material durationMaterial = Material.CLOCK;

        ItemStack durationItem = createItem(durationMaterial,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Duree: " + ChatColor.WHITE + duration,
            "",
            ChatColor.GRAY + "Temps ecoule: " + ChatColor.YELLOW + durationSeconds + "s"
        );

        for (int col = 0; col <= 3; col++) {
            contents.set(3, col, ClickableItem.empty(durationItem));
        }
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                // Filtrer les lignes vides a la fin
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (String line : loreLines) {
                    if (!line.isEmpty() || !lore.isEmpty()) {
                        lore.add(line);
                    }
                }
                // Retirer les lignes vides a la fin
                while (!lore.isEmpty() && lore.get(lore.size() - 1).isEmpty()) {
                    lore.remove(lore.size() - 1);
                }
                if (!lore.isEmpty()) {
                    meta.setLore(lore);
                }
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
