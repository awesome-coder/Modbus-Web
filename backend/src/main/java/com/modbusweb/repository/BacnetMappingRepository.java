package com.modbusweb.repository;

import com.modbusweb.domain.BacnetObjectType;
import com.modbusweb.domain.entity.BacnetMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BacnetMappingRepository extends JpaRepository<BacnetMapping, Long> {

    @EntityGraph(attributePaths = {"point", "point.device"})
    List<BacnetMapping> findByPointDeviceIdOrderByObjectTypeAscObjectInstanceAsc(Long deviceId);

    @Query(
            "select m from BacnetMapping m join fetch m.point p join fetch p.device "
                    + "where m.id = :id")
    Optional<BacnetMapping> findWithPointById(@Param("id") Long id);

    Optional<BacnetMapping> findByPointId(Long pointId);

    boolean existsByPointId(Long pointId);

    boolean existsByObjectTypeAndObjectInstance(BacnetObjectType objectType, int objectInstance);

    boolean existsByObjectTypeAndObjectInstanceAndIdNot(
            BacnetObjectType objectType, int objectInstance, Long id);

    boolean existsByObjectNameIgnoreCase(String objectName);

    boolean existsByObjectNameIgnoreCaseAndIdNot(String objectName, Long id);

    @Query(
            "select coalesce(max(m.objectInstance), 0) from BacnetMapping m "
                    + "where m.objectType = :type")
    int findMaxInstance(@Param("type") BacnetObjectType type);

    @Query("select m from BacnetMapping m join fetch m.point p join fetch p.device")
    List<BacnetMapping> findAllWithPoint();
}
