# Abitour Server

Отдельный backend-проект для `Abitour`.

## Установка

```bash
sudo bash <(curl -fsSL https://raw.githubusercontent.com/Pan4ur/AbitourBack/main/deploy/install.sh)
```

Во время установки скрипт спросит:

- `ABITOUR_PORT`
- `ABITOUR_JWT_SECRET`
- `ABITOUR_DB_JDBC_URL`
- `ABITOUR_DB_USER`
- `ABITOUR_DB_PASSWORD`
- `ABITOUR_DB_MAX_POOL_SIZE`
- `ABITOUR_UPLOADS_DIR`

Если просто нажать Enter, будет использовано значение по умолчанию.

Конфигурация сохраняется в `/etc/abitour-server/abitour-server.env`.

## Проверка

```bash
systemctl status abitour-server
journalctl -u abitour-server -f
```

## Удаление

Полное удаление вместе с данными:

```bash
sudo bash <(curl -fsSL https://raw.githubusercontent.com/Pan4ur/AbitourBack/main/deploy/uninstall.sh) --yes
```

Удаление сервиса с сохранением данных:

```bash
sudo bash <(curl -fsSL https://raw.githubusercontent.com/Pan4ur/AbitourBack/main/deploy/uninstall.sh) --yes --keep-data
```

## Переменные окружения

- `ABITOUR_PORT`
- `ABITOUR_JWT_SECRET`
- `ABITOUR_DB_JDBC_URL`
- `ABITOUR_DB_USER`
- `ABITOUR_DB_PASSWORD`
- `ABITOUR_DB_MAX_POOL_SIZE`
- `ABITOUR_UPLOADS_DIR`

## Эндпоинты

- `POST /api/v1/auth/login`
- `GET /api/v1/quests/active`
- `GET /api/v1/tasks/{locationId}`
- `POST /api/v1/scan?questId={questId}`
- `POST /api/v1/tasks/{taskId}/answer?questId={questId}`
- `GET /api/v1/progress?questId={questId}`
- `GET /api/v1/result?questId={questId}`
- `GET /api/v1/teacher/progress`
- `POST /api/v1/teacher/hint`
