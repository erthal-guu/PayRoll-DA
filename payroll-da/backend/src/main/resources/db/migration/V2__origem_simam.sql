-- Importações feitas pela consulta automática no SIMAM (TCE-PR).
ALTER TABLE importacao DROP CONSTRAINT importacao_origem_check;
ALTER TABLE importacao ADD CONSTRAINT importacao_origem_check CHECK (origem IN ('UPLOAD', 'URL', 'SIMAM'));
