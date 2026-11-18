package com.groww.watchlist.repository;

import com.groww.watchlist.model.LastSeenState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LastSeenStateRepository extends JpaRepository<LastSeenState, Long> {
    List<LastSeenState> findByUserId(String userId);
    Optional<LastSeenState> findByUserIdAndSymbol(String userId, String symbol);
    void deleteByUserIdAndSymbol(String userId, String symbol);
    void deleteByUserId(String userId);
}
