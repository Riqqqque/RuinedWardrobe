package dev.rique.prismwardrobe.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rique.prismwardrobe.config.DatabaseType;
import dev.rique.prismwardrobe.config.PluginConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class DatabaseManager {

    private final PluginConfig pluginConfig;
    private HikariDataSource dataSource;
    private ThreadPoolExecutor executor;
    private final AtomicLong totalLatencyNs = new AtomicLong();
    private final AtomicLong latencySamples = new AtomicLong();

    public DatabaseManager(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public void initialize(File dataFolder) {
        PluginConfig.StorageSettings storage = pluginConfig.storageSettings();
        HikariConfig hikariConfig = new HikariConfig();

        if (storage.type() == DatabaseType.SQLITE) {
            File databaseFile = new File(dataFolder, storage.sqliteFile());
            File parent = databaseFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(Math.min(4, storage.maxPoolSize()));
            hikariConfig.setMinimumIdle(1);
            // Hikari init SQL must be a single statement; run WAL mode separately after pool init.
            hikariConfig.setConnectionInitSql("PRAGMA foreign_keys=ON");
        } else {
            String jdbcUrl = "jdbc:mariadb://" + storage.mysqlHost() + ":" + storage.mysqlPort() + "/" + storage.mysqlDatabase() + "?" + storage.mysqlParams();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(storage.mysqlUsername());
            hikariConfig.setPassword(storage.mysqlPassword());
            hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
            hikariConfig.setMaximumPoolSize(storage.maxPoolSize());
            hikariConfig.setMinimumIdle(storage.minIdle());
        }

        hikariConfig.setConnectionTimeout(storage.connectionTimeoutMs());
        hikariConfig.setValidationTimeout(5000);
        hikariConfig.setLeakDetectionThreshold(Duration.ofSeconds(15).toMillis());
        hikariConfig.setPoolName("PrismWardrobePool");
        dataSource = new HikariDataSource(hikariConfig);
        if (storage.type() == DatabaseType.SQLITE) {
            configureSqlitePragmas();
        }

        PluginConfig.DbExecutionSettings executionSettings = pluginConfig.dbExecutionSettings();
        executor = new ThreadPoolExecutor(
                executionSettings.workerThreads(),
                executionSettings.workerThreads(),
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(executionSettings.writeQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "PrismWardrobe-DB");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void configureSqlitePragmas() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to configure SQLite PRAGMA settings", ex);
        }
    }

    public <T> CompletableFuture<T> runQuery(Function<Connection, T> function) {
        return submit(function, 0);
    }

    public <T> CompletableFuture<T> runWrite(Function<Connection, T> function) {
        return submit(function, pluginConfig.dbExecutionSettings().writeRetries());
    }

    private <T> CompletableFuture<T> submit(Function<Connection, T> function, int retries) {
        return CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            while (true) {
                long startNs = System.nanoTime();
                try (Connection connection = dataSource.getConnection()) {
                    T result = function.apply(connection);
                    recordLatency(startNs);
                    return result;
                } catch (Exception ex) {
                    recordLatency(startNs);
                    if (attempt >= retries) {
                        throw new DatabaseException("Database operation failed after retries", ex);
                    }
                    attempt++;
                    sleep(pluginConfig.dbExecutionSettings().retryDelayMs());
                }
            }
        }, executor);
    }

    private void recordLatency(long startNs) {
        totalLatencyNs.addAndGet(System.nanoTime() - startNs);
        latencySamples.incrementAndGet();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int queueDepth() {
        return executor == null ? 0 : executor.getQueue().size();
    }

    public double averageLatencyMs() {
        long samples = latencySamples.get();
        if (samples == 0) {
            return 0.0;
        }
        return (totalLatencyNs.get() / 1_000_000.0) / samples;
    }

    public boolean isReady() {
        return dataSource != null;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    public int poolActiveConnections() {
        if (dataSource == null || dataSource.getHikariPoolMXBean() == null) {
            return 0;
        }
        return dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public static final class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
