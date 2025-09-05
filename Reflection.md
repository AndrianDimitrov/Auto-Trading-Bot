Reflection

Solution and key design decisions
I built a crypto trading bot with a Spring Boot backend and a lightweight HTML/JS frontend. The bot runs in two modes:

Backtest mode simulates trades on historical data from Binance.

Live mode executes trades on current market data.

The database (PostgreSQL) tracks account balance, holdings, trades, and equity curve. I used raw SQL queries via JdbcTemplate instead of ORM, following the requirements. The trading logic is encapsulated in BotService with a simple pluggable strategy. The frontend dashboard displays portfolio, equity curve, trades, and provides controls to start, pause, resume, and stop the bot.

Trade-offs / shortcuts
I kept the trading strategy very basic, focusing on system integration instead of complex indicators. Error handling is implemented for missing or invalid market data, but could be extended. The frontend is functional but minimal, without advanced chart overlays.

External tools / AI (LLMs)
I used AI mainly for boilerplate code. I also asked for guidance on integrating with the external market data API, since I hadn’t worked with this type of API before, and for advice on a simple trading strategy to get started. All suggestions were adapted to my project, tested manually, and in many cases refactored to fit my design decisions.

If I had more time
I would focus on improving the trading strategy by adding more indicators, polishing the frontend with trade markers on the charts, and extending error handling. I would also implement proper unit and integration tests to strengthen reliability.