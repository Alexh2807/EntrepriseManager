/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 */
package de.lightplugins.economy.utils;

import de.lightplugins.economy.master.Main;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import org.bukkit.Bukkit;

public class TableStatements {
    public Main plugin;

    public TableStatements(Main plugin) {
        this.plugin = plugin;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void createTableStatement(String statement) {
        Connection connection = null;
        PreparedStatement ps = null;
        try {
            connection = this.plugin.ds.getConnection();
            ps = connection.prepareStatement(statement);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void checkTableUpdate(String rowName, String dataType, String tableName) {
        Connection connection = null;
        Statement psAdd = null;
        try {
            connection = this.plugin.ds.getConnection();
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet result = meta.getColumns(null, null, tableName, rowName);
            if (!result.next()) {
                String statementString = "ALTER TABLE " + tableName + " ADD " + rowName + " " + dataType;
                try {
                    Bukkit.getLogger().log(Level.INFO, "[lightEconomy] FIRST TRY - Add row " + rowName + " into table " + tableName);
                    Statement stmt = connection.createStatement();
                    stmt.executeUpdate(statementString);
                    stmt.close();
                    Bukkit.getLogger().log(Level.INFO, "[lightEconomy] FIRST TRY - Successfully added row " + rowName + " into table " + tableName);
                } catch (SQLException ex) {
                    Bukkit.getLogger().log(Level.WARNING, "[lightEconomy] SECOND TRY - Add row " + rowName + " into table " + tableName);
                    statementString = "ALTER TABLE " + tableName + " ADD COLUMN " + rowName + " " + dataType;
                    Statement stmt = connection.createStatement();
                    stmt.executeUpdate(statementString);
                    stmt.close();
                    Bukkit.getLogger().log(Level.WARNING, "[lightEconomy] SECOND TRY - Successfully added row " + rowName + " into table " + tableName);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (psAdd != null) {
                try {
                    psAdd.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

