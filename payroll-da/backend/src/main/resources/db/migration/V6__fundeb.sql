-- Transferências do FUNDEB por município e ano, lidas do RREO - Demonstrativo das Receitas e Despesas com
-- Manutenção e Desenvolvimento do Ensino (MDE) do SIMAM. Fica o último período importado do ano (valores
-- acumulados até ele; no último bimestre, o ano inteiro).
CREATE TABLE IF NOT EXISTS fundeb_municipio (
    cod_ibge                 VARCHAR(7)    NOT NULL,
    municipio                VARCHAR(120)  NOT NULL,
    ano                      SMALLINT      NOT NULL,
    periodo                  VARCHAR(60)   NOT NULL,
    mes_final                SMALLINT      NOT NULL CHECK (mes_final BETWEEN 1 AND 12),
    receitas_destinadas      NUMERIC(16,2),
    receitas_recebidas       NUMERIC(16,2),
    transferencias           NUMERIC(16,2),
    complementacao_uniao     NUMERIC(16,2),
    resultado_liquido        NUMERIC(16,2),
    despesas_profissionais   NUMERIC(16,2),
    total_despesas           NUMERIC(16,2),
    perc_profissionais       NUMERIC(7,2),
    importado_em             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (cod_ibge, ano)
);
