package com.nexus.pdv.shared.time;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Intervalo de datas no fuso do tenant: {@code [start 00:00, end+1 00:00)}.
 * Filtros de tela: Hoje, 7 dias, 30 dias ou Personalizado.
 */
public record Period(LocalDate start, LocalDate end, ZoneId zone) {

    public static final int MAX_DAYS = 3660;

    public enum Preset { TODAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM }

    public static Period resolve(Preset preset, LocalDate from, LocalDate to, ZoneId zone, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Preset effective = preset == null ? (from != null || to != null ? Preset.CUSTOM : Preset.LAST_30_DAYS) : preset;
        return switch (effective) {
            case TODAY -> new Period(today, today, zone);
            case LAST_7_DAYS -> new Period(today.minusDays(6), today, zone);
            case LAST_30_DAYS -> new Period(today.minusDays(29), today, zone);
            case CUSTOM -> custom(from != null ? from : today.minusDays(29), to != null ? to : today, zone);
        };
    }

    public static Period custom(LocalDate from, LocalDate to, ZoneId zone) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A data inicial deve ser anterior à final.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Período muito longo.");
        }
        return new Period(from, to, zone);
    }

    public Instant startInstant() {
        return start.atStartOfDay(zone).toInstant();
    }

    public Instant endExclusive() {
        return end.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** Período imediatamente anterior, de mesma duração (para variação percentual). */
    public Period previous() {
        long days = days();
        return new Period(start.minusDays(days), start.minusDays(1), zone);
    }

    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }
}
