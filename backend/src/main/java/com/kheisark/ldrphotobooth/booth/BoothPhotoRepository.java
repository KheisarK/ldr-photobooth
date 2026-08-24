package com.kheisark.ldrphotobooth.booth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoothPhotoRepository extends JpaRepository<BoothPhoto, Long> {

    List<BoothPhoto> findAllByBoothAndParticipantOrderByPhotoIndexAsc(Booth booth, Participant participant);

    long countByBoothAndParticipant(Booth booth, Participant participant);
}
