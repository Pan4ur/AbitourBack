#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="abitour-server"
APP_USER="abitour"
APP_GROUP="abitour"
INSTALL_DIR="/opt/${APP_NAME}"
CONFIG_DIR="/etc/${APP_NAME}"
DATA_DIR="/var/lib/${APP_NAME}"
SERVICE_FILE="/etc/systemd/system/${APP_NAME}.service"
YES="false"
KEEP_DATA="false"

SUDO=""
if [[ "${EUID}" -ne 0 ]]; then
    if ! command -v sudo >/dev/null 2>&1; then
        echo "sudo is required to uninstall ${APP_NAME}" >&2
        exit 1
    fi
    SUDO="sudo"
fi

log() {
    echo "[${APP_NAME}] $*"
}

run_root() {
    if [[ -n "${SUDO}" ]]; then
        "${SUDO}" "$@"
    else
        "$@"
    fi
}

usage() {
    cat <<EOF
Usage: uninstall.sh [--yes] [--keep-data]

  --yes        Skip confirmation prompt.
  --keep-data  Keep ${CONFIG_DIR} and ${DATA_DIR}.
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --yes)
                YES="true"
                ;;
            --keep-data)
                KEEP_DATA="true"
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                echo "Unknown argument: $1" >&2
                usage >&2
                exit 1
                ;;
        esac
        shift
    done
}

confirm() {
    if [[ "${YES}" == "true" ]]; then
        return
    fi

    echo "This will remove ${APP_NAME}."
    if [[ "${KEEP_DATA}" == "false" ]]; then
        echo "Application files, config and all data will be deleted."
    else
        echo "Application files will be removed, but config and data will be kept."
    fi
    read -r -p "Type ${APP_NAME} to continue: " answer
    [[ "${answer}" == "${APP_NAME}" ]] || {
        echo "Aborted."
        exit 1
    }
}

stop_service() {
    if run_root systemctl list-unit-files "${APP_NAME}.service" >/dev/null 2>&1; then
        log "stopping service"
        run_root systemctl disable --now "${APP_NAME}" || true
    fi

    if [[ -f "${SERVICE_FILE}" ]]; then
        run_root rm -f "${SERVICE_FILE}"
    fi

    run_root systemctl daemon-reload || true
}

remove_files() {
    log "removing application files"
    run_root rm -rf "${INSTALL_DIR}"

    if [[ "${KEEP_DATA}" == "false" ]]; then
        log "removing config and data"
        run_root rm -rf "${CONFIG_DIR}" "${DATA_DIR}"
    fi
}

remove_user() {
    if id -u "${APP_USER}" >/dev/null 2>&1; then
        log "removing service user"
        run_root userdel "${APP_USER}" || true
    fi

    if getent group "${APP_GROUP}" >/dev/null 2>&1; then
        run_root groupdel "${APP_GROUP}" || true
    fi
}

main() {
    parse_args "$@"
    confirm
    stop_service
    remove_files
    remove_user
    log "uninstall complete"
}

main "$@"
