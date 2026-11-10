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
