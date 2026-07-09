package com.stripe.platform.wallet.service;

import com.stripe.platform.wallet.domain.EntryType;
import com.stripe.platform.wallet.domain.LedgerEntry;
import com.stripe.platform.wallet.domain.Transaction;
import com.stripe.platform.wallet.domain.Wallet;
import com.stripe.platform.wallet.repository.LedgerEntryRepository;
import com.stripe.platform.wallet.repository.TransactionRepository;
import com.stripe.platform.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository      walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;


    @Transactional
    public Wallet createWallet(UUID ownerId, String currency) {
        Wallet wallet = Wallet.create(ownerId, currency);
        Wallet saved  = walletRepository.save(wallet);

        log.info("Wallet created: id={} owner={} currency={}",
                saved.getId(), saved.getOwnerId(), saved.getCurrency());
        return saved;
    }


    @Transactional(readOnly = true)
    public Long getBalance(UUID walletId) {
        return ledgerEntryRepository
                .findTopByWalletIdOrderByIdDesc(walletId)
                .map(LedgerEntry::getRunningBalance)
                .orElse(0L);
    }


    @Transactional
    public Transaction debitWallet(UUID walletId,
                                   Long amountCents,
                                   String currency,
                                   String idempotencyKey,
                                   UUID referenceId) {

        Optional<Transaction> existing =
                transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency hit: debit already processed key={}", idempotencyKey);
            return existing.get();
        }


        walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));


        Long currentBalance = getBalance(walletId);
        if (currentBalance < amountCents) {
            throw new InsufficientFundsException(walletId, amountCents, currentBalance);
        }


        Transaction txn = transactionRepository.save(
                Transaction.create(
                        idempotencyKey, // dedup key
                        "PAYMENT",      // type
                        amountCents,    // amount
                        currency,       // currency
                        referenceId     // links back to payment-service payment id
                )
        );


        Long balanceAfter = currentBalance - amountCents;
        ledgerEntryRepository.save(
                LedgerEntry.of(
                        txn.getId(),    // FK → the transaction we just inserted
                        walletId,       // which wallet
                        EntryType.DEBIT,// direction: money leaving
                        amountCents,    // how much (always positive)
                        balanceAfter    // balance after this entry
                )
        );

        log.info("Wallet debited: walletId={} amount={} newBalance={}",
                walletId, amountCents, balanceAfter);
        return txn;
    }

    @Transactional
    public Transaction creditWallet(UUID walletId,
                                    Long amountCents,
                                    String currency,
                                    String idempotencyKey,
                                    UUID referenceId) {

        // Idempotency check
        Optional<Transaction> existing =
                transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency hit: credit already processed key={}", idempotencyKey);
            return existing.get();
        }

        // Wallet must exist
        walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        Transaction txn = transactionRepository.save(
                Transaction.create(idempotencyKey, "PAYMENT",
                        amountCents, currency, referenceId)
        );

        Long currentBalance = getBalance(walletId);
        Long balanceAfter   = currentBalance + amountCents;
        ledgerEntryRepository.save(
                LedgerEntry.of(txn.getId(), walletId,
                        EntryType.CREDIT, amountCents, balanceAfter)
        );

        log.info("Wallet credited: walletId={} amount={} newBalance={}",
                walletId, amountCents, balanceAfter);
        return txn;
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getLedgerHistory(UUID walletId) {
        // Returns all entries oldest-first — shows the full money trail
        return ledgerEntryRepository.findByWalletIdOrderByIdAsc(walletId);
    }
}