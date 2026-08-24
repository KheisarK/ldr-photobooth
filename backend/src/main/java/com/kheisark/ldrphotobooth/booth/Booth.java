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
import java.util.UUID;

@Entity
@Table(name = "booths")
public class Booth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BoothStatus status;

    @Column(length = 80)
    private String participantAName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 1024)
    private String resultPath;

    @Column(length = 36)
    private String ownerToken;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BoothMode mode;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FrameStyle frameStyle;

    private Instant completedAt;

    protected Booth() {
    }

    public Booth(String code, String participantAName, BoothMode mode) {
        this.code = code;
        this.participantAName = participantAName;
        this.status = BoothStatus.WAITING_A;
        this.createdAt = Instant.now();
        this.ownerToken = UUID.randomUUID().toString();
        this.mode = mode;
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

    public String getOwnerToken() {
        return ownerToken;
    }

    public BoothMode getMode() { return mode == null ? BoothMode.REFERENCE : mode; }

    public FrameStyle getFrameStyle() { return frameStyle; }

    public Instant getCompletedAt() { return completedAt; }

    public void finishParticipantA() {
        status = BoothStatus.WAITING_B;
    }

    public void finishParticipantB() {
        this.status = BoothStatus.READY_TO_FINALIZE;
    }

    public void complete(String resultPath, FrameStyle frameStyle) {
        this.resultPath = resultPath;
        this.frameStyle = frameStyle;
        if (this.completedAt == null) this.completedAt = Instant.now();
        this.status = BoothStatus.COMPLETED;
    }
}
