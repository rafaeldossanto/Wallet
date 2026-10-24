package com.wallet.core.waitlist.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnouncementFilesTest {

    @Test
    void theFirstLineIsTheSubjectAndTheRestIsTheBody() {
        AnnouncementMessage message = AnnouncementFiles.parse("O Wallet abriu\n\nOlá!\nJá dá para entrar.\n");

        assertThat(message.subject()).isEqualTo("O Wallet abriu");
        assertThat(message.body()).isEqualTo("Olá!\nJá dá para entrar.");
    }

    /** A file saved on Windows, with a byte order mark and CRLF line ends. */
    @Test
    void aWindowsFileReadsTheSame() {
        AnnouncementMessage message = AnnouncementFiles.parse("﻿O Wallet abriu\r\n\r\nOlá!\r\nJá dá para entrar.\r\n");

        assertThat(message.subject()).isEqualTo("O Wallet abriu");
        assertThat(message.body()).isEqualTo("Olá!\nJá dá para entrar.");
    }

    @Test
    void aFileWithoutSubjectOrBodyIsRefused() {
        assertThatThrownBy(() -> AnnouncementFiles.parse("")).hasMessageContaining("subject");
        assertThatThrownBy(() -> AnnouncementFiles.parse("  \nCorpo")).hasMessageContaining("subject");
        assertThatThrownBy(() -> AnnouncementFiles.parse("Assunto\n\n  \n")).hasMessageContaining("body");
    }
}
