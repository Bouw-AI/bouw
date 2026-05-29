#!/usr/bin/env bash
# install.sh — single-script installer for Jarvis (MCP Agent)
#
# Usage:
#   ./install.sh             interactive install / reconfigure
#   ./install.sh --uninstall remove service, launcher, and optionally ~/.jarvis
#
# All files go under JARVIS_HOME (default ~/.jarvis).
# Environment variables collected during install are stored in ~/.jarvis/jarvis.env (chmod 600).
# The agent workspace (file/shell operations) lives at ~/.jarvis/workspace.
set -euo pipefail

# ── constants ─────────────────────────────────────────────────────────────────
JARVIS_HOME="${JARVIS_HOME:-$HOME/.jarvis}"
SERVICE_NAME="jarvis"
LAUNCHER_NAME="jarvis"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_USER="$(id -un)"
ENV_FILE="$JARVIS_HOME/jarvis.env"
CONFIG_YML="$JARVIS_HOME/config/application.yml"
MCP_JSON="$JARVIS_HOME/config/mcp-servers.json"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
LAUNCHER_PATH="/usr/local/bin/${LAUNCHER_NAME}"

# ── colours ───────────────────────────────────────────────────────────────────
info()    { printf '\033[1;34m[jarvis]\033[0m %s\n' "$*"; }
success() { printf '\033[1;32m[jarvis]\033[0m %s\n' "$*"; }
warn()    { printf '\033[1;33m[jarvis]\033[0m %s\n' "$*"; }
die()     { printf '\033[1;31m[jarvis]\033[0m %s\n' "$*" >&2; exit 1; }
ask()     { printf '\033[1;35m   >\033[0m %s' "$*"; }

require_cmd() { command -v "$1" >/dev/null 2>&1; }

# ── wait for health ───────────────────────────────────────────────────────────
wait_for_health() {
  local max="${1:-45}" elapsed=0
  info "Waiting for agent server to become healthy (up to ${max}s)..."
  until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do
    elapsed=$((elapsed + 1))
    if [[ $elapsed -ge $max ]]; then
      warn "Server did not become healthy within ${max}s."
      warn "Inspect logs:  sudo journalctl -u $SERVICE_NAME -n 50"
      return 1
    fi
    sleep 1
  done
  success "Server healthy at http://localhost:8080"
}

# ── uninstall ─────────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--uninstall" ]]; then
  info "Stopping and removing $SERVICE_NAME service..."
  sudo systemctl disable --now "$SERVICE_NAME" 2>/dev/null || true
  sudo rm -f "$SERVICE_FILE"
  sudo systemctl daemon-reload
  sudo rm -f "$LAUNCHER_PATH"
  success "Service and launcher removed."

  if [[ -d "$JARVIS_HOME" ]]; then
    ask "Delete $JARVIS_HOME (config + workspace + logs)? [y/N] "; read -r _confirm; echo
    if [[ "${_confirm,,}" == "y" ]]; then
      rm -rf "$JARVIS_HOME"
      success "$JARVIS_HOME deleted."
    else
      info "Kept $JARVIS_HOME."
    fi
  fi
  exit 0
fi

# ── detect existing install ───────────────────────────────────────────────────
ALREADY_INSTALLED=false
SKIP_BUILD=false

if [[ -f "$JARVIS_HOME/bin/mcp-integration.jar" ]]; then
  ALREADY_INSTALLED=true
  warn "Existing Jarvis installation detected at $JARVIS_HOME."
  echo
  echo "  1) Reconfigure only  (keep existing jars, update env + config, restart service)"
  echo "  2) Full reinstall    (rebuild jars from source, update everything)"
  echo
  ask "Choice [1]: "; read -r _choice; echo
  _choice="${_choice:-1}"
  if [[ "$_choice" == "1" ]]; then
    SKIP_BUILD=true
    info "Will reconfigure without rebuilding."
  else
    info "Full reinstall selected — jars will be rebuilt."
  fi
fi

echo
info "Jarvis home : $JARVIS_HOME"
info "Repo root   : $REPO_DIR"
echo

# ── 1. directory tree ─────────────────────────────────────────────────────────
mkdir -p \
  "$JARVIS_HOME/bin" \
  "$JARVIS_HOME/config" \
  "$JARVIS_HOME/venv" \
  "$JARVIS_HOME/workspace" \
  "$JARVIS_HOME/logs"
info "Directory tree ready at $JARVIS_HOME"

# ── 2. system dependencies ────────────────────────────────────────────────────
need_apt_update=false
pkgs_to_install=()

# Java 21
java_ok=false
if require_cmd java && java -version 2>&1 | grep -qE '"(2[1-9]|[3-9][0-9])'; then
  info "Java 21+ already present."
  java_ok=true
fi

if [[ "$java_ok" == "false" ]]; then
  warn "Java 21 not found — will install Temurin JDK 21 via Adoptium apt repo."
  sudo install -d -m 0755 /etc/apt/keyrings
  wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
  # shellcheck source=/dev/null
  . /etc/os-release
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] \
https://packages.adoptium.net/artifactory/deb ${VERSION_CODENAME} main" \
    | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null
  need_apt_update=true
  pkgs_to_install+=(temurin-21-jdk)
fi

require_cmd git     || pkgs_to_install+=(git)
require_cmd mvn     || pkgs_to_install+=(maven)
require_cmd python3 || pkgs_to_install+=(python3)
require_cmd curl    || pkgs_to_install+=(curl)

if ! python3 -c "import venv" 2>/dev/null; then
  pkgs_to_install+=(python3-venv)
fi

if [[ "$need_apt_update" == "true" ]] || [[ ${#pkgs_to_install[@]} -gt 0 ]]; then
  sudo apt-get update -qq
fi

if [[ ${#pkgs_to_install[@]} -gt 0 ]]; then
  info "Installing system packages: ${pkgs_to_install[*]}"
  sudo apt-get install -y "${pkgs_to_install[@]}"
fi

# ── 3. prompts ────────────────────────────────────────────────────────────────
echo
printf '\033[1;34m─── Environment Configuration ─────────────────────────────────────────\033[0m\n'
echo

# Load existing values so they can be used as defaults on reconfigure
_existing_key="" _existing_agent_key="" _existing_redis_host="" _existing_redis_port="6379"
_existing_model="" _existing_github_token=""
if [[ -f "$ENV_FILE" ]]; then
  _existing_key=$(grep -E '^OPEN_ROUTER_API_KEY=' "$ENV_FILE" | cut -d= -f2- || true)
  _existing_agent_key=$(grep -E '^AGENT_API_KEY=' "$ENV_FILE" | cut -d= -f2- || true)
  _existing_redis_host=$(grep -E '^REDIS_HOST=' "$ENV_FILE" | cut -d= -f2- || true)
  _existing_redis_port=$(grep -E '^REDIS_PORT=' "$ENV_FILE" | cut -d= -f2- || echo "6379")
  _existing_model=$(grep -E '^LLM_MODEL=' "$ENV_FILE" | cut -d= -f2- || true)
  _existing_github_token=$(grep -E '^GITHUB_TOKEN=' "$ENV_FILE" | cut -d= -f2- || true)
fi

# 3a. OpenRouter API key (required)
OPENROUTER_KEY=""
while [[ -z "$OPENROUTER_KEY" ]]; do
  if [[ -n "$_existing_key" ]]; then
    ask "OpenRouter API key [current hidden, press Enter to keep]: "
    read -rsp "" OPENROUTER_KEY; echo
    [[ -z "$OPENROUTER_KEY" ]] && OPENROUTER_KEY="$_existing_key"
  else
    ask "OpenRouter API key (sk-or-v1-...): "
    read -rsp "" OPENROUTER_KEY; echo
  fi
  if [[ -z "$OPENROUTER_KEY" ]]; then
    warn "An OpenRouter API key is required (used for the LLM and web search). Try again."
  fi
done
success "OpenRouter API key set."

# 3b. LLM model (optional)
echo
info "Default model: openai/gpt-oss-120b (OpenRouter)"
ask "LLM model override [press Enter for default${_existing_model:+, current: $_existing_model}]: "
read -r LLM_MODEL_INPUT; echo
LLM_MODEL="${LLM_MODEL_INPUT:-${_existing_model:-openai/gpt-oss-120b}}"
info "Model: $LLM_MODEL"

# 3c. Agent API key (optional — secures /api/agent/** with X-API-Key header)
echo
info "Agent API key: if set, all /api/agent/** calls require  X-API-Key: <value>"
if [[ -n "$_existing_agent_key" ]]; then
  ask "Agent API key [current hidden, press Enter to keep, or type 'clear' to remove]: "
  read -rsp "" _agent_key_input; echo
  if [[ "${_agent_key_input,,}" == "clear" ]]; then
    AGENT_API_KEY=""
    info "Agent API key cleared — endpoints will be open."
  elif [[ -z "$_agent_key_input" ]]; then
    AGENT_API_KEY="$_existing_agent_key"
    info "Agent API key kept."
  else
    AGENT_API_KEY="$_agent_key_input"
    success "Agent API key updated."
  fi
else
  ask "Agent API key (leave blank to leave endpoints open): "
  read -rsp "" AGENT_API_KEY; echo
  if [[ -n "$AGENT_API_KEY" ]]; then
    success "Agent API key set."
  else
    info "Endpoints will be open (no X-API-Key required)."
  fi
fi

# 3d. Redis for long-term memory (optional)
echo
info "Long-term memory requires a running Redis instance."
MEMORY_ENABLED=false
REDIS_HOST_VAL=""
REDIS_PORT_VAL=6379

_redis_default_hint=""
[[ -n "$_existing_redis_host" ]] && _redis_default_hint=" [current: ${_existing_redis_host}:${_existing_redis_port}]"

ask "Redis host for long-term memory (leave blank to disable)${_redis_default_hint}: "
read -r REDIS_HOST_INPUT; echo

if [[ -z "$REDIS_HOST_INPUT" && -n "$_existing_redis_host" ]]; then
  ask "Keep existing Redis config (${_existing_redis_host}:${_existing_redis_port})? [Y/n] "
  read -r _keep_redis; echo
  if [[ "${_keep_redis,,}" != "n" ]]; then
    REDIS_HOST_INPUT="$_existing_redis_host"
    REDIS_PORT_VAL="$_existing_redis_port"
  fi
fi

if [[ -n "$REDIS_HOST_INPUT" ]]; then
  REDIS_HOST_VAL="$REDIS_HOST_INPUT"

  ask "Redis port [${REDIS_PORT_VAL}]: "
  read -r _port_input; echo
  REDIS_PORT_VAL="${_port_input:-$REDIS_PORT_VAL}"

  # Install Redis locally if requested for localhost
  if [[ "$REDIS_HOST_VAL" == "localhost" || "$REDIS_HOST_VAL" == "127.0.0.1" ]]; then
    if ! require_cmd redis-server; then
      ask "Redis not found locally — install redis-server? [Y/n] "
      read -r _inst_redis; echo
      if [[ "${_inst_redis,,}" != "n" ]]; then
        sudo apt-get install -y redis-server
        sudo systemctl enable --now redis-server
        info "Redis installed and started."
      fi
    elif ! systemctl is-active --quiet redis-server 2>/dev/null; then
      info "Redis is installed but not running — starting it."
      sudo systemctl start redis-server
    else
      info "Local Redis already running."
    fi
  fi

  # Test connectivity before enabling memory (avoid database conflicts)
  info "Testing Redis connectivity at ${REDIS_HOST_VAL}:${REDIS_PORT_VAL}..."
  if require_cmd redis-cli && redis-cli -h "$REDIS_HOST_VAL" -p "$REDIS_PORT_VAL" ping >/dev/null 2>&1; then
    MEMORY_ENABLED=true
    success "Redis reachable — long-term memory enabled."
  else
    warn "Cannot reach Redis at ${REDIS_HOST_VAL}:${REDIS_PORT_VAL}. Long-term memory will be DISABLED."
    warn "Fix connectivity and re-run  $LAUNCHER_NAME config  to enable it later."
    REDIS_HOST_VAL=""
    MEMORY_ENABLED=false
  fi
fi

# 3e. GitHub token (optional — for cloud-agent repo cloning)
echo
info "GitHub token enables the cloud-agent feature (/api/agents — repo clone + agent loop)."
if [[ -n "$_existing_github_token" ]]; then
  ask "GitHub personal-access token [current hidden, press Enter to keep, or 'clear' to remove]: "
  read -rsp "" _gh_token_input; echo
  if [[ "${_gh_token_input,,}" == "clear" ]]; then
    GITHUB_TOKEN=""
    info "GitHub token cleared."
  elif [[ -z "$_gh_token_input" ]]; then
    GITHUB_TOKEN="$_existing_github_token"
    info "GitHub token kept."
  else
    GITHUB_TOKEN="$_gh_token_input"
    success "GitHub token updated."
  fi
else
  ask "GitHub personal-access token (leave blank to skip): "
  read -rsp "" GITHUB_TOKEN; echo
  if [[ -n "$GITHUB_TOKEN" ]]; then
    success "GitHub token set."
  else
    info "Cloud-agent repo cloning will use public repos only."
  fi
fi

CLOUD_AGENTS_ENABLED=false
[[ -n "$GITHUB_TOKEN" ]] && CLOUD_AGENTS_ENABLED=true

# ── 4. write jarvis.env ───────────────────────────────────────────────────────
cat > "$ENV_FILE" <<EOF
# Jarvis environment — sourced by the systemd service and the jarvis launcher.
# Permissions: 600 (owner-read-only).  Do not commit this file.

OPEN_ROUTER_API_KEY=${OPENROUTER_KEY}
LLM_MODEL=${LLM_MODEL}

# Secure the /api/agent/** endpoints with an X-API-Key header (leave blank to disable).
AGENT_API_KEY=${AGENT_API_KEY}

# Long-term Redis-backed semantic memory
MEMORY_ENABLED=${MEMORY_ENABLED}
REDIS_HOST=${REDIS_HOST_VAL}
REDIS_PORT=${REDIS_PORT_VAL}

# Cloud-agent feature (POST /api/agents — clone repo, run agent loop)
CLOUD_AGENTS_ENABLED=${CLOUD_AGENTS_ENABLED}
GITHUB_TOKEN=${GITHUB_TOKEN}

# Jarvis home directory (workspace root is $JARVIS_HOME/workspace)
AGENT_HOME=${JARVIS_HOME}
EOF
chmod 600 "$ENV_FILE"
success "Wrote $ENV_FILE (chmod 600)"

# ── 5. write config/application.yml ──────────────────────────────────────────
cat > "$CONFIG_YML" <<EOF
# Per-installation overrides — merged on top of the bundled application.yml.
mcp:
  config-file: ${JARVIS_HOME}/config/mcp-servers.json

search:
  openrouter-script: ${JARVIS_HOME}/bin/openrouter-search-mcp.py

llm:
  model: \${LLM_MODEL:${LLM_MODEL}}

agent:
  api-key: \${AGENT_API_KEY:}
  home: ${JARVIS_HOME}
  tools:
    workspace-root: ${JARVIS_HOME}/workspace
  cloud:
    enabled: \${CLOUD_AGENTS_ENABLED:false}
    github-token: \${GITHUB_TOKEN:}
    cleanup-on-complete: true

logging:
  file:
    name: ${JARVIS_HOME}/logs/jarvis.log
EOF
info "Wrote $CONFIG_YML"

# Seed mcp-servers.json from example if not present (never overwrite an existing one)
if [[ ! -f "$MCP_JSON" ]]; then
  if [[ -f "$REPO_DIR/mcp-servers.example.json" ]]; then
    cp "$REPO_DIR/mcp-servers.example.json" "$MCP_JSON"
    info "Seeded $MCP_JSON from mcp-servers.example.json"
  else
    printf '{"mcpServers":{}}\n' > "$MCP_JSON"
    info "Created empty $MCP_JSON"
  fi
else
  info "$MCP_JSON already exists — not overwritten."
fi

# ── 6. python venv for web search ────────────────────────────────────────────
info "Setting up Python venv for the web-search MCP server..."
python3 -m venv "$JARVIS_HOME/venv"
"$JARVIS_HOME/venv/bin/pip" install --no-cache-dir --quiet mcp
cp "$REPO_DIR/openrouter-search-mcp.py" "$JARVIS_HOME/bin/openrouter-search-mcp.py"
info "Python venv ready; openrouter-search-mcp.py copied."

# ── 7. build fat jars ────────────────────────────────────────────────────────
if [[ "$SKIP_BUILD" == "true" ]]; then
  info "Skipping Maven build (reconfigure-only mode)."
else
  info "Building fat jars — this may take a few minutes..."
  MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m}" \
    mvn -f "$REPO_DIR/pom.xml" \
        -pl mcp-integration,agent-terminal -am \
        clean package -DskipTests -q
  cp "$REPO_DIR"/mcp-integration/target/mcp-integration-*.jar  "$JARVIS_HOME/bin/mcp-integration.jar"
  cp "$REPO_DIR"/agent-terminal/target/agent-terminal-*.jar     "$JARVIS_HOME/bin/agent-terminal.jar"
  success "Jars built and copied to $JARVIS_HOME/bin/"
fi

# ── 8. install the jarvis launcher ───────────────────────────────────────────
info "Installing $LAUNCHER_NAME launcher to $LAUNCHER_PATH..."
sudo tee "$LAUNCHER_PATH" > /dev/null <<LAUNCHER
#!/usr/bin/env bash
# jarvis — Jarvis agent launcher (installed by install.sh)
#
# Commands:
#   jarvis [run]   start service if needed, open terminal chat
#   jarvis serve   run server in the foreground (no systemd)
#   jarvis start / stop / restart / status / logs
#   jarvis config  re-prompt for credentials, restart service
#   jarvis uninstall
set -euo pipefail

JARVIS_HOME="\${JARVIS_HOME:-$JARVIS_HOME}"
ENV_FILE="\$JARVIS_HOME/jarvis.env"
CONFIG_YML="\$JARVIS_HOME/config/application.yml"
SERVICE_NAME="$SERVICE_NAME"

info()    { printf '\033[1;34m[jarvis]\033[0m %s\n' "\$*"; }
success() { printf '\033[1;32m[jarvis]\033[0m %s\n' "\$*"; }
warn()    { printf '\033[1;33m[jarvis]\033[0m %s\n' "\$*"; }
die()     { printf '\033[1;31m[jarvis]\033[0m %s\n' "\$*" >&2; exit 1; }

load_env() {
  [[ -f "\$ENV_FILE" ]] || return 0
  # shellcheck disable=SC2046
  export \$(grep -v '^\s*#' "\$ENV_FILE" | grep -v '^\s*\$' | xargs)
}

wait_for_health() {
  local max="\${1:-45}" elapsed=0
  until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do
    elapsed=\$((elapsed + 1))
    [[ \$elapsed -ge \$max ]] && { warn "Server did not respond within \${max}s. Try: jarvis logs"; return 1; }
    sleep 1
  done
}

cmd_run() {
  load_env
  if ! systemctl is-active --quiet "\$SERVICE_NAME" 2>/dev/null; then
    info "Starting \$SERVICE_NAME service..."
    sudo systemctl start "\$SERVICE_NAME"
  fi
  info "Waiting for agent server..."
  if wait_for_health 45; then
    success "Server ready at http://localhost:8080"
  fi
  export AGENT_SERVER_URL="http://localhost:8080"
  [[ -n "\${AGENT_API_KEY:-}" ]] && export AGENT_API_KEY
  exec java -jar "\$JARVIS_HOME/bin/agent-terminal.jar"
}

cmd_serve() {
  load_env
  exec java -jar "\$JARVIS_HOME/bin/mcp-integration.jar" \
    "--spring.config.additional-location=file:\${CONFIG_YML}"
}

cmd_start()   { sudo systemctl start   "\$SERVICE_NAME"; }
cmd_stop()    { sudo systemctl stop    "\$SERVICE_NAME"; }
cmd_restart() { sudo systemctl restart "\$SERVICE_NAME"; }
cmd_status()  { systemctl status       "\$SERVICE_NAME"; }
cmd_logs()    { exec journalctl -u "\$SERVICE_NAME" -f; }

cmd_config() {
  [[ -f "\$ENV_FILE" ]] && load_env
  local new_key=""
  while [[ -z "\$new_key" ]]; do
    printf '\033[1;35m   >\033[0m OpenRouter API key [current hidden, Enter to keep]: '
    read -rsp "" new_key; echo
    [[ -z "\$new_key" ]] && new_key="\${OPEN_ROUTER_API_KEY:-}"
    [[ -z "\$new_key" ]] && warn "Key is required."
  done
  printf '\033[1;35m   >\033[0m Redis host for long-term memory (blank to disable): '
  read -r redis_host; echo
  local mem=false rhost="" rport=6379
  if [[ -n "\$redis_host" ]]; then
    printf '\033[1;35m   >\033[0m Redis port [6379]: '
    read -r rport_in; echo
    rport="\${rport_in:-6379}"
    if redis-cli -h "\$redis_host" -p "\$rport" ping >/dev/null 2>&1; then
      mem=true; rhost="\$redis_host"
      success "Redis reachable — long-term memory enabled."
    else
      warn "Redis not reachable — memory left disabled."
    fi
  fi
  cat > "\$ENV_FILE" <<ENV
OPEN_ROUTER_API_KEY=\${new_key}
LLM_MODEL=\${LLM_MODEL:-openai/gpt-oss-120b}
AGENT_API_KEY=\${AGENT_API_KEY:-}
MEMORY_ENABLED=\${mem}
REDIS_HOST=\${rhost}
REDIS_PORT=\${rport}
CLOUD_AGENTS_ENABLED=\${CLOUD_AGENTS_ENABLED:-false}
GITHUB_TOKEN=\${GITHUB_TOKEN:-}
AGENT_HOME=\${JARVIS_HOME}
ENV
  chmod 600 "\$ENV_FILE"
  success "jarvis.env updated."
  if systemctl is-active --quiet "\$SERVICE_NAME" 2>/dev/null; then
    info "Restarting service..."
    sudo systemctl restart "\$SERVICE_NAME"
    wait_for_health 45 && success "Service restarted and healthy."
  fi
}

cmd_uninstall() {
  info "Stopping and removing \$SERVICE_NAME service..."
  sudo systemctl disable --now "\$SERVICE_NAME" 2>/dev/null || true
  sudo rm -f "/etc/systemd/system/\${SERVICE_NAME}.service"
  sudo systemctl daemon-reload
  sudo rm -f "$LAUNCHER_PATH"
  success "Service and launcher removed."
  if [[ -d "\$JARVIS_HOME" ]]; then
    printf '\033[1;35m   >\033[0m Delete \$JARVIS_HOME? [y/N] '
    read -r _c; echo
    [[ "\${_c,,}" == "y" ]] && rm -rf "\$JARVIS_HOME" && success "\$JARVIS_HOME deleted." || info "Kept \$JARVIS_HOME."
  fi
}

CMD="\${1:-run}"
shift || true
case "\$CMD" in
  run)       cmd_run       ;;
  serve)     cmd_serve     ;;
  start)     cmd_start     ;;
  stop)      cmd_stop      ;;
  restart)   cmd_restart   ;;
  status)    cmd_status    ;;
  logs)      cmd_logs      ;;
  config)    cmd_config    ;;
  uninstall) cmd_uninstall ;;
  *)
    cat <<USAGE
Usage: jarvis [command]

  (none) / run   Start service if needed, open terminal chat
  serve          Run the server in the foreground (no systemd)
  start          Start the background service
  stop           Stop the background service
  restart        Restart the background service
  status         Show service status
  logs           Stream service logs  (journalctl -f)
  config         Reconfigure credentials, restart service
  uninstall      Remove service, launcher, and optionally ~/.jarvis
USAGE
    exit 1
    ;;
esac
LAUNCHER
sudo chmod 0755 "$LAUNCHER_PATH"
success "Launcher installed: $LAUNCHER_PATH"

# ── 9. install systemd service ────────────────────────────────────────────────
info "Installing systemd service $SERVICE_NAME..."
sudo tee "$SERVICE_FILE" > /dev/null <<SERVICE
# Jarvis agent service — managed by install.sh
[Unit]
Description=Jarvis MCP Agent Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${INSTALL_USER}
Environment=JARVIS_HOME=${JARVIS_HOME}
Environment=AGENT_HOME=${JARVIS_HOME}
EnvironmentFile=${ENV_FILE}
# Prepend the venv so the web-search MCP subprocess finds python3 + mcp package.
Environment=PATH=${JARVIS_HOME}/venv/bin:/usr/local/bin:/usr/bin:/bin
WorkingDirectory=${JARVIS_HOME}
ExecStart=/usr/bin/java -jar ${JARVIS_HOME}/bin/mcp-integration.jar \
  --spring.config.additional-location=file:${JARVIS_HOME}/config/application.yml
StandardOutput=append:${JARVIS_HOME}/logs/jarvis.log
StandardError=append:${JARVIS_HOME}/logs/jarvis.log
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
SERVICE

sudo systemctl daemon-reload

# Stop any previous incarnation cleanly before (re)starting
if systemctl is-active --quiet "$SERVICE_NAME" 2>/dev/null; then
  info "Restarting existing $SERVICE_NAME service..."
  sudo systemctl restart "$SERVICE_NAME"
else
  sudo systemctl enable --now "$SERVICE_NAME"
  info "Service enabled and started."
fi

# ── 10. health check ──────────────────────────────────────────────────────────
wait_for_health 60

# ── done ──────────────────────────────────────────────────────────────────────
echo
printf '\033[1;32m══════════════════════════════════════════════════════════════════\033[0m\n'
printf '\033[1;32m  Jarvis is running at http://localhost:8080\033[0m\n'
printf '\033[1;32m══════════════════════════════════════════════════════════════════\033[0m\n'
echo
cat <<MSG
  Start chatting:      jarvis
  Server in foreground: jarvis serve
  Service status/logs: jarvis status  |  jarvis logs
  Reconfigure:         jarvis config
  Workspace:           $JARVIS_HOME/workspace
  Config:              $JARVIS_HOME/config/
  Env vars:            $ENV_FILE
  Uninstall:           jarvis uninstall
MSG
echo
