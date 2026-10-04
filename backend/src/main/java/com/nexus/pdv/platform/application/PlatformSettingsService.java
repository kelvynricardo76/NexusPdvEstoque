package com.nexus.pdv.platform.application;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parâmetros globais da plataforma (não pertencem a nenhum tenant). */
@Service
public class PlatformSettingsService {

    static final String DEFAULT_TRIAL_DAYS = "platform.default_trial_days";
    static final String PAST_DUE_GRACE_DAYS = "platform.past_due_grace_days";

    private final JdbcTemplate jdbc;

    public PlatformSettingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int defaultTrialDays() {
        return readInt(DEFAULT_TRIAL_DAYS, 14);
    }

    public int pastDueGraceDays() {
        return readInt(PAST_DUE_GRACE_DAYS, 7);
    }

    @Transactional
    public void update(int defaultTrialDays, int pastDueGraceDays) {
        if (defaultTrialDays < 0 || defaultTrialDays > 365 || pastDueGraceDays < 0 || pastDueGraceDays > 90) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Valores fora do intervalo permitido.");
        }
        write(DEFAULT_TRIAL_DAYS, defaultTrialDays);
        write(PAST_DUE_GRACE_DAYS, pastDueGraceDays);
    }

    private int readInt(String key, int fallback) {
        return jdbc.query("SELECT meta_value FROM app_metadata WHERE meta_key = ?",
                rs -> rs.next() ? Integer.parseInt(rs.getString(1)) : fallback, key);
    }

    private void write(String key, int value) {
        int updated = jdbc.update("UPDATE app_metadata SET meta_value = ?, updated_at = CURRENT_TIMESTAMP WHERE meta_key = ?",
                String.valueOf(value), key);
        if (updated == 0) {
            jdbc.update("INSERT INTO app_metadata (meta_key, meta_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    key, String.valueOf(value));
        }
    }
}
