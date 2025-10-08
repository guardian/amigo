#!/usr/bin/env bash
set -euo pipefail

if [[ "$USER" != "vscode" ]]; then
  echo "Please run this script as the vscode user."
  exit 1
fi

echo "user: $USER"
echo "shell: $SHELL"

# ---- system dependencies ----
echo -e "\033[1;34m[setup] Installing system dependencies...\033[0m"
export DEBIAN_FRONTEND=noninteractive
sudo bash -lc 'apt-get update || (sleep 2 && apt-get update)'
sudo bash -lc 'apt-get install -y --no-install-recommends ca-certificates curl git ripgrep'
sudo apt-get clean
sudo rm -rf /var/lib/apt/lists/*

## ---- set up mise-en-place ----
echo -e "\033[1;34m[setup] Setting up mise for this script...\033[0m"
# Shims mode doesn't update paths on failed installs, and the sbt installation will fail
# See: https://github.com/mise-plugins/mise-sbt/issues/3
# Activating without shims means the Java path will get set correctly, so repeated installation will work
# We don't persist this for future sessions because mise's shims are already on the path
eval "$(mise activate bash)"

echo -e "\033[1;34m[setup] mise activated.\033[0m"
mise --version
## ---- make sure mise can use the checked out project's tools definition ----
mise trust || true

# try mise install twice to handle dependencies between installations
# In particular, sbt requires Java and will fail in the initial install
# Once Java is installed (after the first installation) the second will succeed
# See: https://github.com/mise-plugins/mise-sbt/issues/3
for i in 1 2; do
  mise install && break
  echo "mise install failed, retrying in 2 seconds... (attempt $i)"
  sleep 2
done

echo -e "\033[1;34m[setup] Installs Claude Code:\033[0m"
npm install -g @anthropic-ai/claude-code

echo -e "\033[1;32m========== setup: complete ==========\033[0m"
