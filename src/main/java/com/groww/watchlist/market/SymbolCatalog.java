package com.groww.watchlist.market;

import com.groww.watchlist.dto.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Built-in list of popular symbols. It powers instant (zero-network) search
 * suggestions, the "quick add" chips, and realistic base prices for the demo
 * data provider.
 */
@Component
public class SymbolCatalog {

    public record Entry(String symbol, String name, String exchange, String type, double basePrice, int sector) {}

    private static final List<Entry> ENTRIES = List.of(
            // India (NSE)
            new Entry("RELIANCE.NS", "Reliance Industries", "NSE", "EQUITY", 1420, 0),
            new Entry("TCS.NS", "Tata Consultancy Services", "NSE", "EQUITY", 3150, 1),
            new Entry("INFY.NS", "Infosys", "NSE", "EQUITY", 1560, 1),
            new Entry("HDFCBANK.NS", "HDFC Bank", "NSE", "EQUITY", 1010, 2),
            new Entry("ICICIBANK.NS", "ICICI Bank", "NSE", "EQUITY", 1380, 2),
            new Entry("SBIN.NS", "State Bank of India", "NSE", "EQUITY", 860, 2),
            new Entry("KOTAKBANK.NS", "Kotak Mahindra Bank", "NSE", "EQUITY", 2100, 2),
            new Entry("AXISBANK.NS", "Axis Bank", "NSE", "EQUITY", 1180, 2),
            new Entry("BAJFINANCE.NS", "Bajaj Finance", "NSE", "EQUITY", 1020, 2),
            new Entry("WIPRO.NS", "Wipro", "NSE", "EQUITY", 265, 1),
            new Entry("HCLTECH.NS", "HCL Technologies", "NSE", "EQUITY", 1620, 1),
            new Entry("TECHM.NS", "Tech Mahindra", "NSE", "EQUITY", 1590, 1),
            new Entry("ITC.NS", "ITC", "NSE", "EQUITY", 415, 3),
            new Entry("HINDUNILVR.NS", "Hindustan Unilever", "NSE", "EQUITY", 2450, 3),
            new Entry("BHARTIARTL.NS", "Bharti Airtel", "NSE", "EQUITY", 2010, 0),
            new Entry("LT.NS", "Larsen & Toubro", "NSE", "EQUITY", 3650, 0),
            new Entry("TATAMOTORS.NS", "Tata Motors", "NSE", "EQUITY", 690, 4),
            new Entry("MARUTI.NS", "Maruti Suzuki", "NSE", "EQUITY", 12800, 4),
            new Entry("TATASTEEL.NS", "Tata Steel", "NSE", "EQUITY", 168, 0),
            new Entry("SUNPHARMA.NS", "Sun Pharma", "NSE", "EQUITY", 1790, 3),
            new Entry("ADANIENT.NS", "Adani Enterprises", "NSE", "EQUITY", 2480, 0),
            new Entry("ZOMATO.NS", "Zomato (Eternal)", "NSE", "EQUITY", 305, 4),
            new Entry("TITAN.NS", "Titan Company", "NSE", "EQUITY", 3500, 3),
            new Entry("NTPC.NS", "NTPC", "NSE", "EQUITY", 340, 0),
            new Entry("ONGC.NS", "ONGC", "NSE", "EQUITY", 245, 0),
            // US
            new Entry("AAPL", "Apple", "NASDAQ", "EQUITY", 232, 1),
            new Entry("MSFT", "Microsoft", "NASDAQ", "EQUITY", 505, 1),
            new Entry("GOOGL", "Alphabet (Class A)", "NASDAQ", "EQUITY", 245, 1),
            new Entry("AMZN", "Amazon", "NASDAQ", "EQUITY", 228, 4),
            new Entry("NVDA", "NVIDIA", "NASDAQ", "EQUITY", 188, 1),
            new Entry("TSLA", "Tesla", "NASDAQ", "EQUITY", 345, 4),
            new Entry("META", "Meta Platforms", "NASDAQ", "EQUITY", 690, 1),
            new Entry("NFLX", "Netflix", "NASDAQ", "EQUITY", 1180, 4),
            new Entry("AMD", "Advanced Micro Devices", "NASDAQ", "EQUITY", 165, 1),
            new Entry("INTC", "Intel", "NASDAQ", "EQUITY", 36, 1),
            new Entry("ORCL", "Oracle", "NYSE", "EQUITY", 245, 1),
            new Entry("ADBE", "Adobe", "NASDAQ", "EQUITY", 345, 1),
            new Entry("CRM", "Salesforce", "NYSE", "EQUITY", 255, 1),
            new Entry("JPM", "JPMorgan Chase", "NYSE", "EQUITY", 305, 2),
            new Entry("V", "Visa", "NYSE", "EQUITY", 345, 2),
            new Entry("DIS", "Walt Disney", "NYSE", "EQUITY", 112, 4),
            new Entry("UBER", "Uber Technologies", "NYSE", "EQUITY", 92, 4),
            new Entry("KO", "Coca-Cola", "NYSE", "EQUITY", 68, 3),
            new Entry("PEP", "PepsiCo", "NASDAQ", "EQUITY", 148, 3),
            new Entry("BA", "Boeing", "NYSE", "EQUITY", 215, 0),
            new Entry("SPY", "SPDR S&P 500 ETF", "NYSE Arca", "ETF", 670, -1),
            new Entry("QQQ", "Invesco QQQ Trust", "NASDAQ", "ETF", 610, -1),
            // Indices & crypto
            new Entry("^NSEI", "NIFTY 50", "NSE", "INDEX", 24800, -1),
            new Entry("^BSESN", "SENSEX", "BSE", "INDEX", 81500, -1),
            new Entry("^GSPC", "S&P 500", "SNP", "INDEX", 6700, -1),
            new Entry("^IXIC", "NASDAQ Composite", "NASDAQ", "INDEX", 22500, -1),
            new Entry("BTC-USD", "Bitcoin USD", "CCC", "CRYPTOCURRENCY", 95000, -1),
            new Entry("ETH-USD", "Ethereum USD", "CCC", "CRYPTOCURRENCY", 3400, -1)
    );

    private final Map<String, Entry> bySymbol = ENTRIES.stream()
            .collect(Collectors.toMap(Entry::symbol, Function.identity()));

    public Entry find(String symbol) {
        return bySymbol.get(symbol);
    }

    /** Prefix matches on symbol rank above substring matches on name. */
    public List<SearchResult> search(String query, int limit) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return List.of();
        List<SearchResult> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (e.symbol().toLowerCase(Locale.ROOT).startsWith(q)) out.add(toResult(e));
        }
        for (Entry e : ENTRIES) {
            SearchResult r = toResult(e);
            boolean nameHit = e.name().toLowerCase(Locale.ROOT).contains(q)
                    || e.symbol().toLowerCase(Locale.ROOT).contains(q);
            if (nameHit && out.stream().noneMatch(x -> x.symbol().equals(r.symbol()))) out.add(r);
        }
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private SearchResult toResult(Entry e) {
        return new SearchResult(e.symbol(), e.name(), e.exchange(), e.type());
    }
}
