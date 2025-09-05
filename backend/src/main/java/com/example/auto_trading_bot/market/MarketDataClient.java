package com.example.auto_trading_bot.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class MarketDataClient {

    private final WebClient http;

    public MarketDataClient(@Value("${app.exchange.baseUrl}") String baseUrl,
                            WebClient.Builder builder) {
        this.http = builder.baseUrl(baseUrl).build();
    }


    public List<Candle> getNumberOfLines(String symbol, String interval, int limit) {
        List<List<Object>> raw = http.get()
                .uri(uri -> uri.path("/api/v3/klines")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("limit", limit)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(List.class)
                .onErrorReturn(List.of())
                .block();

        List<Candle> out = new ArrayList<>();
        if (raw == null) return out;

        for (List<?> k : raw) {
            Instant ts = Instant.ofEpochMilli(((Number) k.get(0)).longValue());
            BigDecimal open = new BigDecimal(k.get(1).toString());
            BigDecimal high = new BigDecimal(k.get(2).toString());
            BigDecimal low = new BigDecimal(k.get(3).toString());
            BigDecimal close = new BigDecimal(k.get(4).toString());
            BigDecimal vol = new BigDecimal(k.get(5).toString());
            out.add(new Candle(ts, open, high, low, close, vol));
        }
        return out;
    }

    public BigDecimal getLastPrice(String symbol) {
        var map = http.get()
                .uri(uri -> uri.path("/api/v3/ticker/price")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .onErrorResume(e -> Mono.just(java.util.Map.of("price", "0")))
                .block();

        return new BigDecimal(map.get("price").toString());
    }
}
