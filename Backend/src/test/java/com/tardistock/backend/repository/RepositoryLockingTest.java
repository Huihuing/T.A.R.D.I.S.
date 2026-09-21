package com.tardistock.backend.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RepositoryLockingTest {

    @Test
    void balanceMutationRepositoriesUsePessimisticWriteLocks()
            throws Exception {
        assertPessimisticWrite(
                MemberRepository.class.getMethod(
                        "findByUsernameForUpdate", String.class));

        assertPessimisticWrite(
                WalletRepository.class.getMethod(
                        "findForUpdateByMember",
                        com.tardistock.backend.entity.Member.class));

        assertPessimisticWrite(
                WalletRepository.class.getMethod(
                        "findAllForUpdateByUsernames",
                        Collection.class));

        assertPessimisticWrite(
                PortfolioRepository.class.getMethod(
                        "findForUpdateByMemberAndSymbol",
                        com.tardistock.backend.entity.Member.class,
                        String.class));

        assertPessimisticWrite(
                UserEconomyRepository.class.getMethod(
                        "findForUpdateByMember",
                        com.tardistock.backend.entity.Member.class));
    }

    private void assertPessimisticWrite(Method method) {
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
