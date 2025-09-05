package com.example.auto_trading_bot.bot;

import com.example.auto_trading_bot.market.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class TradingStrategy {

    public enum Signal { BUY, SELL, HOLD }

        public Signal signal(List<Candle> candles) {
            if (candles.size() < 6) return Signal.HOLD;

            BigDecimal sma5 = sma(candles, 5);
            BigDecimal last = candles.get(candles.size()-1).close();

            if (last.compareTo(sma5) > 0)  return Signal.BUY;
            if (last.compareTo(sma5) < 0)  return Signal.SELL;
            return Signal.HOLD;
        }

        private BigDecimal sma(List<Candle> c, int n) {
            int len = c.size();
            BigDecimal sum = BigDecimal.ZERO;
            for (int i=len-n; i<len; i++) sum = sum.add(c.get(i).close());
            return sum.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP);
        }

}
