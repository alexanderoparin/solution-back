package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.model.WbWarehouse;
import ru.oparin.solution.repository.WbWarehouseRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для работы со складами WB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbWarehouseService {

    private final WbWarehouseRepository warehouseRepository;

    /**
     * Сохраняет или обновляет список складов WB.
     * Если склад с таким ID уже существует - обновляет его.
     * Если склад новый - создает запись.
     *
     * @param warehouses список складов из API
     * @return результат обработки (количество созданных и обновленных)
     */
    @Transactional
    public ProcessingResult saveOrUpdateWarehouses(List<WbWarehouseResponse> warehouses) {
        if (warehouses == null || warehouses.isEmpty()) {
            log.warn("Получен пустой список складов WB");
            return new ProcessingResult(0, 0);
        }

        // Получаем существующие склады из БД
        Set<Integer> existingIds = warehouseRepository.findAll().stream()
                .map(WbWarehouse::getId)
                .collect(Collectors.toSet());

        int createdCount = 0;
        int updatedCount = 0;

        for (WbWarehouseResponse warehouseDto : warehouses) {
            if (!isValidWarehouse(warehouseDto)) {
                log.warn("Пропущен некорректный склад: id={}", warehouseDto.getId());
                continue;
            }

            if (existingIds.contains(warehouseDto.getId())) {
                // Обновляем существующий склад
                updateWarehouse(warehouseDto);
                updatedCount++;
            } else {
                // Создаем новый склад
                createWarehouse(warehouseDto);
                createdCount++;
            }
        }

        log.info("Обработано складов WB: создано {}, обновлено {}", createdCount, updatedCount);
        return new ProcessingResult(createdCount, updatedCount);
    }

    private boolean isValidWarehouse(WbWarehouseResponse warehouse) {
        return warehouse != null
                && warehouse.getId() != null
                && warehouse.getName() != null;
    }

    private void createWarehouse(WbWarehouseResponse warehouseDto) {
        WbWarehouse warehouse = mapToWarehouse(warehouseDto);
        warehouseRepository.save(warehouse);
    }

    private void updateWarehouse(WbWarehouseResponse warehouseDto) {
        WbWarehouse existingWarehouse = warehouseRepository.findById(warehouseDto.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Склад с ID " + warehouseDto.getId() + " не найден в БД"));

        updateWarehouseFields(existingWarehouse, warehouseDto);
        warehouseRepository.save(existingWarehouse);
    }

    private WbWarehouse mapToWarehouse(WbWarehouseResponse dto) {
        return WbWarehouse.builder()
                .id(dto.getId())
                .address(dto.getAddress())
                .name(dto.getName())
                .city(dto.getCity())
                .longitude(dto.getLongitude())
                .latitude(dto.getLatitude())
                .cargoType(dto.getCargoType())
                .deliveryType(dto.getDeliveryType())
                .federalDistrict(dto.getFederalDistrict())
                .selected(dto.getSelected())
                .build();
    }

    private void updateWarehouseFields(WbWarehouse warehouse, WbWarehouseResponse dto) {
        warehouse.setAddress(dto.getAddress());
        warehouse.setName(dto.getName());
        warehouse.setCity(dto.getCity());
        warehouse.setLongitude(dto.getLongitude());
        warehouse.setLatitude(dto.getLatitude());
        warehouse.setCargoType(dto.getCargoType());
        warehouse.setDeliveryType(dto.getDeliveryType());
        warehouse.setFederalDistrict(dto.getFederalDistrict());
        warehouse.setSelected(dto.getSelected());
    }

    /**
     * Получает все склады WB из БД.
     */
    public List<WbWarehouse> getAllWarehouses() {
        return warehouseRepository.findAllByOrderByIdAsc();
    }

    private record ProcessingResult(int createdCount, int updatedCount) {
    }
}

