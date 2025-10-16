package com.dome.quotemanagement.enums;

/**
 * Enum representing the possible categories for a quote.
 * Used to categorize quotes from a business perspective.
 */
public enum QuoteCategory {
    TAILORED("tailored"),
    TENDER("tender"),
    COORDINATOR("coordinator");
    
    private final String value;
    
    QuoteCategory(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get QuoteCategory from string value
     * @param value the string value
     * @return the corresponding QuoteCategory
     * @throws IllegalArgumentException if value is not valid
     */
    public static QuoteCategory fromValue(String value) {
        if (value == null) {
            return null;
        }
        
        for (QuoteCategory category : QuoteCategory.values()) {
            if (category.value.equalsIgnoreCase(value.trim())) {
                return category;
            }
        }
        
        throw new IllegalArgumentException("Invalid quote category: " + value + ". Valid values are: tailored, tender, coordinator");
    }
    
    /**
     * Check if a string value is valid
     * @param value the string value to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String value) {
        try {
            fromValue(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
