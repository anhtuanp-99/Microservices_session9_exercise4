package com.pharmacy.pharmacyservice.client;

import com.pharmacy.pharmacyservice.dto.external.StockDTO;
import com.pharmacy.pharmacyservice.entity.Medicine;
import com.pharmacy.pharmacyservice.repository.MedicineRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseGateway {

    private static final String FALLBACK_MESSAGE =
            "Không thể kết nối kho tổng. Hệ thống sẽ sử dụng dữ liệu tồn kho cục bộ để tiếp tục giao dịch";

    private final WarehouseClient warehouseClient;
    private final MedicineRepository medicineRepository;   // ← THÊM để lấy tồn kho cục bộ

    @CircuitBreaker(name = "warehouseCB", fallbackMethod = "checkWarehouseFallback")
    public StockDTO checkStock(Long medicineId) {
        log.info("Gọi Warehouse-Service để kiểm tra tồn kho, medicineId={}", medicineId);
        return warehouseClient.checkStock(medicineId);
    }

    public StockDTO checkWarehouseFallback(Long medicineId, Exception e) {
        log.warn(">>> FALLBACK Warehouse: medicineId={}, nguyên nhân={}",
                medicineId, e.getClass().getSimpleName());

        // Lấy thông tin thuốc từ DB cục bộ
        Medicine medicine = medicineRepository.findById(medicineId).orElse(null);

        int localStock = medicine != null && medicine.getStockQuantity() != null
                ? medicine.getStockQuantity()
                : 0;

        String medicineName = medicine != null ? medicine.getName() : "N/A";

        return StockDTO.builder()
                .medicineId(medicineId)
                .medicineName(medicineName)
                .availableQuantity(localStock)              // ← Tồn kho cục bộ
                .warehouseLocation("Cục bộ — " + (medicine != null ? "Có sẵn" : "Không rõ"))
                .source("LOCAL_CACHE")                       // ← Đánh dấu nguồn
                .message(FALLBACK_MESSAGE)                   // ← Message yêu cầu
                .build();
    }
}