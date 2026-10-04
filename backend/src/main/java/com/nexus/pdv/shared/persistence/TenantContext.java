package com.nexus.pdv.shared.persistence;

import java.util.function.Supplier;

/**
 * Permite executar código em "contexto de sistema", no qual o filtro de tenant do
 * Hibernate não é aplicado. Uso restrito a fluxos sem usuário de tenant autenticado:
 * login (busca global por e-mail), provisionamento, seeds e jobs internos.
 *
 * <p>Uma nova sessão/transação deve ser aberta DENTRO do bloco, pois o Hibernate fixa o
 * tenant no momento em que a sessão é criada.
 */
public final class TenantContext {

    private static final ThreadLocal<Integer> SYSTEM_DEPTH = ThreadLocal.withInitial(() -> 0);

    private TenantContext() {
    }

    public static <T> T callAsSystem(Supplier<T> action) {
        SYSTEM_DEPTH.set(SYSTEM_DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = SYSTEM_DEPTH.get() - 1;
            if (depth == 0) {
                SYSTEM_DEPTH.remove();
            } else {
                SYSTEM_DEPTH.set(depth);
            }
        }
    }

    public static void runAsSystem(Runnable action) {
        callAsSystem(() -> {
            action.run();
            return null;
        });
    }

    public static boolean isSystem() {
        return SYSTEM_DEPTH.get() > 0;
    }
}
