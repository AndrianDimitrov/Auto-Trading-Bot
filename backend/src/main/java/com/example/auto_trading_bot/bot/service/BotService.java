package com.example.auto_trading_bot.bot.service;

import com.example.auto_trading_bot.bot.TradingStrategy;
import com.example.auto_trading_bot.dto.EquityPoint;
import com.example.auto_trading_bot.dto.TradeDTO;
import com.example.auto_trading_bot.market.Candle;
import com.example.auto_trading_bot.market.MarketDataClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class BotService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BotService.class);

    private final JdbcTemplate db;
    private final MarketDataClient data;
    private final TradingStrategy strategy;
    private volatile Instant lastProcessedBarTs;

    private final ExecutorService exec =
            Executors.newSingleThreadExecutor(new CustomizableThreadFactory("bot-"));

    private volatile String status = "IDLE";
    private volatile String mode = "BACKTEST";
    private volatile String symbol = "BTCUSDT";
    private volatile String interval = "1m";

    public BotService(JdbcTemplate db, MarketDataClient data, TradingStrategy strategy) {
        this.db = db;
        this.data = data;
        this.strategy = strategy;
    }

    public synchronized void startBacktest(String symbol, String interval) {
        stop();
        this.mode = "BACKTEST";
        this.status = "RUNNING";
        this.symbol = symbol;
        this.interval = interval;
        exec.submit(this::runBackTest);
    }

    public synchronized void startLive(String symbol, String interval) {
        stop();
        this.mode = "LIVE";
        this.status = "RUNNING";
        this.symbol = symbol;
        this.interval = interval;
        exec.submit(this::runLive);
    }

    public synchronized void pause() {
        this.status = "PAUSED";
    }

    public synchronized void resume() {
        if ("PAUSED".equals(this.status)) this.status = "RUNNING";
    }

    public synchronized void stop() {
        this.status = "IDLE";
    }

    public String status() {
        return status;
    }

    public String mode() {
        return mode;
    }

    public String symbol() {
        return symbol;
    }

    public String interval() {
        return interval;
    }

    private void runBackTest() {
        resetEquity();
        List<Candle> candles;
        try {
            candles = data.getNumberOfLines(symbol, interval, 1000);
        } catch (Exception e) {
            log.warn("Market data error (backtest) symbol={} interval={}: {}", symbol, interval, e.toString());
            this.status = "IDLE";
            return;
        }
        if (candles == null || candles.size() < 2) {
            log.warn("No/insufficient candles for backtest: symbol={} interval={} size={}",
                    symbol, interval, candles == null ? 0 : candles.size());
            this.status = "IDLE";
            return;
        }

        int start = Math.min(30, candles.size() - 1);
        for (int i = start; i < candles.size(); i++) {
            while ("PAUSED".equals(status)) sleep(200);
            if (!"RUNNING".equals(status)) return;

            var window = candles.subList(0, i + 1);
            var c = candles.get(i);
            tick(window, c.close(), c.ts());
        }
        this.status = "IDLE";
    }

    private void runLive() {
        resetEquity();
        log.info("ENTER runLive symbol={} interval={}", symbol, interval);
        while ("RUNNING".equals(status) || "PAUSED".equals(status)) {
            while ("PAUSED".equals(status)) sleep(250);
            if (!"RUNNING".equals(status)) break;

            List<Candle> kl;
            try {
                kl = data.getNumberOfLines(symbol, interval, 120);
            } catch (Exception e) {
                log.warn("Market data error (live) symbol={} interval={}: {}", symbol, interval, e.toString());
                sleep(5_000);
                continue;
            }
            if (kl == null || kl.size() < 2) {
                log.warn("No/insufficient candles (live): symbol={} interval={} size={}",
                        symbol, interval, kl == null ? 0 : kl.size());
                sleep(5_000);
                continue;
            }

            var ctx = kl.subList(0, kl.size() - 1);
            var closed = kl.get(kl.size() - 2);

            if (lastProcessedBarTs != null && closed.ts().equals(lastProcessedBarTs)) {
                log.debug("SKIP already processed candle ts={}", closed.ts());
            } else {
                lastProcessedBarTs = closed.ts();
                tick(ctx, closed.close(), closed.ts());
            }

            sleep(10_000);
        }
    }


    private void tick(List<Candle> ctx, BigDecimal price, Instant ts) {
        TradingStrategy.Signal sig = strategy.signal(ctx);

        BigDecimal cash = db.queryForObject("SELECT cash_balance FROM account LIMIT 1", BigDecimal.class);
        BigDecimal qty = db.query((con) -> con.prepareStatement("SELECT quantity FROM holdings WHERE symbol=?"),
                ps -> ps.setString(1, symbol),
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);

        log.info("tick ts={} sig={} price={} cash={} qty={}", ts, sig, price, cash, qty);

        if (sig == TradingStrategy.Signal.BUY && cash.compareTo(BigDecimal.valueOf(1)) > 0) {
            BigDecimal invest = cash.multiply(new BigDecimal("0.05"));
            BigDecimal buyQty = invest.divide(price, 8, RoundingMode.DOWN);
            if (buyQty.compareTo(BigDecimal.ZERO) > 0) {
                db.update("UPDATE account SET cash_balance=cash_balance-?", buyQty.multiply(price));
                int upd = db.update("UPDATE holdings SET quantity=quantity+? WHERE symbol=?", buyQty, symbol);
                if (upd == 0) db.update("INSERT INTO holdings(symbol,quantity) VALUES (?,?)", symbol, buyQty);
                int rows1 = db.update(con -> {
                    var ps = con.prepareStatement(
                            "INSERT INTO trades(ts,symbol,side,qty,price,fee,pnl) VALUES (?,?,?,?,?,?,NULL)"
                    );
                    ps.setTimestamp(1, Timestamp.from(ts));
                    ps.setString(2, symbol);
                    ps.setString(3, "BUY");
                    ps.setBigDecimal(4, buyQty);
                    ps.setBigDecimal(5, price);
                    ps.setBigDecimal(6, BigDecimal.ZERO);
                    return ps;
                });
                log.info("INSERTED TRADE BUY rows={}", rows1);
            }
        } else if (sig == TradingStrategy.Signal.SELL && qty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal sellQty = qty.multiply(new BigDecimal("0.5")).setScale(8, RoundingMode.DOWN);
            if (sellQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal proceeds = sellQty.multiply(price);

                BigDecimal avgEntry = averageEntryPrice();
                if (avgEntry.compareTo(BigDecimal.ZERO) == 0) {
                    avgEntry = price;
                }
                BigDecimal pnl = price.subtract(avgEntry).multiply(sellQty);

                db.update("UPDATE account SET cash_balance=cash_balance+?", proceeds);
                db.update("UPDATE holdings SET quantity=quantity-? WHERE symbol=?", sellQty, symbol);
                int rows2 = db.update(con -> {
                    var ps = con.prepareStatement(
                            "INSERT INTO trades(ts,symbol,side,qty,price,fee,pnl) VALUES (?,?,?,?,?,?,?)"
                    );
                    ps.setTimestamp(1, Timestamp.from(ts));
                    ps.setString(2, symbol);
                    ps.setString(3, "SELL");
                    ps.setBigDecimal(4, sellQty);
                    ps.setBigDecimal(5, price);
                    ps.setBigDecimal(6, BigDecimal.ZERO);
                    ps.setBigDecimal(7, pnl);
                    return ps;
                });
                log.info("INSERTED TRADE SELL rows={}", rows2);

            }
        }

        BigDecimal newCash = db.queryForObject("SELECT cash_balance FROM account LIMIT 1", BigDecimal.class);
        BigDecimal newQty = db.query((con) -> con.prepareStatement("SELECT quantity FROM holdings WHERE symbol=?"),
                ps -> ps.setString(1, symbol),
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);

        BigDecimal equity = newCash.add(newQty.multiply(price));
        int rowsEq = db.update(con -> {
            var ps = con.prepareStatement("INSERT INTO equity_curve(ts,equity) VALUES (?,?)");
            ps.setTimestamp(1, Timestamp.from(ts));
            ps.setBigDecimal(2, equity);
            return ps;
        });
        log.info("INSERTED EQUITY rows={}", rowsEq);
    }


    private BigDecimal averageEntryPrice() {
        List<BigDecimal> prices = db.query("SELECT price FROM trades WHERE side='BUY' ORDER BY ts DESC LIMIT 10",
                (rs, i) -> rs.getBigDecimal(1));
        if (prices.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (var p : prices) sum = sum.add(p);
        return sum.divide(BigDecimal.valueOf(prices.size()), 8, RoundingMode.HALF_UP);
    }

    private void resetEquity() {
        db.update("DELETE FROM equity_curve");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    public List<TradeDTO> trades() {
        return db.query("SELECT id, ts, symbol, side, qty, price, fee, pnl FROM trades ORDER BY ts DESC LIMIT 500",
                (rs, i) -> new TradeDTO(
                        rs.getLong(1), rs.getTimestamp(2).toInstant(),
                        rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6),
                        rs.getBigDecimal(7), (java.math.BigDecimal) rs.getObject(8)
                )
        );
    }

    public List<EquityPoint> equity() {
        return db.query("SELECT ts, equity FROM equity_curve ORDER BY ts ASC",
                (rs, i) -> new EquityPoint(rs.getTimestamp(1).toInstant(), rs.getBigDecimal(2)));
    }

    public java.util.Map<String, Object> portfolio() {
        BigDecimal cash = db.queryForObject("SELECT cash_balance FROM account LIMIT 1", BigDecimal.class);
        BigDecimal qty = db.query((con) -> con.prepareStatement("SELECT quantity FROM holdings WHERE symbol=?"),
                ps -> ps.setString(1, symbol),
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);
        return java.util.Map.of("cash", cash, "positionQty", qty, "symbol", symbol);
    }
}
