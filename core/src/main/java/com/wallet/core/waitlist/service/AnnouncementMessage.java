package com.wallet.core.waitlist.service;

/** The opening announcement as the operator wrote it: sent as is to everyone on the list. */
public record AnnouncementMessage(String subject, String body) {
}
