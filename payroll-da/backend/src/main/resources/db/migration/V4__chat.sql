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
