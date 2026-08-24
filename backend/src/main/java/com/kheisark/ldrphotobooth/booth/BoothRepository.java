package com.kheisark.ldrphotobooth.booth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    Optional<Booth> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booth from Booth booth where upper(booth.code) = upper(:code)")
    Optional<Booth> findByCodeIgnoreCaseForUpdate(@Param("code") String code);

    boolean existsByCode(String code);
}
