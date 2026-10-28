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
import Login from './components/Login.jsx'
import Ranking from './components/Ranking.jsx'
import TabelaEntidades from './components/TabelaEntidades.jsx'
import Usuarios from './components/Usuarios.jsx'
import { periodoTxt } from './formato.js'
import { useCarregar } from './useCarregar.js'

const ICONES = {
  consultar: 'M11 4a7 7 0 1 0 4.2 12.6l4.1 4.1 1.4-1.4-4.1-4.1A7 7 0 0 0 11 4Zm0 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10Z',
  dashboard: 'M4 13h6V4H4v9Zm0 7h6v-5H4v5Zm10 0h6v-9h-6v9Zm0-16v5h6V4h-6Z',
  importacao: 'M12 3 6.5 8.5l1.4 1.4L11 6.8V16h2V6.8l3.1 3.1 1.4-1.4L12 3ZM5 18v3h14v-3h-2v1H7v-1H5Z',
  usuarios: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-3.3 0-8 1.7-8 5v1h16v-1c0-3.3-4.7-5-8-5Z',
}

// Prefeituras só veem o dashboard do próprio município; importar e consultar o SIMAM é do administrador.
const TELAS_ADMIN = { consultar: 'Consulta automática', dashboard: 'Dashboard', importacao: 'Importar CSV', usuarios: 'Usuários' }
const TELAS_PREFEITURA = { dashboard: 'Dashboard', usuarios: 'Minha conta' }

function telaInicial(TELAS) {
  const h = window.location.hash.slice(1)
  return TELAS[h] ? h : Object.keys(TELAS)[0]
}

const iniciais = (nome) => nome.split(' ').filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase()

function saudacao() {
  const h = new Date().getHours()
  return h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite'
}

/** Pede login antes de mostrar o painel. */
export default function App() {
  const [usuario, setUsuario] = useState(null)
  const [verificando, setVerificando] = useState(api.temToken())

  useEffect(() => {
    if (verificando) api.eu().then(setUsuario).catch(() => null).finally(() => setVerificando(false))
    const expirou = () => setUsuario(null)
    window.addEventListener('payroll:sessao-expirada', expirou)
    return () => window.removeEventListener('payroll:sessao-expirada', expirou)
  }, [])

  if (verificando) return null
  if (!usuario) return <Login aoEntrar={setUsuario} />
  return <Painel usuario={usuario} sair={async () => { await api.sair(); setUsuario(null) }} />
}

function Painel({ usuario, sair }) {
  const TELAS = usuario.perfil === 'ADMIN' ? TELAS_ADMIN : TELAS_PREFEITURA
  const [tela, setTela] = useState(() => telaInicial(TELAS))
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
  const ranking = useCarregar(() => (pronto ? api.entidades(filtros, { ordem: 'usoLimite', direcao: 'desc', tamanho: 6 }) : null), [chave, versao])
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

  const ultimoPeriodo = periodos.dados?.[0]
  const erro = periodos.erro || resumo.erro || tabela.erro
  const semDados = periodos.dados && periodos.dados.length === 0

  return (
    <div className="app">
      <aside className="side">
        <div className="logo"><span className="marca" aria-hidden="true" /><span>Payroll <b>DA</b><small>Despesa com pessoal · LRF · PR</small></span></div>
        <div className="secao-menu">Menu</div>
        <nav className="nav" aria-label="Menu principal">
          {Object.entries(TELAS).map(([k, nome]) => (
            <button key={k} aria-current={tela === k ? 'page' : undefined} onClick={() => irPara(k)}>
              <svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true"><path d={ICONES[k]} fill="currentColor" /></svg>{nome}
            </button>
          ))}
        </nav>
        <div className="side-foot">
          <div className="usuario-logado">
            <span className="avatar" aria-hidden="true">{iniciais(usuario.nome)}</span>
            <div className="quem">
              <b>{usuario.nome}</b>
              <span>{usuario.perfil === 'ADMIN' ? 'Administrador' : `Prefeitura · ${usuario.municipio || usuario.codIbge}`}</span>
            </div>
            <button className="sair" onClick={sair} title="Sair" aria-label="Sair">
              <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true"><path fill="currentColor" d="M10 4H5v16h5v-2H7V6h3V4Zm5.6 3.6-1.4 1.4 2 2H9v2h7.2l-2 2 1.4 1.4L20 12l-4.4-4.4Z" /></svg>
            </button>
          </div>
        </div>
      </aside>

      <main>
        <header className="topo">
          <div className="ola">{saudacao()},<b>{usuario.nome.split(' ')[0]}</b></div>
          {ultimoPeriodo && <span className="selo"><i aria-hidden="true" />Dados até {periodoTxt(ultimoPeriodo)}</span>}
        </header>
        <div className="tela" key={tela}>
        {tela === 'consultar' ? (
          <Consultar aoConsultar={() => setVersao((v) => v + 1)} verNoDashboard={verNoDashboard} irPara={irPara}
            perguntar={(texto) => setPedidoChat({ texto, em: Date.now() })} />
        ) : tela === 'importacao' ? (
          <Importacao aoImportar={() => setVersao((v) => v + 1)} />
        ) : tela === 'usuarios' ? (
          <Usuarios usuario={usuario} municipios={municipios.dados || []} />
        ) : (
          <>
            <section className="hero">
              <div>
                <span className="rotulo-hero">
                  {resumo.dados ? `12 meses até ${periodoTxt(resumo.dados.periodo)}` : 'Lei de Responsabilidade Fiscal'}
                </span>
                <h1>Despesa com pessoal dos municípios</h1>
                <div className="sub">Limites da LRF: Executivo 54% e Legislativo 6% da receita corrente líquida.</div>
              </div>
              {resumo.dados && (
                <div className="hero-resumo">
                  <div><strong>{resumo.dados.entidades}</strong><span>entidades no filtro</span></div>
                  <div className={resumo.dados.faixas.ACIMA_LIMITE ? 'ruim' : ''}>
                    <strong>{resumo.dados.faixas.ACIMA_LIMITE}</strong><span>acima do limite máximo</span>
                  </div>
                </div>
              )}
            </section>

            {erro && <div className="erro">{erro}</div>}
            {semDados && (
              <div className="panel vazio">
                Ainda não há dados importados.{' '}
                {TELAS.importacao && <button className="link" onClick={() => irPara('importacao')}>Importar um arquivo do TCE-PR</button>}
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
        </div>
      </main>
      <Assistente pedido={pedidoChat} aplicar={aplicarSugestao}
        contexto={{ tela, filtros, entidadeId: selecionada?.entidadeId ?? null }} />
    </div>
  )
}
