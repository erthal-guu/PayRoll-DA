import { useEffect, useState } from 'react'
import { api } from './api/index.js'
import Assistente from './components/Assistente.jsx'
import Composicao from './components/Composicao.jsx'
import Consultar from './components/Consultar.jsx'
import DetalheEntidade from './components/DetalheEntidade.jsx'
import EvolucaoFaixas from './components/EvolucaoFaixas.jsx'
import FaixasDonut from './components/FaixasDonut.jsx'
import Filtros from './components/Filtros.jsx'
import Importacao from './components/Importacao.jsx'
import Kpis from './components/Kpis.jsx'
import Ranking from './components/Ranking.jsx'
import TabelaEntidades from './components/TabelaEntidades.jsx'
import { periodoTxt } from './formato.js'
import { useCarregar } from './useCarregar.js'

const TELAS = { consultar: 'Consultar', dashboard: 'Dashboard', importacao: 'Importação' }

function telaInicial() {
  const h = window.location.hash.slice(1)
  return TELAS[h] ? h : 'consultar'
}

export default function App() {
  const [tela, setTela] = useState(telaInicial)
  const [versao, setVersao] = useState(0) // muda após uma importação para recarregar tudo
  const [filtros, setFiltros] = useState({})
  const [ordem, setOrdem] = useState({ campo: 'usoLimite', direcao: 'desc' })
  const [pagina, setPagina] = useState(0)
  const [selecionada, setSelecionada] = useState(null)
  const [pedidoChat, setPedidoChat] = useState(null)

  const periodos = useCarregar(() => api.periodos(), [versao])
  const municipios = useCarregar(() => api.municipios(), [versao])

  // começa no período mais recente
  useEffect(() => {
    const ultimo = periodos.dados?.[0]
    if (ultimo && !filtros.ano) setFiltros((f) => ({ ...f, ano: ultimo.ano, mes: ultimo.mes }))
  }, [periodos.dados])

  useEffect(() => setPagina(0), [filtros, ordem])

  const pronto = Boolean(filtros.ano)
  const chave = JSON.stringify(filtros)
  const resumo = useCarregar(() => (pronto ? api.resumo(filtros) : null), [chave, versao])
  const faixas = useCarregar(() => api.evolucaoFaixas(filtros), [filtros.poder, filtros.codIbge, filtros.busca, versao])
  const ranking = useCarregar(() => (pronto ? api.entidades(filtros, { ordem: 'usoLimite', direcao: 'desc', tamanho: 8 }) : null), [chave, versao])
  const tabela = useCarregar(
    () => (pronto ? api.entidades(filtros, { ordem: ordem.campo, direcao: ordem.direcao, pagina, tamanho: 12 }) : null),
    [chave, ordem.campo, ordem.direcao, pagina, versao])

  function irPara(t) {
    setTela(t)
    try { window.history.replaceState(null, '', `#${t}`) } catch { /* sem histórico disponível */ }
  }

  function abrirEntidade(d) {
    setSelecionada({ entidadeId: d.entidadeId, entidade: d.entidade, municipio: d.municipio, poder: d.poder, limiteMaximo: d.limiteMaximo })
  }

  function verNoDashboard(d) {
    setFiltros({ ano: d.ano, mes: d.mes, codIbge: d.codIbge })
    abrirEntidade(d)
    irPara('dashboard')
    window.scrollTo({ top: 0 })
  }

  /** Aplica uma sugestão do assistente: filtros, entidade no detalhe e tela. */
  async function aplicarSugestao(s) {
    const f = s.filtros || {}
    const novo = {
      ano: Number(f.ano) || filtros.ano, mes: Number(f.mes) || filtros.mes,
      poder: f.poder || null, codIbge: f.codIbge || null, faixa: f.faixa || null, busca: f.busca || null,
    }
    if (Object.keys(f).length) setFiltros(novo)
    if (s.entidadeId) {
      try { abrirEntidade(await api.demonstrativo(s.entidadeId, novo.ano, novo.mes)) } catch { /* entidade sem dado no período */ }
    }
    irPara(TELAS[s.tela] ? s.tela : 'dashboard')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const erro = periodos.erro || resumo.erro || tabela.erro
  const semDados = periodos.dados && periodos.dados.length === 0

  return (
    <div className="app">
      <aside className="side">
        <div className="logo">Payroll <b>DA</b><small>Despesa com pessoal · LRF · PR</small></div>
        <nav className="nav" aria-label="Menu principal">
          {Object.entries(TELAS).map(([k, nome]) => (
            <button key={k} aria-current={tela === k ? 'page' : undefined} onClick={() => irPara(k)}>{nome}</button>
          ))}
        </nav>
        <div className="side-foot">
          {api.modo === 'mock'
            ? <><span className="tag">Dados de exemplo</span><br />Valores fictícios gerados no navegador para testar o painel.</>
            : <>Dados importados do TCE-PR via API.</>}
        </div>
      </aside>

      <main>
        {tela === 'consultar' ? (
          <Consultar periodos={periodos.dados || []} municipios={municipios.dados || []}
            aoConsultar={() => setVersao((v) => v + 1)} verNoDashboard={verNoDashboard}
            perguntar={(texto) => setPedidoChat({ texto, em: Date.now() })} />
        ) : tela === 'importacao' ? (
          <Importacao aoImportar={() => setVersao((v) => v + 1)} />
        ) : (
          <>
            <div>
              <h1>Despesa com pessoal dos municípios</h1>
              <div className="sub">
                Limites da LRF: Executivo 54% e Legislativo 6% da receita corrente líquida
                {resumo.dados && ` · 12 meses até ${periodoTxt(resumo.dados.periodo)}`}
              </div>
            </div>

            {erro && <div className="erro">{erro}</div>}
            {semDados && (
              <div className="panel vazio">
                Ainda não há dados importados. <button className="link" onClick={() => irPara('importacao')}>Importar um arquivo do TCE-PR</button>
              </div>
            )}

            {!semDados && (
              <>
                <Filtros filtros={filtros} setFiltros={setFiltros} periodos={periodos.dados || []}
                  municipios={municipios.dados || []} nomeEntidade={selecionada?.entidade} limparEntidade={() => setSelecionada(null)} />
                <div className={resumo.carregando ? 'carregando' : ''}><Kpis resumo={resumo.dados} /></div>

                {selecionada && <DetalheEntidade key={selecionada.entidadeId} entidade={selecionada} fechar={() => setSelecionada(null)} />}

                <div className="grade-3">
                  <FaixasDonut resumo={resumo.dados} faixa={filtros.faixa} setFaixa={(faixa) => setFiltros((f) => ({ ...f, faixa }))} />
                  <EvolucaoFaixas dados={faixas.dados} periodoAtual={filtros.ano ? filtros : null}
                    setPeriodo={(p) => setFiltros((f) => ({ ...f, ano: p.ano, mes: p.mes }))} />
                </div>

                <div className="grade">
                  <Ranking itens={ranking.dados?.itens} selecionada={selecionada?.entidadeId}
                    selecionar={(e) => { setSelecionada(e); if (e) window.scrollTo({ top: 0, behavior: 'smooth' }) }} />
                  <Composicao resumo={resumo.dados} />
                </div>

                <TabelaEntidades pagina={tabela.dados} ordem={ordem} setOrdem={setOrdem} irPara={setPagina}
                  carregando={tabela.carregando} selecionada={selecionada?.entidadeId}
                  selecionar={(e) => { setSelecionada(e); window.scrollTo({ top: 0, behavior: 'smooth' }) }} />
              </>
            )}
          </>
        )}
      </main>
      <Assistente pedido={pedidoChat} aplicar={aplicarSugestao}
        contexto={{ tela, filtros, entidadeId: selecionada?.entidadeId ?? null }} />
    </div>
  )
}
