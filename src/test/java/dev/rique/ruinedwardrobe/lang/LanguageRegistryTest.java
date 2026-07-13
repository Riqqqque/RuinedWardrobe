package dev.rique.ruinedwardrobe.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageRegistryTest {

    @Test
    void localeNamesCannotEscapeLanguageDirectory() {
        assertEquals("en_US", LanguageRegistry.sanitizeLocale("../../server"));
        assertEquals("en_US", LanguageRegistry.sanitizeLocale("C:\\private\\lang"));
        assertEquals("pt_BR", LanguageRegistry.sanitizeLocale("pt_BR"));
    }
}
