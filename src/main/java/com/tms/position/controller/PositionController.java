package com.tms.position.controller;

import com.tms.position.dto.PositionResponse;
import com.tms.position.entity.Position;
import com.tms.position.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Position", description = "Position management APIs")
public class PositionController {

    private final PositionService positionService;

    @GetMapping("/{positionId}")
    @Operation(summary = "Get position by ID", description = "Retrieves a position by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Position found"),
        @ApiResponse(responseCode = "404", description = "Position not found")
    })
    public ResponseEntity<PositionResponse> getPosition(
            @Parameter(description = "Position ID") @PathVariable Long positionId) {

        log.info("Getting position: positionId={}", positionId);

        return positionService.getPosition(positionId)
            .map(this::toPositionResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/account/{accountCode}")
    @Operation(summary = "Get positions by account", description = "Retrieves all positions for an account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Positions retrieved successfully")
    })
    public ResponseEntity<List<PositionResponse>> getPositionsByAccount(
            @Parameter(description = "Account code") @PathVariable String accountCode,
            @Parameter(description = "As of date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {

        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        log.info("Getting positions for account: accountCode={}, asOfDate={}", accountCode, effectiveDate);

        List<Position> positions = positionService.getPositionsByAccount(accountCode, effectiveDate);
        List<PositionResponse> responses = positions.stream()
            .map(this::toPositionResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/account/{accountCode}/summary")
    @Operation(summary = "Get account position summary", description = "Retrieves summary of all positions for an account")
    public ResponseEntity<AccountPositionSummary> getAccountSummary(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {

        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        List<Position> positions = positionService.getPositionsByAccount(accountCode, effectiveDate);

        AccountPositionSummary summary = AccountPositionSummary.builder()
            .accountCode(accountCode)
            .asOfDate(effectiveDate)
            .positionCount(positions.size())
            .totalCostBasis(positions.stream()
                .map(Position::getCostBasis)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
            .totalRealizedPnl(positions.stream()
                .map(Position::getRealizedPnl)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
            .build();

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the service is running")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Position Service is running");
    }

    private PositionResponse toPositionResponse(Position position) {
        return PositionResponse.builder()
            .positionId(position.getPositionId())
            .accountId(position.getAccountId())
            .accountCode(position.getAccountCode())
            .instrumentId(position.getInstrumentId())
            .symbol(position.getSymbol())
            .quantity(position.getQuantity())
            .avgCost(position.getAvgCost())
            .costBasis(position.getCostBasis())
            .realizedPnl(position.getRealizedPnl())
            .currency(position.getCurrency())
            .asOfDate(position.getAsOfDate())
            .updatedAt(position.getUpdatedAt())
            .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AccountPositionSummary {
        private String accountCode;
        private LocalDate asOfDate;
        private int positionCount;
        private java.math.BigDecimal totalCostBasis;
        private java.math.BigDecimal totalRealizedPnl;
    }
}
