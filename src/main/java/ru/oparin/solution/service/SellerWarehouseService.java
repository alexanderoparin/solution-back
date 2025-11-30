package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.SellerWarehouseResponse;
import ru.oparin.solution.model.SellerWarehouse;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.SellerWarehouseRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для работы со складами продавца.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SellerWarehouseService {

    private final SellerWarehouseRepository warehouseRepository;

    /**
     * Сохраняет или обновляет список складов продавца.
     * Если склад с таким ID уже существует - обновляет его.
     * Если склад новый - создает запись.
     *
     * @param warehouses список складов из API
     * @param seller     продавец, владелец складов
     * @return результат обработки (количество созданных и обновленных)
     */
    @Transactional
    public ProcessingResult saveOrUpdateWarehouses(List<SellerWarehouseResponse> warehouses, User seller) {
        if (warehouses == null || warehouses.isEmpty()) {
            log.info("Получен пустой список складов продавца для продавца (ID: {})", seller.getId());
            return new ProcessingResult(0, 0);
        }

        // Получаем существующие склады продавца из БД
        Set<Long> existingIds = warehouseRepository.findBySellerId(seller.getId()).stream()
                .map(SellerWarehouse::getId)
                .collect(Collectors.toSet());

        int createdCount = 0;
        int updatedCount = 0;

        for (SellerWarehouseResponse warehouseDto : warehouses) {
            if (!isValidWarehouse(warehouseDto)) {
                log.warn("Пропущен некорректный склад продавца: id={}, name={}", 
                        warehouseDto.getId(), warehouseDto.getName());
                continue;
            }

            if (existingIds.contains(warehouseDto.getId())) {
                // Обновляем существующий склад
                updateWarehouse(warehouseDto, seller);
                updatedCount++;
            } else {
                // Создаем новый склад
                createWarehouse(warehouseDto, seller);
                createdCount++;
            }
        }

        log.info("Обработано складов продавца для продавца (ID: {}): создано {}, обновлено {}", 
                seller.getId(), createdCount, updatedCount);
        return new ProcessingResult(createdCount, updatedCount);
    }

    private boolean isValidWarehouse(SellerWarehouseResponse warehouse) {
        return warehouse != null
                && warehouse.getId() != null
                && warehouse.getName() != null;
    }

    private void createWarehouse(SellerWarehouseResponse warehouseDto, User seller) {
        SellerWarehouse warehouse = mapToWarehouse(warehouseDto, seller);
        warehouseRepository.save(warehouse);
    }

    private void updateWarehouse(SellerWarehouseResponse warehouseDto, User seller) {
        SellerWarehouse existingWarehouse = warehouseRepository
                .findByIdAndSellerId(warehouseDto.getId(), seller.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Склад продавца с ID " + warehouseDto.getId() + " не найден в БД для продавца " + seller.getId()));

        updateWarehouseFields(existingWarehouse, warehouseDto);
        warehouseRepository.save(existingWarehouse);
    }

    private SellerWarehouse mapToWarehouse(SellerWarehouseResponse dto, User seller) {
        return SellerWarehouse.builder()
                .id(dto.getId())
                .seller(seller)
                .name(dto.getName())
                .officeId(dto.getOfficeId())
                .cargoType(dto.getCargoType())
                .deliveryType(dto.getDeliveryType())
                .isDeleting(dto.getIsDeleting())
                .isProcessing(dto.getIsProcessing())
                .build();
    }

    private void updateWarehouseFields(SellerWarehouse warehouse, SellerWarehouseResponse dto) {
        warehouse.setName(dto.getName());
        warehouse.setOfficeId(dto.getOfficeId());
        warehouse.setCargoType(dto.getCargoType());
        warehouse.setDeliveryType(dto.getDeliveryType());
        warehouse.setIsDeleting(dto.getIsDeleting());
        warehouse.setIsProcessing(dto.getIsProcessing());
    }

    /**
     * Получает все склады продавца из БД.
     */
    public List<SellerWarehouse> getSellerWarehouses(Long sellerId) {
        return warehouseRepository.findBySellerId(sellerId);
    }

    private record ProcessingResult(int createdCount, int updatedCount) {
    }
}

