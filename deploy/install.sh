#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="abitour-server"
APP_USER="abitour"
APP_GROUP="abitour"
INSTALL_DIR="/opt/${APP_NAME}"
CONFIG_DIR="/etc/${APP_NAME}"
DATA_DIR="/var/lib/${APP_NAME}"
ENV_FILE="${CONFIG_DIR}/${APP_NAME}.env"
SERVICE_FILE="/etc/systemd/system/${APP_NAME}.service"
REPO_SLUG="${ABITOUR_REPO:-Pan4ur/AbitourBack}"
REPO_REF="${ABITOUR_REF:-main}"
DEFAULT_PORT="8080"
DEFAULT_DB_JDBC_URL="jdbc:sqlite:${DATA_DIR}/abitour.db"
DEFAULT_DB_MAX_POOL_SIZE="10"
DEFAULT_UPLOADS_DIR="${DATA_DIR}/uploads"

ABITOUR_PORT_VALUE=""
ABITOUR_JWT_SECRET_VALUE=""
ABITOUR_DB_JDBC_URL_VALUE=""
ABITOUR_DB_USER_VALUE=""
ABITOUR_DB_PASSWORD_VALUE=""
ABITOUR_DB_MAX_POOL_SIZE_VALUE=""
ABITOUR_UPLOADS_DIR_VALUE=""

SUDO=""
if [[ "${EUID}" -ne 0 ]]; then
    if ! command -v sudo >/dev/null 2>&1; then
        echo "Для установки ${APP_NAME} требуется sudo" >&2
        exit 1
    fi
    SUDO="sudo"
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

log() {
    echo "[${APP_NAME}] $*"
}

fail() {
    echo "[${APP_NAME}] ERROR: $*" >&2
    exit 1
}

run_root() {
    if [[ -n "${SUDO}" ]]; then
        "${SUDO}" "$@"
    else
        "$@"
    fi
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

read_existing_env_value() {
    local key="$1"
    if run_root test -f "${ENV_FILE}"; then
        run_root bash -lc "set -a; source '${ENV_FILE}'; printf '%s' \"\${${key}:-}\""
    fi
}

resolve_default() {
    local key="$1"
    local fallback="${2:-}"
    local current="${!key:-}"
    if [[ -n "${current}" ]]; then
        printf '%s' "${current}"
        return
    fi

    local existing
    existing="$(read_existing_env_value "${key}")"
    if [[ -n "${existing}" ]]; then
        printf '%s' "${existing}"
        return
    fi

    printf '%s' "${fallback}"
}

install_packages() {
    if command_exists apt-get; then
        run_root apt-get update
        run_root apt-get install -y ca-certificates curl tar unzip openjdk-21-jre-headless
        return
    fi

    if command_exists dnf; then
        run_root dnf install -y ca-certificates curl tar unzip java-21-openjdk-headless
        return
    fi

    if command_exists yum; then
        run_root yum install -y ca-certificates curl tar unzip java-21-openjdk-headless
        return
    fi

    fail "неподдерживаемый пакетный менеджер. Установите curl, tar, unzip и Java 21 вручную."
}

ensure_user() {
    if id -u "${APP_USER}" >/dev/null 2>&1; then
        return
    fi

    log "создаю системного пользователя ${APP_USER}"
    run_root useradd \
        --system \
        --home-dir "${INSTALL_DIR}" \
        --shell /usr/sbin/nologin \
        --user-group \
        --comment "Сервис backend Abitour" \
        "${APP_USER}"
}

generate_secret() {
    if command_exists openssl; then
        openssl rand -hex 32
        return
    fi

    od -An -N 32 -tx1 /dev/urandom | tr -d ' \n'
}

prompt_with_default() {
    local prompt="$1"
    local default="$2"
    local value

    if [[ -n "${default}" ]]; then
        read -r -p "${prompt} [${default}]: " value
        printf '%s' "${value:-${default}}"
    else
        read -r -p "${prompt}: " value
        printf '%s' "${value}"
    fi
}

prompt_secret() {
    local prompt="$1"
    local existing="$2"
    local value

    if [[ -n "${existing}" ]]; then
        read -r -s -p "${prompt} [оставьте пустым, чтобы сохранить текущее]: " value
        printf '\n' >&2
        printf '%s' "${value:-${existing}}"
    else
        read -r -s -p "${prompt} [оставьте пустым для автогенерации]: " value
        printf '\n' >&2
        if [[ -n "${value}" ]]; then
            printf '%s' "${value}"
        else
            printf '%s' "$(generate_secret)"
        fi
    fi
}

prompt_password() {
    local prompt="$1"
    local existing="$2"
    local value

    if [[ -n "${existing}" ]]; then
        read -r -s -p "${prompt} [оставьте пустым, чтобы сохранить текущее]: " value
        printf '\n' >&2
        printf '%s' "${value:-${existing}}"
    else
        read -r -s -p "${prompt} [оставьте пустым для пустого значения]: " value
        printf '\n' >&2
        printf '%s' "${value}"
    fi
}

prompt_positive_integer() {
    local prompt="$1"
    local default="$2"
    local value

    while true; do
        value="$(prompt_with_default "${prompt}" "${default}")"
        if [[ "${value}" =~ ^[0-9]+$ ]] && (( value > 0 )); then
            printf '%s' "${value}"
            return
        fi
        echo "Введите положительное целое число." >&2
    done
}

prompt_port() {
    local prompt="$1"
    local default="$2"
    local value

    while true; do
        value="$(prompt_with_default "${prompt}" "${default}")"
        if [[ "${value}" =~ ^[0-9]+$ ]] && (( value >= 1 && value <= 65535 )); then
            printf '%s' "${value}"
            return
        fi
        echo "Введите номер порта от 1 до 65535." >&2
    done
}

prompt_configuration() {
    log "запрашиваю конфигурацию"

    ABITOUR_PORT_VALUE="$(prompt_port "Порт приложения" "$(resolve_default "ABITOUR_PORT" "${DEFAULT_PORT}")")"
    ABITOUR_JWT_SECRET_VALUE="$(prompt_secret "JWT-секрет" "$(resolve_default "ABITOUR_JWT_SECRET" "")")"
    ABITOUR_DB_JDBC_URL_VALUE="$(prompt_with_default "JDBC URL базы данных" "$(resolve_default "ABITOUR_DB_JDBC_URL" "${DEFAULT_DB_JDBC_URL}")")"
    ABITOUR_DB_USER_VALUE="$(prompt_with_default "Пользователь базы данных" "$(resolve_default "ABITOUR_DB_USER" "")")"
    ABITOUR_DB_PASSWORD_VALUE="$(prompt_password "Пароль базы данных" "$(resolve_default "ABITOUR_DB_PASSWORD" "")")"
    ABITOUR_DB_MAX_POOL_SIZE_VALUE="$(prompt_positive_integer "Максимальный размер пула базы данных" "$(resolve_default "ABITOUR_DB_MAX_POOL_SIZE" "${DEFAULT_DB_MAX_POOL_SIZE}")")"
    ABITOUR_UPLOADS_DIR_VALUE="$(prompt_with_default "Каталог загрузок" "$(resolve_default "ABITOUR_UPLOADS_DIR" "${DEFAULT_UPLOADS_DIR}")")"
}

prepare_directories() {
    log "подготавливаю директории"
    run_root mkdir -p "${INSTALL_DIR}" "${CONFIG_DIR}" "${DATA_DIR}"
    run_root chown -R "${APP_USER}:${APP_GROUP}" "${INSTALL_DIR}" "${DATA_DIR}"
    run_root chmod 755 "${INSTALL_DIR}" "${DATA_DIR}"
    run_root chmod 750 "${CONFIG_DIR}"

    if [[ "${ABITOUR_DB_JDBC_URL_VALUE}" == jdbc:sqlite:* ]]; then
        local db_path="${ABITOUR_DB_JDBC_URL_VALUE#jdbc:sqlite:}"
        if [[ "${db_path}" != ":memory:" ]]; then
            local db_dir
            db_dir="$(dirname "${db_path}")"
            run_root mkdir -p "${db_dir}"
            run_root chown -R "${APP_USER}:${APP_GROUP}" "${db_dir}"
            run_root chmod 755 "${db_dir}"
        fi
    fi

    run_root mkdir -p "${ABITOUR_UPLOADS_DIR_VALUE}"
    run_root chown -R "${APP_USER}:${APP_GROUP}" "${ABITOUR_UPLOADS_DIR_VALUE}"
    run_root chmod 755 "${ABITOUR_UPLOADS_DIR_VALUE}"
}

download_source() {
    local archive_url="https://codeload.github.com/${REPO_SLUG}/tar.gz/refs/heads/${REPO_REF}"
    log "скачиваю исходники ${REPO_SLUG}@${REPO_REF}"
    curl -fsSL "${archive_url}" -o "${TMP_DIR}/source.tar.gz"
    tar -xzf "${TMP_DIR}/source.tar.gz" -C "${TMP_DIR}"

    SOURCE_DIR="$(find "${TMP_DIR}" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
    [[ -n "${SOURCE_DIR}" ]] || fail "failed to extract repository archive"
}

build_application() {
    local jar_path

    log "собираю приложение"
    pushd "${SOURCE_DIR}" >/dev/null
    export GRADLE_USER_HOME="${TMP_DIR}/gradle-home"
    chmod +x ./gradlew
    if ! ./gradlew --no-daemon clean buildFatJar --console=plain; then
        log "задача buildFatJar недоступна, перехожу на обычную сборку"
        ./gradlew --no-daemon clean build --console=plain
    fi
    jar_path="$(find build/libs -maxdepth 1 -type f -name '*-all.jar' | head -n 1)"
    popd >/dev/null

    [[ -n "${jar_path}" ]] || fail "fat jar не найден после сборки"
    cp "${SOURCE_DIR}/${jar_path}" "${TMP_DIR}/${APP_NAME}.jar"
}

install_application() {
    log "устанавливаю файлы приложения"
    run_root install -m 0644 "${TMP_DIR}/${APP_NAME}.jar" "${INSTALL_DIR}/${APP_NAME}.jar"
    run_root chown "${APP_USER}:${APP_GROUP}" "${INSTALL_DIR}/${APP_NAME}.jar"
}

ensure_env_file() {
    log "записываю env-файл"
    {
        printf 'ABITOUR_PORT=%q\n' "${ABITOUR_PORT_VALUE}"
        printf 'ABITOUR_JWT_SECRET=%q\n' "${ABITOUR_JWT_SECRET_VALUE}"
        printf 'ABITOUR_DB_JDBC_URL=%q\n' "${ABITOUR_DB_JDBC_URL_VALUE}"
        printf 'ABITOUR_DB_USER=%q\n' "${ABITOUR_DB_USER_VALUE}"
        printf 'ABITOUR_DB_PASSWORD=%q\n' "${ABITOUR_DB_PASSWORD_VALUE}"
        printf 'ABITOUR_DB_MAX_POOL_SIZE=%q\n' "${ABITOUR_DB_MAX_POOL_SIZE_VALUE}"
        printf 'ABITOUR_UPLOADS_DIR=%q\n' "${ABITOUR_UPLOADS_DIR_VALUE}"
    } > "${TMP_DIR}/${APP_NAME}.env"
    run_root install -m 0600 "${TMP_DIR}/${APP_NAME}.env" "${ENV_FILE}"
    run_root chmod 600 "${ENV_FILE}"
}

write_service_file() {
    log "создаю systemd unit"
    cat > "${TMP_DIR}/${APP_NAME}.service" <<EOF
[Unit]
Description=Backend-сервис Abitour
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${APP_USER}
Group=${APP_GROUP}
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=-${ENV_FILE}
ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/${APP_NAME}.jar -port=\${ABITOUR_PORT}
Restart=always
RestartSec=5
SuccessExitStatus=143
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ReadWritePaths=${DATA_DIR}

[Install]
WantedBy=multi-user.target
EOF
    run_root install -m 0644 "${TMP_DIR}/${APP_NAME}.service" "${SERVICE_FILE}"
}

enable_service() {
    log "включаю и запускаю сервис"
    run_root systemctl daemon-reload
    run_root systemctl enable --now "${APP_NAME}"
}

show_summary() {
    log "установка завершена"
    echo
    echo "Сервис    : ${APP_NAME}"
    echo "Статус    : systemctl status ${APP_NAME}"
    echo "Логи      : journalctl -u ${APP_NAME} -f"
    echo "Конфиг    : ${ENV_FILE}"
    echo "Данные    : ${DATA_DIR}"
    echo
}

main() {
    install_packages
    ensure_user
    prompt_configuration
    prepare_directories
    download_source
    build_application
    install_application
    ensure_env_file
    write_service_file
    enable_service
    show_summary
}

main "$@"
