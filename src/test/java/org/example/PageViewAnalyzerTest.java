package org.example;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageViewAnalyzerTest {
    private final PageViewAnalyzer analyzer = new PageViewAnalyzer();

    @Test
    void aggregatesDailyViewsAndCalculatesGrowthFromCompleteMonths() {
        List<PageView> daily = new ArrayList<>();
        daily.addAll(days(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 10));
        daily.addAll(days(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), 20));

        PageViewAnalyzer.Statistics result = analyzer.analyze(daily,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29));

        assertEquals(890, result.totalViews());
        assertEquals(445.0, result.averageMonthlyViews());
        assertEquals(87.1, result.growthPercent());
        assertEquals("first_half_vs_second_half_complete_months", result.growthMethod());
        assertEquals("growing", result.trend());
        assertEquals(2, result.completeMonths());
        assertEquals(310L, result.monthlyViews().get(0).views());
        assertEquals(580L, result.monthlyViews().get(1).views());
        assertTrue(result.monthlyViews().stream().allMatch(PageViewAnalyzer.MonthlyViews::complete));
    }

    @Test
    void includesPartialMonthViewsInTotalButNotInMonthlyTrend() {
        List<PageView> daily = new ArrayList<>();
        daily.addAll(days(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 31), 10));
        daily.addAll(days(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), 20));
        daily.addAll(days(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 1), 30));

        PageViewAnalyzer.Statistics result = analyzer.analyze(daily,
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 3, 1));

        assertEquals(780, result.totalViews());
        assertEquals(580.0, result.averageMonthlyViews());
        assertNull(result.growthPercent());
        assertEquals("unavailable_insufficient_data", result.growthMethod());
        assertEquals("insufficient_data", result.trend());
        assertEquals(1, result.completeMonths());
        assertFalse(result.monthlyViews().get(0).complete());
        assertTrue(result.monthlyViews().get(1).complete());
        assertFalse(result.monthlyViews().get(2).complete());
    }

    @Test
    void missingDayIsNotImputedAsZeroOrTreatedAsComplete() {
        List<PageView> daily = new ArrayList<>(days(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 10));
        daily.remove(4);
        daily.addAll(days(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), 20));

        PageViewAnalyzer.Statistics result = analyzer.analyze(daily,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29));

        assertEquals(880, result.totalViews());
        assertEquals(30, result.monthlyViews().get(0).observedDays());
        assertEquals(31, result.monthlyViews().get(0).expectedDays());
        assertFalse(result.monthlyViews().get(0).complete());
        assertEquals(1, result.completeMonths());
        assertNull(result.growthPercent());
    }

    @Test
    void strongMonthlySpikeReducesHeuristicReliability() {
        List<PageView> baseline = days(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30), 10);
        List<PageView> spike = new ArrayList<>(baseline);
        spike.removeIf(view -> view.getDate().getMonthValue() == 4);
        spike.addAll(days(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), 100));

        PageViewAnalyzer.Statistics normal = analyzer.analyze(baseline,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
        PageViewAnalyzer.Statistics spiky = analyzer.analyze(spike,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));

        assertEquals(0, normal.outlierMonths());
        assertEquals(1, spiky.outlierMonths());
        assertTrue(spiky.reliabilityScore() < normal.reliabilityScore());
    }

    @Test
    void sixMonthsCompareFirstThreeAndLastThreeAverages() {
        List<PageView> daily = monthlyTotals(YearMonth.of(2024, 1),
                100, 100, 100, 200, 200, 200);

        PageViewAnalyzer.Statistics result = analyzer.analyze(daily,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));

        assertEquals(100.0, result.growthPercent());
        assertEquals("first_3_vs_last_3_complete_months", result.growthMethod());
        assertEquals("growing", result.trend());
    }

    @Test
    void fiveMonthsUseFirstAndSecondHalves() {
        List<PageView> daily = monthlyTotals(YearMonth.of(2024, 1),
                100, 100, 100, 200, 200);

        PageViewAnalyzer.Statistics result = analyzer.analyze(daily,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 5, 31));

        assertEquals(66.7, result.growthPercent());
        assertEquals("first_half_vs_second_half_complete_months", result.growthMethod());
    }

    @Test
    void hugeFirstMonthUsesWindowAverageAndLowersReliability() {
        PageViewAnalyzer.Statistics stable = analyzer.analyze(
                monthlyTotals(YearMonth.of(2024, 1), 100, 100, 100, 100, 100, 100),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
        PageViewAnalyzer.Statistics spiky = analyzer.analyze(
                monthlyTotals(YearMonth.of(2024, 1), 1000, 100, 100, 100, 100, 100),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));

        assertEquals(-75.0, spiky.growthPercent());
        assertEquals(1, spiky.outlierMonths());
        assertEquals(100, stable.reliabilityScore());
        assertTrue(spiky.reliabilityScore() < 70);
    }

    @Test
    void shortHistoryAndVolatileHistoryScoreBelowStableLongHistory() {
        PageViewAnalyzer.Statistics stable = analyzer.analyze(
                monthlyTotals(YearMonth.of(2024, 1), 300, 300, 300, 300, 300, 300),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
        PageViewAnalyzer.Statistics shortHistory = analyzer.analyze(
                monthlyTotals(YearMonth.of(2024, 1), 300, 300, 300),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));
        PageViewAnalyzer.Statistics volatileHistory = analyzer.analyze(
                monthlyTotals(YearMonth.of(2024, 1), 300, 300, 300, 300, 300, 3000),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));

        assertEquals(100, stable.reliabilityScore());
        assertEquals(50, shortHistory.reliabilityScore());
        assertTrue(volatileHistory.reliabilityScore() < 70);
        assertTrue(volatileHistory.reliabilityScore() < stable.reliabilityScore());
    }

    private List<PageView> monthlyTotals(YearMonth firstMonth, long... totals) {
        List<PageView> result = new ArrayList<>();
        for (int i = 0; i < totals.length; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                PageView view = new PageView();
                view.setTimestamp(month.atDay(day).format(DateTimeFormatter.BASIC_ISO_DATE) + "00");
                view.setViews(day == 1 ? totals[i] : 0);
                result.add(view);
            }
        }
        return result;
    }

    private List<PageView> days(LocalDate first, LocalDate last, long views) {
        List<PageView> result = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            PageView view = new PageView();
            view.setTimestamp(date.format(DateTimeFormatter.BASIC_ISO_DATE) + "00");
            view.setViews(views);
            result.add(view);
        }
        return result;
    }
}
