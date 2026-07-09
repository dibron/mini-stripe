package com.stripe.platform.wallet.api;

import com.stripe.platform.wallet.domain.LedgerEntry;
import com.stripe.platform.wallet.domain.Transaction;
import com.stripe.platform.wallet.domain.Wallet;
import com.stripe.platform.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;



    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {

        Wallet wallet = walletService.createWallet(
                request.ownerId(),
                request.currency()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(WalletResponse.from(wallet, 0L));
    }


    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable UUID walletId) {

        Wallet wallet  = walletService.getWallet(walletId);
        Long   balance = walletService.getBalance(walletId);
        return ResponseEntity.ok(WalletResponse.from(wallet, balance));
    }


    @GetMapping("/{walletId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID walletId) {

        Long balance = walletService.getBalance(walletId);
        return ResponseEntity.ok(new BalanceResponse(walletId, balance));
    }



    @PostMapping("/{walletId}/credit")
    public ResponseEntity<TransactionResponse> credit(
            @PathVariable UUID walletId,
            @Valid @RequestBody DebitCreditRequest request) {

        Transaction txn = walletService.creditWallet(
                walletId,
                request.amountCents(),
                request.currency(),
                request.idempotencyKey(),
                request.referenceId()
        );
        Long balanceAfter = walletService.getBalance(walletId);
        return ResponseEntity.ok(TransactionResponse.from(txn, balanceAfter));
    }


    @PostMapping("/{walletId}/debit")
    public ResponseEntity<TransactionResponse> debit(
            @PathVariable UUID walletId,
            @Valid @RequestBody DebitCreditRequest request) {

        Transaction txn = walletService.debitWallet(
                walletId,
                request.amountCents(),
                request.currency(),
                request.idempotencyKey(),
                request.referenceId()
        );
        Long balanceAfter = walletService.getBalance(walletId);
        return ResponseEntity.ok(TransactionResponse.from(txn, balanceAfter));
    }

    @GetMapping("/{walletId}/history")
    public ResponseEntity<List<LedgerEntryResponse>> getHistory(
            @PathVariable UUID walletId) {

        List<LedgerEntryResponse> history = walletService
                .getLedgerHistory(walletId)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }


    public record CreateWalletRequest(
            @NotNull(message = "ownerId is required")
            UUID ownerId,

            @NotBlank(message = "currency is required")
            @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code e.g. USD, INR")
            String currency
    ) {}

    public record DebitCreditRequest(
            @NotNull(message = "amountCents is required")
            @Positive(message = "amountCents must be greater than zero")
            Long amountCents,

            @NotBlank(message = "currency is required")
            @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
            String currency,

            @NotBlank(message = "idempotencyKey is required")
            String idempotencyKey,

            UUID referenceId   // optional — links to payment_id or saga_id
    ) {}



    public record WalletResponse(
            UUID    id,
            UUID    ownerId,
            String  currency,
            String  status,
            Long    balanceCents,
            Instant createdAt
    ) {
        public static WalletResponse from(Wallet w, Long balance) {
            return new WalletResponse(
                    w.getId(),
                    w.getOwnerId(),
                    w.getCurrency(),
                    w.getStatus(),
                    balance,
                    w.getCreatedAt()
            );
        }
    }

    public record BalanceResponse(
            UUID walletId,
            Long balanceCents
    ) {}

    public record TransactionResponse(
            UUID   id,
            String type,
            Long   amountCents,
            String currency,
            String status,
            Long   balanceAfterCents
    ) {
        public static TransactionResponse from(Transaction t, Long balanceAfter) {
            return new TransactionResponse(
                    t.getId(),
                    t.getType(),
                    t.getAmount(),
                    t.getCurrency(),
                    t.getStatus(),
                    balanceAfter
            );
        }
    }

    public record LedgerEntryResponse(
            Long    id,
            UUID    walletId,
            String  entryType,
            Long    amountCents,
            Long    runningBalance,
            Instant createdAt
    ) {
        public static LedgerEntryResponse from(LedgerEntry e) {
            return new LedgerEntryResponse(
                    e.getId(),
                    e.getWalletId(),
                    e.getEntryType().name(),
                    e.getAmount(),
                    e.getRunningBalance(),
                    e.getCreatedAt()
            );
        }
    }
}