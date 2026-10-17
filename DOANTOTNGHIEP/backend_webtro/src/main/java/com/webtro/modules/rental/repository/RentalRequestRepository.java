package com.webtro.modules.rental.repository;

import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.modules.rental.entity.RentalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RentalRequest r where r.id = :id and r.deletedAt is null")
    Optional<RentalRequest> findAliveByIdForUpdate(@Param("id") Long id);

    Optional<RentalRequest> findByIdAndDeletedAtIsNull(Long id);

    Page<RentalRequest> findByTenantIdAndDeletedAtIsNull(Long tenantId, Pageable pageable);

    Page<RentalRequest> findByTenantIdAndStatusAndDeletedAtIsNull(
            Long tenantId, RentalRequestStatus status, Pageable pageable);

    @Query("""
            select r from RentalRequest r
            where r.deletedAt is null
              and r.listingId in (
                select l.id from Listing l
                where l.ownerId = :ownerId and l.deletedAt is null
              )
            """)
    Page<RentalRequest> findForLandlord(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("""
            select r from RentalRequest r
            where r.deletedAt is null
              and r.status = :status
              and r.listingId in (
                select l.id from Listing l
                where l.ownerId = :ownerId and l.deletedAt is null
              )
            """)
    Page<RentalRequest> findForLandlordByStatus(
            @Param("ownerId") Long ownerId,
            @Param("status") RentalRequestStatus status,
            Pageable pageable);

    boolean existsByListingIdAndTenantIdAndStatusInAndDeletedAtIsNull(
            Long listingId, Long tenantId, Collection<RentalRequestStatus> statuses);

    boolean existsByListingIdAndStatusAndDeletedAtIsNull(Long listingId, RentalRequestStatus status);

    boolean existsByTenantIdAndStatusAndDeletedAtIsNull(Long tenantId, RentalRequestStatus status);

    List<RentalRequest> findByListingIdAndStatusInAndDeletedAtIsNull(
            Long listingId, Collection<RentalRequestStatus> statuses);

    List<RentalRequest> findByTenantIdAndStatusInAndDeletedAtIsNull(
            Long tenantId, Collection<RentalRequestStatus> statuses);
}
