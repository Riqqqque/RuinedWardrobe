package dev.rique.ruinedwardrobe.storage;

import dev.rique.ruinedwardrobe.config.DatabaseType;
import dev.rique.ruinedwardrobe.config.MessageFormatMode;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLTransientException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseManagerTest {

    @Test
    void mariaDbDriverLoadsWithoutOptionalWindowsNativeAuthenticationLibraries() {
        assertDoesNotThrow(() -> Class.forName("org.mariadb.jdbc.Driver"));
    }

    @Test
    void runWriteReturnsFailedFutureWhenQueueIsFull(@TempDir Path tempDir) throws Exception {
        DatabaseManager manager = createManager(tempDir, 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            CompletableFuture<Integer> first = manager.runWrite(connection -> {
                started.countDown();
                await(release);
                return 1;
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));

            CompletableFuture<Integer> second = manager.runWrite(connection -> {
                await(release);
                return 2;
            });

            CompletableFuture<Integer> rejected = assertDoesNotThrow(() -> manager.runWrite(connection -> 3));
            CompletionException exception = assertThrows(CompletionException.class, rejected::join);
            assertInstanceOf(DatabaseManager.DatabaseException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("queue is full"));

            release.countDown();
            assertEquals(1, first.join());
            assertEquals(2, second.join());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void shutdownDrainsRunningAndQueuedWork(@TempDir Path tempDir) throws Exception {
        DatabaseManager manager = createManager(tempDir, 2, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondExecuted = new AtomicBoolean(false);

        CompletableFuture<Integer> first = manager.runWrite(connection -> {
            started.countDown();
            await(release);
            return 1;
        });
        CompletableFuture<Integer> second = manager.runWrite(connection -> {
            secondExecuted.set(true);
            return 2;
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            release.countDown();
        }, "database-manager-test-release");
        releaser.setDaemon(true);
        releaser.start();

        long startedAt = System.nanoTime();
        manager.shutdown();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertEquals(1, first.join());
        assertEquals(2, second.join());
        assertTrue(secondExecuted.get());
        assertTrue(elapsedMs >= 150L);
    }

    @Test
    void sqlitePathCannotEscapePluginDataFolder(@TempDir Path tempDir) {
        DatabaseManager.DatabaseException exception = assertThrows(
                DatabaseManager.DatabaseException.class,
                () -> DatabaseManager.resolveSqliteDatabaseFile(tempDir.toFile(), "../outside.db"));

        assertTrue(exception.getMessage().contains("must stay inside"));
    }

    @Test
    void deterministicWriteFailureIsNotRetried(@TempDir Path tempDir) {
        DatabaseManager manager = createManager(tempDir, 4, 1, 3);
        AtomicInteger attempts = new AtomicInteger();

        try {
            CompletableFuture<Void> failed = manager.runWrite(connection -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("invalid payload");
            });

            assertThrows(CompletionException.class, failed::join);
            assertEquals(1, attempts.get());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void transientWriteFailureUsesConfiguredRetries(@TempDir Path tempDir) {
        DatabaseManager manager = createManager(tempDir, 4, 1, 2);
        AtomicInteger attempts = new AtomicInteger();

        try {
            int result = manager.runWrite(connection -> {
                if (attempts.getAndIncrement() < 2) {
                    throw new RuntimeException(new SQLTransientException("temporary"));
                }
                return 7;
            }).join();

            assertEquals(7, result);
            assertEquals(3, attempts.get());
        } finally {
            manager.shutdown();
        }
    }

    private DatabaseManager createManager(Path tempDir, int queueCapacity, int workerThreads) {
        return createManager(tempDir, queueCapacity, workerThreads, 0);
    }

    private DatabaseManager createManager(Path tempDir, int queueCapacity, int workerThreads, int writeRetries) {
        PluginConfig pluginConfig = new PluginConfig(
                3,
                54,
                2,
                0L,
                false,
                "en_US",
                MessageFormatMode.BOTH,
                new PluginConfig.StorageSettings(
                        DatabaseType.SQLITE,
                        "data/wardrobe.db",
                        "127.0.0.1",
                        3306,
                        "ruinedwardrobe",
                        "root",
                        "",
                        "useUnicode=true&characterEncoding=utf8&useSSL=false",
                        4,
                        1,
                        5000L),
                new PluginConfig.CacheSettings(1000L, 60L),
                new PluginConfig.SyncSettings(5L, 50),
                new PluginConfig.SessionSettings(false, false),
                new PluginConfig.ArmorSyncSettings(true, 40L, 500, 10L),
                new PluginConfig.DeathSettings(false),
                PluginConfig.RestrictionSettings.defaultSettings(),
                new PluginConfig.MetricsSettings(false),
                new PluginConfig.IntegrationSettings(false, false, false),
                new PluginConfig.GuiBehaviorSettings(true, true, false, false, 0),
                new PluginConfig.AntiDupeSettings(false),
                new PluginConfig.DbExecutionSettings(queueCapacity, writeRetries, 10L, workerThreads),
                new PluginConfig.HealthSettings(true, 120L),
                new PluginConfig.AuditSettings(false, "logs", 4096, false, true, true)
        );

        DatabaseManager manager = new DatabaseManager(pluginConfig);
        manager.initialize(tempDir.toFile());
        return manager;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }
}
