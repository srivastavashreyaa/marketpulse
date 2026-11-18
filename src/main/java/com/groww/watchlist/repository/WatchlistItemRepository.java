package com.groww.watchlist.repository;

import com.groww.watchlist.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUserIdOrderByAddedAtAsc(String userId);
    Optional<WatchlistItem> findByUserIdAndSymbol(String userId, String symbol);
    long countByUserId(String userId);
    void deleteByUserIdAndSymbol(String userId, String symbol);
    void deleteByUserId(String userId);
}
