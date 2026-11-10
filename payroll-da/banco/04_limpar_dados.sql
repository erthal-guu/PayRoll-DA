-- Limpa todos os dados do painel, mantendo os usuários e as sessões de login.
-- ATENÇÃO: apaga de vez importações, despesas, entidades, FUNDEB, professores e conversas do assistente.
-- Rode no banco "PayRoll-DA":
--   psql -U payroll -d PayRoll-DA -f banco/04_limpar_dados.sql
--
-- Ficam de fora: usuario, sessao e flyway_schema_history (controle das migrações).

BEGIN;

TRUNCATE TABLE
    importacao_erro,
    despesa_pessoal,
    importacao,
    entidade,
    fundeb_municipio,
    plano_magisterio_nivel,
    plano_magisterio,
    piso_magisterio,
    chat_mensagem,
    chat_conversa
RESTART IDENTITY;

COMMIT;
