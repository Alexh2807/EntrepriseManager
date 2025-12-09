package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.listener.PhoneChatListener;
import com.gravityyfh.roleplaycity.phone.model.CallRecord;
import com.gravityyfh.roleplaycity.phone.model.PhoneAccount;
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

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * GUI pour l'historique des appels.
 * Interface compacte de 4 blocs de large centree.
 */
public class PhoneCallHistoryGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;
    private int currentPage = 0;
    private FilterType currentFilter = FilterType.ALL;

    public PhoneCallHistoryGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    public void open(Player player) {
        open(player, 0, FilterType.ALL);
    }

    public void open(Player player, int page, FilterType filter) {
        this.currentPage = page;
        this.currentFilter = filter;

        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        SmartInventory.builder()
            .id("phone_call_history")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.DARK_GRAY + "\u23F0 Historique")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        PhoneAccount account = phoneService.getOrCreateAccount(player);
        String myNumber = account != null ? account.getPhoneNumber() : "";

        List<CallRecord> allHistory = phoneService.getCallHistory(player, 100);

        // Compter par type
        long allCount = allHistory.size();
        long missedCount = allHistory.stream()
            .filter(c -> c.getStatus() == CallRecord.CallStatus.MISSED)
            .filter(c -> !c.getCallerNumber().equals(myNumber))
            .count();
        long outgoingCount = allHistory.stream()
            .filter(c -> c.getCallerNumber().equals(myNumber))
            .count();
        long incomingCount = allHistory.stream()
            .filter(c -> !c.getCallerNumber().equals(myNumber))
            .count();

        // === LIGNE 0: Barre de statut ===
        String statusText = ChatColor.WHITE + "" + allCount + " appel(s)";
        ItemStack statusItem = createGlass(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta statusMeta = statusItem.getItemMeta();
        if (statusMeta != null) {
            statusMeta.setDisplayName(statusText);
            statusItem.setItemMeta(statusMeta);
        }
        for (int col = 0; col <= 3; col++) {
            contents.set(0, col, ClickableItem.empty(statusItem));
        }

        // === LIGNE 1: Filtres (colonnes 0-3) ===
        // Tous
        ItemStack allItem = createItem(
            currentFilter == FilterType.ALL ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            (currentFilter == FilterType.ALL ? ChatColor.GREEN : ChatColor.GRAY) + "Tous (" + allCount + ")"
        );
        contents.set(1, 0, ClickableItem.of(allItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            open(player, 0, FilterType.ALL);
        }));

        // Manques
        ItemStack missedItem = createItem(
            currentFilter == FilterType.MISSED ? Material.RED_CONCRETE : Material.GRAY_CONCRETE,
            (currentFilter == FilterType.MISSED ? ChatColor.RED : ChatColor.GRAY) + "Manques (" + missedCount + ")"
        );
        contents.set(1, 1, ClickableItem.of(missedItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            open(player, 0, FilterType.MISSED);
        }));

        // Emis
        ItemStack outItem = createItem(
            currentFilter == FilterType.OUTGOING ? Material.CYAN_CONCRETE : Material.GRAY_CONCRETE,
            (currentFilter == FilterType.OUTGOING ? ChatColor.AQUA : ChatColor.GRAY) + "Emis (" + outgoingCount + ")"
        );
        contents.set(1, 2, ClickableItem.of(outItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            open(player, 0, FilterType.OUTGOING);
        }));

        // Recus
        ItemStack inItem = createItem(
            currentFilter == FilterType.INCOMING ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE,
            (currentFilter == FilterType.INCOMING ? ChatColor.YELLOW : ChatColor.GRAY) + "Recus (" + incomingCount + ")"
        );
        contents.set(1, 3, ClickableItem.of(inItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            open(player, 0, FilterType.INCOMING);
        }));

        // === LIGNES 2-4: Liste des appels ===
        List<CallRecord> filteredHistory = filterHistory(allHistory, myNumber);

        if (filteredHistory.isEmpty()) {
            ItemStack noCallItem = createItem(Material.LIME_WOOL,
                ChatColor.GREEN + "Aucun appel",
                "",
                ChatColor.GRAY + "Historique vide"
            );
            contents.set(3, 1, ClickableItem.empty(noCallItem));
            contents.set(3, 2, ClickableItem.empty(noCallItem));
        } else {
            int itemsPerPage = 12; // 4 colonnes x 3 lignes
            int totalPages = (int) Math.ceil((double) filteredHistory.size() / itemsPerPage);
            int startIndex = currentPage * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, filteredHistory.size());

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

            int index = startIndex;
            for (int row = 2; row <= 4 && index < endIndex; row++) {
                for (int col = 0; col <= 3 && index < endIndex; col++) {
                    CallRecord record = filteredHistory.get(index);

                    boolean isOutgoing = record.getCallerNumber().equals(myNumber);
                    String otherNumber = isOutgoing ? record.getCalleeNumber() : record.getCallerNumber();
                    String displayName = phoneService.getContactDisplayName(player.getUniqueId(), otherNumber);
                    if (displayName == null) displayName = otherNumber;

                    Material mat;
                    String prefix;
                    List<String> lore = new ArrayList<>();

                    switch (record.getStatus()) {
                        case COMPLETED -> {
                            mat = isOutgoing ? Material.LIME_DYE : Material.GREEN_DYE;
                            prefix = isOutgoing ? ChatColor.AQUA + "\u2197 " : ChatColor.GREEN + "\u2199 ";
                            lore.add(ChatColor.GREEN + "Complete");
                            if (record.getDurationSeconds() > 0) {
                                lore.add(ChatColor.GRAY + formatDuration(record.getDurationSeconds()));
                            }
                        }
                        case MISSED -> {
                            mat = Material.RED_DYE;
                            prefix = ChatColor.RED + "\u2716 ";
                            lore.add(ChatColor.RED + "Manque");
                        }
                        case REJECTED -> {
                            mat = Material.ORANGE_DYE;
                            prefix = ChatColor.GOLD + "\u2715 ";
                            lore.add(ChatColor.GOLD + "Rejete");
                        }
                        default -> {
                            mat = Material.GRAY_DYE;
                            prefix = ChatColor.GRAY + "? ";
                            lore.add(ChatColor.GRAY + "Echec");
                        }
                    }

                    lore.add("");
                    lore.add(ChatColor.GRAY + sdf.format(new Date(record.getStartedAt())));
                    lore.add(ChatColor.YELLOW + "Clic: Rappeler");
                    // Verifier si c'est deja un contact
                    boolean isContact = phoneService.hasContact(player, otherNumber);
                    if (!isContact) {
                        lore.add(ChatColor.AQUA + "Shift: Ajouter contact");
                    }

                    ItemStack callItem = createItem(mat, prefix + truncate(displayName, 12), lore.toArray(new String[0]));

                    final String numberToCall = otherNumber;
                    final String finalDisplayName = displayName;
                    final boolean finalIsContact = isContact;
                    contents.set(row, col, ClickableItem.of(callItem, e -> {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

                        if (e.isShiftClick() && !finalIsContact) {
                            // Ajouter rapidement comme contact
                            openQuickAddContact(player, numberToCall, finalDisplayName);
                        } else {
                            // Rappeler
                            player.closeInventory();
                            if (!phoneManager.hasPhoneInHand(player)) {
                                player.sendMessage(ChatColor.RED + "[Tel] Tenez votre telephone en main.");
                                return;
                            }
                            phoneService.initiateCall(player, numberToCall);
                        }
                    }));
                    index++;
                }
            }

            // === LIGNE 5: Navigation (Dock) ===
            // Page precedente
            if (currentPage > 0) {
                ItemStack prevItem = createItem(Material.ARROW, ChatColor.YELLOW + "< Page " + currentPage);
                contents.set(5, 0, ClickableItem.of(prevItem, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    open(player, currentPage - 1, currentFilter);
                }));
            }

            // Page suivante
            if (currentPage < totalPages - 1) {
                ItemStack nextItem = createItem(Material.ARROW, ChatColor.YELLOW + "Page " + (currentPage + 2) + " >");
                contents.set(5, 3, ClickableItem.of(nextItem, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    open(player, currentPage + 1, currentFilter);
                }));
            }
        }

        // Retour (toujours au centre)
        addBackButton(contents, player);
    }

    private List<CallRecord> filterHistory(List<CallRecord> allHistory, String myNumber) {
        return allHistory.stream()
            .filter(record -> {
                boolean isOutgoing = record.getCallerNumber().equals(myNumber);
                return switch (currentFilter) {
                    case ALL -> true;
                    case MISSED -> record.getStatus() == CallRecord.CallStatus.MISSED && !isOutgoing;
                    case OUTGOING -> isOutgoing;
                    case INCOMING -> !isOutgoing;
                };
            })
            .toList();
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

    /**
     * GUI pour ajouter rapidement un contact depuis l'historique des appels.
     */
    private void openQuickAddContact(Player player, String number, String suggestedName) {
        // Verifier si deja en contact
        if (phoneService.hasContact(player, number)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Ce numero est deja dans vos contacts.");
            return;
        }

        // Verifier si le numero existe
        if (!phoneService.phoneNumberExists(number)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Ce numero n'existe pas.");
            return;
        }

        SmartInventory.builder()
            .id("phone_quick_add_contact_history")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    PhoneGUIUtils.initPhoneLayout(contents, 4);

                    // Titre
                    ItemStack titleItem = createItem(Material.EMERALD,
                        ChatColor.GREEN + "" + ChatColor.BOLD + "Ajouter Contact",
                        ChatColor.GRAY + "Numero: " + number
                    );
                    contents.set(0, 1, ClickableItem.empty(titleItem));
                    contents.set(0, 2, ClickableItem.empty(titleItem));

                    // Option 1: Utiliser le nom suggere
                    contents.set(2, 0, ClickableItem.of(
                        createItem(Material.LIME_CONCRETE,
                            ChatColor.GREEN + "" + ChatColor.BOLD + "Utiliser: " + suggestedName,
                            ChatColor.GRAY + "Ajouter avec ce nom"
                        ),
                        e -> {
                            if (phoneService.addContact(p, number, suggestedName)) {
                                p.sendMessage(ChatColor.GREEN + "[Telephone] Contact " + suggestedName + " ajoute!");
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                            } else {
                                p.sendMessage(ChatColor.RED + "[Telephone] Erreur lors de l'ajout.");
                            }
                            open(p, currentPage, currentFilter);
                        }
                    ));

                    // Option 2: Personnaliser le nom
                    contents.set(2, 3, ClickableItem.of(
                        createItem(Material.NAME_TAG,
                            ChatColor.GOLD + "" + ChatColor.BOLD + "Personnaliser",
                            ChatColor.GRAY + "Choisir un autre nom"
                        ),
                        e -> {
                            p.closeInventory();
                            p.sendMessage(PhoneMessages.ENTER_CONTACT_NAME);
                            p.sendMessage(PhoneMessages.SMS_CANCEL_HINT);
                            PhoneChatListener.awaitInput(p, PhoneChatListener.InputType.CONTACT_NAME, number);
                        }
                    ));

                    // Retour
                    contents.set(3, 1, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.RED + "Annuler"),
                        e -> open(p, currentPage, currentFilter)
                    ));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(4, 9)
            .title(ChatColor.AQUA + "+ Contact")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength - 2) + ".." : text;
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m " + secs + "s";
    }

    /**
     * Types de filtre.
     */
    public enum FilterType {
        ALL, MISSED, OUTGOING, INCOMING
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
