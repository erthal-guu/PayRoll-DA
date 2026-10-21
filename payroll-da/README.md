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

## Demonstração sem instalar nada (sem Docker, Node ou Java)

Abra `demo/payroll-da-demo.html` com um duplo clique (Chrome, Edge ou Firefox). É o painel inteiro com dados fictícios
gerados no navegador: dashboard, filtros, detalhe por entidade, consulta (simulada), importação de CSV
(`dados/exemplo_despesa_pessoal.csv`) e geração do Excel. Não usa backend nem banco; a consulta automática ao SIMAM e o
assistente com IA só funcionam de verdade com o backend rodando. Para regenerar o arquivo: `cd frontend && npm run build:demo`
e copiar `dist-demo/index.html`.

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

## 3. Integração com o TCE-PR (SIMAM)

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
> as amostras do que o SIMAM devolveu ficam em `tce.simam.diagnostico` (padrão `/tmp/simam-diagnostico`, dentro do container).

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
