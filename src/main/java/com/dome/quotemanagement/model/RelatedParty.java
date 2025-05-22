package com.dome.quotemanagement.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "related_parties")
public class RelatedParty {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String href;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    public enum PartyRole {
        CUSTOMER,
        PROVIDER
    }
} 