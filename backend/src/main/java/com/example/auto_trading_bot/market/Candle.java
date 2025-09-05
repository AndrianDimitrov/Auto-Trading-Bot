package com.example.auto_trading_bot.market;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
        Instant ts,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {}
