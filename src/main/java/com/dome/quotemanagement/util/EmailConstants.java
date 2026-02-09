package com.dome.quotemanagement.util;

public final class EmailConstants {

    private EmailConstants() {}

    // --- Footer used in all email bodies ---
    public static final String FOOTER_DO_NOT_REPLY = "Please do not reply to this email.";

    // --- New Quote Created ---
    public static final String SUBJECT_NEW_QUOTE_CREATED = "New Quote Requested";

    public static final String BODY_NEW_QUOTE_CREATED = "New quote requested for product %s from %s\n\nMessage: %s\n\n" + FOOTER_DO_NOT_REPLY;

    // --- New Attachment Uploaded to Quote ---
    public static final String SUBJECT_NEW_ATTACHMENT_UPLOADED = "New Document Uploaded";

    public static final String BODY_NEW_ATTACHMENT_UPLOADED = "A new attachment %s has been uploaded to your quote by %s\n\n" + FOOTER_DO_NOT_REPLY;

    // --- Quote Status Update ---
    public static final String SUBJECT_QUOTE_STATUS_UPDATE = "Quote Status Update";

    public static final String BODY_QUOTE_STATUS_UPDATE = "Quote status has been updated by %s\nStatus changed to: %s\n\n" + FOOTER_DO_NOT_REPLY;

    // --- New Note Added to Quote (generic text note) ---
    public static final String SUBJECT_NEW_NOTE_ADDED = "New Note Added to Quote";

    public static final String BODY_NEW_NOTE_ADDED = "A new note has been added to quote from %s\n\nMessage: %s\n\n" + FOOTER_DO_NOT_REPLY;



    // --- Prefixes used to detect note type from messageContent ---
    public static final String PREFIX_ATTACHMENT_UPLOADED = "Attachment uploaded: ";

    public static final String PREFIX_STATUS_CHANGED_TO = "Status changed to: ";
}
