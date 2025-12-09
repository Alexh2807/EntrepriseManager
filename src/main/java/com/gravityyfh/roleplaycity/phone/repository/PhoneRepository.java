package com.gravityyfh.roleplaycity.phone.repository;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.model.*;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Repository pour la persistance des donnees telephoniques.
 * Gere les comptes, contacts, messages et historique d'appels.
 */
public class PhoneRepository {

    private final RoleplayCity plugin;

    public PhoneRepository(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    private Connection getConnection() throws SQLException {
        return plugin.getConnectionManager().getConnection();
    }

    // ==================== PHONE ACCOUNTS ====================

    /**
     * Cree un nouveau compte telephone pour un joueur.
     */
    public PhoneAccount createAccount(UUID ownerUuid, String ownerName) {
        String phoneNumber = generateUniquePhoneNumber();
        long now = System.currentTimeMillis();

        String sql = "INSERT INTO phone_accounts (owner_uuid, owner_name, phone_number, created_at) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, ownerName);
            stmt.setString(3, phoneNumber);
            stmt.setLong(4, now);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new PhoneAccount(id, ownerUuid, ownerName, phoneNumber, now, null);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur creation compte telephone", e);
        }
        return null;
    }

    /**
     * Recupere le compte telephone d'un joueur.
     */
    public PhoneAccount getAccountByUuid(UUID ownerUuid) {
        String sql = "SELECT * FROM phone_accounts WHERE owner_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ownerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapPhoneAccount(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation compte par UUID", e);
        }
        return null;
    }

    /**
     * Recupere un compte par son numero de telephone.
     */
    public PhoneAccount getAccountByPhoneNumber(String phoneNumber) {
        String sql = "SELECT * FROM phone_accounts WHERE phone_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapPhoneAccount(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation compte par numero", e);
        }
        return null;
    }

    /**
     * Verifie si un joueur a deja un compte telephone.
     */
    public boolean hasAccount(UUID ownerUuid) {
        String sql = "SELECT 1 FROM phone_accounts WHERE owner_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ownerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur verification compte", e);
        }
        return false;
    }

    /**
     * Met a jour le nom du proprietaire (en cas de changement de pseudo).
     */
    public void updateOwnerName(UUID ownerUuid, String newName) {
        String sql = "UPDATE phone_accounts SET owner_name = ?, updated_at = ? WHERE owner_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newName);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, ownerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur mise a jour nom proprietaire", e);
        }
    }

    private PhoneAccount mapPhoneAccount(ResultSet rs) throws SQLException {
        return new PhoneAccount(
            rs.getInt("id"),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getString("owner_name"),
            rs.getString("phone_number"),
            rs.getLong("created_at"),
            rs.getObject("updated_at") != null ? rs.getLong("updated_at") : null
        );
    }

    /**
     * Genere un nouveau numero de telephone unique (public).
     */
    public String generateNewPhoneNumber() {
        return generateUniquePhoneNumber();
    }

    /**
     * Met a jour le numero de telephone d'un compte.
     */
    public boolean updateAccountPhoneNumber(UUID ownerUuid, String newNumber) {
        String sql = "UPDATE phone_accounts SET phone_number = ?, updated_at = ? WHERE owner_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newNumber);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, ownerUuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur mise a jour numero telephone", e);
        }
        return false;
    }

    /**
     * Genere un numero de telephone unique au format XXX-XXXX.
     */
    private String generateUniquePhoneNumber() {
        Random random = new Random();
        String number;
        int attempts = 0;
        int maxAttempts = 100;

        do {
            // Format: XXX-XXXX (ex: 123-4567)
            int prefix = 100 + random.nextInt(900); // 100-999
            int suffix = 1000 + random.nextInt(9000); // 1000-9999
            number = String.format("%03d-%04d", prefix, suffix);
            attempts++;
        } while (isPhoneNumberTaken(number) && attempts < maxAttempts);

        if (attempts >= maxAttempts) {
            // Fallback: utiliser timestamp
            long timestamp = System.currentTimeMillis() % 10000000;
            number = String.format("%03d-%04d", (int)(timestamp / 10000), (int)(timestamp % 10000));
        }

        return number;
    }

    private boolean isPhoneNumberTaken(String phoneNumber) {
        String sql = "SELECT 1 FROM phone_accounts WHERE phone_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur verification numero", e);
        }
        return false;
    }

    // ==================== CONTACTS ====================

    /**
     * Ajoute un contact au repertoire d'un joueur.
     */
    public Contact addContact(UUID ownerUuid, String contactName, String contactNumber) {
        String sql = "INSERT INTO phone_contacts (owner_uuid, contact_name, contact_number, created_at) VALUES (?, ?, ?, ?)";
        long now = System.currentTimeMillis();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, contactName);
            stmt.setString(3, contactNumber);
            stmt.setLong(4, now);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new Contact(id, ownerUuid, contactName, contactNumber, now);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur ajout contact", e);
        }
        return null;
    }

    /**
     * Recupere tous les contacts d'un joueur.
     */
    public List<Contact> getContacts(UUID ownerUuid) {
        List<Contact> contacts = new ArrayList<>();
        String sql = "SELECT * FROM phone_contacts WHERE owner_uuid = ? ORDER BY contact_name";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ownerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    contacts.add(mapContact(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation contacts", e);
        }
        return contacts;
    }

    /**
     * Recupere un contact par son numero.
     */
    public Contact getContactByNumber(UUID ownerUuid, String contactNumber) {
        String sql = "SELECT * FROM phone_contacts WHERE owner_uuid = ? AND contact_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, contactNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapContact(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation contact par numero", e);
        }
        return null;
    }

    /**
     * Modifie le nom d'un contact.
     */
    public void updateContactName(int contactId, String newName) {
        String sql = "UPDATE phone_contacts SET contact_name = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newName);
            stmt.setInt(2, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur mise a jour contact", e);
        }
    }

    /**
     * Supprime un contact.
     */
    public void deleteContact(int contactId) {
        String sql = "DELETE FROM phone_contacts WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur suppression contact", e);
        }
    }

    /**
     * Verifie si un contact existe deja.
     */
    public boolean contactExists(UUID ownerUuid, String contactNumber) {
        String sql = "SELECT 1 FROM phone_contacts WHERE owner_uuid = ? AND contact_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, contactNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur verification contact", e);
        }
        return false;
    }

    private Contact mapContact(ResultSet rs) throws SQLException {
        return new Contact(
            rs.getInt("id"),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getString("contact_name"),
            rs.getString("contact_number"),
            rs.getLong("created_at")
        );
    }

    // ==================== MESSAGES (SMS) ====================

    /**
     * Envoie un SMS.
     */
    public Message sendMessage(String senderNumber, String recipientNumber, String content) {
        String sql = "INSERT INTO phone_messages (sender_number, recipient_number, content, sent_at, is_read) VALUES (?, ?, ?, ?, ?)";
        long now = System.currentTimeMillis();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, senderNumber);
            stmt.setString(2, recipientNumber);
            stmt.setString(3, content);
            stmt.setLong(4, now);
            stmt.setBoolean(5, false);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new Message(id, senderNumber, recipientNumber, content, now, false);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur envoi message", e);
        }
        return null;
    }

    /**
     * Recupere les messages recus par un numero.
     */
    public List<Message> getReceivedMessages(String phoneNumber) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM phone_messages WHERE recipient_number = ? ORDER BY sent_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation messages recus", e);
        }
        return messages;
    }

    /**
     * Recupere les messages envoyes par un numero.
     */
    public List<Message> getSentMessages(String phoneNumber) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM phone_messages WHERE sender_number = ? ORDER BY sent_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation messages envoyes", e);
        }
        return messages;
    }

    /**
     * Recupere la conversation entre deux numeros.
     */
    public List<Message> getConversation(String number1, String number2) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM phone_messages WHERE " +
                     "(sender_number = ? AND recipient_number = ?) OR " +
                     "(sender_number = ? AND recipient_number = ?) " +
                     "ORDER BY sent_at ASC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, number1);
            stmt.setString(2, number2);
            stmt.setString(3, number2);
            stmt.setString(4, number1);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation conversation", e);
        }
        return messages;
    }

    /**
     * Marque un message comme lu.
     */
    public void markMessageAsRead(int messageId) {
        String sql = "UPDATE phone_messages SET is_read = 1 WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur marquage message lu", e);
        }
    }

    /**
     * Marque tous les messages d'un expediteur comme lus.
     */
    public void markConversationAsRead(String recipientNumber, String senderNumber) {
        String sql = "UPDATE phone_messages SET is_read = 1 WHERE recipient_number = ? AND sender_number = ? AND is_read = 0";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, recipientNumber);
            stmt.setString(2, senderNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur marquage conversation lue", e);
        }
    }

    /**
     * Compte les messages non lus d'un numero.
     */
    public int countUnreadMessages(String phoneNumber) {
        String sql = "SELECT COUNT(*) FROM phone_messages WHERE recipient_number = ? AND is_read = 0";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur comptage messages non lus", e);
        }
        return 0;
    }

    /**
     * Supprime un message.
     */
    public void deleteMessage(int messageId) {
        String sql = "DELETE FROM phone_messages WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur suppression message", e);
        }
    }

    private Message mapMessage(ResultSet rs) throws SQLException {
        return new Message(
            rs.getInt("id"),
            rs.getString("sender_number"),
            rs.getString("recipient_number"),
            rs.getString("content"),
            rs.getLong("sent_at"),
            rs.getBoolean("is_read")
        );
    }

    /**
     * Recupere toutes les conversations d'un numero, groupees par interlocuteur.
     * Retourne le dernier message de chaque conversation, trie par date (plus recent d'abord).
     */
    public List<Message> getAllConversationsGrouped(String phoneNumber) {
        List<Message> conversations = new ArrayList<>();
        // Cette requete recupere le dernier message de chaque conversation
        String sql = """
            SELECT m1.* FROM phone_messages m1
            INNER JOIN (
                SELECT
                    CASE
                        WHEN sender_number = ? THEN recipient_number
                        ELSE sender_number
                    END AS other_number,
                    MAX(sent_at) AS max_sent_at
                FROM phone_messages
                WHERE sender_number = ? OR recipient_number = ?
                GROUP BY other_number
            ) m2 ON (
                (m1.sender_number = ? AND m1.recipient_number = m2.other_number) OR
                (m1.recipient_number = ? AND m1.sender_number = m2.other_number)
            ) AND m1.sent_at = m2.max_sent_at
            ORDER BY m1.sent_at DESC
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);
            stmt.setString(2, phoneNumber);
            stmt.setString(3, phoneNumber);
            stmt.setString(4, phoneNumber);
            stmt.setString(5, phoneNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conversations.add(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation conversations groupees", e);
        }
        return conversations;
    }

    /**
     * Compte les messages non lus d'une conversation specifique.
     */
    public int countUnreadInConversation(String myNumber, String otherNumber) {
        String sql = "SELECT COUNT(*) FROM phone_messages WHERE recipient_number = ? AND sender_number = ? AND is_read = 0";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, myNumber);
            stmt.setString(2, otherNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur comptage messages non lus conversation", e);
        }
        return 0;
    }

    // ==================== CALL HISTORY ====================

    /**
     * Enregistre un appel.
     */
    public CallRecord recordCall(String callerNumber, String calleeNumber, CallRecord.CallStatus status, Long duration) {
        String sql = "INSERT INTO phone_calls (caller_number, callee_number, started_at, duration_seconds, status) VALUES (?, ?, ?, ?, ?)";
        long now = System.currentTimeMillis();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, callerNumber);
            stmt.setString(2, calleeNumber);
            stmt.setLong(3, now);
            if (duration != null) {
                stmt.setLong(4, duration);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setString(5, status.name());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new CallRecord(id, callerNumber, calleeNumber, now, duration, status);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur enregistrement appel", e);
        }
        return null;
    }

    /**
     * Met a jour la duree d'un appel.
     */
    public void updateCallDuration(int callId, long durationSeconds) {
        String sql = "UPDATE phone_calls SET duration_seconds = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, durationSeconds);
            stmt.setInt(2, callId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur mise a jour duree appel", e);
        }
    }

    /**
     * Recupere l'historique d'appels d'un numero.
     */
    public List<CallRecord> getCallHistory(String phoneNumber, int limit) {
        List<CallRecord> calls = new ArrayList<>();
        String sql = "SELECT * FROM phone_calls WHERE caller_number = ? OR callee_number = ? " +
                     "ORDER BY started_at DESC LIMIT ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);
            stmt.setString(2, phoneNumber);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    calls.add(mapCallRecord(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation historique appels", e);
        }
        return calls;
    }

    /**
     * Recupere les appels manques d'un numero.
     */
    public List<CallRecord> getMissedCalls(String phoneNumber) {
        List<CallRecord> calls = new ArrayList<>();
        String sql = "SELECT * FROM phone_calls WHERE callee_number = ? AND status = 'MISSED' " +
                     "ORDER BY started_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phoneNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    calls.add(mapCallRecord(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur recuperation appels manques", e);
        }
        return calls;
    }

    private CallRecord mapCallRecord(ResultSet rs) throws SQLException {
        Long duration = rs.getObject("duration_seconds") != null ? rs.getLong("duration_seconds") : null;
        return new CallRecord(
            rs.getInt("id"),
            rs.getString("caller_number"),
            rs.getString("callee_number"),
            rs.getLong("started_at"),
            duration,
            CallRecord.CallStatus.valueOf(rs.getString("status"))
        );
    }
}
