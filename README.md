## установка

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Pan4ur/AbitourBack/main/deploy/install.sh)
```

## проверка
```bash
systemctl status abitour-server
journalctl -u abitour-server -f
```

## удаление

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Pan4ur/AbitourBack/main/deploy/uninstall.sh) --yes
bash <(curl -fsSL https://raw.githubusercontent.com/Pan4ur/AbitourBack/main/deploy/uninstall.sh) --yes --keep-data
```

## переменные окружения

- `ABITOUR_JWT_SECRET`
- `ABITOUR_DB_JDBC_URL`
- `ABITOUR_DB_USER`
- `ABITOUR_DB_PASSWORD`
- `ABITOUR_DB_MAX_POOL_SIZE`
- `ABITOUR_UPLOADS_DIR`

## эндпоинты

- `POST /api/v1/auth/login`
- `GET /api/v1/quests/active`
- `GET /api/v1/tasks/{locationId}`
- `POST /api/v1/scan?questId={questId}`
- `POST /api/v1/tasks/{taskId}/answer?questId={questId}`
- `GET /api/v1/progress?questId={questId}`
- `GET /api/v1/result?questId={questId}`
- `GET /api/v1/teacher/progress`
- `POST /api/v1/teacher/hint`
