package de.lightplugins.economy.database.querys;

import de.lightplugins.economy.master.Main;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoneyTableAsync {
    public Main plugin;
    private final String tableName = "MoneyTable";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Lock playerBalanceLock = new ReentrantLock();
    private final Lock getPlayersBalanceListLock = new ReentrantLock();
    private final Lock createNewPlayerLock = new ReentrantLock();
    private final Lock updatePlayerNameLock = new ReentrantLock();
    private final Lock setMoneyLock = new ReentrantLock();
    private final Lock deleteAccountLock = new ReentrantLock();
    private final Lock isPlayerAccountLock = new ReentrantLock();

    public MoneyTableAsync(Main plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Double> playerBalance(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.playerBalanceLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.preparePlayerBalanceQuery(playerName, connection);
                    ResultSet rs = ps.executeQuery();

                    Double balance = null;
                    if (rs.next()) {
                        balance = rs.getDouble("money");
                    }

                    rs.close();
                    ps.close();
                    connection.close();

                    this.logInfo("Retrieved balance for " + playerName + ": " + balance);
                    return balance;
                } catch (SQLException e) {
                    this.logError("Failed to get balance for " + playerName, e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return null;
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return null;
            } finally {
                this.playerBalanceLock.unlock();
            }
        });
    }

    public CompletableFuture<HashMap<String, Double>> getPlayersBalanceList() {
        return CompletableFuture.supplyAsync(() -> {
            this.getPlayersBalanceListLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.preparePlayersBalanceListQuery(connection);
                    ResultSet rs = ps.executeQuery();

                    HashMap<String, Double> balances = this.extractPlayerBalances(rs);

                    rs.close();
                    ps.close();
                    connection.close();

                    this.logInfo("Retrieved balances list: " + balances.size() + " players");
                    return balances;
                } catch (SQLException e) {
                    this.logError("Failed to get balances list", e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return new HashMap<String, Double>();
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return new HashMap<String, Double>();
            } finally {
                this.getPlayersBalanceListLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> createNewPlayer(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.createNewPlayerLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareNewPlayerInsert(playerName, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Created player account for " + playerName + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to create player account for " + playerName, e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return false;
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return false;
            } finally {
                this.createNewPlayerLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> updatePlayerName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.updatePlayerNameLock.lock();
            try {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.preparePlayerNameUpdate(offlinePlayer, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Updated player name for " + playerName + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to update player name for " + playerName, e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return false;
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return false;
            } finally {
                this.updatePlayerNameLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> setMoney(String playerName, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            this.setMoneyLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.preparePlayerBalanceUpdate(playerName, amount, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Set balance for " + playerName + " to " + amount + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to set balance for " + playerName, e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return false;
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return false;
            } finally {
                this.setMoneyLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> deleteAccount(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.deleteAccountLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareDeleteAccount(playerName, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Deleted account for " + playerName + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to delete account for " + playerName, e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return false;
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return false;
            } finally {
                this.deleteAccountLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> isPlayerAccount(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.isPlayerAccountLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareIsPlayerAccount(playerName, connection);
                    ResultSet rs = ps.executeQuery();

                    boolean isPlayer = rs.next();

                    rs.close();
                    ps.close();
                    connection.close();

                    this.logInfo("Checked if " + playerName + " is player account: " + isPlayer);
                    return isPlayer;
                } catch (SQLException e) {
                    this.logError("Failed to check if " + playerName + " is player account", e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return false;
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return false;
            } finally {
                this.isPlayerAccountLock.unlock();
            }
        });
    }

    private PreparedStatement preparePlayerBalanceQuery(String playerName, Connection connection) throws SQLException {
        Player offlinePlayer = Bukkit.getPlayer(playerName);
        if (offlinePlayer != null) {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM MoneyTable WHERE uuid = ?");
            ps.setString(1, offlinePlayer.getUniqueId().toString());
            return ps;
        }
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM MoneyTable WHERE name = ?");
        ps.setString(1, playerName);
        return ps;
    }

    private PreparedStatement preparePlayersBalanceListQuery(Connection connection) throws SQLException {
        return connection.prepareStatement("SELECT * FROM MoneyTable WHERE isPlayer = '1'");
    }

    private HashMap<String, Double> extractPlayerBalances(ResultSet rs) throws SQLException {
        HashMap<String, Double> playerList = new HashMap<String, Double>();
        while (rs.next()) {
            playerList.put(rs.getString("name"), rs.getDouble("money"));
        }
        return playerList;
    }

    private PreparedStatement prepareNewPlayerInsert(String playerName, Connection connection) throws SQLException {
        Player offlinePlayer = Bukkit.getPlayer(playerName);
        FileConfiguration settings = Main.settings.getConfig();
        double startBalance = settings.getDouble("settings.start-balance");
        PreparedStatement ps = connection.prepareStatement("INSERT INTO MoneyTable (uuid, name, money, isPlayer) VALUES (?, ?, ?, ?)");
        if (offlinePlayer != null) {
            ps.setString(1, offlinePlayer.getUniqueId().toString());
            ps.setBoolean(4, true);
        } else {
            UUID uuid = UUID.randomUUID();
            ps.setString(1, uuid.toString());
            ps.setBoolean(4, false);
            startBalance = 0.0;
        }
        ps.setString(2, playerName);
        ps.setDouble(3, startBalance);
        return ps;
    }

    private PreparedStatement preparePlayerNameUpdate(OfflinePlayer offlinePlayer, Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE MoneyTable SET name = ? WHERE uuid = ?");
        ps.setString(1, offlinePlayer.getName());
        ps.setString(2, offlinePlayer.getUniqueId().toString());
        return ps;
    }

    private PreparedStatement preparePlayerBalanceUpdate(String playerName, double amount, Connection connection) throws SQLException {
        PreparedStatement ps;
        Player offlinePlayer = Bukkit.getPlayer(playerName);
        if (offlinePlayer != null) {
            ps = connection.prepareStatement("UPDATE MoneyTable SET money = ? WHERE uuid = ?");
            ps.setString(2, offlinePlayer.getUniqueId().toString());
        } else {
            ps = connection.prepareStatement("UPDATE MoneyTable SET money = ? WHERE name = ?");
            ps.setString(2, playerName);
        }
        ps.setDouble(1, amount);
        return ps;
    }

    private PreparedStatement prepareDeleteAccount(String playerName, Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM MoneyTable WHERE name = ?");
        ps.setString(1, playerName);
        return ps;
    }

    private PreparedStatement prepareIsPlayerAccount(String playerName, Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM MoneyTable WHERE isPlayer = '1' AND name =?");
        ps.setString(1, playerName);
        return ps;
    }

    private void logError(String message, Throwable e) {
        this.logger.error(message, e);
    }

    private void logInfo(String message) {
        if (Main.settings.getConfig().getBoolean("settings.debug")) {
            this.logger.info(message);
        }
    }
}
