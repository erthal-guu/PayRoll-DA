#!/usr/bin/env sh
# Sobe banco + API + painel usando apenas o Docker (nada de Java/Node/Maven/Postgres na máquina).
# Opcional: export ANTHROPIC_API_KEY=sk-ant-...  (assistente IA)
cd "$(dirname "$0")" && docker compose up --build
