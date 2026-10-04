package com.nexus.pdv.plan.infrastructure;

import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.FeatureDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureDefinitionRepository extends JpaRepository<FeatureDefinition, FeatureCode> {

    List<FeatureDefinition> findAllByOrderByDisplayOrderAsc();
}
