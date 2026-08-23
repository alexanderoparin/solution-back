package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CabinetIntegration;
import ru.oparin.solution.model.CabinetIntegrationType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий интеграций кабинета.
 */
@Repository
public interface CabinetIntegrationRepository extends JpaRepository<CabinetIntegration, Long> {

    List<CabinetIntegration> findByCabinetId(Long cabinetId);

    List<CabinetIntegration> findByCabinetIdIn(Collection<Long> cabinetIds);

    Optional<CabinetIntegration> findByCabinetIdAndIntegrationType(Long cabinetId, CabinetIntegrationType integrationType);

    Optional<CabinetIntegration> findTopByCredentialPrimaryAndIntegrationTypeOrderByCabinetIdDesc(
            String credentialPrimary,
            CabinetIntegrationType integrationType
    );

    boolean existsByCredentialPrimaryAndIntegrationTypeIn(String credentialPrimary, Collection<CabinetIntegrationType> types);

    boolean existsByCredentialPrimaryAndIntegrationTypeInAndCabinetIdNot(
            String credentialPrimary,
            Collection<CabinetIntegrationType> types,
            Long cabinetId
    );
}
