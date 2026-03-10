package dev.rique.prismwardrobe.storage;

import dev.rique.prismwardrobe.config.DatabaseType;
import dev.rique.prismwardrobe.config.MessageFormatMode;
import dev.rique.prismwardrobe.config.PluginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseManagerTest {

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

    private DatabaseManager createManager(Path tempDir, int queueCapacity, int workerThreads) {
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
                        "prismwardrobe",
                        "root",
                        "",
                        "useUnicode=true&characterEncoding=utf8&useSSL=false",
                        4,
                        1,
                        5000L),
                new PluginConfig.CacheSettings(1000L, 60L),
                new PluginConfig.SyncSettings(5L, 50),
                PluginConfig.RestrictionSettings.defaultSettings(),
                new PluginConfig.MetricsSettings(false, 0),
                new PluginConfig.IntegrationSettings(false, false, false),
                new PluginConfig.GuiBehaviorSettings(true, true, false, false, 0),
                new PluginConfig.AntiDupeSettings(false),
                new PluginConfig.DbExecutionSettings(queueCapacity, 0, 10L, workerThreads),
                120L
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
