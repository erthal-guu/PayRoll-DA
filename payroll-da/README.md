# Payroll DA – Despesa com pessoal dos municípios do Paraná

Importa o Demonstrativo da Despesa com Pessoal (RGF) publicado pelo TCE-PR, grava no PostgreSQL
e mostra um dashboard interativo com a situação de cada prefeitura e câmara frente aos limites da LRF
(Executivo 54% e Legislativo 6% da receita corrente líquida; alerta em 90% e prudencial em 95% do limite).

```
payroll-da/
├── backend/    Java 21 + Spring Boot 3.3 + Flyway + PostgreSQL
├── frontend/   React 18 + Vite + Recharts
├── dados/      exemplo_despesa_pessoal.csv (dados fictícios para testar a importação)
└── docker-compose.yml
```

## 0. Rodar tudo só com Docker (sem instalar nada)

Precisa apenas do Docker (Docker Desktop no Windows). Maven, Java, Node e PostgreSQL rodam dentro dos containers.

```bash
./rodar.sh          # Windows: rodar.bat   (equivale a: docker compose up --build)
```

- Painel: http://localhost:5173  ·  API: http://localhost:8080  ·  Banco: `localhost:5432` (payroll/payroll)
- Na primeira vez demora (baixa imagens e compila). Depois: `docker compose up`.
- Carregar dados: tela **Importação** ou `curl -F "arquivo=@dados/exemplo_despesa_pessoal.csv" http://localhost:8080/api/importacoes/upload`
- Parar: `Ctrl+C`; apagar também o banco: `docker compose down -v`.

Prévia das telas em `docs/telas/`.

## 1. Rodar só o front com dados mockados (nada além do Node)

```bash
cd frontend
npm install
npm run dev          # abre em http://localhost:5173
```

O arquivo `frontend/.env` vem com `VITE_USAR_MOCK=true`: os dados são gerados no navegador e a tela
de importação lê o CSV de verdade (mesmas regras do backend), então dá para testar com
`dados/exemplo_despesa_pessoal.csv`.

## 2. Rodar tudo (banco + API + front)

Com Docker:

```bash
docker compose up --build
```

Sem Docker: suba um PostgreSQL com banco `payroll_da` (usuário e senha `payroll`), depois

```bash
cd backend && mvn spring-boot:run          # API em http://localhost:8080 (Flyway cria as tabelas)
cd frontend && VITE_USAR_MOCK=false npm run dev
```

Para carregar dados: tela **Importação** do painel, ou

```bash
curl -F "arquivo=@dados/exemplo_despesa_pessoal.csv" http://localhost:8080/api/importacoes/upload
curl http://localhost:8080/api/importacoes            # acompanha o status
```

## 3. Integração com o TCE-PR

A página `simam.tce.pr.gov.br/Paginas/Rel_LRF.aspx` gera um relatório por vez e proíbe acesso
automatizado, então a integração **não** acessa esse formulário. Ela tem duas entradas:

| Entrada | Endpoint | Uso |
|---|---|---|
| Upload de CSV | `POST /api/importacoes/upload` (multipart, campo `arquivo`) | CSV exportado pelo usuário |
| Download por URL | `POST /api/importacoes/url` com `{"url": "https://..."}` | arquivo de dados abertos do TCE-PR (.csv ou .zip) |

O download só aceita `https` e os domínios em `tce.importacao.dominios-permitidos`. Quando o link oficial
de dados abertos for confirmado, coloque-o em `TCE_URL_PADRAO` e o corpo da requisição pode ir vazio.

**Ajustar ao CSV oficial:** os nomes das colunas ficam em `backend/src/main/resources/application.yml`,
em `tce.importacao.colunas` (campo lógico → nome no cabeçalho). A comparação ignora maiúsculas, acentos,
espaços e underlines. Também dá para trocar `charset` e `delimitador`.

Como a importação funciona:

1. O arquivo é salvo em disco temporário e a importação é registrada com status `PROCESSANDO` (resposta 202).
2. Um executor dedicado lê o CSV em streaming (Commons CSV), converte números no padrão `1.234,56` e valida cada linha.
3. Linhas válidas são gravadas em lotes de 1.000 com `INSERT ... ON CONFLICT`: reimportar um período atualiza, não duplica.
4. Linhas inválidas vão para `importacao_erro` (até 200 por importação) e o status final fica `CONCLUIDA`, `COM_ERROS` ou `FALHOU`.

## Botão Consultar (importa + Excel)

A tela **Consultar** repete o formulário do TCE-PR (tipo, município, entidade, relatório, ano e período).
Ao clicar em Consultar:

1. Se `TCE_URL_CONSULTA` estiver configurado (placeholders `{codIbge}`, `{ano}`, `{mes}`), o backend baixa o CSV
   desse endereço e importa no banco na mesma requisição. Sem ele, usa o que já foi importado.
2. A tela mostra o demonstrativo no layout do RGF (despesa bruta, deduções, DTP, RCL, percentual, limites e margem).
3. A planilha é gerada (`GET /api/consultas/excel`, Apache POI) com as abas **Demonstrativo** e **Evolução** e baixada
   automaticamente, se a opção estiver marcada.

## Assistente IA (Claude)

`POST /api/chat` recebe a conversa e o contexto da tela (filtros e entidade aberta). O `ChatService` chama a
API de Mensagens da Anthropic com quatro ferramentas, executadas no servidor sobre as mesmas consultas do dashboard:

| Ferramenta | Para quê |
|---|---|
| `resumo_painel` | totais e faixas do período |
| `buscar_entidades` | perguntas como "quais câmaras passaram do prudencial?" |
| `evolucao_entidade` | tendência de uma entidade nos últimos 12 meses |
| `sugerir_ajuste` | propõe filtros, entidade ou tela; o front mostra um botão **Aplicar** |

As instruções do assistente trazem as regras da LRF (limites, alerta, prudencial, vedações do art. 22 e medidas do
art. 23) e proíbem inventar números. Configure no servidor:

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # nunca vai para o front
export ANTHROPIC_MODELO=claude-sonnet-5-5   # opcional
```

No modo mock, o assistente usa o Claude do próprio claude.ai quando a demo é aberta lá; rodando local sem backend,
ele avisa que precisa da API.

## API do dashboard

Todos aceitam `ano`, `mes`, `poder` (`EXECUTIVO`/`LEGISLATIVO`), `codIbge` e `busca`. Sem `ano`/`mes`, usa o período mais recente.

| Endpoint | Retorna |
|---|---|
| `GET /api/dashboard/periodos` | períodos importados |
| `GET /api/dashboard/municipios` | municípios com dados |
| `GET /api/dashboard/resumo` | totais, uso médio do limite, entidades por faixa, composição da despesa |
| `GET /api/dashboard/entidades` | lista paginada; aceita `faixa`, `ordem` (`usoLimite`, `percentual`, `despesaTotal`, `entidade`, `municipio`), `direcao`, `pagina`, `tamanho` |
| `GET /api/dashboard/entidades/{id}/evolucao` | série mensal da entidade |
| `GET /api/dashboard/entidades/{id}/demonstrativo?ano&mes` | demonstrativo completo de um período |
| `GET /api/dashboard/municipios/{codIbge}/entidades` | prefeitura e câmara do município |
| `POST /api/consultas` | botão Consultar: `{codIbge, entidadeId, ano, mes}` |
| `GET /api/consultas/excel?entidadeId&ano&mes` | planilha .xlsx |
| `POST /api/chat` | assistente: `{mensagens: [{papel, texto}], contexto}` → `{texto, sugestoes}` |
| `GET /api/dashboard/evolucao-faixas` | entidades por faixa em cada período |

O front (`frontend/src/api/mock.js`) implementa exatamente esses formatos, por isso a troca de mock para API
é só mudar `VITE_USAR_MOCK`.

## O que foi verificado e o que falta

- Front: build de produção, build da demo e teste em navegador simulado: filtros, faixa, detalhe, importação, Consultar com geração do Excel e assistente com ferramentas e sugestões aplicadas.
- SQL: migração e consultas do dashboard rodadas num PostgreSQL 16 com o CSV de exemplo (1.200 linhas), incluindo o upsert.
- Backend Java: **não foi compilado** no ambiente onde foi escrito (sem acesso ao Maven Central). Rode `mvn test` na primeira vez;
  há testes do leitor de CSV em `CsvDespesaParserTest`.
- Falta confirmar o link e o layout do arquivo de dados abertos do TCE-PR e ajustar `tce.importacao.colunas`.
