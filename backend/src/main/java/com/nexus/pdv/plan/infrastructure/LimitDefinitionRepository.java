package com.nexus.pdv.plan.infrastructure;

import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.plan.domain.LimitDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitDefinitionRepository extends JpaRepository<LimitDefinition, LimitCode> {

    List<LimitDefinition> findAllByOrderByDisplayOrderAsc();
}
