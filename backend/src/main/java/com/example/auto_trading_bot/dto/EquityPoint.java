package com.example.auto_trading_bot.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPoint(
        Instant ts,
        BigDecimal equity
) {}
