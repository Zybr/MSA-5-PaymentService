# Task 4.2. Локальный запуск

Команды ниже запускаются из корня репозитория. Каждый блок самодостаточен: его можно запускать отдельно через RunMarkdown в IDE. Переходить в `Task4.2` командой `cd` не требуется.

## 1. Проверить инструменты

Проверить Docker.

```bash
docker version
```

Проверить Docker Compose.

```bash
docker compose version
```

Проверить `xclip`, который используется для передачи `paymentId` между независимыми Markdown-блоками через буфер обмена.

```bash
command -v xclip
```

Проверить `jq`, который форматирует JSON-ответы.

```bash
command -v jq
```

Для стека нужно около 4 ГБ свободной оперативной памяти. Порты `8080`, `8081`, `8082`, `8088`, `9200`, `9300`, `9600` и `26500` должны быть свободны.

## 2. Запустить систему

Собрать Java-сервис и запустить весь стек.

```bash
docker compose --env-file Task4.2/.env -f Task4.2/docker-compose.yml up --build -d
```

При первом запуске Docker скачает образы Camunda и Elasticsearch. Это может занять несколько минут.

Проверить контейнеры.

```bash
docker compose --env-file Task4.2/.env -f Task4.2/docker-compose.yml ps
```

Проверить деплой BPMN.

```bash
docker compose --env-file Task4.2/.env -f Task4.2/docker-compose.yml logs deploy-process
```

Ожидаемый результат:

```text
Process deployed.
```

Проверить Java-сервис.

```bash
curl -fsS http://localhost:8080/health | jq .
```

Ожидаемый результат:

```json
{"status":"UP"}
```

## 3. Проверить успешный платёж

Запустить процесс.

```bash
curl -fsS -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"success"}' \
  | jq . \
  | tee /dev/stderr \
  | jq -r .paymentId \
  | xclip -selection clipboard
```

Команда выводит `paymentId` и копирует его в буфер обмена. Проверить результат, прочитав идентификатор через `xclip`.

```bash
sleep 2
xclip -selection clipboard -o \
  | xargs -I{} curl -fsS "http://localhost:8080/payments/{}" \
  | jq .
```

Итоговый статус:

```text
COMPLETED
```

Доступные сценарии:

| `scenario`          | Результат                                      |
|---------------------|------------------------------------------------|
| `success`           | Успешный платёж                                |
| `debit-failure`     | Ошибка списания                                |
| `fraud-deny`        | Отказ антифрода и возврат                      |
| `compliance-deny`   | Отказ compliance и возврат                     |
| `limits-deny`       | Отказ проверки лимитов и возврат               |
| `manual-review`     | Ожидание решения через endpoint `/review`      |
| `manual-allow`      | Автоматическая эмуляция ручного разрешения     |
| `manual-deny`       | Автоматическая эмуляция ручного отказа         |
| `cutoff`            | Разрешение после истечения cut-off             |
| `credit-failure`    | Ошибка зачисления и автоматический возврат      |

## 4. Проверить компенсацию

Запустить отказ антифрода. `POST` только запускает асинхронный BPMN-процесс, поэтому начальный статус `PROCESS_STARTED` является нормальным. Команда сохраняет `paymentId` в буфер обмена.

```bash
curl -fsS -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"fraud-deny"}' \
  | jq . \
  | tee /dev/stderr \
  | jq -r .paymentId \
  | xclip -selection clipboard
```

Подождать завершения процесса и получить итоговое состояние.

```bash
sleep 2
xclip -selection clipboard -o \
  | xargs -I{} curl -fsS "http://localhost:8080/payments/{}" \
  | jq .
```

Итоговый статус платежа — `REFUNDED`. В `history` есть события `FRAUD_DENY`, `REFUND_SUCCEEDED` и `SECURITY_NOTIFIED`.

Запустить сбой зачисления контрагенту.

```bash
curl -fsS -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"credit-failure"}' \
  | jq . \
  | tee /dev/stderr \
  | jq -r .paymentId \
  | xclip -selection clipboard
```

Подождать завершения процесса и получить итоговое состояние.

```bash
sleep 2
xclip -selection clipboard -o \
  | xargs -I{} curl -fsS "http://localhost:8080/payments/{}" \
  | jq .
```

Итоговый статус платежа — `REFUNDED`.

## 5. Проверить ручной антифрод

Запустить процесс с ожиданием ручного решения.

```bash
curl -fsS -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"manual-review"}' \
  | jq . \
  | tee /dev/stderr \
  | jq -r .paymentId \
  | xclip -selection clipboard
```

Команда выводит `paymentId` и копирует его в буфер обмена. Передать разрешение для сохранённого платежа.

```bash
xclip -selection clipboard -o \
  | xargs -I{} curl -fsS -X POST "http://localhost:8080/payments/{}/review" \
      -H 'Content-Type: application/json' \
      -d '{"decision":"ALLOW"}' \
  | jq .
```

Ответ на запрос решения может ещё содержать `MANUAL_REVIEW_PENDING`, потому что сообщение обрабатывается асинхронно. Проверить итоговое состояние.

```bash
sleep 2
xclip -selection clipboard -o \
  | xargs -I{} curl -fsS "http://localhost:8080/payments/{}" \
  | jq .
```

Для `ALLOW` итоговый статус — `COMPLETED`. Для отказа использовать `DENY`: тогда итоговый статус будет `REFUNDED`, а процесс уведомит службу безопасности.

## 6. Проверить cut-off

В рабочем процессе cut-off равен 20 минутам. Для быстрой проверки передать две секунды.

```bash
curl -fsS -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"cutoff","cutOffDuration":"PT2S"}' \
  | jq . \
  | tee /dev/stderr \
  | jq -r .paymentId \
  | xclip -selection clipboard
```

Подождать срабатывания таймера и получить итоговое состояние.

```bash
sleep 4
xclip -selection clipboard -o \
  | xargs -I{} curl -fsS "http://localhost:8080/payments/{}" \
  | jq .
```

После таймера платёж автоматически разрешается. В `history` появляется `CUT_OFF_EXPIRED`, итоговый статус — `COMPLETED`.

## 7. Запустить автоматическую проверку

Smoke-тест проверяет все поддерживаемые сценарии и компенсации.

```bash
./Task4.2/smoke-test.sh
```

Ожидаемый результат:

```text
All payment scenarios passed.
```

## 8. Открыть Camunda

- Operate: `http://localhost:8081`
- Tasklist: `http://localhost:8082`
- логин и пароль: `demo` / `demo`

Процесс в Operate появляется с небольшой задержкой после выполнения.

## 9. Посмотреть логи

Логи Java-сервиса.

```bash
docker compose --env-file Task4.2/.env -f Task4.2/docker-compose.yml logs -f payment-orchestrator
```

Логи Zeebe.

```bash
docker compose --env-file Task4.2/.env -f Task4.2/docker-compose.yml logs -f zeebe
```

## 10. Остановить систему

Остановить контейнеры и сохранить данные Camunda.

```bash
docker compose --env-file Task4.2/.env -f Task4.2/docker-compose.yml down
```

Полностью удалить контейнеры и локальные данные.

```bash
docker compose --env-file Task4.2/.env -f Task4.2/docker-compose.yml down -v
```
