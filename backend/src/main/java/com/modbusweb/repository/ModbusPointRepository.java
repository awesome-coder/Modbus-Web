package com.modbusweb.repository;

import com.modbusweb.domain.entity.ModbusPoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModbusPointRepository extends JpaRepository<ModbusPoint, Long> {

    @EntityGraph(attributePaths = {"bacnetMapping"})
    List<ModbusPoint> findByDeviceIdOrderByNameAsc(Long deviceId);

    @EntityGraph(attributePaths = {"bacnetMapping", "device"})
    List<ModbusPoint> findByDeviceIdAndEnabledTrueOrderByNameAsc(Long deviceId);

    @Query(
            "select p from ModbusPoint p join fetch p.device d "
                    + "left join fetch p.bacnetMapping where p.id = :id")
    Optional<ModbusPoint> findWithDeviceById(@Param("id") Long id);

    Optional<ModbusPoint> findByDeviceIdAndCodeIgnoreCase(Long deviceId, String code);

    boolean existsByDeviceIdAndCodeIgnoreCase(Long deviceId, String code);

    boolean existsByDeviceIdAndCodeIgnoreCaseAndIdNot(Long deviceId, String code, Long id);

    long countByDeviceId(Long deviceId);
}
