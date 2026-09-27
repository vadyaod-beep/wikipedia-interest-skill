package org.example;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates observed Wikimedia daily data; missing days are never treated as zero views. */
public class PageViewAnalyzer {
    public record MonthlyViews(
            String month,
            Long views,
            int observedDays,
            int expectedDays,
            boolean complete
    ) {
    }

    public record Statistics(
            long totalViews,
            Double averageMonthlyViews,
            Double growthPercent,
            String growthMethod,
            String trend,
            int reliabilityScore,
            int completeMonths,
            int observedDays,
            int expectedDays,
            int outlierMonths,
            List<MonthlyViews> monthlyViews
    ) {
    }

    public Statistics analyze(List<PageView> dailyViews, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }
        if (dailyViews == null || dailyViews.isEmpty()) {
            throw new IllegalArgumentException("Wikimedia returned no daily pageviews for this period");
        }

        Map<LocalDate, Long> days = new HashMap<>();
        for (PageView view : dailyViews) {
            LocalDate date = view.getDate();
            if (date.isBefore(start) || date.isAfter(end)) {
                continue;
            }
            if (view.getViews() < 0 || days.putIfAbsent(date, view.getViews()) != null) {
                throw new IllegalArgumentException("Invalid or duplicate daily pageview at " + date);
            }
        }
        if (days.isEmpty()) {
            throw new IllegalArgumentException("Wikimedia returned no daily pageviews in the selected period");
        }

        List<MonthlyViews> months = new ArrayList<>();
        List<Long> completeValues = new ArrayList<>();
        long total = 0;
        int observed = 0;
        int expected = Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1);

        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
            LocalDate from = start.isAfter(month.atDay(1)) ? start : month.atDay(1);
            LocalDate to = end.isBefore(month.atEndOfMonth()) ? end : month.atEndOfMonth();
            int expectedInMonth = Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);
            long views = 0;
            int observedInMonth = 0;
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                Long value = days.get(day);
                if (value != null) {
                    views = Math.addExact(views, value);
                    observedInMonth++;
                }
            }
            boolean complete = expectedInMonth == month.lengthOfMonth() && observedInMonth == expectedInMonth;
            Long observedViews = observedInMonth == 0 ? null : views;
            months.add(new MonthlyViews(month.toString(), observedViews, observedInMonth, expectedInMonth, complete));
            if (complete) {
                completeValues.add(views);
            }
            total = Math.addExact(total, views);
            observed += observedInMonth;
        }

        Double average = completeValues.isEmpty() ? null
                : round1(completeValues.stream().mapToLong(Long::longValue).average().orElseThrow());
        Double growth = null;
        String growthMethod = "unavailable_insufficient_data";
        String trend = "insufficient_data";
        int count = completeValues.size();
        if (count >= 2) {
            int split = count >= 6 ? 3 : count / 2;
            int recentStart = count >= 6 ? count - 3 : split;
            growthMethod = count >= 6
                    ? "first_3_vs_last_3_complete_months"
                    : "first_half_vs_second_half_complete_months";
            double baseline = mean(completeValues.subList(0, split));
            double recent = mean(completeValues.subList(recentStart, count));
            if (baseline > 0) {
                growth = round1((recent - baseline) * 100.0 / baseline);
                trend = growth > 5 ? "growing" : growth < -5 ? "declining" : "stable";
            } else {
                growthMethod = "unavailable_zero_baseline";
            }
        }

        int outliers = countOutliers(completeValues);
        double coverage = observed / (double) expected;
        double sufficiency = Math.min(1.0, count / 6.0);
        double outlierFactor = count == 0 ? 1.0 : 1.0 - 0.5 * outliers / count;
        double volatilityFactor = 1.0 / (1.0 + coefficientOfVariation(completeValues));
        int reliability = (int) Math.round(100 * coverage * sufficiency
                * outlierFactor * volatilityFactor);

        return new Statistics(total, average, growth, growthMethod, trend, reliability,
                completeValues.size(), observed, expected, outliers, List.copyOf(months));
    }

    private double mean(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElseThrow();
    }

    /** Population standard deviation divided by the mean; zero for an all-zero series. */
    private double coefficientOfVariation(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double average = mean(values);
        if (average == 0) {
            return 0;
        }
        double squaredDeviation = values.stream()
                .mapToDouble(value -> Math.pow(value - average, 2))
                .average().orElseThrow();
        return Math.sqrt(squaredDeviation) / average;
    }

    private int countOutliers(List<Long> values) {
        if (values.size() < 4) {
            return 0;
        }
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        double median = sorted.size() % 2 == 0
                ? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0
                : sorted.get(sorted.size() / 2);
        if (median == 0) {
            return (int) values.stream().filter(value -> value > 0).count();
        }
        return (int) values.stream()
                .filter(value -> value > 2.5 * median || value < 0.4 * median)
                .count();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
