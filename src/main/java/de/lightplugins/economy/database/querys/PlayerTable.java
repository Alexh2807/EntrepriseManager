/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package de.lightplugins.economy.database.querys;

import de.lightplugins.economy.master.Main;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerTable {
    public Main plugin;
    private final String tableName = "PlayerData";
    Logger logger = LoggerFactory.getLogger(this.getClass());

    public PlayerTable(Main plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> alreadyTrusted(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            /*
             * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
             * 
             * org.benf.cfr.reader.util.ConfusedCFRException: Started 3 blocks at once
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.getStartingBlocks(Op04StructuredStatement.java:412)
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:487)
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
             *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
             *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:538)
             *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1050)
             *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
             *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
             *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
             *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
             *     at org.benf.cfr.reader.Main.main(Main.java:54)
             *     at async.DecompilerRunnable.cfrDecompilation(DecompilerRunnable.java:348)
             *     at async.DecompilerRunnable.call(DecompilerRunnable.java:309)
             *     at async.DecompilerRunnable.call(DecompilerRunnable.java:31)
             *     at java.util.concurrent.FutureTask.run(FutureTask.java:266)
             *     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
             *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
             *     at java.lang.Thread.run(Thread.java:750)
             */
            throw new IllegalStateException("Decompilation failed");
        });
    }

    public CompletableFuture<List<String>> getTrustedBanks(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            /*
             * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
             * 
             * org.benf.cfr.reader.util.ConfusedCFRException: Started 3 blocks at once
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.getStartingBlocks(Op04StructuredStatement.java:412)
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:487)
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
             *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
             *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:538)
             *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1050)
             *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
             *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
             *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
             *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
             *     at org.benf.cfr.reader.Main.main(Main.java:54)
             *     at async.DecompilerRunnable.cfrDecompilation(DecompilerRunnable.java:348)
             *     at async.DecompilerRunnable.call(DecompilerRunnable.java:309)
             *     at async.DecompilerRunnable.call(DecompilerRunnable.java:31)
             *     at java.util.concurrent.FutureTask.run(FutureTask.java:266)
             *     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
             *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
             *     at java.lang.Thread.run(Thread.java:750)
             */
            throw new IllegalStateException("Decompilation failed");
        });
    }

    public CompletableFuture<List<String>> getOwnTruster(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            /*
             * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
             * 
             * org.benf.cfr.reader.util.ConfusedCFRException: Started 3 blocks at once
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.getStartingBlocks(Op04StructuredStatement.java:412)
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:487)
             *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
             *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
             *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
             *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:538)
             *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1050)
             *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
             *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
             *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
             *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
             *     at org.benf.cfr.reader.Main.main(Main.java:54)
             *     at async.DecompilerRunnable.cfrDecompilation(DecompilerRunnable.java:348)
             *     at async.DecompilerRunnable.call(DecompilerRunnable.java:309)
             *     at async.DecompilerRunnable.call(DecompilerRunnable.java:31)
             *     at java.util.concurrent.FutureTask.run(FutureTask.java:266)
             *     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
             *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
             *     at java.lang.Thread.run(Thread.java:750)
             */
            throw new IllegalStateException("Decompilation failed");
        });
    }

    public CompletableFuture<Boolean> addTrustedPlayerTo(String uuid, String targetBankAccountUserUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = this.plugin.ds.getConnection();){
                Boolean bl;
                block14: {
                    PreparedStatement ps = connection.prepareStatement("INSERT INTO PlayerData (uuid, trustedBank) VALUES (?, ?)");
                    try {
                        ps.setString(1, uuid);
                        ps.setString(2, targetBankAccountUserUUID);
                        ps.execute();
                        bl = true;
                        if (ps == null) break block14;
                    } catch (Throwable throwable) {
                        if (ps != null) {
                            try {
                                ps.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    }
                    ps.close();
                }
                return bl;
            } catch (SQLException e) {
                this.logError("An error occurred while executing the SQL query.", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> removeTrustedPlayer(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = this.plugin.ds.getConnection();){
                Boolean bl;
                block14: {
                    PreparedStatement ps = connection.prepareStatement("DELETE FROM PlayerData WHERE uuid = ?");
                    try {
                        ps.setString(1, uuid);
                        int rowsAffected = ps.executeUpdate();
                        bl = rowsAffected > 0;
                        if (ps == null) break block14;
                    } catch (Throwable throwable) {
                        if (ps != null) {
                            try {
                                ps.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    }
                    ps.close();
                }
                return bl;
            } catch (SQLException e) {
                this.logError("An error occurred while executing the SQL query.", e);
                return false;
            }
        });
    }

    private void logError(String message, Throwable e) {
        Logger logger = LoggerFactory.getLogger(this.getClass());
        logger.error(message, e);
    }
}

