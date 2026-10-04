package com.nexus.pdv.dashboard.application;

import com.nexus.pdv.dashboard.api.DashboardDtos.DailySales;
import com.nexus.pdv.dashboard.api.DashboardDtos.HourlySales;
import com.nexus.pdv.shared.time.Period;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Agregações por dia/hora feitas no fuso do tenant (e não em UTC), para que vendas noturnas
 * contem no dia correto.
 */
public final class SalesAggregation {

    private SalesAggregation() {
    }

    public static List<DailySales> byDay(List<Object[]> rows, Period period) {
        Map<LocalDate, BigDecimal> totals = new TreeMap<>();
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (Object[] row : rows) {
            LocalDate date = period.dateOf((Instant) row[0]);
            totals.merge(date, (BigDecimal) row[1], BigDecimal::add);
            counts.merge(date, 1L, Long::sum);
        }
        List<DailySales> result = new ArrayList<>();
        for (LocalDate date = period.start(); !date.isAfter(period.end()); date = date.plusDays(1)) {
            result.add(new DailySales(date, totals.getOrDefault(date, BigDecimal.ZERO), counts.getOrDefault(date, 0L)));
        }
        return result;
    }

    public static List<HourlySales> byHour(List<Object[]> rows, Period period) {
        BigDecimal[] totals = new BigDecimal[24];
        long[] counts = new long[24];
        for (int hour = 0; hour < 24; hour++) {
            totals[hour] = BigDecimal.ZERO;
        }
        for (Object[] row : rows) {
            int hour = ((Instant) row[0]).atZone(period.zone()).getHour();
            totals[hour] = totals[hour].add((BigDecimal) row[1]);
            counts[hour]++;
        }
        List<HourlySales> result = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            result.add(new HourlySales(hour, totals[hour], counts[hour]));
        }
        return result;
    }
}
