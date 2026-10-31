-- Distributed locks for leader election (Spring Integration JdbcLockRegistry schema, spring-integration-jdbc 7.1.2).
CREATE TABLE INT_LOCK
(
    LOCK_KEY      CHAR(36)     NOT NULL,
    REGION        VARCHAR(100) NOT NULL,
    CLIENT_ID     CHAR(36),
    CREATED_DATE  TIMESTAMP    NOT NULL,
    EXPIRED_AFTER TIMESTAMP    NOT NULL,
    CONSTRAINT INT_LOCK_PK PRIMARY KEY (LOCK_KEY, REGION)
);
