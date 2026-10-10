package com.lifelink.model;

import com.lifelink.model.enums.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a match between an open SeekerRequest and an eligible Donor.
 * Tracks match lifecycle (PENDING -> ACCEPTED/DECLINED/EXPIRED) and phone disclosure.
 */
@Entity
@Table(name = "matches",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_matches_request_donor", columnNames = {"request_id", "donor_id"})
    },
    indexes = {
        @Index(name = "idx_matches_status", columnList = "status"),
        @Index(name = "idx_matches_req_donor", columnList = "request_id, donor_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"request", "donor"})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private SeekerRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MatchStatus status;

    @Column(name = "contact_shared", nullable = false)
    @Builder.Default
    private boolean contactShared = false;

    @Column(name = "matched_at", nullable = false, updatable = false)
    private LocalDateTime matchedAt;

    @PrePersist
    protected void onCreate() {
        if (this.matchedAt == null) {
            this.matchedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = MatchStatus.PENDING;
        }
    }

    @Transient
    public boolean isCompatibleOnlyMatch() {
        if (donor == null || donor.getDonorProfile() == null || request == null || request.getBloodGroup() == null) {
            return false;
        }
        return donor.getDonorProfile().getBloodGroup() != request.getBloodGroup();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Match match = (Match) o;
        return getId() != null && Objects.equals(getId(), match.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
