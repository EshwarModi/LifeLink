package com.lifelink.model;

import com.lifelink.model.enums.MatchStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "seeker_request_id", nullable = false)
    private SeekerRequest seekerRequest;

    @ManyToOne
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(name = "contact_shared", nullable = false)
    private Boolean contactShared = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Match() {}

    public Match(Long id, SeekerRequest seekerRequest, User donor, MatchStatus status, Boolean contactShared, LocalDateTime createdAt) {
        this.id = id;
        this.seekerRequest = seekerRequest;
        this.donor = donor;
        this.status = status != null ? status : MatchStatus.PENDING;
        this.contactShared = contactShared != null ? contactShared : false;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = MatchStatus.PENDING;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SeekerRequest getSeekerRequest() { return seekerRequest; }
    public void setSeekerRequest(SeekerRequest seekerRequest) { this.seekerRequest = seekerRequest; }

    public User getDonor() { return donor; }
    public void setDonor(User donor) { this.donor = donor; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public Boolean getContactShared() { return contactShared; }
    public void setContactShared(Boolean contactShared) { this.contactShared = contactShared; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Builder
    public static MatchBuilder builder() { return new MatchBuilder(); }

    public static class MatchBuilder {
        private Long id;
        private SeekerRequest seekerRequest;
        private User donor;
        private MatchStatus status = MatchStatus.PENDING;
        private Boolean contactShared = false;
        private LocalDateTime createdAt;

        public MatchBuilder id(Long id) { this.id = id; return this; }
        public MatchBuilder seekerRequest(SeekerRequest seekerRequest) { this.seekerRequest = seekerRequest; return this; }
        public MatchBuilder donor(User donor) { this.donor = donor; return this; }
        public MatchBuilder status(MatchStatus status) { this.status = status; return this; }
        public MatchBuilder contactShared(Boolean contactShared) { this.contactShared = contactShared; return this; }
        public MatchBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Match build() {
            return new Match(id, seekerRequest, donor, status, contactShared, createdAt);
        }
    }
}
