package com.dome.quotemanagement.util;

public final class EmailConstants {

    private EmailConstants() {}

    // --- Footer used in all email bodies ---
    public static final String FOOTER_DO_NOT_REPLY = "THIS IS AN AUTOMATED NOTIFICATION. Please do not reply to this email!";

    // --- New Quote Created ---
    public static final String SUBJECT_NEW_QUOTE_CREATED = "New Quote Request";

    public static final String BODY_NEW_QUOTE_CREATED = "You have received a new quotation request for the product %s from %s.<br><br>Message: %s<br><br>" + FOOTER_DO_NOT_REPLY;

    // --- New Attachment Uploaded to Quote ---
    public static final String SUBJECT_NEW_ATTACHMENT_UPLOADED = "New Document Uploaded";

    public static final String BODY_NEW_ATTACHMENT_UPLOADED = "A new document %s has been uploaded to your quote by %s.<br><br>" + FOOTER_DO_NOT_REPLY;

    // --- Quote Status Update ---
    public static final String SUBJECT_QUOTE_STATUS_UPDATE = "Quote Status Update";

    // DEPRECATED Generic status update
    public static final String BODY_QUOTE_STATUS_UPDATE = "Quote status has been updated by %s.<br>Status changed to: %s<br><br>" + FOOTER_DO_NOT_REPLY;

    // Status-specific update templates
    public static final String BODY_STATUS_IN_PROGRESS = "Status changed to: %s<br><br>Your quotation request has been updated by %s.<br><br>Your quotation request has been accepted by the provider and it's being evaluated. The provider will send a document proposal as soon as possible.<br><br>" + FOOTER_DO_NOT_REPLY;

    public static final String BODY_STATUS_APPROVED = "Status changed to: %s<br><br>Your quotation request has been updated by %s.<br><br>The provider has sent you a document proposal that you can evaluate and decide whether to accept or refuse.<br><br>" + FOOTER_DO_NOT_REPLY;

    public static final String BODY_STATUS_ACCEPTED = "Status changed to: %s<br><br>Your quotation request has been updated by %s.<br><br>The customer has accepted your proposal. Please create a customized offering based on the agreed proposal.<br><br>" + FOOTER_DO_NOT_REPLY;

    public static final String BODY_STATUS_CANCELED = "Status changed to: %s<br><br>Your quotation request has been updated by %s.<br><br>The quotation request has been canceled. No further action can be performed. You can still send a message in the chat.<br><br>" + FOOTER_DO_NOT_REPLY;

    // --- New Note Added to Quote (generic text note) ---
    public static final String SUBJECT_NEW_NOTE_ADDED = "You Have a New Message";

    public static final String BODY_NEW_NOTE_ADDED = "You received a new message related to quotation request from %s.<br><br>Message: %s<br><br>" + FOOTER_DO_NOT_REPLY;



    // --- Prefixes used to detect note type from messageContent ---
    public static final String PREFIX_ATTACHMENT_UPLOADED = "Attachment uploaded: ";

    public static final String PREFIX_STATUS_CHANGED_TO = "Status changed to: ";
}
