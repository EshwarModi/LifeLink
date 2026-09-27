package com.lifelink.model;

import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.UrgencyLevel;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "seeker_requests")
public class SeekerRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "seeker_id", nullable = false)
    private User seeker;

    @Column(name = "blood_group", nullable = false)
    private String bloodGroup;

    @Column(nullable = false)
    private Integer units;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UrgencyLevel urgency;

    @Column(name = "hospital_name", nullable = false)
    private String hospitalName;

    @Column(name = "hospital_address", nullable = false)
    private String hospitalAddress;

    @Column(name = "required_by_date", nullable = false)
    private LocalDate requiredByDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SeekerRequest() {}

    public SeekerRequest(Long id, User seeker, String bloodGroup, Integer units, UrgencyLevel urgency, String hospitalName, String hospitalAddress, LocalDate requiredByDate, RequestStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.seeker = seeker;
        this.bloodGroup = bloodGroup;
        this.units = units;
        this.urgency = urgency;
        this.hospitalName = hospitalName;
        this.hospitalAddress = hospitalAddress;
        this.requiredByDate = requiredByDate;
        this.status = status != null ? status : RequestStatus.OPEN;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = RequestStatus.OPEN;
        }
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getSeeker() { return seeker; }
    public void setSeeker(User seeker) { this.seeker = seeker; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }

    public UrgencyLevel getUrgency() { return urgency; }
    public void setUrgency(UrgencyLevel urgency) { this.urgency = urgency; }

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public String getHospitalAddress() { return hospitalAddress; }
    public void setHospitalAddress(String hospitalAddress) { this.hospitalAddress = hospitalAddress; }

    public LocalDate getRequiredByDate() { return requiredByDate; }
    public void setRequiredByDate(LocalDate requiredByDate) { this.requiredByDate = requiredByDate; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Builder
    public static SeekerRequestBuilder builder() { return new SeekerRequestBuilder(); }

    public static class SeekerRequestBuilder {
        private Long id;
        private User seeker;
        private String bloodGroup;
        private Integer units;
        private UrgencyLevel urgency;
        private String hospitalName;
        private String hospitalAddress;
        private LocalDate requiredByDate;
        private RequestStatus status = RequestStatus.OPEN;
        private LocalDateTime createdAt;

        public SeekerRequestBuilder id(Long id) { this.id = id; return this; }
        public SeekerRequestBuilder seeker(User seeker) { this.seeker = seeker; return this; }
        public SeekerRequestBuilder bloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; return this; }
        public SeekerRequestBuilder units(Integer units) { this.units = units; return this; }
        public SeekerRequestBuilder urgency(UrgencyLevel urgency) { this.urgency = urgency; return this; }
        public SeekerRequestBuilder hospitalName(String hospitalName) { this.hospitalName = hospitalName; return this; }
        public SeekerRequestBuilder hospitalAddress(String hospitalAddress) { this.hospitalAddress = hospitalAddress; return this; }
        public SeekerRequestBuilder requiredByDate(LocalDate requiredByDate) { this.requiredByDate = requiredByDate; return this; }
        public SeekerRequestBuilder status(RequestStatus status) { this.status = status; return this; }
        public SeekerRequestBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public SeekerRequest build() {
            return new SeekerRequest(id, seeker, bloodGroup, units, urgency, hospitalName, hospitalAddress, requiredByDate, status, createdAt);
        }
    }
}
