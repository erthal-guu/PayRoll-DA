-- Passo 2: cria as tabelas do Payroll DA no banco "PayRoll-DA".
-- Rode conectado ao banco "PayRoll-DA" com o usuário payroll:
--   psql -U payroll -d PayRoll-DA -f banco/02_criar_tabelas.sql
-- No pgAdmin: abra o Query Tool no banco "PayRoll-DA" e execute.
--
-- É o mesmo conteúdo das migrações V1 a V5 do Flyway (backend/src/main/resources/db/migration).
-- Este passo é opcional: se o banco estiver vazio, o backend cria as tabelas sozinho na primeira subida.
-- Se as tabelas já existirem, o Flyway reconhece o banco como versão 3 e não tenta recriá-las.

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

-- Usuários do painel. ADMIN vê todos os municípios e importa dados;
-- PREFEITURA vê só o município (cod_ibge) ao qual está ligado.
CREATE TABLE usuario (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    perfil      VARCHAR(12)  NOT NULL CHECK (perfil IN ('ADMIN', 'PREFEITURA')),
    cod_ibge    VARCHAR(7),
    municipio   VARCHAR(120),
    ativo       BOOLEAN      NOT NULL DEFAULT true,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_usuario_prefeitura CHECK (perfil = 'ADMIN' OR cod_ibge IS NOT NULL)
);

CREATE UNIQUE INDEX uk_usuario_email ON usuario (lower(email));

-- Sessões de login. Guarda só o hash SHA-256 do token entregue ao navegador.
CREATE TABLE sessao (
    token_hash  CHAR(64)    PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    expira_em   TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_sessao_usuario ON sessao (usuario_id);

-- Conversas do Assistente IA. Cada usuário vê só as próprias conversas.
CREATE TABLE IF NOT EXISTS chat_conversa (
    id             BIGSERIAL    PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    titulo         VARCHAR(120) NOT NULL,
    criada_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizada_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_chat_conversa_usuario ON chat_conversa (usuario_id, atualizada_em DESC);

-- Mensagens de cada conversa, na ordem do id. sugestoes guarda os botões "Aplicar" da resposta.
CREATE TABLE IF NOT EXISTS chat_mensagem (
    id           BIGSERIAL    PRIMARY KEY,
    conversa_id  BIGINT       NOT NULL REFERENCES chat_conversa (id) ON DELETE CASCADE,
    papel        VARCHAR(10)  NOT NULL CHECK (papel IN ('user', 'assistant')),
    texto        TEXT         NOT NULL,
    sugestoes    JSONB,
    criada_em    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_chat_mensagem_conversa ON chat_mensagem (conversa_id, id);

-- Tela dos professores: piso salarial do magistério (PSPN) e plano de carreira de cada município.
-- As tabelas de vencimentos (atual, em conformidade com o piso e defasagem) são calculadas a partir daqui.

-- Piso Salarial Profissional Nacional do Magistério, por ano, para 20 horas semanais.
CREATE TABLE IF NOT EXISTS piso_magisterio (
    ano            SMALLINT      PRIMARY KEY,
    valor_20h      NUMERIC(12,2) NOT NULL CHECK (valor_20h > 0),
    ato_legal      VARCHAR(300),
    atualizado_em  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Estrutura da tabela de vencimentos do magistério do município: vencimento da classe inicial (1-2) do primeiro
-- nível e o percentual que cada classe seguinte soma sobre a anterior.
CREATE TABLE IF NOT EXISTS plano_magisterio (
    id               BIGSERIAL     PRIMARY KEY,
    cod_ibge         VARCHAR(7)    NOT NULL,
    municipio        VARCHAR(120)  NOT NULL,
    ano              SMALLINT      NOT NULL,
    carga_horaria    SMALLINT      NOT NULL DEFAULT 20 CHECK (carga_horaria > 0),
    vencimento_base  NUMERIC(12,2) NOT NULL CHECK (vencimento_base > 0),
    perc_classe      NUMERIC(6,3)  NOT NULL DEFAULT 2 CHECK (perc_classe >= 0),
    classe_final     SMALLINT      NOT NULL DEFAULT 30 CHECK (classe_final BETWEEN 2 AND 60),
    lei              VARCHAR(500),
    atualizado_em    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_plano_magisterio UNIQUE (cod_ibge, ano, carga_horaria)
);

-- Níveis do plano (ex.: I magistério 0%, II licenciatura 16%, III pós-graduação 19,5% sobre o vencimento base).
CREATE TABLE IF NOT EXISTS plano_magisterio_nivel (
    plano_id         BIGINT        NOT NULL REFERENCES plano_magisterio (id) ON DELETE CASCADE,
    ordem            SMALLINT      NOT NULL,
    nome             VARCHAR(80)   NOT NULL,
    perc_sobre_base  NUMERIC(6,3)  NOT NULL DEFAULT 0 CHECK (perc_sobre_base >= 0),
    PRIMARY KEY (plano_id, ordem)
);
