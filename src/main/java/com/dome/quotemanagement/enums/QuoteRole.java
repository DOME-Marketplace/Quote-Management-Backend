package com.dome.quotemanagement.enums;

/**
 * Constants for RelatedParty roles in quotes.
 * All comparisons are case-insensitive.
 */
public class QuoteRole {
    
    public static final String CUSTOMER = "Buyer";
    public static final String SELLER = "Seller";
    public static final String BUYER_OPERATOR = "BuyerOperator";
    public static final String SELLER_OPERATOR = "SellerOperator";
    
    private QuoteRole() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Check if two role strings match (case-insensitive)
     * @param role1 first role to compare
     * @param role2 second role to compare
     * @return true if roles match (case-insensitive), false otherwise
     */
    public static boolean equalsIgnoreCase(String role1, String role2) {
        if (role1 == null && role2 == null) {
            return true;
        }
        if (role1 == null || role2 == null) {
            return false;
        }
        return role1.trim().equalsIgnoreCase(role2.trim());
    }
    
    /**
     * Check if a role string matches CUSTOMER (case-insensitive)
     * @param role the role to check
     * @return true if role is CUSTOMER (case-insensitive), false otherwise
     */
    public static boolean isCustomer(String role) {
        return equalsIgnoreCase(role, CUSTOMER);
    }
    
    /**
     * Check if a role string matches SELLER (case-insensitive)
     * @param role the role to check
     * @return true if role is SELLER (case-insensitive), false otherwise
     */
    public static boolean isSeller(String role) {
        return equalsIgnoreCase(role, SELLER);
    }
    
    /**
     * Check if a role string matches BUYER_OPERATOR (case-insensitive)
     * @param role the role to check
     * @return true if role is BUYER_OPERATOR (case-insensitive), false otherwise
     */
    public static boolean isBuyerOperator(String role) {
        return equalsIgnoreCase(role, BUYER_OPERATOR);
    }
    
    /**
     * Check if a role string matches SELLER_OPERATOR (case-insensitive)
     * @param role the role to check
     * @return true if role is SELLER_OPERATOR (case-insensitive), false otherwise
     */
    public static boolean isSellerOperator(String role) {
        return equalsIgnoreCase(role, SELLER_OPERATOR);
    }
}

