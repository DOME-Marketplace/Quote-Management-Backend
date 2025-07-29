package com.dome.quotemanagement.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for formatting log messages, especially JSON content
 * Uses ASCII-safe characters for maximum compatibility
 */
@Slf4j
public class LogFormatUtil {
    
    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    private static final ObjectMapper COMPACT_MAPPER = new ObjectMapper();
    
    /**
     * Formats JSON string with pretty indentation for better readability
     * Falls back to original string if parsing fails
     */
    public static String prettifyJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return jsonString;
        }
        
        try {
            JsonNode jsonNode = COMPACT_MAPPER.readTree(jsonString);
            return PRETTY_MAPPER.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            // If it's not valid JSON, return original string
            return jsonString;
        }
    }
    
    /**
     * Formats JSON with controlled length for logs
     * - For short JSON (< maxLength): pretty print
     * - For long JSON: show truncated message with size info
     */
    public static String formatJsonForLog(String jsonString, int maxLength) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return jsonString;
        }
        
        if (jsonString.length() <= maxLength) {
            return prettifyJson(jsonString);
        } else {
            return String.format(
                "[LARGE_JSON_TRUNCATED] Size: %d characters\n%s...\n--- Use DEBUG level for full content ---",
                jsonString.length(),
                jsonString.substring(0, Math.min(maxLength, jsonString.length()))
            );
        }
    }
    
    /**
     * Creates a nicely formatted log section header (ASCII-safe)
     */
    public static String createLogSection(String title) {
        int titleLength = title.length();
        int totalWidth = Math.max(50, titleLength + 6);
        int padding = (totalWidth - titleLength - 2) / 2;
        
        StringBuilder header = new StringBuilder();
        header.append("=".repeat(totalWidth)).append("\n");
        header.append("=").append(" ".repeat(padding)).append(title).append(" ".repeat(padding));
        if (header.length() < totalWidth) {
            header.append(" ");
        }
        header.append("=");
        
        return header.toString();
    }
    
    /**
     * Creates a log section footer (ASCII-safe)
     */
    public static String createLogSectionEnd() {
        return "=" + "=".repeat(48) + "=";
    }
    
    /**
     * Formats HTTP method and URL for better visibility (ASCII-safe)
     */
    public static String formatHttpRequest(String method, String url) {
        return String.format("[REQ] %s %s", method, url);
    }
    
    /**
     * Formats HTTP response status for better visibility (ASCII-safe)
     */
    public static String formatHttpResponse(String method, String url, int status, long duration) {
        String statusIcon = getStatusIcon(status);
        return String.format("%s %s %s - %d (%dms)", statusIcon, method, url, status, duration);
    }
    
    /**
     * Gets appropriate ASCII indicator for HTTP status code
     */
    private static String getStatusIcon(int status) {
        if (status >= 200 && status < 300) return "[OK]";   // Success
        if (status >= 300 && status < 400) return "[REDIR]"; // Redirect
        if (status >= 400 && status < 500) return "[WARN]";  // Client Error
        if (status >= 500) return "[ERROR]"; // Server Error
        return "[INFO]"; // Info
    }
    
    /**
     * Formats duration with appropriate units
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        } else if (milliseconds < 60000) {
            return String.format("%.2fs", milliseconds / 1000.0);
        } else {
            long minutes = milliseconds / 60000;
            long seconds = (milliseconds % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    /**
     * Masks sensitive data in strings (for PII protection)
     */
    public static String maskSensitiveData(String data, String fieldName) {
        if (data == null) return null;
        
        // Common sensitive field patterns
        if (isContentField(fieldName)) {
            return "[CONTENT_MASKED]";
        }
        
        if (isSensitiveField(fieldName)) {
            return "XXXX" + data.substring(Math.max(0, data.length() - 4));
        }
        
        return data;
    }
    
    private static boolean isContentField(String fieldName) {
        return fieldName != null && (
            fieldName.toLowerCase().contains("content") ||
            fieldName.toLowerCase().contains("attachment") ||
            fieldName.toLowerCase().contains("file")
        );
    }
    
    private static boolean isSensitiveField(String fieldName) {
        return fieldName != null && (
            fieldName.toLowerCase().contains("password") ||
            fieldName.toLowerCase().contains("token") ||
            fieldName.toLowerCase().contains("key") ||
            fieldName.toLowerCase().contains("secret") ||
            fieldName.toLowerCase().contains("ssn") ||
            fieldName.toLowerCase().contains("credit")
        );
    }
}