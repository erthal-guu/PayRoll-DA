# Payroll DA – Despesa com pessoal dos municípios do Paraná

Importa o Demonstrativo da Despesa com Pessoal (RGF) publicado pelo TCE-PR, grava no PostgreSQL
e mostra um dashboard interativo com a situação de cada prefeitura e câmara frente aos limites da LRF
(Executivo 54% e Legislativo 6% da receita corrente líquida; alerta em 90% e prudencial em 95% do limite).

```
payroll-da/
├── backend/    Java 21 + Spring Boot 3.3 + Flyway + PostgreSQL
├── frontend/   React 18 + Vite + Recharts
├── banco/      scripts SQL para criar o banco local PayRoll-DA
└── dados/      exemplo_despesa_pessoal.csv (dados fictícios para testar a importação)
```

## 1. Rodar o projeto (banco + API + painel)

Precisa de Java 21, Maven, Node.js e PostgreSQL instalados na máquina.

1. Crie o banco `PayRoll-DA` no PostgreSQL com os scripts da pasta `banco/` (no pgAdmin, abra cada arquivo no Query Tool):

| Script | Onde rodar | O que faz |
|---|---|---|
| `01_criar_banco.sql` | banco `postgres`, como superusuário | cria o usuário `payroll` (senha `payroll`) e o banco `PayRoll-DA` |
| `02_criar_tabelas.sql` | banco `PayRoll-DA`, usuário `payroll` | cria as tabelas (opcional: o backend cria sozinho se o banco estiver vazio) |
| `03_dados_exemplo.sql` | banco `PayRoll-DA`, usuário `payroll` | carrega 1.200 registros fictícios para o painel não abrir vazio (opcional) |

```bash
psql -U postgres -f banco/01_criar_banco.sql
psql -U payroll -d PayRoll-DA -f banco/02_criar_tabelas.sql
psql -U payroll -d PayRoll-DA -f banco/03_dados_exemplo.sql
```

2. Em um terminal, suba a API (o Flyway cria as tabelas na primeira vez):

```bash
cd backend
mvn spring-boot:run          # API em http://localhost:8080
```

3. Em outro terminal, suba o painel:

```bash
cd frontend
npm install
npm run dev                  # painel em http://localhost:5173
```

Outro banco ou outra senha: use as variáveis `DB_URL`, `DB_USER` e `DB_PASSWORD` antes do `mvn spring-boot:run`.

Prévia das telas em `docs/telas/`.

Para carregar dados: tela **Importação** do painel, ou

```bash
curl -F "arquivo=@dados/exemplo_despesa_pessoal.csv" http://localhost:8080/api/importacoes/upload
curl http://localhost:8080/api/importacoes            # acompanha o status
```

## 2. Integração com o TCE-PR (SIMAM)

Há duas formas de trazer o Demonstrativo da Despesa com Pessoal (RGF - Anexo 1) para a base:

| Forma | Tela | Como funciona |
|---|---|---|
| **Manual** | Importar CSV | Você abre o SIMAM, escolhe os filtros, clica em Consultar e usa **Exportar → CSV** no relatório. Depois envia o arquivo na tela. `POST /api/importacoes/upload` |
| **Automática** | Consulta automática | Você escolhe os filtros no sistema e ele faz o caminho do SIMAM sozinho (`POST /api/simam/filtros` e `POST /api/simam/consultar`) |

Filtros do formulário `Rel_LRF.aspx?relTipo=1`, em cascata (cada escolha libera o próximo): **Tipo** (Município ou Consórcio
Intermunicipal) → **Município** → **Entidade** → **Relatório** → **Ano** → **Período**. As opções de cada filtro vêm do próprio SIMAM.

Automação (`backend/.../simam`): abre o formulário ASP.NET, aplica cada filtro com o postback correspondente, clica em
**Consultar**, abre a tela de visualização (ReportViewer) e baixa o CSV pelo link de exportação; se o CSV não vier, lê o HTML
do relatório. O relatório é lido pelos rótulos das linhas (despesa bruta, pessoal ativo, inativos, terceirização,
não computadas, RCL ajustada, limite) e grava-se um registro por entidade e período (o último mês da janela de 12 meses). Reimportar atualiza.

> **Atenção:** o SIMAM gera um relatório por vez e a versão anterior deste projeto registrou que a página proíbe acesso
> automatizado. Confirme com o TCE-PR/termos de uso antes de usar em produção. O cliente faz um pedido por vez, com pausa
> entre eles, e se identifica no `User-Agent`.
>
> **Não validado contra o site real** (o ambiente de desenvolvimento não alcança o SIMAM): o leitor do relatório foi testado
> com uma página de visualização real e o cliente, com um servidor que simula o formulário ASP.NET. Se uma consulta falhar,
> as amostras do que o SIMAM devolveu ficam em `tce.simam.diagnostico` (padrão `/tmp/simam-diagnostico`).

Configuração: `tce.simam.url` (`TCE_SIMAM_URL`), `tce.simam.espera-ms`. O upload também aceita o CSV consolidado
(colunas em `tce.importacao.colunas`) e há importação por URL de dados abertos (`POST /api/importacoes/url`).

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

O front chama esses endpoints em `frontend/src/api/http.js`. O endereço da API fica em `VITE_API_URL` (`frontend/.env`).

## O que foi verificado e o que falta

- Front: build de produção. O painel depende do backend: sem a API no ar, as telas mostram erro de conexão.
- SQL: migração e consultas do dashboard rodadas num PostgreSQL 16 com o CSV de exemplo (1.200 linhas), incluindo o upsert.
- Backend Java: **não foi compilado** no ambiente onde foi escrito (sem acesso ao Maven Central). Rode `mvn test` na primeira vez;
  há testes do leitor de CSV em `CsvDespesaParserTest`.
- Falta confirmar o link e o layout do arquivo de dados abertos do TCE-PR e ajustar `tce.importacao.colunas`.
