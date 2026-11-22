package com.wudjabudja.wudjabudja_backend.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    List<Vote> findByOptionIdInOrderByIdAsc(Collection<Long> optionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Vote v WHERE v.userId = :userId AND v.optionId IN :optionIds")
    int deleteByUserIdAndOptionIdIn(@Param("userId") Long userId, @Param("optionIds") Collection<Long> optionIds);
}
