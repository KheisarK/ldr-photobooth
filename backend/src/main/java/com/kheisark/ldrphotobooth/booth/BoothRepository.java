package com.kheisark.ldrphotobooth.booth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    Optional<Booth> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);
}
