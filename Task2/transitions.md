# Таблица переходов состояний платежа

| Исходное состояние      | Переходное состояние    | Событие                  |
|-------------------------|-------------------------|--------------------------|
| —                       | `CREATED`               | `PAYMENT_CREATED`        |
| `CREATED`               | `CANCELLED`             | `CUSTOMER_CANCELLED`     |
| `CREATED`               | `DEBIT_PENDING`         | `DEBIT_REQUESTED`        |
| `DEBIT_PENDING`         | `DEBIT_PENDING`         | `DEBIT_RESULT_UNKNOWN`   |
| `DEBIT_PENDING`         | `FUNDS_DEBITED`         | `DEBIT_SUCCEEDED`        |
| `DEBIT_PENDING`         | `FAILED`                | `DEBIT_FAILED`           |
| `FUNDS_DEBITED`         | `REFUND_PENDING`        | `CUSTOMER_CANCELLED`     |
| `FUNDS_DEBITED`         | `CHECKS_PENDING`        | `CHECKS_STARTED`         |
| `CHECKS_PENDING`        | `CHECKS_PENDING`        | `FRAUD_ALLOWED`          |
| `CHECKS_PENDING`        | `CHECKS_PENDING`        | `COMPLIANCE_ALLOWED`     |
| `CHECKS_PENDING`        | `CHECKS_PENDING`        | `LIMITS_ALLOWED`         |
| `CHECKS_PENDING`        | `APPROVED`              | `ALL_CHECKS_ALLOWED`     |
| `CHECKS_PENDING`        | `REJECTED`              | `FRAUD_DENIED`           |
| `CHECKS_PENDING`        | `REJECTED`              | `COMPLIANCE_DENIED`      |
| `CHECKS_PENDING`        | `REJECTED`              | `LIMITS_DENIED`          |
| `CHECKS_PENDING`        | `MANUAL_REVIEW_PENDING` | `MANUAL_REVIEW_REQUIRED` |
| `CHECKS_PENDING`        | `APPROVED`              | `CUT_OFF_EXPIRED`        |
| `CHECKS_PENDING`        | `REFUND_PENDING`        | `CUSTOMER_CANCELLED`     |
| `MANUAL_REVIEW_PENDING` | `CHECKS_PENDING`        | `FRAUD_ALLOWED`          |
| `MANUAL_REVIEW_PENDING` | `REJECTED`              | `FRAUD_DENIED`           |
| `MANUAL_REVIEW_PENDING` | `APPROVED`              | `CUT_OFF_EXPIRED`        |
| `MANUAL_REVIEW_PENDING` | `REFUND_PENDING`        | `CUSTOMER_CANCELLED`     |
| `APPROVED`              | `CREDIT_PENDING`        | `CREDIT_STARTED`         |
| `APPROVED`              | `REFUND_PENDING`        | `CUSTOMER_CANCELLED`     |
| `REJECTED`              | `REFUND_PENDING`        | `REFUND_STARTED`         |
| `CREDIT_PENDING`        | `CREDIT_PENDING`        | `CREDIT_RESULT_UNKNOWN`  |
| `CREDIT_PENDING`        | `COMPLETED`             | `CREDIT_SUCCEEDED`       |
| `CREDIT_PENDING`        | `REFUND_PENDING`        | `CREDIT_FAILED`          |
| `REFUND_PENDING`        | `REFUND_PENDING`        | `REFUND_FAILED`          |
| `REFUND_PENDING`        | `REFUNDED`              | `REFUND_SUCCEEDED`       |
