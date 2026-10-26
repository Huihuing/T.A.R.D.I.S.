package com.tardistock.backend.integration;

import com.tardistock.backend.controller.AccountController;
import com.tardistock.backend.controller.TradeController;
import com.tardistock.backend.controller.LimitOrderController;
import com.tardistock.backend.entity.*;
import com.tardistock.backend.repository.*;
import com.tardistock.backend.service.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL/InnoDB locks and Flyway migrations; never connects to a remote database. */
@SpringJUnitConfig(TradingIntegrityIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "TARDIS_TEST_DB_URL", matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/tardis_audit.*")
class TradingIntegrityIntegrationTest {
    @Autowired MemberRepository members;
    @Autowired WalletRepository wallets;
    @Autowired PortfolioRepository portfolios;
    @Autowired TradeHistoryRepository histories;
    @Autowired LedgerEntryRepository ledgerEntries;
    @Autowired MutationRequestRepository requests;
    @Autowired MutationRequestService mutations;
    @Autowired TradeController trades;
    @Autowired AccountController accounts;
    @Autowired FinnhubPriceService prices;
    @Autowired NotificationService notifications;
    @Autowired LedgerService ledger;
    @Autowired LimitOrderRepository limitOrders;
    @Autowired LimitOrderService orderService;
    @Autowired LimitOrderController orderController;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Flyway flyway;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() {
        reset(prices, notificationMock(), ledgerMock());
        when(prices.getTradingPrice(anyString())).thenReturn(100.0);
        alice = "a_" + UUID.randomUUID().toString().replace("-", "");
        bob = "b_" + UUID.randomUUID().toString().replace("-", "");
        inTransaction(() -> {
            for (String username : List.of(alice, bob)) {
                Member member = members.save(new Member(username, "test-password", username,
                        username + "@example.test", "test-pin-hash"));
                wallets.save(new Wallet(member, 1_000.0));
            }
            return null;
        });
    }

    @Test
    void allMigrationsAndHibernateSchemaValidateAgainstMySql() {
        assertDoesNotThrow(() -> flyway.validate());
        assertEquals("11", flyway.info().current().getVersion().getVersion());
    }

    @Test
    void concurrentBuysCannotOverspendOrLoseHoldingUpdates() throws Exception {
        List<ResponseEntity<?>> results = concurrently(
                () -> trades.buyRequest(Map.of("symbol", "AAPL", "amount", 8), auth(alice), key()),
                () -> trades.buyRequest(Map.of("symbol", "AAPL", "amount", 8), auth(alice), key()));
        assertEquals(1, results.stream().filter(this::successful).count());
        assertBalance(alice, "200.00");
        inTransaction(() -> {
            Member member = member(alice);
            assertEquals(8, portfolios.findByMemberAndSymbol(member, "AAPL").orElseThrow().getAmount());
            assertEquals(1, histories.findTop500ByMemberOrderByTradeTimeDesc(member).size());
            assertEquals(1, ledgerEntries.findTop100ByMemberOrderByCreatedAtDesc(member).size());
            return null;
        });
    }

    @Test
    void concurrentSellsCannotSellTheSameSharesTwice() throws Exception {
        inTransaction(() -> { portfolios.save(new Portfolio(member(alice), "AAPL", 10, 100)); return null; });
        List<ResponseEntity<?>> results = concurrently(
                () -> trades.sellRequest(Map.of("symbol", "AAPL", "amount", 8), auth(alice), key()),
                () -> trades.sellRequest(Map.of("symbol", "AAPL", "amount", 8), auth(alice), key()));
        assertEquals(1, results.stream().filter(this::successful).count());
        assertBalance(alice, "1800.00");
        inTransaction(() -> {
            assertEquals(2, portfolios.findByMemberAndSymbol(member(alice), "AAPL").orElseThrow().getAmount());
            assertEquals(1, histories.findTop500ByMemberOrderByTradeTimeDesc(member(alice)).size());
            return null;
        });
    }

    @Test
    void concurrentDuplicateBuyCommitsOnceAndReplaysReceipt() throws Exception {
        String key = key();
        Map<String, Object> payload = Map.of("symbol", "AAPL", "amount", 2);
        List<ResponseEntity<?>> results = concurrently(
                () -> trades.buyRequest(payload, auth(alice), key),
                () -> trades.buyRequest(payload, auth(alice), key));
        assertTrue(results.stream().allMatch(this::successful));
        assertBalance(alice, "800.00");
        inTransaction(() -> {
            assertEquals(1, histories.findTop500ByMemberOrderByTradeTimeDesc(member(alice)).size());
            assertTrue(requests.findByUsernameAndRequestKey(alice, key).isPresent());
            return null;
        });
        assertEquals(409, trades.buyRequest(Map.of("symbol", "AAPL", "amount", 3), auth(alice), key)
                .getStatusCode().value());
        assertEquals(409, trades.sellRequest(payload, auth(alice), key).getStatusCode().value());
        assertBalance(alice, "800.00");
    }

    @Test
    void duplicateTransferReturnsOneReceiptAndOnePairOfLedgerEntries() throws Exception {
        String key = key();
        Map<String, Object> payload = transferPayload(bob, "125.01");
        List<ResponseEntity<?>> results = concurrently(
                () -> accounts.transferRequest(payload, auth(alice), key),
                () -> accounts.transferRequest(payload, auth(alice), key));
        assertTrue(results.stream().allMatch(this::successful));
        assertEquals(String.valueOf(((Map<?, ?>) results.get(0).getBody()).get("transactionId")),
                String.valueOf(((Map<?, ?>) results.get(1).getBody()).get("transactionId")));
        assertBalance(alice, "874.99");
        assertBalance(bob, "1125.01");
        assertLedgerCount(alice, 1);
        assertLedgerCount(bob, 1);
        assertEquals(409, accounts.transferRequest(transferPayload(bob, "126.00"), auth(alice), key)
                .getStatusCode().value());
    }

    @Test
    void oppositeDirectionTransfersUseConsistentLocksAndConserveMoney() throws Exception {
        List<ResponseEntity<?>> results = concurrently(
                () -> accounts.transferRequest(transferPayload(bob, "125.00"), auth(alice), key()),
                () -> accounts.transferRequest(transferPayload(alice, "75.00"), auth(bob), key()));
        assertTrue(results.stream().allMatch(this::successful));
        assertBalance(alice, "950.00");
        assertBalance(bob, "1050.00");
        assertLedgerCount(alice, 2);
        assertLedgerCount(bob, 2);
    }

    @Test
    void failureAfterBothLedgerWritesRollsBackWalletsLedgerAndReservation() {
        String key = key();
        doThrow(new IllegalStateException("injected notification persistence failure"))
                .when(notificationMock()).create(any(), eq("TRANSFER"), anyString());
        ResponseEntity<?> failed = accounts.transferRequest(transferPayload(bob, "125.00"), auth(alice), key);
        assertEquals(500, failed.getStatusCode().value());
        assertBalance(alice, "1000.00");
        assertBalance(bob, "1000.00");
        assertLedgerCount(alice, 0);
        assertLedgerCount(bob, 0);
        assertTrue(requests.findByUsernameAndRequestKey(alice, key).isEmpty());
        reset(notificationMock());
        assertTrue(successful(accounts.transferRequest(transferPayload(bob, "125.00"), auth(alice), key)));
        assertBalance(alice, "875.00");
    }

    @Test
    void staleOrUnavailableQuoteCannotChangeWalletPortfolioHistoryOrLedger() {
        when(prices.getTradingPrice("AAPL")).thenReturn(0.0);
        ResponseEntity<?> response = trades.buyRequest(Map.of("symbol", "AAPL", "amount", 1), auth(alice), key());
        assertFalse(successful(response));
        assertBalance(alice, "1000.00");
        assertLedgerCount(alice, 0);
        inTransaction(() -> {
            assertTrue(portfolios.findByMember(member(alice)).isEmpty());
            assertTrue(histories.findTop500ByMemberOrderByTradeTimeDesc(member(alice)).isEmpty());
            return null;
        });
    }

    @Test
    void centsSurviveLargeBalancesAndTransferLedgerMatchesExactly() {
        inTransaction(() -> {
            Wallet wallet = wallets.findByMember(member(alice)).orElseThrow();
            wallet.setBalanceAmount(new BigDecimal("9007199254740992.03"));
            wallets.save(wallet);
            return null;
        });
        assertTrue(successful(accounts.transferRequest(transferPayload(bob, "0.01"), auth(alice), key())));
        assertBalance(alice, "9007199254740992.02");
        assertBalance(bob, "1000.01");
        inTransaction(() -> {
            LedgerEntry entry = ledgerEntries.findTop100ByMemberOrderByCreatedAtDesc(member(alice)).getFirst();
            assertEquals(new BigDecimal("-0.01"), entry.getAmountValue());
            assertEquals(new BigDecimal("9007199254740992.02"), entry.getBalanceAfterAmount());
            return null;
        });
    }

    @Test
    void quantityOverflowAndZeroCentTradeLeaveStateUnchanged() {
        inTransaction(() -> { portfolios.save(new Portfolio(member(alice), "AAPL", Integer.MAX_VALUE, 1)); return null; });
        assertFalse(successful(trades.buyRequest(Map.of("symbol", "AAPL", "amount", 1), auth(alice), key())));
        when(prices.getTradingPrice("DUST")).thenReturn(0.000001);
        assertFalse(successful(trades.buyRequest(Map.of("symbol", "DUST", "amount", 1), auth(alice), key())));
        assertBalance(alice, "1000.00");
        assertLedgerCount(alice, 0);
    }

    @Test
    void ledgerFailureAfterHoldingAndHistoryWritesRollsBackTheWholeTrade() {
        String key = key();
        doThrow(new IllegalStateException("injected ledger persistence failure")).when(ledgerMock())
                .record(any(Member.class), eq("STOCK_BUY"), any(BigDecimal.class), any(BigDecimal.class),
                        isNull(), eq("AAPL"), anyString());
        assertThrows(IllegalStateException.class, () -> trades.buyRequest(
                Map.of("symbol", "AAPL", "amount", 2), auth(alice), key));
        assertBalance(alice, "1000.00");
        assertLedgerCount(alice, 0);
        inTransaction(() -> {
            assertTrue(histories.findTop500ByMemberOrderByTradeTimeDesc(member(alice)).isEmpty());
            assertTrue(portfolios.findByMember(member(alice)).isEmpty());
            assertTrue(requests.findByUsernameAndRequestKey(alice, key).isEmpty());
            return null;
        });
    }

    @Test
    void repeatedLimitOrderCreationCommitsOnlyOnePendingOrder() throws Exception {
        String key = key();
        Map<String, Object> payload = Map.of("side", "BUY", "symbol", "AAPL", "amount", 2, "limitPrice", "100.00");
        List<ResponseEntity<?>> results = concurrently(
                () -> orderController.createRequest(payload, auth(alice), key),
                () -> orderController.createRequest(payload, auth(alice), key));
        assertTrue(results.stream().allMatch(response -> response.getStatusCode().is2xxSuccessful()));
        inTransaction(() -> {
            assertEquals(1, limitOrders.countByMemberAndStatus(member(alice), "PENDING"));
            return null;
        });
        assertBalance(alice, "1000.00");
        assertEquals(409, orderController.createRequest(
                Map.of("side", "BUY", "symbol", "AAPL", "amount", 3, "limitPrice", "100.00"),
                auth(alice), key).getStatusCode().value());
    }

    @Test
    void simultaneousLimitOrderScansCannotFillTheSameOrderTwice() throws Exception {
        String symbol = "L" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        LimitOrder order = orderService.create(alice, "BUY", symbol, 2, 100.0);
        concurrently(
                () -> ResponseEntity.ok(Map.of("processed", orderService.processSymbol(symbol, () -> 100.0))),
                () -> ResponseEntity.ok(Map.of("processed", orderService.processSymbol(symbol, () -> 100.0))));
        assertBalance(alice, "800.00");
        assertLedgerCount(alice, 1);
        inTransaction(() -> {
            assertEquals("FILLED", limitOrders.findById(order.getId()).orElseThrow().getStatus());
            assertEquals(1, histories.findTop500ByMemberOrderByTradeTimeDesc(member(alice)).size());
            return null;
        });
    }

    @Test
    void cancellationRacingWithFillHasExactlyOneTerminalOutcome() throws Exception {
        String symbol = "L" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        LimitOrder order = orderService.create(alice, "BUY", symbol, 2, 100.0);
        concurrently(
                () -> ResponseEntity.ok(Map.of("processed", orderService.processSymbol(symbol, () -> 100.0))),
                () -> {
                    try {
                        orderService.cancel(alice, order.getId());
                        return ResponseEntity.ok(Map.of("status", "CANCELLED"));
                    } catch (IllegalStateException alreadyFilled) {
                        return ResponseEntity.status(409).build();
                    }
                });
        String status = inTransaction(() -> limitOrders.findById(order.getId()).orElseThrow().getStatus());
        assertTrue(Set.of("CANCELLED", "FILLED").contains(status));
        assertBalance(alice, "FILLED".equals(status) ? "800.00" : "1000.00");
        assertLedgerCount(alice, "FILLED".equals(status) ? 1 : 0);
    }

    @Test
    void unavailableQuoteKeepsPersistedLimitOrderPending() {
        LimitOrder order = orderService.create(alice, "BUY", "SAFE", 2, 100.0);
        assertEquals(0, orderService.processSymbol("SAFE", () -> 0.0));
        assertBalance(alice, "1000.00");
        assertLedgerCount(alice, 0);
        inTransaction(() -> {
            assertEquals("PENDING", limitOrders.findById(order.getId()).orElseThrow().getStatus());
            return null;
        });
    }

    @Test
    void marketAndLimitBuysUseMarketPrecisionForAverageAndCentsForCash() {
        when(prices.getTradingPrice("FRACTION")).thenReturn(0.006);
        assertTrue(successful(trades.buyRequest(Map.of("symbol", "FRACTION", "amount", 1), auth(alice), key())));
        LimitOrder order = orderService.create(bob, "BUY", "FRACTION", 1, 0.01);
        assertEquals(1, orderService.processSymbol("FRACTION", () -> 0.006));
        assertBalance(alice, "999.99");
        assertBalance(bob, "999.99");
        inTransaction(() -> {
            for (String username : List.of(alice, bob)) {
                assertEquals(new BigDecimal("0.006000"), portfolios
                        .findByMemberAndSymbol(member(username), "FRACTION").orElseThrow().getAveragePriceAmount());
            }
            assertEquals("FILLED", limitOrders.findById(order.getId()).orElseThrow().getStatus());
            return null;
        });
    }

    @Test
    void quoteExpiringWhileWaitingForLocksCannotExecuteMarketOrder() {
        when(prices.getTradingPrice("AAPL")).thenReturn(100.0, 0.0);
        assertFalse(successful(trades.buyRequest(Map.of("symbol", "AAPL", "amount", 1), auth(alice), key())));
        assertBalance(alice, "1000.00");
        assertLedgerCount(alice, 0);
    }

    private List<ResponseEntity<?>> concurrently(Supplier<ResponseEntity<?>> first,
                                                  Supplier<ResponseEntity<?>> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ResponseEntity<?>> a = executor.submit(() -> { start.await(); return first.get(); });
            Future<ResponseEntity<?>> b = executor.submit(() -> { start.await(); return second.get(); });
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean successful(ResponseEntity<?> response) {
        return response.getStatusCode().is2xxSuccessful() && response.getBody() instanceof Map<?, ?> body
                && "SUCCESS".equals(body.get("status"));
    }
    private String key() { return UUID.randomUUID().toString(); }
    private Member member(String username) { return members.findByUsername(username).orElseThrow(); }
    private UsernamePasswordAuthenticationToken auth(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }
    private Map<String, Object> transferPayload(String to, String amount) {
        return Map.of("toUser", to, "amount", amount, "accountPassword", "1234");
    }
    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
    private LedgerService ledgerMock() { return AopTestUtils.getUltimateTargetObject(ledger); }
    private NotificationService notificationMock() { return AopTestUtils.getUltimateTargetObject(notifications); }
    private void assertBalance(String username, String expected) {
        assertEquals(new BigDecimal(expected), inTransaction(() -> wallets.findByMember(member(username))
                .orElseThrow().getBalanceAmount()));
    }
    private void assertLedgerCount(String username, int expected) {
        inTransaction(() -> {
            assertEquals(expected, ledgerEntries.findTop100ByMemberOrderByCreatedAtDesc(member(username)).size());
            return null;
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class)
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("TARDIS_TEST_DB_URL");
            if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/tardis_audit.*")) {
                throw new IllegalArgumentException("Integration tests require a dedicated local tardis_audit database.");
            }
            return new DriverManagerDataSource(url, System.getenv("TARDIS_TEST_DB_USERNAME"),
                    System.getenv("TARDIS_TEST_DB_PASSWORD"));
        }
        @Bean(initMethod = "migrate") Flyway flyway(DataSource dataSource) {
            return Flyway.configure().dataSource(dataSource).cleanDisabled(true).load();
        }
        @Bean @DependsOn("flyway")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.tardistock.backend.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MutationRequestService mutationRequestService(MutationRequestRepository requests,
                PlatformTransactionManager manager, ObjectMapper json) {
            return new MutationRequestService(requests, manager, json);
        }
        @Bean FinnhubPriceService prices() { return mock(FinnhubPriceService.class); }
        @Bean NotificationService notifications() { return mock(NotificationService.class); }
        @Bean PasswordEncoder encoder() {
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            when(encoder.matches("1234", "test-pin-hash")).thenReturn(true);
            return encoder;
        }
        @Bean LedgerService ledger(LedgerEntryRepository entries, MemberRepository members) {
            return spy(new LedgerService(entries, members));
        }
        @Bean LimitOrderService orderService(LimitOrderRepository orders, MemberRepository members,
                WalletRepository wallets, PortfolioRepository portfolios, TradeHistoryRepository histories,
                LedgerService ledger, NotificationService notifications) {
            return new LimitOrderService(orders, members, wallets, portfolios, histories, ledger, notifications);
        }
        @Bean LimitOrderController orderController(LimitOrderService orders, MutationRequestService mutations) {
            return new LimitOrderController(orders, mutations);
        }
        @Bean TradeController trades(MemberRepository members, WalletRepository wallets,
                PortfolioRepository portfolios, TradeHistoryRepository histories, FinnhubPriceService prices,
                LedgerService ledger, MutationRequestService mutations) {
            var controller = new TradeController(members, wallets, portfolios, histories, prices,
                    mock(EconomyService.class), ledger);
            controller.setMutationRequestService(mutations);
            return controller;
        }
        @Bean AccountController accounts(WalletRepository wallets, MemberRepository members,
                PasswordEncoder encoder, NotificationService notifications, LedgerService ledger,
                MutationRequestService mutations) {
            var controller = new AccountController(wallets, members, encoder, notifications, ledger,
                    mock(RefreshTokenService.class), mock(GoogleIdentityService.class), mock(PasswordResetService.class));
            controller.setMutationRequestService(mutations);
            return controller;
        }
    }
}
