package com.example.auto_trading_bot.dto;

public record BotStatus (
    String mode,
    String status,
    String symbol,
    String interval
) {}

