package com.groww.watchlist.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groww.watchlist.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Live provider backed by Yahoo Finance's public (unofficial, keyless) chart
 * endpoint. One call per symbol returns the quote metadata AND a year of daily
 * closes, so we never need a second request for history or the display name.
 */
@Component
public class YahooProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooProvider.class);
    private static final String[] HOSTS = {"query1.finance.yahoo.com", "query2.finance.yahoo.com"};
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final Set<String> SEARCH_TYPES = Set.of("EQUITY", "ETF", "INDEX", "CRYPTOCURRENCY");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public FetchResult fetch(String symbol) {
        String path = "/v8/finance/chart/" + enc(symbol) + "?interval=1d&range=1y&includePrePost=false";
        for (String host : HOSTS) {
            try {
                HttpResponse<String> res = get("https://" + host + path, 5);
                if (res.statusCode() == 404) return FetchResult.notFound();
                if (res.statusCode() != 200) {
                    log.debug("Yahoo {} returned {} for {}", host, res.statusCode(), symbol);
                    continue;
                }
                return parse(symbol, mapper.readTree(res.body()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FetchResult.unavailable();
            } catch (IOException e) {
                log.debug("Yahoo {} failed for {}: {}", host, symbol, e.getMessage());
            }
        }
        return FetchResult.unavailable();
    }

    FetchResult parse(String symbol, JsonNode root) {
        JsonNode chart = root.path("chart");
        JsonNode result = chart.path("result").path(0);
        if (result.isMissingNode() || result.isNull()) {
            // Yahoo reports bad tickers as chart.error.code == "Not Found"
            return chart.path("error").isNull() || chart.path("error").isMissingNode()
                    ? FetchResult.unavailable() : FetchResult.notFound();
        }
        JsonNode meta = result.path("meta");
        JsonNode ts = result.path("timestamp");
        JsonNode closeNode = result.path("indicators").path("quote").path(0).path("close");
        long gmtOffset = meta.path("gmtoffset").asLong(0);

        List<long[]> rows = new ArrayList<>();
        List<Double> closeList = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            JsonNode c = closeNode.get(i);
            if (c == null || c.isNull() || c.asDouble() <= 0) continue;
            rows.add(new long[]{Math.floorDiv(ts.get(i).asLong() + gmtOffset, 86_400L)});
            closeList.add(c.asDouble());
        }
        if (closeList.size() < 2) return FetchResult.notFound();

        int n = closeList.size();
        long[] days = new long[n];
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) {
            days[i] = rows.get(i)[0];
            closes[i] = closeList.get(i);
        }

        double price = meta.path("regularMarketPrice").asDouble(0);
        if (price <= 0) price = closes[n - 1];

        // Previous close: if the last bar IS today's session, yesterday is the bar before it;
        // otherwise (market closed / pre-open) the last bar is already the previous close.
        // (chartPreviousClose is the close BEFORE the requested range, so it must not be used here.)
        long marketDay = Math.floorDiv(meta.path("regularMarketTime").asLong(0) + gmtOffset, 86_400L);
        boolean lastBarIsToday = days[n - 1] == marketDay;
        double previousClose = lastBarIsToday ? closes[n - 2] : closes[n - 1];
        if (lastBarIsToday) closes[n - 1] = price;

        double high52 = meta.path("fiftyTwoWeekHigh").asDouble(0);
        double low52 = meta.path("fiftyTwoWeekLow").asDouble(0);
        double minClose = Double.MAX_VALUE, maxClose = 0;
        for (double c : closes) { minClose = Math.min(minClose, c); maxClose = Math.max(maxClose, c); }
        if (high52 <= 0) high52 = maxClose;
        if (low52 <= 0) low52 = minClose;
        high52 = Math.max(high52, price);
        low52 = Math.min(low52, price);

        String name = firstNonBlank(meta.path("longName").asText(null), meta.path("shortName").asText(null), symbol);
        String exchange = firstNonBlank(meta.path("fullExchangeName").asText(null), meta.path("exchangeName").asText(null), "");
        return FetchResult.ok(new StockData(symbol, name, meta.path("currency").asText("USD"), exchange,
                price, previousClose,
                meta.path("regularMarketDayHigh").asDouble(price), meta.path("regularMarketDayLow").asDouble(price),
                high52, low52, meta.path("regularMarketVolume").asLong(0),
                days, closes, StockData.LIVE, false, Instant.now()));
    }

    /** Free-text symbol search; returns an empty list on any failure (callers fall back to the local catalog). */
    public List<SearchResult> search(String query) {
        try {
            String url = "https://" + HOSTS[0] + "/v1/finance/search?quotesCount=8&newsCount=0&listsCount=0&q=" + enc(query);
            HttpResponse<String> res = get(url, 2);
            if (res.statusCode() != 200) return List.of();
            List<SearchResult> out = new ArrayList<>();
            for (JsonNode q : mapper.readTree(res.body()).path("quotes")) {
                String type = q.path("quoteType").asText("");
                if (!SEARCH_TYPES.contains(type)) continue;
                String name = firstNonBlank(q.path("longname").asText(null), q.path("shortname").asText(null), q.path("symbol").asText());
                out.add(new SearchResult(q.path("symbol").asText(), name, q.path("exchDisp").asText(""), type));
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    private HttpResponse<String> get(String url, int timeoutSeconds) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }
}
