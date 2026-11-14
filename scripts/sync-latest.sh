#!/usr/bin/env sh
# Baixa a release mais recente do Vertexx para a pasta plugins do servidor (Linux/macOS).
# Use na primeira instalação ou no script de inicialização do servidor, antes de abrir o Paper.
# Depois de instalado, o próprio plugin se mantém atualizado.
#
# Uso:  ./sync-latest.sh [pasta-plugins] [dono/repositorio]
set -eu

PLUGINS_DIR="${1:-./plugins}"
REPOSITORY="${2:-KoZuuYT/Vertexx}"
TARGET="$PLUGINS_DIR/Vertexx.jar"
MARKER="$PLUGINS_DIR/.vertexx-version"

JSON="$(curl -fsSL -H 'User-Agent: Vertexx-Sync' "https://api.github.com/repos/$REPOSITORY/releases/latest")"
TAG="$(printf '%s' "$JSON" | grep -o '"tag_name": *"[^"]*"' | head -n 1 | cut -d '"' -f 4)"
URL="$(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*\.jar"' | head -n 1 | cut -d '"' -f 4)"

if [ -z "$TAG" ] || [ -z "$URL" ]; then
    echo "Não foi possível encontrar um .jar na release mais recente de $REPOSITORY." >&2
    exit 1
fi

mkdir -p "$PLUGINS_DIR"
if [ -f "$TARGET" ] && [ -f "$MARKER" ] && [ "$(cat "$MARKER")" = "$TAG" ]; then
    echo "Vertexx já está na versão $TAG."
    exit 0
fi

curl -fsSL -H 'User-Agent: Vertexx-Sync' -o "$TARGET.part" "$URL"
mv -f "$TARGET.part" "$TARGET"
printf '%s' "$TAG" > "$MARKER"
echo "Vertexx $TAG instalado em $TARGET"
