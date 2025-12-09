package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.listener.PhoneChatListener;
import com.gravityyfh.roleplaycity.phone.model.Contact;
import com.gravityyfh.roleplaycity.phone.model.Message;
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
 * GUI pour les SMS style iPhone - interface compacte 4 blocs de large.
 * Affiche les conversations par ordre chronologique (comme un vrai telephone).
 * Permet de: voir les conversations, nouveau chat, reprendre une discussion.
 *
 * Layout:
 * Colonnes 0-2: Bordure noire/grise (gauche)
 * Colonnes 3-6: Zone utile (4 blocs de large)
 * Colonnes 7-8: Bordure grise/noire (droite)
 */
public class PhoneSmsGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;
    private int currentPage = 0;

    // Stockage du numero en cours de composition
    private static final Map<UUID, StringBuilder> composingNumbers = new HashMap<>();

    public PhoneSmsGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
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
            .id("phone_sms")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.YELLOW + "\u2709 Messages")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        PhoneAccount myAccount = phoneService.getOrCreateAccount(player);
        String myNumber = myAccount != null ? myAccount.getPhoneNumber() : "";

        int unreadCount = phoneService.countUnreadMessages(player);
        ItemStack phoneItem = phoneManager.findPhoneInInventory(player);
        int credits = phoneItem != null ? phoneManager.getCredits(phoneItem) : 0;

        // === LIGNE 0: Barre de statut ===
        String unreadBadge = unreadCount > 0 ? ChatColor.RED + " (" + unreadCount + ")" : "";
        ItemStack statusItem = createItem(Material.GRAY_STAINED_GLASS_PANE,
            ChatColor.YELLOW + "\u2709 SMS" + unreadBadge + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + credits + "cr"
        );
        for (int col = 0; col <= 3; col++) {
            contents.set(0, col, ClickableItem.empty(statusItem));
        }

        // === LIGNE 1: Nouveau Chat (bouton principal) ===
        contents.set(1, 0, ClickableItem.of(
            createItem(Material.EMERALD,
                ChatColor.GREEN + "" + ChatColor.BOLD + "\u270F Nouveau Chat",
                ChatColor.GRAY + "Nouveau message",
                "",
                ChatColor.YELLOW + "Choisir un contact",
                ChatColor.YELLOW + "ou entrer un numero"
            ),
            e -> {
                playClick(player);
                openNewChatSelectionGUI(player);
            }
        ));

        // Placeholder pour alignement
        contents.set(1, 1, ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));
        contents.set(1, 2, ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));
        contents.set(1, 3, ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

        // === LIGNES 2-4: Conversations par ordre chronologique ===
        List<Message> conversations = phoneService.getAllConversationsGrouped(player);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

        int itemsPerPage = 12; // 4 colonnes x 3 lignes
        int totalPages = (int) Math.ceil((double) conversations.size() / itemsPerPage);
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, conversations.size());

        if (conversations.isEmpty()) {
            contents.set(3, 1, ClickableItem.empty(createItem(Material.BARRIER,
                ChatColor.GRAY + "Aucune conversation",
                "",
                ChatColor.YELLOW + "Cliquez 'Nouveau Chat'",
                ChatColor.YELLOW + "pour commencer")));
        } else {
            int index = startIndex;
            for (int row = 2; row <= 4 && index < endIndex; row++) {
                for (int col = 0; col <= 3 && index < endIndex; col++) {
                    Message lastMsg = conversations.get(index);

                    // Determiner l'autre numero (interlocuteur)
                    String otherNumber = lastMsg.getSenderNumber().equals(myNumber)
                        ? lastMsg.getRecipientNumber()
                        : lastMsg.getSenderNumber();

                    // Nom d'affichage (contact ou numero)
                    String displayName = phoneService.getContactDisplayName(player.getUniqueId(), otherNumber);
                    if (displayName == null) displayName = otherNumber;

                    // Compter les non-lus dans cette conversation
                    int unreadInConv = phoneService.countUnreadInConversation(player, otherNumber);
                    boolean hasUnread = unreadInConv > 0;

                    // Determiner si c'est moi qui ai envoye le dernier message
                    boolean iSentLast = lastMsg.getSenderNumber().equals(myNumber);
                    String msgPrefix = iSentLast ? ChatColor.GRAY + "Vous: " : "";

                    Material mat = hasUnread ? Material.FILLED_MAP : Material.MAP;
                    String unreadIndicator = hasUnread ? ChatColor.RED + " \u25CF (" + unreadInConv + ")" : "";

                    final String finalOtherNumber = otherNumber;
                    final String finalDisplayName = displayName;

                    List<String> lore = new ArrayList<>();
                    lore.add(msgPrefix + ChatColor.WHITE + truncate(lastMsg.getContent(), 20));
                    lore.add(ChatColor.DARK_GRAY + sdf.format(new Date(lastMsg.getSentAt())));
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Clic: Ouvrir conversation");
                    lore.add(ChatColor.AQUA + "Shift: Ajouter contact");

                    contents.set(row, col, ClickableItem.of(
                        createItem(mat,
                            (hasUnread ? ChatColor.GREEN : ChatColor.GRAY) + displayName + unreadIndicator,
                            lore.toArray(new String[0])
                        ),
                        e -> {
                            playClick(player);
                            if (e.isShiftClick()) {
                                // Ajouter rapidement comme contact
                                openQuickAddContact(player, finalOtherNumber, finalDisplayName);
                            } else {
                                // Ouvrir la conversation
                                phoneService.markConversationAsRead(player, finalOtherNumber);
                                openConversation(player, finalOtherNumber, finalDisplayName);
                            }
                        }
                    ));
                    index++;
                }
            }
        }

        // === LIGNE 5: Navigation + Retour ===
        // Page precedente
        if (currentPage > 0) {
            contents.set(5, 0, ClickableItem.of(
                createItem(Material.ARROW, ChatColor.YELLOW + "< Page " + currentPage),
                e -> {
                    playClick(player);
                    open(player, currentPage - 1);
                }
            ));
        }

        // Retour
        contents.set(5, 1, ClickableItem.of(
            createItem(Material.ARROW, ChatColor.RED + "Retour"),
            e -> new PhoneMainGUI(plugin).open(player)
        ));

        // Page suivante
        if (currentPage < totalPages - 1) {
            contents.set(5, 3, ClickableItem.of(
                createItem(Material.ARROW, ChatColor.YELLOW + "Page " + (currentPage + 2) + " >"),
                e -> {
                    playClick(player);
                    open(player, currentPage + 1);
                }
            ));
        }
    }

    /**
     * GUI de selection pour nouveau chat: contacts ou numero manuel.
     */
    private void openNewChatSelectionGUI(Player player) {
        List<Contact> contacts = phoneService.getContacts(player);

        SmartInventory.builder()
            .id("phone_new_chat")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    PhoneGUIUtils.initPhoneLayout(contents, 6);

                    // === LIGNE 0: Titre ===
                    ItemStack titleItem = createItem(Material.EMERALD,
                        ChatColor.GREEN + "" + ChatColor.BOLD + "Nouveau Chat",
                        ChatColor.GRAY + "Choisissez un destinataire"
                    );
                    contents.set(0, 1, ClickableItem.empty(titleItem));
                    contents.set(0, 2, ClickableItem.empty(titleItem));

                    // === LIGNE 1: Bouton numero manuel ===
                    contents.set(1, 0, ClickableItem.of(
                        createItem(Material.NAME_TAG,
                            ChatColor.GOLD + "" + ChatColor.BOLD + "\u260E Numero",
                            ChatColor.GRAY + "Entrer un numero",
                            ChatColor.GRAY + "manuellement"
                        ),
                        e -> {
                            playClick(p);
                            openNumericDialerGUI(p);
                        }
                    ));

                    // Separateur
                    contents.set(1, 1, ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));
                    contents.set(1, 2, ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));
                    contents.set(1, 3, ClickableItem.empty(createItem(Material.BLACK_STAINED_GLASS_PANE, " ")));

                    // === LIGNES 2-4: Contacts recents ===
                    if (contacts.isEmpty()) {
                        contents.set(3, 1, ClickableItem.empty(createItem(Material.BARRIER,
                            ChatColor.GRAY + "Aucun contact",
                            "",
                            ChatColor.YELLOW + "Utilisez 'Numero'",
                            ChatColor.YELLOW + "pour envoyer un SMS")));
                    } else {
                        int index = 0;
                        for (int row = 2; row <= 4 && index < contacts.size(); row++) {
                            for (int col = 0; col <= 3 && index < contacts.size(); col++) {
                                Contact contact = contacts.get(index);
                                final String number = contact.getContactNumber();
                                final String name = contact.getContactName();

                                contents.set(row, col, ClickableItem.of(
                                    createItem(Material.PLAYER_HEAD,
                                        ChatColor.GREEN + name,
                                        ChatColor.GRAY + number,
                                        "",
                                        ChatColor.YELLOW + "Clic: ecrire"
                                    ),
                                    e -> {
                                        p.closeInventory();
                                        p.sendMessage(PhoneMessages.SMS_WRITE_PROMPT);
                                        p.sendMessage(ChatColor.GRAY + "Destinataire: " + ChatColor.WHITE + name);
                                        p.sendMessage(PhoneMessages.SMS_CANCEL_HINT);
                                        PhoneChatListener.awaitInput(p, PhoneChatListener.InputType.SMS_CONTENT, number);
                                    }
                                ));
                                index++;
                            }
                        }
                    }

                    // === LIGNE 5: Retour ===
                    contents.set(5, 1, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> new PhoneSmsGUI(plugin).open(p)
                    ));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(6, 9)
            .title(ChatColor.GREEN + "\u270F Nouveau Chat")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    /**
     * GUI avec pave numerique pour entrer un numero.
     */
    private void openNumericDialerGUI(Player player) {
        UUID uuid = player.getUniqueId();
        composingNumbers.putIfAbsent(uuid, new StringBuilder());

        SmartInventory.builder()
            .id("phone_sms_dialer")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    PhoneGUIUtils.initPhoneLayout(contents, 6);

                    StringBuilder currentNumber = composingNumbers.computeIfAbsent(uuid, k -> new StringBuilder());

                    // === LIGNE 0: Affichage numero ===
                    String displayNumber = formatDisplayNumber(currentNumber.toString());
                    ItemStack numberDisplay = createItem(Material.NAME_TAG,
                        ChatColor.YELLOW + "" + ChatColor.BOLD + displayNumber,
                        ChatColor.GRAY + "Numero destinataire"
                    );
                    contents.set(0, 1, ClickableItem.empty(numberDisplay));
                    contents.set(0, 2, ClickableItem.empty(numberDisplay));

                    // === LIGNES 1-3: Clavier ===
                    contents.set(1, 0, createNumberButton(p, "1"));
                    contents.set(1, 1, createNumberButton(p, "2"));
                    contents.set(1, 2, createNumberButton(p, "3"));

                    contents.set(2, 0, createNumberButton(p, "4"));
                    contents.set(2, 1, createNumberButton(p, "5"));
                    contents.set(2, 2, createNumberButton(p, "6"));

                    contents.set(3, 0, createNumberButton(p, "7"));
                    contents.set(3, 1, createNumberButton(p, "8"));
                    contents.set(3, 2, createNumberButton(p, "9"));

                    // === LIGNE 4: C, 0, <, ECRIRE ===
                    contents.set(4, 0, ClickableItem.of(
                        createItem(Material.RED_CONCRETE, ChatColor.RED + "" + ChatColor.BOLD + "C", ChatColor.GRAY + "Effacer tout"),
                        e -> {
                            currentNumber.setLength(0);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
                            openNumericDialerGUI(p);
                        }
                    ));

                    contents.set(4, 1, createNumberButton(p, "0"));

                    contents.set(4, 2, ClickableItem.of(
                        createItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "" + ChatColor.BOLD + "\u2190", ChatColor.GRAY + "Effacer"),
                        e -> {
                            if (currentNumber.length() > 0) {
                                currentNumber.deleteCharAt(currentNumber.length() - 1);
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                                openNumericDialerGUI(p);
                            }
                        }
                    ));

                    boolean canWrite = currentNumber.length() == 7;
                    contents.set(4, 3, ClickableItem.of(
                        createItem(
                            canWrite ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                            (canWrite ? ChatColor.GREEN : ChatColor.GRAY) + "" + ChatColor.BOLD + "\u270F",
                            canWrite ? ChatColor.GREEN + "ECRIRE" : ChatColor.RED + "7 chiffres"
                        ),
                        e -> {
                            StringBuilder compNumber = composingNumbers.get(uuid);
                            if (compNumber == null || compNumber.length() != 7) {
                                p.sendMessage(PhoneMessages.ENTER_7_DIGITS);
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                                return;
                            }

                            String number = formatPhoneNumber(compNumber.toString());
                            composingNumbers.remove(uuid);
                            p.closeInventory();
                            p.sendMessage(PhoneMessages.SMS_WRITE_PROMPT);
                            p.sendMessage(ChatColor.GRAY + "Destinataire: " + ChatColor.WHITE + number);
                            p.sendMessage(PhoneMessages.SMS_CANCEL_HINT);
                            PhoneChatListener.awaitInput(p, PhoneChatListener.InputType.SMS_CONTENT, number);
                        }
                    ));

                    // === LIGNE 5: Retour ===
                    contents.set(5, 1, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> {
                            composingNumbers.remove(uuid);
                            openNewChatSelectionGUI(p);
                        }
                    ));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(6, 9)
            .title(ChatColor.GOLD + "\u260E Entrer numero")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    private ClickableItem createNumberButton(Player player, String digit) {
        UUID uuid = player.getUniqueId();
        ItemStack button = createItem(Material.LIGHT_GRAY_CONCRETE,
            ChatColor.WHITE + "" + ChatColor.BOLD + digit
        );
        return ClickableItem.of(button, e -> {
            StringBuilder currentNumber = composingNumbers.get(uuid);
            if (currentNumber != null && currentNumber.length() < 7) {
                currentNumber.append(digit);
                float pitch = 1.0f + (Float.parseFloat(digit) * 0.05f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, pitch);
                openNumericDialerGUI(player);
            } else if (currentNumber != null && currentNumber.length() >= 7) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            }
        });
    }

    /**
     * Ouvre une conversation avec un interlocuteur.
     */
    private void openConversation(Player player, String otherNumber, String displayName) {
        List<Message> conversation = phoneService.getConversation(player, otherNumber);
        PhoneAccount myAccount = phoneService.getOrCreateAccount(player);
        String myNumber = myAccount != null ? myAccount.getPhoneNumber() : "";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

        // Verifier si c'est un contact
        boolean isContact = phoneService.hasContact(player, otherNumber);

        SmartInventory.builder()
            .id("phone_conversation")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    PhoneGUIUtils.initPhoneLayout(contents, 6);

                    // === LIGNE 0: Titre + Actions ===
                    contents.set(0, 0, ClickableItem.of(
                        createItem(Material.BELL, ChatColor.GREEN + "Appeler", ChatColor.GRAY + "Appeler " + displayName),
                        e -> {
                            p.closeInventory();
                            if (!phoneManager.hasPhoneInHand(p)) {
                                p.sendMessage(PhoneMessages.PHONE_NOT_IN_HAND);
                                return;
                            }
                            phoneService.initiateCall(p, otherNumber);
                        }
                    ));

                    ItemStack titleItem = createItem(Material.PLAYER_HEAD,
                        ChatColor.GREEN + "" + ChatColor.BOLD + displayName,
                        ChatColor.GRAY + otherNumber
                    );
                    contents.set(0, 1, ClickableItem.empty(titleItem));

                    // Bouton ajouter contact (si pas encore contact)
                    if (!isContact) {
                        contents.set(0, 2, ClickableItem.of(
                            createItem(Material.EMERALD,
                                ChatColor.AQUA + "+ Contact",
                                ChatColor.GRAY + "Ajouter aux contacts"
                            ),
                            e -> {
                                playClick(p);
                                openQuickAddContact(p, otherNumber, displayName);
                            }
                        ));
                    } else {
                        contents.set(0, 2, ClickableItem.empty(titleItem));
                    }

                    contents.set(0, 3, ClickableItem.of(
                        createItem(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Repondre", ChatColor.GRAY + "Ecrire un message"),
                        e -> {
                            p.closeInventory();
                            p.sendMessage(PhoneMessages.SMS_WRITE_PROMPT);
                            p.sendMessage(ChatColor.GRAY + "Destinataire: " + ChatColor.WHITE + displayName);
                            p.sendMessage(PhoneMessages.SMS_CANCEL_HINT);
                            PhoneChatListener.awaitInput(p, PhoneChatListener.InputType.SMS_CONTENT, otherNumber);
                        }
                    ));

                    if (conversation.isEmpty()) {
                        contents.set(2, 1, ClickableItem.empty(createItem(Material.BARRIER,
                            ChatColor.GRAY + "Aucun message")));
                    } else {
                        // Afficher les messages (les plus recents d'abord, max 16)
                        Collections.reverse(conversation);
                        int index = 0;
                        for (Message msg : conversation) {
                            if (index >= 16) break;

                            int row = 1 + (index / 4);
                            int col = index % 4;

                            boolean isMine = msg.getSenderNumber().equals(myNumber);
                            Material mat = isMine ? Material.LIME_CONCRETE : Material.CYAN_CONCRETE;
                            String prefix = isMine ? ChatColor.GREEN + "Moi" : ChatColor.AQUA + displayName;

                            contents.set(row, col, ClickableItem.empty(
                                createItem(mat,
                                    prefix + ChatColor.DARK_GRAY + " " + sdf.format(new Date(msg.getSentAt())),
                                    ChatColor.WHITE + truncate(msg.getContent(), 25)
                                )
                            ));
                            index++;
                        }
                    }

                    // Retour
                    contents.set(5, 1, ClickableItem.of(createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> new PhoneSmsGUI(plugin).open(p)));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(6, 9)
            .title(ChatColor.GREEN + "Conversation")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    /**
     * GUI pour ajouter rapidement un contact depuis une conversation/historique.
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
            .id("phone_quick_add_contact")
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
                            new PhoneSmsGUI(plugin).open(p);
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
                        e -> new PhoneSmsGUI(plugin).open(p)
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

    /**
     * Ouvre la boite de reception (pour compatibilite).
     */
    public void openInbox(Player player) {
        // Rediriger vers l'affichage principal des conversations
        open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    // === UTILITAIRES ===

    private void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private String formatDisplayNumber(String number) {
        if (number.isEmpty()) return "___-____";
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (i == 3) display.append("-");
            display.append(i < number.length() ? number.charAt(i) : "_");
        }
        return display.toString();
    }

    private String formatPhoneNumber(String number) {
        if (number.length() != 7) return number;
        return number.substring(0, 3) + "-" + number.substring(3);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 2) + "..";
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    if (line != null && !line.isEmpty()) lore.add(line);
                }
                if (!lore.isEmpty()) meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
