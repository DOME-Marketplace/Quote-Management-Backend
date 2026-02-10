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

    // Generic status update (deprecated, kept for backward compatibility)
    public static final String BODY_QUOTE_STATUS_UPDATE = "Quote status has been updated by %s\nStatus changed to: %s\n\n" + FOOTER_DO_NOT_REPLY;

    // Status-specific update templates
    public static final String BODY_STATUS_IN_PROGRESS = "Status changed to: %s\r\n\r\nYour quotation request has been updated by %s.\r\n\r\nYour quotation request has been accepted by the provider and it's being evaluated. The provider will send a document proposal as soon as possible.\r\n\r\n" + FOOTER_DO_NOT_REPLY;

    public static final String BODY_STATUS_APPROVED = "Status changed to: %s\r\n\r\nYour quotation request has been updated by %s.\r\n\r\nThe provider has sent you a document proposal that you can evaluate and decide whether to accept or refuse.\r\n\r\n" + FOOTER_DO_NOT_REPLY;

    public static final String BODY_STATUS_ACCEPTED = "Status changed to: %s\r\n\r\nYour quotation request has been updated by %s.\r\n\r\nThe customer has accepted your proposal. Please create a customized offering based on the agreed proposal.\r\n\r\n" + FOOTER_DO_NOT_REPLY;

    public static final String BODY_STATUS_CANCELED = "Status changed to: %s\r\n\r\nYour quotation request has been updated by %s.\r\n\r\nThe quotation request has been canceled. No further action can be performed. You can still send a message in the chat.\r\n\r\n" + FOOTER_DO_NOT_REPLY;

    // --- New Note Added to Quote (generic text note) ---
    public static final String SUBJECT_NEW_NOTE_ADDED = "New Note Added to Quote";

    public static final String BODY_NEW_NOTE_ADDED = "A new note has been added to quote from %s.\r\n\r\nMessage: %s\r\n\r\n" + FOOTER_DO_NOT_REPLY;



    // --- Prefixes used to detect note type from messageContent ---
    public static final String PREFIX_ATTACHMENT_UPLOADED = "Attachment uploaded: ";

    public static final String PREFIX_STATUS_CHANGED_TO = "Status changed to: ";
}
