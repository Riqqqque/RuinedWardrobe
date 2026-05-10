package dev.rique.ruinedwardrobe.lang;

import dev.rique.ruinedwardrobe.config.MessageFormatMode;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @Test
    void minimessagePlaceholdersAreInsertedAsPlainText() {
        LanguageRegistry languageRegistry = mock(LanguageRegistry.class);
        when(languageRegistry.get("test.message")).thenReturn("<green>Hello {name}</green>");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pluginManager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            when(pluginManager.isPluginEnabled("PlaceholderAPI")).thenReturn(false);

            MessageService service = new MessageService(languageRegistry, MessageFormatMode.BOTH, false);
            String output = PlainTextComponentSerializer.plainText().serialize(service.component(
                    null,
                    "test.message",
                    Map.of("name", "<click:run_command:/op Rique>Rique</click>")));

            assertEquals("Hello <click:run_command:/op Rique>Rique</click>", output);
        }
    }
}
