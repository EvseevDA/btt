package com.github.evseevda.domainlogicmodule.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Общие операции для репозиториев со сложными запросами JPQL. */
public abstract class JpaQueries {
    protected final EntityManager em;

    protected JpaQueries(EntityManager em) {
        this.em = em;
    }

    protected <T> TypedQuery<T> select(String jpql, Class<T> type) {
        return em.createQuery(jpql, type);
    }

    protected static <T> Optional<T> one(TypedQuery<T> query) {
        return Optional.ofNullable(query.getSingleResultOrNull());
    }

    protected <T> T ref(Class<T> type, Object id) {
        return id == null ? null : em.getReference(type, id);
    }

    protected void insert(Object entity) {
        em.persist(entity);
        try {
            em.flush();
        } catch (RuntimeException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new DuplicateKeyException("Нарушена уникальность записи", error);
                }
            }
            throw error;
        }
    }

    protected static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC());
    }
}
