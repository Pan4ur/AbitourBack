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
RELEASE_TAG="${ABITOUR_RELEASE_TAG:-latest}"
ASSET_NAME="abitour-server.jar"
JAVA_DIR="${INSTALL_DIR}/java"
JAVA_BIN="java"
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
        run_root apt-get install -y ca-certificates curl tar
        return
    fi

    if command_exists dnf; then
        run_root dnf install -y ca-certificates curl tar
        return
    fi

    if command_exists yum; then
        run_root yum install -y ca-certificates curl tar
        return
    fi

    fail "Неподдерживаемый пакетный менеджер. Установите curl, tar и Java 21 вручную."
}

java_is_21() {
    local java_cmd="$1"
    local version_output

    if ! version_output="$("${java_cmd}" -version 2>&1)"; then
        return 1
    fi

    [[ "${version_output}" == *'"21.'* || "${version_output}" == *' version "21"'* ]]
}

install_java21_from_packages() {
    if command_exists apt-get; then
        local packages=(
            openjdk-21-jre-headless
            openjdk-21-jre
            openjdk-21-jdk-headless
            openjdk-21-jdk
            msopenjdk-21
        )
        local package
        for package in "${packages[@]}"; do
            if run_root apt-get install -y "${package}"; then
                return 0
            fi
        done
        return 1
    fi

    if command_exists dnf; then
        run_root dnf install -y java-21-openjdk-headless || run_root dnf install -y java-21-openjdk-devel
        return
    fi

    if command_exists yum; then
        run_root yum install -y java-21-openjdk-headless || run_root yum install -y java-21-openjdk-devel
        return
    fi

    return 1
}

install_java21_from_adoptium() {
    local archive_url="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jre/hotspot/normal/eclipse"

    log "Java 21 не найдена в репозиториях, скачиваю Temurin 21"
    curl -fsSL "${archive_url}" -o "${TMP_DIR}/java21.tar.gz"
    run_root rm -rf "${JAVA_DIR}"
    run_root mkdir -p "${JAVA_DIR}"
    run_root tar -xzf "${TMP_DIR}/java21.tar.gz" -C "${JAVA_DIR}" --strip-components=1
}

ensure_java21() {
    if command_exists java && java_is_21 "$(command -v java)"; then
        JAVA_BIN="$(command -v java)"
        log "Использую системную Java 21: ${JAVA_BIN}"
        return
    fi

    if install_java21_from_packages; then
        if command_exists java && java_is_21 "$(command -v java)"; then
            JAVA_BIN="$(command -v java)"
            log "Установлена Java 21 из пакетов: ${JAVA_BIN}"
            return
        fi
    fi

    install_java21_from_adoptium
    JAVA_BIN="${JAVA_DIR}/bin/java"

    if ! java_is_21 "${JAVA_BIN}"; then
        fail "Не удалось подготовить Java 21"
    fi
}

ensure_user() {
    if id -u "${APP_USER}" >/dev/null 2>&1; then
        return
    fi

    log "Создаю системного пользователя ${APP_USER}"
    run_root useradd \
        --system \
        --home-dir "${INSTALL_DIR}" \
        --shell /usr/sbin/nologin \
        --user-group \
        --comment "Abitour backend service" \
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
    log "Запрашиваю конфигурацию"

    ABITOUR_PORT_VALUE="$(prompt_port "Порт приложения" "$(resolve_default "ABITOUR_PORT" "${DEFAULT_PORT}")")"
    ABITOUR_JWT_SECRET_VALUE="$(prompt_secret "JWT-секрет" "$(resolve_default "ABITOUR_JWT_SECRET" "")")"
    ABITOUR_DB_JDBC_URL_VALUE="$(prompt_with_default "JDBC URL базы данных" "$(resolve_default "ABITOUR_DB_JDBC_URL" "${DEFAULT_DB_JDBC_URL}")")"
    ABITOUR_DB_USER_VALUE="$(prompt_with_default "Пользователь базы данных" "$(resolve_default "ABITOUR_DB_USER" "")")"
    ABITOUR_DB_PASSWORD_VALUE="$(prompt_password "Пароль базы данных" "$(resolve_default "ABITOUR_DB_PASSWORD" "")")"
    ABITOUR_DB_MAX_POOL_SIZE_VALUE="$(prompt_positive_integer "Максимальный размер пула базы данных" "$(resolve_default "ABITOUR_DB_MAX_POOL_SIZE" "${DEFAULT_DB_MAX_POOL_SIZE}")")"
    ABITOUR_UPLOADS_DIR_VALUE="$(prompt_with_default "Каталог загрузок" "$(resolve_default "ABITOUR_UPLOADS_DIR" "${DEFAULT_UPLOADS_DIR}")")"
}

prepare_directories() {
    log "Подготавливаю директории"
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

download_release_asset() {
    local download_url="https://github.com/${REPO_SLUG}/releases/download/${RELEASE_TAG}/${ASSET_NAME}"

    log "Скачиваю ${ASSET_NAME} из релиза ${RELEASE_TAG}"
    if ! curl -fsSL "${download_url}" -o "${TMP_DIR}/${ASSET_NAME}"; then
        fail "Не удалось скачать ${ASSET_NAME} из https://github.com/${REPO_SLUG}/releases. Убедитесь, что GitHub Actions уже опубликовал релиз."
    fi
}

install_application() {
    log "Устанавливаю файлы приложения"
    run_root install -m 0644 "${TMP_DIR}/${ASSET_NAME}" "${INSTALL_DIR}/${APP_NAME}.jar"
    run_root chown "${APP_USER}:${APP_GROUP}" "${INSTALL_DIR}/${APP_NAME}.jar"
}

ensure_env_file() {
    log "Записываю env-файл"
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
    log "Создаю systemd unit"
    cat > "${TMP_DIR}/${APP_NAME}.service" <<EOF
[Unit]
Description=Abitour backend service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${APP_USER}
Group=${APP_GROUP}
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=-${ENV_FILE}
ExecStart=${JAVA_BIN} -jar ${INSTALL_DIR}/${APP_NAME}.jar -port=\${ABITOUR_PORT}
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
    log "Включаю и запускаю сервис"
    run_root systemctl daemon-reload
    run_root systemctl enable --now "${APP_NAME}"
}

show_summary() {
    log "Установка завершена"
    echo
    echo "Сервис    : ${APP_NAME}"
    echo "Статус    : systemctl status ${APP_NAME}"
    echo "Логи      : journalctl -u ${APP_NAME} -f"
    echo "Конфиг    : ${ENV_FILE}"
    echo "Данные    : ${DATA_DIR}"
    echo "Релиз     : ${RELEASE_TAG}"
    echo
}

main() {
    install_packages
    ensure_java21
    ensure_user
    prompt_configuration
    prepare_directories
    download_release_asset
    install_application
    ensure_env_file
    write_service_file
    enable_service
    show_summary
}

main "$@"
