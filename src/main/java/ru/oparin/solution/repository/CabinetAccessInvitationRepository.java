package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.CabinetAccessInvitation;
import ru.oparin.solution.model.CabinetAccessInvitationStatus;

import java.util.List;
import java.util.Optional;

public interface CabinetAccessInvitationRepository extends JpaRepository<CabinetAccessInvitation, Long> {

    Optional<CabinetAccessInvitation> findByToken(String token);

    List<CabinetAccessInvitation> findByCabinet_IdOrderByCreatedAtDesc(Long cabinetId);

    @Query("""
            select i from CabinetAccessInvitation i
            where i.cabinet.id = :cabinetId
              and lower(i.email) = lower(:email)
              and i.status = :status
            """)
    Optional<CabinetAccessInvitation> findPendingByCabinetAndEmail(
            @Param("cabinetId") Long cabinetId,
            @Param("email") String email,
            @Param("status") CabinetAccessInvitationStatus status
    );

    List<CabinetAccessInvitation> findByCabinet_IdAndStatus(Long cabinetId, CabinetAccessInvitationStatus status);
}
