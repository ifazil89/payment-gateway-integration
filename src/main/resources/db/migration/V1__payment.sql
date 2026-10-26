-- One row per payment. Card numbers and CVVs are NEVER stored (only the masked PAN).
CREATE TABLE payment (
    id                    VARCHAR(36)              NOT NULL PRIMARY KEY,
    client_id             VARCHAR(64)              NOT NULL,
    idempotency_key       VARCHAR(100)             NOT NULL,
    request_hash          VARCHAR(64)              NOT NULL,
    merchant_order_id     VARCHAR(64)              NOT NULL,
    card_type             VARCHAR(20)              NOT NULL,
    masked_pan            VARCHAR(19),
    amount                NUMERIC(19, 3)           NOT NULL,
    currency              VARCHAR(3)               NOT NULL,
    email                 VARCHAR(254)             NOT NULL,
    state                 VARCHAR(30)              NOT NULL,
    failure_reason        VARCHAR(500),
    ni_order_reference    VARCHAR(64),
    ni_payment_reference  VARCHAR(64),
    ni_state              VARCHAR(30),
    threeds_trans_status  VARCHAR(2),
    threeds_acs_url       VARCHAR(2048),
    threeds_creq          VARCHAR(4000),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT                   NOT NULL,
    CONSTRAINT uk_payment_client_idempotency UNIQUE (client_id, idempotency_key)
);

CREATE INDEX ix_payment_ni_order ON payment (ni_order_reference);
CREATE INDEX ix_payment_merchant_order ON payment (merchant_order_id);
