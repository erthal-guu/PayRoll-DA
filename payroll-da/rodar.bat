@echo off
REM Sobe banco + API + painel usando apenas o Docker Desktop (nada de Java/Node/Maven/Postgres na maquina).
REM Opcional: set ANTHROPIC_API_KEY=sk-ant-...  (assistente IA)
docker compose up --build
