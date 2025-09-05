package com.example.auto_trading_bot.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeDTO(
        long id,
        Instant ts,
        String symbol,
        String side,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal pnl
) {}
