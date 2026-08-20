package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbSellerWarehouseResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbSellerWarehouse;
import ru.oparin.solution.repository.WbSellerWarehouseRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Синхронизация складов продавца (Marketplace API) в {@code seller_warehouses}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbSellerWarehouseService {

    private final WbSellerWarehouseRepository sellerWarehouseRepository;

    /**
     * Сохраняет снимок складов продавца кабинета: upsert из WB, удаление пропавших.
     *
     * @param cabinet    кабинет
     * @param warehouses ответ GET /api/v3/warehouses
     * @return актуальный список складов кабинета после синхронизации
     */
    @Transactional
    public List<WbSellerWarehouse> saveOrUpdateForCabinet(Cabinet cabinet, List<WbSellerWarehouseResponse> warehouses) {
        if (warehouses == null) {
            log.warn("Получен пустой ответ со складами продавца, cabinetId={}", cabinet.getId());
            return sellerWarehouseRepository.findByCabinet_Id(cabinet.getId());
        }

        Set<Long> incomingIds = new HashSet<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (WbSellerWarehouseResponse dto : warehouses) {
            if (!isValidWarehouse(dto)) {
                log.warn("Пропущен некорректный склад продавца: id={}", dto != null ? dto.getId() : null);
                continue;
            }
            incomingIds.add(dto.getId());
            WbSellerWarehouse existing = sellerWarehouseRepository
                    .findByCabinet_IdAndWarehouseId(cabinet.getId(), dto.getId())
                    .orElse(null);
            if (existing == null) {
                sellerWarehouseRepository.save(mapToWarehouse(cabinet, dto));
                createdCount++;
            } else {
                updateWarehouseFields(existing, dto);
                sellerWarehouseRepository.save(existing);
                updatedCount++;
            }
        }

        int deletedCount = deleteMissingWarehouses(cabinet.getId(), incomingIds);
        log.info("Склады продавца cabinetId={}: создано {}, обновлено {}, удалено {}",
                cabinet.getId(), createdCount, updatedCount, deletedCount);
        return sellerWarehouseRepository.findByCabinet_Id(cabinet.getId());
    }

    /**
     * Склады продавца кабинета.
     */
    public List<WbSellerWarehouse> findByCabinetId(Long cabinetId) {
        return sellerWarehouseRepository.findByCabinet_Id(cabinetId);
    }

    private int deleteMissingWarehouses(Long cabinetId, Set<Long> incomingIds) {
        List<WbSellerWarehouse> existing = sellerWarehouseRepository.findByCabinet_Id(cabinetId);
        List<WbSellerWarehouse> toDelete = existing.stream()
                .filter(warehouse -> !incomingIds.contains(warehouse.getWarehouseId()))
                .toList();
        if (!toDelete.isEmpty()) {
            sellerWarehouseRepository.deleteAll(toDelete);
        }
        return toDelete.size();
    }

    private boolean isValidWarehouse(WbSellerWarehouseResponse warehouse) {
        return warehouse != null
                && warehouse.getId() != null
                && warehouse.getName() != null
                && !warehouse.getName().isBlank();
    }

    private WbSellerWarehouse mapToWarehouse(Cabinet cabinet, WbSellerWarehouseResponse dto) {
        return WbSellerWarehouse.builder()
                .cabinet(cabinet)
                .warehouseId(dto.getId())
                .name(dto.getName())
                .officeId(dto.getOfficeId())
                .cargoType(dto.getCargoType())
                .deliveryType(dto.getDeliveryType())
                .isDeleting(Boolean.TRUE.equals(dto.getIsDeleting()))
                .isProcessing(Boolean.TRUE.equals(dto.getIsProcessing()))
                .build();
    }

    private void updateWarehouseFields(WbSellerWarehouse warehouse, WbSellerWarehouseResponse dto) {
        warehouse.setName(dto.getName());
        warehouse.setOfficeId(dto.getOfficeId());
        warehouse.setCargoType(dto.getCargoType());
        warehouse.setDeliveryType(dto.getDeliveryType());
        warehouse.setIsDeleting(Boolean.TRUE.equals(dto.getIsDeleting()));
        warehouse.setIsProcessing(Boolean.TRUE.equals(dto.getIsProcessing()));
    }
}
