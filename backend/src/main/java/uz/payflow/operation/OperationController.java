package uz.payflow.operation;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.payflow.config.CurrentUserId;

@Tag(name = "Payments", description = "Deposits, transfers and operation history")
@RestController
class OperationController {

    private final PaymentService paymentService;
    private final HistoryService historyService;

    OperationController(PaymentService paymentService, HistoryService historyService) {
        this.paymentService = paymentService;
        this.historyService = historyService;
    }

    @PostMapping("/api/accounts/{accountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    OperationResponse deposit(@CurrentUserId Long userId, @PathVariable Long accountId,
                              @Valid @RequestBody DepositRequest request) {
        return paymentService.deposit(userId, accountId, request);
    }

    /** 201 for a new transfer, 200 plus {@code Idempotent-Replayed: true} when a retry hits a known key. */
    @PostMapping("/api/transfers")
    ResponseEntity<OperationResponse> transfer(
            @CurrentUserId Long userId,
            @Parameter(description = "Any unique string, e.g. a UUID. Send the same value when retrying.")
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Size(min = 8, max = 64, message = "Idempotency-Key: от 8 до 64 символов") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        PaymentService.TransferResult result = paymentService.transfer(userId, idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.operation());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.operation());
    }

    @GetMapping("/api/operations")
    PageResponse<OperationResponse> history(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) OperationType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return historyService.history(userId, accountId, type, from, to, page, size);
    }
}
