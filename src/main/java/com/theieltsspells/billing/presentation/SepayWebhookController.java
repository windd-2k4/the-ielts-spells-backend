package com.theieltsspells.billing.presentation;

import com.theieltsspells.billing.application.SepayWebhookService;
import com.theieltsspells.billing.application.dto.SepayWebhookPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks")
public class SepayWebhookController {

    private final SepayWebhookService sepayWebhookService;

    @PostMapping("/sepay")
    @Operation(summary = "Webhook tiếp nhận biến động số dư từ SePay")
    public ResponseEntity<Map<String, Object>> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SepayWebhookPayload payload
    ) {
        Map<String, Object> result = sepayWebhookService.processWebhook(authHeader, payload);
        return ResponseEntity.ok(result);
    }
}
