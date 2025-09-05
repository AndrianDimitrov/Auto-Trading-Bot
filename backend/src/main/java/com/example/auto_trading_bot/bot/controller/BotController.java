package com.example.auto_trading_bot.bot.controller;

import com.example.auto_trading_bot.bot.service.BotService;
import com.example.auto_trading_bot.dto.BotStatus;
import com.example.auto_trading_bot.dto.EquityPoint;
import com.example.auto_trading_bot.dto.TradeDTO;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BotController {

    private final BotService bot;

    public BotController(BotService bot) { this.bot = bot; }

    private static String sanitize(String s) {
        return s == null ? "" : s.trim();
    }

    @PostMapping("/bot/start")
    public Map<String,Object> start(@RequestParam String mode,
                                    @RequestParam String symbol,
                                    @RequestParam String interval) {
        mode = sanitize(mode);
        symbol = sanitize(symbol);
        interval = sanitize(interval);

        if (!(mode.equals("BACKTEST") || mode.equals("LIVE"))) {
            throw new IllegalArgumentException("mode must be BACKTEST or LIVE");
        }
        if (!symbol.matches("[A-Z0-9]{3,20}")) {
            throw new IllegalArgumentException("invalid symbol");
        }
        if (!interval.matches("[0-9]+[smhdw]")) {
            throw new IllegalArgumentException("invalid interval");
        }

        if (mode.equals("BACKTEST")) bot.startBacktest(symbol, interval);
        else bot.startLive(symbol, interval);
        return Map.of("ok", true, "mode", mode, "symbol", symbol, "interval", interval);
    }

    @PostMapping("/bot/pause")  public BotStatus pause()  { bot.pause();  return new BotStatus(bot.mode(), "PAUSED",  bot.symbol(), bot.interval()); }
    @PostMapping("/bot/resume") public BotStatus resume() { bot.resume(); return new BotStatus(bot.mode(), "RUNNING", bot.symbol(), bot.interval()); }
    @PostMapping("/bot/stop")   public BotStatus stop()   { bot.stop();   return new BotStatus(bot.mode(), "IDLE",    bot.symbol(), bot.interval()); }
    @GetMapping("/bot/status")  public BotStatus status() { return new BotStatus(bot.mode(), bot.status(), bot.symbol(), bot.interval()); }

    @GetMapping("/trades")   public List<TradeDTO> trades() { return bot.trades(); }
    @GetMapping("/equity")   public List<EquityPoint> equity() { return bot.equity(); }
    @GetMapping("/portfolio")public java.util.Map<String,Object> portfolio() { return bot.portfolio(); }
}
