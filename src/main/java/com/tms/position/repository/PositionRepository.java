package com.tms.position.repository;

import com.tms.position.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByAccountIdAndInstrumentIdAndAsOfDate(
        Long accountId, Long instrumentId, LocalDate asOfDate);

    List<Position> findByAccountIdAndAsOfDate(Long accountId, LocalDate asOfDate);

    List<Position> findByAccountCodeAndAsOfDate(String accountCode, LocalDate asOfDate);

    List<Position> findBySymbolAndAsOfDate(String symbol, LocalDate asOfDate);

    @Query("SELECT p FROM Position p WHERE p.accountCode = :accountCode AND p.asOfDate = :asOfDate ORDER BY p.symbol")
    List<Position> findPositionsByAccount(@Param("accountCode") String accountCode,
                                          @Param("asOfDate") LocalDate asOfDate);

    @Query("SELECT SUM(p.marketValue) FROM Position p WHERE p.accountCode = :accountCode AND p.asOfDate = :asOfDate")
    Optional<java.math.BigDecimal> getTotalMarketValue(@Param("accountCode") String accountCode,
                                                        @Param("asOfDate") LocalDate asOfDate);
}
