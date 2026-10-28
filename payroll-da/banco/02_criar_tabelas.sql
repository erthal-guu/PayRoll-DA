-- Passo 2: cria as tabelas do Payroll DA no banco "PayRoll-DA".
-- Rode conectado ao banco "PayRoll-DA" com o usuário payroll:
--   psql -U payroll -d PayRoll-DA -f banco/02_criar_tabelas.sql
-- No pgAdmin: abra o Query Tool no banco "PayRoll-DA" e execute.
--
-- É o mesmo conteúdo das migrações V1 e V2 do Flyway (backend/src/main/resources/db/migration).
-- Este passo é opcional: se o banco estiver vazio, o backend cria as tabelas sozinho na primeira subida.
-- Se as tabelas já existirem, o Flyway reconhece o banco como versão 2 e não tenta recriá-las.

-- Entidades fiscalizadas (prefeituras e câmaras) dos municípios do Paraná.
CREATE TABLE entidade (
    id           BIGSERIAL PRIMARY KEY,
    cod_ibge     VARCHAR(7)   NOT NULL,
    municipio    VARCHAR(120) NOT NULL,
    nome         VARCHAR(200) NOT NULL,
    poder        VARCHAR(12)  NOT NULL CHECK (poder IN ('EXECUTIVO', 'LEGISLATIVO')),
    CONSTRAINT uk_entidade UNIQUE (cod_ibge, nome)
);

CREATE TABLE importacao (
    id                 BIGSERIAL PRIMARY KEY,
    origem             VARCHAR(10)  NOT NULL CHECK (origem IN ('UPLOAD', 'URL', 'SIMAM')),
    referencia         VARCHAR(500) NOT NULL,
    status             VARCHAR(12)  NOT NULL CHECK (status IN ('PROCESSANDO', 'CONCLUIDA', 'COM_ERROS', 'FALHOU')),
    linhas_lidas       INTEGER      NOT NULL DEFAULT 0,
    linhas_importadas  INTEGER      NOT NULL DEFAULT 0,
    linhas_com_erro    INTEGER      NOT NULL DEFAULT 0,
    mensagem           TEXT,
    iniciada_em        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    concluida_em       TIMESTAMPTZ
);

CREATE TABLE importacao_erro (
    id             BIGSERIAL PRIMARY KEY,
    importacao_id  BIGINT  NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,
    linha          BIGINT  NOT NULL,
    mensagem       TEXT    NOT NULL
);

-- Demonstrativo da Despesa com Pessoal (RGF), um registro por entidade e período.
-- "mes" é o último mês do período de 12 meses apurado.
CREATE TABLE despesa_pessoal (
    id                      BIGSERIAL PRIMARY KEY,
    entidade_id             BIGINT        NOT NULL REFERENCES entidade (id),
    ano                     SMALLINT      NOT NULL,
    mes                     SMALLINT      NOT NULL CHECK (mes BETWEEN 1 AND 12),
    despesa_bruta           NUMERIC(16,2) NOT NULL,
    pessoal_ativo           NUMERIC(16,2) NOT NULL DEFAULT 0,
    inativos_pensionistas   NUMERIC(16,2) NOT NULL DEFAULT 0,
    outras_despesas         NUMERIC(16,2) NOT NULL DEFAULT 0,
    nao_computadas          NUMERIC(16,2) NOT NULL DEFAULT 0,
    despesa_total           NUMERIC(16,2) NOT NULL,
    rcl_ajustada            NUMERIC(16,2) NOT NULL,
    percentual              NUMERIC(7,4)  NOT NULL,
    limite_maximo           NUMERIC(5,2)  NOT NULL,
    importacao_id           BIGINT        REFERENCES importacao (id),
    atualizado_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_despesa_periodo UNIQUE (entidade_id, ano, mes)
);

CREATE INDEX ix_despesa_periodo ON despesa_pessoal (ano, mes);

-- Busca sem acento e sem diferenciar maiúsculas, sem depender da extensão unaccent.
CREATE FUNCTION unaccent_lower(t TEXT) RETURNS TEXT
    IMMUTABLE LANGUAGE sql AS
$$ SELECT lower(translate(t,
    'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇáàâãäéèêëíìîïóòôõöúùûüç',
    'AAAAAEEEEIIIIOOOOOUUUUCaaaaaeeeeiiiiooooouuuuc')) $$;
CREATE INDEX ix_entidade_municipio ON entidade (cod_ibge);
