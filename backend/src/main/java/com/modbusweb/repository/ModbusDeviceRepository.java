package com.modbusweb.repository;

import com.modbusweb.domain.entity.ModbusDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModbusDeviceRepository extends JpaRepository<ModbusDevice, Long> {

    List<ModbusDevice> findAllByOrderByNameAsc();

    Optional<ModbusDevice> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
