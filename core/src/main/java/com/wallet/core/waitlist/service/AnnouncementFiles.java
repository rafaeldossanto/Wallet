package com.wallet.core.waitlist.service;

import lombok.experimental.UtilityClass;

import static org.springframework.util.StringUtils.hasText;

/** The text file of the announcement: the first line is the subject, the rest is the body. */
@UtilityClass
public class AnnouncementFiles {

    private static final char BYTE_ORDER_MARK = '﻿';

    /** @throws IllegalArgumentException when there is no subject or no body: nothing is sent from a half-written file */
    public AnnouncementMessage parse(String fileText) {
        String text = fileText.replace("\r\n", "\n").replace('\r', '\n');
        if (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) {
            text = text.substring(1);
        }
        int lineEnd = text.indexOf('\n');
        String subject = (lineEnd < 0 ? text : text.substring(0, lineEnd)).strip();
        String body = lineEnd < 0 ? "" : text.substring(lineEnd + 1).strip();
        if (!hasText(subject)) {
            throw new IllegalArgumentException("The first line of the announcement file is the subject, and it is empty");
        }
        if (!hasText(body)) {
            throw new IllegalArgumentException("The announcement file has a subject but no body after the first line");
        }
        return new AnnouncementMessage(subject, body);
    }
}
