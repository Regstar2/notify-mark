package com.regstar.obsidiannotification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SourceDisplayNameResolverTest {
    @Test
    public void fallbackNameFromDocumentId_decodesFolderName() {
        assertEquals(
                "Уведомления",
                SourceDisplayNameResolver.fallbackNameFromDocumentId("primary:Уведомления", true)
        );
    }

    @Test
    public void fallbackNameFromDocumentId_returnsLastPathSegment() {
        assertEquals(
                "Tasks.md",
                SourceDisplayNameResolver.fallbackNameFromDocumentId("primary:Vault/Tasks.md", false)
        );
    }

    @Test
    public void fallbackNameFromUri_decodesEncodedSegment() {
        assertEquals(
                "Уведомления",
                SourceDisplayNameResolver.fallbackNameFromRawUri(
                        "content://provider/tree/primary%3A%D0%A3%D0%B2%D0%B5%D0%B4%D0%BE%D0%BC%D0%BB%D0%B5%D0%BD%D0%B8%D1%8F",
                        true
                )
        );
    }

    @Test
    public void sourceCountLabel_usesRussianPluralForms() {
        assertEquals("1 источник", SourceDisplayNameResolver.sourceCountLabel(1));
        assertEquals("2 источника", SourceDisplayNameResolver.sourceCountLabel(2));
        assertEquals("5 источников", SourceDisplayNameResolver.sourceCountLabel(5));
        assertEquals("3 внешних источника", SourceDisplayNameResolver.externalSourceCountLabel(3));
    }

    @Test
    public void markdownFileCountLabel_usesRussianPluralForms() {
        assertEquals("1 markdown-файл", SourceDisplayNameResolver.markdownFileCountLabel(1));
        assertEquals("3 markdown-файла", SourceDisplayNameResolver.markdownFileCountLabel(3));
        assertEquals("12 markdown-файлов", SourceDisplayNameResolver.markdownFileCountLabel(12));
    }
}
