package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.model.MusicTrack;
import com.gravityyfh.roleplaycity.phone.service.MusicService;
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

import java.util.*;

/**
 * GUI pour le lecteur de musique.
 * Interface compacte de 4 blocs de large centree.
 */
public class PhoneMusicGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final MusicService musicService;
    private int currentPage = 0;

    public PhoneMusicGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.musicService = plugin.getMusicService();
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        this.currentPage = page;

        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        SmartInventory.builder()
            .id("phone_music")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.DARK_GRAY + "\u266B Musique")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        // Verifier si OpenAudioMc est disponible
        if (musicService == null || !musicService.isAvailable()) {
            // === Service non disponible ===
            ItemStack titleItem = createItem(Material.RED_STAINED_GLASS_PANE,
                ChatColor.RED + "" + ChatColor.BOLD + "Musique"
            );
            for (int col = 0; col <= 3; col++) {
                contents.set(0, col, ClickableItem.empty(titleItem));
            }

            ItemStack unavailableItem = createItem(Material.BARRIER,
                ChatColor.RED + "Non disponible",
                "",
                ChatColor.GRAY + "OpenAudioMc requis",
                ChatColor.GRAY + "Contactez un admin"
            );
            contents.set(2, 1, ClickableItem.empty(unavailableItem));
            contents.set(2, 2, ClickableItem.empty(unavailableItem));

            // Retour
            addBackButton(contents, player);
            return;
        }

        // Verifier si le joueur est connecte au chat vocal
        boolean isConnectedToVoice = musicService.isPlayerConnected(player);

        boolean isPlaying = musicService.isPlaying(player);
        MusicTrack currentTrack = musicService.getCurrentTrack(player);

        // === LIGNE 0: Barre de statut ===
        String statusText;
        Material statusMaterial;
        if (!isConnectedToVoice) {
            statusText = ChatColor.RED + "\u26A0 Non connecte au chat vocal";
            statusMaterial = Material.RED_STAINED_GLASS_PANE;
        } else if (isPlaying && currentTrack != null) {
            statusText = ChatColor.GREEN + "\u25B6 " + currentTrack.getName();
            statusMaterial = Material.LIME_STAINED_GLASS_PANE;
        } else {
            statusText = ChatColor.GRAY + "\u25A0 Aucune musique";
            statusMaterial = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack statusItem = createGlass(statusMaterial);
        ItemMeta statusMeta = statusItem.getItemMeta();
        if (statusMeta != null) {
            statusMeta.setDisplayName(statusText);
            statusItem.setItemMeta(statusMeta);
        }
        for (int col = 0; col <= 3; col++) {
            contents.set(0, col, ClickableItem.empty(statusItem));
        }

        // === LIGNE 1: Controles lecture ou avertissement connexion ===
        if (!isConnectedToVoice) {
            // Bouton pour se connecter au chat vocal
            ItemStack connectItem = createItem(Material.ENDER_PEARL,
                ChatColor.YELLOW + "" + ChatColor.BOLD + "\u260E Se connecter",
                "",
                ChatColor.GRAY + "Vous devez etre connecte",
                ChatColor.GRAY + "au chat vocal pour ecouter",
                ChatColor.GRAY + "de la musique.",
                "",
                ChatColor.AQUA + "Cliquez pour recevoir le lien"
            );
            contents.set(1, 1, ClickableItem.of(connectItem, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                musicService.sendConnectUrl(player);
                player.sendMessage(ChatColor.YELLOW + "[Telephone] " + ChatColor.WHITE + "Lien de connexion envoye !");
                player.sendMessage(ChatColor.GRAY + "Cliquez sur le lien dans le chat pour vous connecter au chat vocal.");
            }));
            contents.set(1, 2, ClickableItem.of(connectItem, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                musicService.sendConnectUrl(player);
                player.sendMessage(ChatColor.YELLOW + "[Telephone] " + ChatColor.WHITE + "Lien de connexion envoye !");
                player.sendMessage(ChatColor.GRAY + "Cliquez sur le lien dans le chat pour vous connecter au chat vocal.");
            }));
        } else if (isPlaying && currentTrack != null) {
            // Bouton Stop
            ItemStack stopItem = createItem(Material.RED_CONCRETE,
                ChatColor.RED + "" + ChatColor.BOLD + "\u25A0 Stop",
                "",
                ChatColor.GRAY + "Arreter la musique"
            );
            contents.set(1, 1, ClickableItem.of(stopItem, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                musicService.stop(player);
                player.sendMessage(ChatColor.YELLOW + "[Tel] Musique arretee.");
                open(player, currentPage);
            }));
            contents.set(1, 2, ClickableItem.of(stopItem, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                musicService.stop(player);
                player.sendMessage(ChatColor.YELLOW + "[Tel] Musique arretee.");
                open(player, currentPage);
            }));
        } else {
            // Info "Selectionnez une piste"
            ItemStack selectItem = createItem(Material.NOTE_BLOCK,
                ChatColor.AQUA + "Selectionnez",
                "",
                ChatColor.GRAY + "Choisissez une piste"
            );
            contents.set(1, 1, ClickableItem.empty(selectItem));
            contents.set(1, 2, ClickableItem.empty(selectItem));
        }

        // === LIGNES 2-4: Liste des pistes (4 par ligne, 3 lignes = 12 par page) ===
        List<MusicTrack> tracks = phoneManager.getMusicTracks();
        int itemsPerPage = 12;
        int totalPages = (int) Math.ceil((double) tracks.size() / itemsPerPage);
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, tracks.size());

        if (tracks.isEmpty()) {
            ItemStack noTrackItem = createItem(Material.BARRIER,
                ChatColor.GRAY + "Aucune piste",
                "",
                ChatColor.DARK_GRAY + "Pas de musique configuree"
            );
            contents.set(3, 1, ClickableItem.empty(noTrackItem));
            contents.set(3, 2, ClickableItem.empty(noTrackItem));
        } else {
            int index = startIndex;
            for (int row = 2; row <= 4 && index < endIndex; row++) {
                for (int col = 0; col <= 3 && index < endIndex; col++) {
                    MusicTrack track = tracks.get(index);

                    Material discMaterial = getDiscMaterial(index);
                    boolean isCurrent = currentTrack != null && currentTrack.getName().equals(track.getName());

                    ItemStack trackItem = createItem(discMaterial,
                        (isCurrent && isPlaying ? ChatColor.GREEN : ChatColor.AQUA) + track.getName(),
                        "",
                        isCurrent && isPlaying ? ChatColor.GREEN + "\u25B6 En lecture" : ChatColor.YELLOW + "Cliquez pour ecouter"
                    );

                    final MusicTrack trackToPlay = track;
                    contents.set(row, col, ClickableItem.of(trackItem, e -> {
                        // Verifier connexion OpenAudioMc
                        if (!musicService.isPlayerConnected(player)) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                            player.sendMessage(ChatColor.RED + "[Telephone] " + ChatColor.WHITE + "Vous devez etre connecte au chat vocal pour ecouter de la musique !");
                            player.sendMessage(ChatColor.GRAY + "Cliquez sur 'Se connecter' pour recevoir le lien de connexion.");
                            open(player, currentPage); // Rafraichir pour montrer le bouton de connexion
                            return;
                        }

                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                        musicService.play(player, trackToPlay);
                        player.sendMessage(ChatColor.GREEN + "[Telephone] \u266B " + ChatColor.WHITE + "Lecture: " + trackToPlay.getName());
                        open(player, currentPage);
                    }));
                    index++;
                }
            }
        }

        // === LIGNE 5: Navigation (Dock) ===
        // Page precedente
        if (currentPage > 0) {
            ItemStack prevItem = createItem(Material.ARROW,
                ChatColor.YELLOW + "< Page " + currentPage
            );
            contents.set(5, 0, ClickableItem.of(prevItem, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                open(player, currentPage - 1);
            }));
        }

        // Retour
        addBackButton(contents, player);

        // Page suivante
        if (currentPage < totalPages - 1) {
            ItemStack nextItem = createItem(Material.ARROW,
                ChatColor.YELLOW + "Page " + (currentPage + 2) + " >"
            );
            contents.set(5, 3, ClickableItem.of(nextItem, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                open(player, currentPage + 1);
            }));
        }
    }

    private void addBackButton(InventoryContents contents, Player player) {
        ItemStack backItem = createItem(Material.ARROW, ChatColor.RED + "Retour");
        contents.set(5, 1, ClickableItem.of(backItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            new PhoneMainGUI(plugin).open(player);
        }));
        contents.set(5, 2, ClickableItem.of(backItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            new PhoneMainGUI(plugin).open(player);
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    private Material getDiscMaterial(int index) {
        Material[] discs = {
            Material.MUSIC_DISC_CAT,
            Material.MUSIC_DISC_BLOCKS,
            Material.MUSIC_DISC_CHIRP,
            Material.MUSIC_DISC_FAR,
            Material.MUSIC_DISC_MALL,
            Material.MUSIC_DISC_MELLOHI,
            Material.MUSIC_DISC_STAL,
            Material.MUSIC_DISC_STRAD,
            Material.MUSIC_DISC_WARD,
            Material.MUSIC_DISC_WAIT,
            Material.MUSIC_DISC_PIGSTEP,
            Material.MUSIC_DISC_OTHERSIDE
        };
        return discs[index % discs.length];
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
