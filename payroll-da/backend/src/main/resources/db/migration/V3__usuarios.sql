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
