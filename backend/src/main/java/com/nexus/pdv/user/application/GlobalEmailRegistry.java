package com.nexus.pdv.user.application;

import com.nexus.pdv.shared.persistence.TenantContext;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifica unicidade global de e-mail. Roda em transação/sessão própria e em contexto de
 * sistema, pois a sessão do tenant só enxerga usuários do próprio tenant. A constraint única
 * no banco continua sendo a garantia final contra corridas.
 */
@Service
public class GlobalEmailRegistry {

    private final UserRepository userRepository;
    private final TransactionTemplate newReadOnlyTx;

    public GlobalEmailRegistry(UserRepository userRepository, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.newReadOnlyTx = new TransactionTemplate(transactionManager);
        this.newReadOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newReadOnlyTx.setReadOnly(true);
    }

    public boolean isTaken(String email) {
        String normalized = User.normalizeEmail(email);
        return Boolean.TRUE.equals(TenantContext.callAsSystem(
                () -> newReadOnlyTx.execute(status -> userRepository.existsByEmail(normalized))));
    }
}
