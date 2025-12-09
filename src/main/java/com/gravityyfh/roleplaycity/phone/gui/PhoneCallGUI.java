package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.listener.PhoneChatListener;
import com.gravityyfh.roleplaycity.phone.model.CallRecord;
import com.gravityyfh.roleplaycity.phone.model.Contact;
import com.gravityyfh.roleplaycity.phone.model.PhoneAccount;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import de.lightplugins.economy.master.Main;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * GUI pour les appels telephoniques.
 * Permet de: composer un numero, appeler un contact, voir l'historique
 */
public class PhoneCallGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    public PhoneCallGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    public void open(Player player) {
        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        SmartInventory.builder()
            .id("phone_call")
            .provider(this)
            .size(5, 9)
            .title(ChatColor.DARK_GREEN + "\u260E Appels")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fillBorders(ClickableItem.empty(createGlassPane(Material.LIME_STAINED_GLASS_PANE)));

        // Verifier si en appel
        if (phoneService.isInCall(player.getUniqueId())) {
            showInCallMenu(player, contents);
            return;
        }

        // === Composer un numero ===
        ItemStack dialItem = createItem(Material.OAK_SIGN,
            ChatColor.GREEN + "" + ChatColor.BOLD + "\u260E Composer un numero",
            ChatColor.GRAY + "Entrez un numero pour appeler",
            "",
            ChatColor.GRAY + "Format: " + ChatColor.WHITE + "XXX-XXXX",
            "",
            ChatColor.YELLOW + "Cliquez pour composer"
        );
        contents.set(1, 2, ClickableItem.of(dialItem, e -> {
            player.closeInventory();
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "[Telephone] " + ChatColor.WHITE + "Entrez le numero a appeler:");
            player.sendMessage(ChatColor.GRAY + "(Format: XXX-XXXX ou tapez 'annuler')");
            player.sendMessage("");
            PhoneChatListener.awaitInput(player, PhoneChatListener.InputType.CALL_NUMBER, null);
        }));

        // === Appeler depuis contacts ===
        List<Contact> contacts = phoneService.getContacts(player);
        ItemStack contactsItem = createItem(Material.PLAYER_HEAD,
            ChatColor.AQUA + "" + ChatColor.BOLD + "\u2706 Appeler un contact",
            ChatColor.GRAY + "Selectionnez dans votre repertoire",
            "",
            ChatColor.GRAY + "Contacts: " + ChatColor.WHITE + contacts.size(),
            "",
            ChatColor.YELLOW + "Cliquez pour choisir"
        );
        contents.set(1, 4, ClickableItem.of(contactsItem, e -> {
            openContactsForCall(player);
        }));

        // === Historique des appels ===
        ItemStack historyItem = createItem(Material.CLOCK,
            ChatColor.GOLD + "" + ChatColor.BOLD + "\u23F0 Historique",
            ChatColor.GRAY + "Voir les derniers appels",
            "",
            ChatColor.YELLOW + "Cliquez pour voir"
        );
        contents.set(1, 6, ClickableItem.of(historyItem, e -> {
            openCallHistory(player);
        }));

        // === Info credits ===
        ItemStack phoneItem = phoneManager.findPhoneInInventory(player);
        int credits = phoneItem != null ? phoneManager.getCredits(phoneItem) : 0;
        ItemStack infoItem = createItem(Material.GOLD_INGOT,
            ChatColor.YELLOW + "Credits: " + ChatColor.WHITE + credits,
            ChatColor.GRAY + "Cout: " + phoneManager.getCallCostPerMinute() + " credits/min"
        );
        contents.set(3, 4, ClickableItem.empty(infoItem));

        // === Retour ===
        ItemStack backItem = createItem(Material.ARROW,
            ChatColor.RED + "Retour",
            ChatColor.GRAY + "Retourner au menu principal"
        );
        contents.set(4, 4, ClickableItem.of(backItem, e -> {
            new PhoneMainGUI(plugin).open(player);
        }));
    }

    private void showInCallMenu(Player player, InventoryContents contents) {
        var call = phoneService.getActiveCall(player.getUniqueId());
        if (call == null) return;

        String otherNumber = call.isCaller(player.getUniqueId()) ? call.getCalleeNumber() : call.getCallerNumber();
        String displayName = phoneService.getContactDisplayName(player.getUniqueId(), otherNumber);
        if (displayName == null) displayName = otherNumber;

        // Info appel en cours
        ItemStack callInfoItem = createItem(Material.BELL,
            ChatColor.GREEN + "" + ChatColor.BOLD + "Appel en cours",
            ChatColor.GRAY + "Avec: " + ChatColor.WHITE + displayName,
            ChatColor.GRAY + "Numero: " + ChatColor.WHITE + otherNumber,
            "",
            ChatColor.GRAY + "Duree: " + ChatColor.WHITE + call.getFormattedDuration()
        );
        contents.set(2, 4, ClickableItem.empty(callInfoItem));

        // Raccrocher
        ItemStack hangupItem = createItem(Material.RED_WOOL,
            ChatColor.RED + "" + ChatColor.BOLD + "Raccrocher",
            ChatColor.GRAY + "Terminer l'appel"
        );
        contents.set(3, 4, ClickableItem.of(hangupItem, e -> {
            phoneService.hangUp(player);
            player.closeInventory();
        }));
    }

    private void openContactsForCall(Player player) {
        List<Contact> contacts = phoneService.getContacts(player);

        if (contacts.isEmpty()) {
            player.sendMessage(ChatColor.RED + "[Telephone] Aucun contact dans votre repertoire.");
            return;
        }

        SmartInventory.builder()
            .id("phone_call_contacts")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    contents.fillBorders(ClickableItem.empty(createGlassPane(Material.LIME_STAINED_GLASS_PANE)));

                    Pagination pagination = contents.pagination();
                    ClickableItem[] items = new ClickableItem[contacts.size()];

                    for (int i = 0; i < contacts.size(); i++) {
                        Contact contact = contacts.get(i);
                        ItemStack contactItem = createItem(Material.PLAYER_HEAD,
                            ChatColor.GREEN + contact.getContactName(),
                            ChatColor.GRAY + "Numero: " + ChatColor.WHITE + contact.getContactNumber(),
                            "",
                            ChatColor.YELLOW + "Cliquez pour appeler"
                        );

                        items[i] = ClickableItem.of(contactItem, e -> {
                            p.closeInventory();
                            // initiateCall gere les messages d'erreur
                            phoneService.initiateCall(p, contact.getContactNumber());
                        });
                    }

                    pagination.setItems(items);
                    pagination.setItemsPerPage(21);
                    pagination.addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1));

                    // Navigation
                    if (!pagination.isFirst()) {
                        contents.set(4, 3, ClickableItem.of(createItem(Material.ARROW, ChatColor.YELLOW + "Page precedente"),
                            e -> SmartInventory.builder().id("phone_call_contacts").provider(this).size(5, 9)
                                .title(ChatColor.DARK_GREEN + "Appeler un contact").manager(Main.bankMenuInventoryManager)
                                .build().open(p, pagination.previous().getPage())));
                    }
                    if (!pagination.isLast()) {
                        contents.set(4, 5, ClickableItem.of(createItem(Material.ARROW, ChatColor.YELLOW + "Page suivante"),
                            e -> SmartInventory.builder().id("phone_call_contacts").provider(this).size(5, 9)
                                .title(ChatColor.DARK_GREEN + "Appeler un contact").manager(Main.bankMenuInventoryManager)
                                .build().open(p, pagination.next().getPage())));
                    }

                    contents.set(4, 4, ClickableItem.of(createItem(Material.BARRIER, ChatColor.RED + "Retour"),
                        e -> new PhoneCallGUI(plugin).open(p)));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(5, 9)
            .title(ChatColor.DARK_GREEN + "Appeler un contact")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    private void openCallHistory(Player player) {
        List<CallRecord> history = phoneService.getCallHistory(player, 21);
        PhoneAccount account = phoneService.getOrCreateAccount(player);
        String myNumber = account != null ? account.getPhoneNumber() : "";

        SmartInventory.builder()
            .id("phone_call_history")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    contents.fillBorders(ClickableItem.empty(createGlassPane(Material.ORANGE_STAINED_GLASS_PANE)));

                    if (history.isEmpty()) {
                        contents.set(2, 4, ClickableItem.empty(createItem(Material.BARRIER,
                            ChatColor.GRAY + "Aucun appel",
                            ChatColor.DARK_GRAY + "Votre historique est vide")));
                    } else {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
                        int slot = 10;

                        for (CallRecord record : history) {
                            if (slot > 34) break;
                            if (slot % 9 == 0 || slot % 9 == 8) {
                                slot++;
                                continue;
                            }

                            boolean isOutgoing = record.getCallerNumber().equals(myNumber);
                            String otherNumber = isOutgoing ? record.getCalleeNumber() : record.getCallerNumber();
                            String displayName = phoneService.getContactDisplayName(p.getUniqueId(), otherNumber);
                            if (displayName == null) displayName = otherNumber;

                            Material mat;
                            String statusText;
                            switch (record.getStatus()) {
                                case COMPLETED -> {
                                    mat = Material.LIME_DYE;
                                    statusText = ChatColor.GREEN + "Complete";
                                }
                                case MISSED -> {
                                    mat = Material.RED_DYE;
                                    statusText = ChatColor.RED + "Manque";
                                }
                                case REJECTED -> {
                                    mat = Material.ORANGE_DYE;
                                    statusText = ChatColor.GOLD + "Rejete";
                                }
                                default -> {
                                    mat = Material.GRAY_DYE;
                                    statusText = ChatColor.GRAY + "Echec";
                                }
                            }

                            String direction = isOutgoing ? ChatColor.AQUA + "\u2192 Sortant" : ChatColor.YELLOW + "\u2190 Entrant";

                            ItemStack callItem = createItem(mat,
                                (isOutgoing ? ChatColor.AQUA : ChatColor.YELLOW) + displayName,
                                direction,
                                ChatColor.GRAY + "Statut: " + statusText,
                                ChatColor.GRAY + "Duree: " + ChatColor.WHITE + record.getFormattedDuration(),
                                ChatColor.GRAY + "Date: " + ChatColor.WHITE + sdf.format(new Date(record.getStartedAt())),
                                "",
                                ChatColor.YELLOW + "Cliquez pour rappeler"
                            );

                            final String numberToCall = otherNumber;
                            contents.set(slot / 9, slot % 9, ClickableItem.of(callItem, e -> {
                                p.closeInventory();
                                // initiateCall gere les messages d'erreur
                                phoneService.initiateCall(p, numberToCall);
                            }));
                            slot++;
                        }
                    }

                    contents.set(4, 4, ClickableItem.of(createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> new PhoneCallGUI(plugin).open(p)));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(5, 9)
            .title(ChatColor.GOLD + "Historique des appels")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

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

    private ItemStack createGlassPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
