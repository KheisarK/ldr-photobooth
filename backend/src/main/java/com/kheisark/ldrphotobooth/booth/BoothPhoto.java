package com.kheisark.ldrphotobooth.booth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "booth_photos",
        uniqueConstraints = @UniqueConstraint(columnNames = {"booth_id", "participant", "photo_index"})
)
public class BoothPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booth_id", nullable = false)
    private Booth booth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Participant participant;

    @Column(name = "photo_index", nullable = false)
    private int photoIndex;

    @Column(nullable = false, length = 1024)
    private String filePath;

    protected BoothPhoto() {
    }

    public BoothPhoto(Booth booth, Participant participant, int photoIndex, String filePath) {
        this.booth = booth;
        this.participant = participant;
        this.photoIndex = photoIndex;
        this.filePath = filePath;
    }

    public Long getId() {
        return id;
    }

    public Booth getBooth() {
        return booth;
    }

    public Participant getParticipant() {
        return participant;
    }

    public int getPhotoIndex() {
        return photoIndex;
    }

    public String getFilePath() {
        return filePath;
    }
}
