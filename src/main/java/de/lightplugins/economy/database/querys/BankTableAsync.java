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
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankTableAsync {
    public Main plugin;
    private final String tableName = "BankTable";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Lock playerBalanceLock = new ReentrantLock();
    private final Lock playerCurrentBankLevelLock = new ReentrantLock();
    private final Lock createBankAccountLock = new ReentrantLock();
    private final Lock updatePlayerNameLock = new ReentrantLock();
    private final Lock setBankMoney = new ReentrantLock();
    private final Lock getPlayersBalanceListLock = new ReentrantLock();
    private final Lock setBankLevelLock = new ReentrantLock();

    public BankTableAsync(Main plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Double> playerBankBalance(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.playerBalanceLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareBankBalanceQuery(playerName, connection);
                    ResultSet rs = ps.executeQuery();

                    Double balance = null;
                    if (rs.next()) {
                        balance = rs.getDouble("money");
                    }

                    rs.close();
                    ps.close();
                    connection.close();

                    this.logInfo("Retrieved bank balance for " + playerName + ": " + balance);
                    return balance;
                } catch (SQLException e) {
                    this.logError("Failed to get bank balance for " + playerName, e);
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

    public CompletableFuture<Integer> playerCurrentBankLevel(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.playerCurrentBankLevelLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareBankLevelQuery(playerName, connection);
                    ResultSet rs = ps.executeQuery();

                    int level = 1;
                    if (rs.next()) {
                        level = rs.getInt("level");
                    }

                    rs.close();
                    ps.close();
                    connection.close();

                    this.logInfo("Retrieved bank level for " + playerName + ": " + level);
                    return level;
                } catch (SQLException e) {
                    this.logError("Failed to get bank level for " + playerName, e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return 1;
                }
            } catch (SQLException e) {
                this.logError("Failed to get connection", e);
                return 1;
            } finally {
                this.playerCurrentBankLevelLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> createBankAccount(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.createBankAccountLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareNewBankAccountInsert(playerName, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Created bank account for " + playerName + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to create bank account for " + playerName, e);
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
                this.createBankAccountLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> updatePlayerBankName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            this.updatePlayerNameLock.lock();
            try {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareBankAccountNameUpdate(offlinePlayer, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Updated bank name for " + playerName + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to update bank name for " + playerName, e);
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

    public CompletableFuture<Boolean> setBankLevel(String playerName, int level) {
        return CompletableFuture.supplyAsync(() -> {
            this.setBankLevelLock.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareBankLevelUpdate(playerName, level, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Set bank level for " + playerName + " to " + level + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to set bank level for " + playerName, e);
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
                this.setBankLevelLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> setBankMoney(String playerName, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            this.setBankMoney.lock();
            try {
                Connection connection = this.plugin.ds.getConnection();
                try {
                    PreparedStatement ps = this.prepareBankBalanceUpdate(playerName, amount, connection);
                    int result = ps.executeUpdate();

                    ps.close();
                    connection.close();

                    this.logInfo("Set bank balance for " + playerName + " to " + amount + ": " + (result > 0));
                    return result > 0;
                } catch (SQLException e) {
                    this.logError("Failed to set bank balance for " + playerName, e);
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
                this.setBankMoney.unlock();
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

                    this.logInfo("Retrieved bank balances list: " + balances.size() + " players");
                    return balances;
                } catch (SQLException e) {
                    this.logError("Failed to get bank balances list", e);
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

    private PreparedStatement prepareBankBalanceQuery(String playerName, Connection connection) throws SQLException {
        PreparedStatement ps;
        Player offlinePlayer = Bukkit.getPlayer(playerName);
        if (offlinePlayer != null) {
            ps = connection.prepareStatement("SELECT * FROM BankTable WHERE uuid = ?");
            ps.setString(1, offlinePlayer.getUniqueId().toString());
        } else {
            ps = connection.prepareStatement("SELECT * FROM BankTable WHERE name = ?");
            ps.setString(1, playerName);
        }
        return ps;
    }

    private PreparedStatement prepareBankLevelQuery(String playerName, Connection connection) throws SQLException {
        PreparedStatement ps;
        Player offlinePlayer = Bukkit.getPlayer(playerName);
        if (offlinePlayer != null) {
            ps = connection.prepareStatement("SELECT * FROM BankTable WHERE uuid = ?");
            ps.setString(1, offlinePlayer.getUniqueId().toString());
        } else {
            ps = connection.prepareStatement("SELECT * FROM BankTable WHERE name = ?");
            ps.setString(1, playerName);
        }
        return ps;
    }

    private PreparedStatement prepareNewBankAccountInsert(String playerName, Connection connection) throws SQLException {
        Player offlinePlayer = Bukkit.getPlayer(playerName);
        PreparedStatement ps = connection.prepareStatement("INSERT INTO BankTable (uuid, name, money, level) VALUES (?, ?, ?, ?)");
        if (offlinePlayer != null) {
            ps.setString(1, offlinePlayer.getUniqueId().toString());
        } else {
            UUID uuid = UUID.randomUUID();
            ps.setString(1, uuid.toString());
        }
        ps.setString(2, playerName);
        ps.setDouble(3, 0.0);
        ps.setInt(4, 1);
        return ps;
    }

    private PreparedStatement prepareBankAccountNameUpdate(OfflinePlayer offlinePlayer, Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE BankTable SET name = ? WHERE uuid = ?");
        ps.setString(1, offlinePlayer.getName());
        ps.setString(2, offlinePlayer.getUniqueId().toString());
        return ps;
    }

    private PreparedStatement preparePlayersBalanceListQuery(Connection connection) throws SQLException {
        return connection.prepareStatement("SELECT * FROM BankTable");
    }

    private HashMap<String, Double> extractPlayerBalances(ResultSet rs) throws SQLException {
        HashMap<String, Double> playerList = new HashMap<String, Double>();
        while (rs.next()) {
            playerList.put(rs.getString("name"), rs.getDouble("money"));
        }
        return playerList;
    }

    private PreparedStatement prepareBankLevelUpdate(String playerName, int level, Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE BankTable SET level = ? WHERE name = ?");
        ps.setInt(1, level);
        ps.setString(2, playerName);
        return ps;
    }

    private PreparedStatement prepareBankBalanceUpdate(String playerName, double amount, Connection connection) throws SQLException {
        PreparedStatement ps;
        Player offlinePlayer = Bukkit.getPlayer(playerName);
        if (offlinePlayer != null) {
            ps = connection.prepareStatement("UPDATE BankTable SET money = ? WHERE uuid = ?");
            ps.setString(2, offlinePlayer.getUniqueId().toString());
        } else {
            ps = connection.prepareStatement("UPDATE BankTable SET money = ? WHERE name = ?");
            ps.setString(2, playerName);
        }
        ps.setDouble(1, amount);
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
