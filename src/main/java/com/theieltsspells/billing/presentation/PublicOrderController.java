package com.theieltsspells.billing.presentation;

import com.theieltsspells.billing.application.OrderApplicationService;
import com.theieltsspells.billing.application.dto.CheckoutRequest;
import com.theieltsspells.billing.application.dto.CheckoutResponse;
import com.theieltsspells.billing.application.dto.OrderStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Public - Orders & Checkout")
public class PublicOrderController {

    private final OrderApplicationService orderService;

    @PostMapping("/checkout")
    @Operation(summary = "Đăng ký khóa học và sinh mã thanh toán VietQR")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = orderService.checkout(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderCode}/status")
    @Operation(summary = "Kiểm tra trạng thái thanh toán của đơn hàng")
    public ResponseEntity<OrderStatusResponse> getStatus(@PathVariable String orderCode) {
        return ResponseEntity.ok(orderService.getOrderStatus(orderCode));
    }
}
