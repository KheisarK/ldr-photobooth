package com.kheisark.ldrphotobooth.booth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "booths")
public class Booth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BoothStatus status;

    @Column(length = 80)
    private String participantAName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 1024)
    private String resultPath;

    protected Booth() {
    }

    public Booth(String code, String participantAName) {
        this.code = code;
        this.participantAName = participantAName;
        this.status = BoothStatus.WAITING_A;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public BoothStatus getStatus() {
        return status;
    }

    public String getParticipantAName() {
        return participantAName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getResultPath() {
        return resultPath;
    }

    public void finishParticipantA() {
        status = BoothStatus.WAITING_B;
    }

    public void complete(String resultPath) {
        this.resultPath = resultPath;
        this.status = BoothStatus.COMPLETED;
    }
}
