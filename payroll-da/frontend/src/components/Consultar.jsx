import { useEffect, useState } from 'react'
import { api } from '../api/index.js'
import { salvarArquivo } from '../arquivos.js'
import { FAIXAS, MESES, moedaCheia, pct } from '../formato.js'

const ETAPAS = ['Abrindo o formulário do SIMAM (TCE-PR)', 'Aplicando os filtros e gerando o relatório', 'Importando para a base de dados']
const CLASSE = { tipo: '', municipio: 'largo', entidade: 'inteiro', relatorio: 'inteiro' }

export default function Consultar({ aoConsultar, verNoDashboard, perguntar, irPara }) {
  const [selecao, setSelecao] = useState({})
  const [filtros, setFiltros] = useState([])
  const [carregandoFiltros, setCarregandoFiltros] = useState(true)
  const [baixarAuto, setBaixarAuto] = useState(false)
  const [etapa, setEtapa] = useState(-1)
  const [resultado, setResultado] = useState(null)
  const [erro, setErro] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [erroSimam, setErroSimam] = useState(null)
  const [tentativa, setTentativa] = useState(0)

  // cada escolha pode liberar o próximo filtro no SIMAM, então a lista é relida a cada mudança
  useEffect(() => {
    let atual = true
    setCarregandoFiltros(true)
    api.simamFiltros(selecao)
      .then((l) => { if (atual) { setFiltros(l); setErroSimam(null) } })
      .catch((e) => { if (atual) setErroSimam(e.message) })
      .finally(() => { if (atual) setCarregandoFiltros(false) })
    return () => { atual = false }
  }, [selecao, tentativa])

  function escolher(chave, valor) {
    const ordem = filtros.map((f) => f.chave)
    const novo = {}
    ordem.slice(0, ordem.indexOf(chave)).forEach((k) => { if (selecao[k]) novo[k] = selecao[k] })
    if (valor) novo[chave] = valor
    setSelecao(novo)
    setResultado(null)
  }

  // só contam os campos que o SIMAM liberou e que têm opções; os outros não são exigidos
  const exigidos = filtros.filter((f) => f.habilitado && f.opcoes.length > 0)
  // campo liberado mas vazio, logo depois de campos já preenchidos: o SIMAM não tem nada para essas escolhas
  const semOpcoes = filtros.filter((f, i) => f.habilitado && f.opcoes.length === 0
    && filtros.slice(0, i).every((a) => !a.habilitado || !a.opcoes.length || a.valor))
  const completo = exigidos.length > 0 && exigidos.every((f) => f.valor) && semOpcoes.length === 0
  const rotulo = (chave) => filtros.find((f) => f.chave === chave)?.opcoes.find((o) => o.valor === selecao[chave])?.rotulo

  async function baixarExcel(r = resultado) {
    setAviso(null)
    try {
      const { blob, nome } = await api.excel({ entidadeId: r.demonstrativo.entidadeId, ano: r.demonstrativo.ano, mes: r.demonstrativo.mes })
      const st = await salvarArquivo(blob, nome)
      setAviso(st === 'salvo' ? `Planilha ${nome} gerada.` : 'Download cancelado.')
    } catch (e) {
      setAviso(e.message)
    }
  }

  async function consultar(ev) {
    ev?.preventDefault()
    setErro(null); setAviso(null); setResultado(null)
    try {
      setEtapa(0)
      const consulta = api.consultarSimam(selecao)
      setTimeout(() => setEtapa((e) => (e === 0 ? 1 : e)), 400)
      setTimeout(() => setEtapa((e) => (e === 1 ? 2 : e)), 1500)
      const r = await consulta
      setResultado(r)
      aoConsultar()
      if (r.demonstrativo && baixarAuto) await baixarExcel(r)
    } catch (e) {
      setErro(e.message)
    } finally {
      setEtapa(-1)
    }
  }

  const d = resultado?.demonstrativo
  const linhas = d ? [
    ['Despesa bruta com pessoal (I)', moedaCheia(d.despesaBruta), true],
    ['Pessoal ativo', moedaCheia(d.pessoalAtivo)],
    ['Pessoal inativo e pensionistas', moedaCheia(d.inativosPensionistas)],
    ['Outras despesas de pessoal (terceirização)', moedaCheia(d.outrasDespesas)],
    ['Despesas não computadas (II)', moedaCheia(d.naoComputadas), true],
    ['Despesa total com pessoal (III = I − II)', moedaCheia(d.despesaTotal), true],
    ['Receita corrente líquida ajustada (IV)', moedaCheia(d.rclAjustada), true],
    ['Despesa total sobre a RCL (III / IV)', pct(d.percentual), true],
    ['Limite máximo', pct(d.limiteMaximo)],
    ['Limite prudencial (95%)', pct(d.limiteMaximo * 0.95)],
    ['Limite de alerta (90%)', pct(d.limiteMaximo * 0.9)],
    ['Margem até o limite máximo', moedaCheia((d.rclAjustada * d.limiteMaximo) / 100 - d.despesaTotal), true],
  ] : []

  return (
    <>
      <div>
        <h1>Consulta automática</h1>
        <div className="sub">Escolha os filtros do SIMAM: o sistema busca o relatório no TCE-PR e grava na base, sem você baixar nada.</div>
      </div>
      {erro && <div className="erro"><b>A consulta falhou.</b> {erro}</div>}

      {erroSimam && !filtros.length ? (
        <div className="panel sem-simam">
          <div className="panel-head"><h2>Não consegui abrir o SIMAM do TCE-PR</h2></div>
          <p className="erro">{erroSimam}</p>
          <p className="hint">A consulta automática acessa <code>simam.tce.pr.gov.br</code> a partir do backend. Confira se o computador
            onde o backend roda tem internet, se o site do TCE-PR abre no navegador e se nenhum firewall ou proxy está bloqueando o Java.</p>
          <div className="acoes-consulta">
            <button className="btn" onClick={() => setTentativa((t) => t + 1)} disabled={carregandoFiltros}>
              {carregandoFiltros ? 'Tentando…' : 'Tentar de novo'}
            </button>
            {irPara && <button className="btn ghost" onClick={() => irPara('importacao')}>Importar o CSV manualmente</button>}
          </div>
        </div>
      ) : (
      <>
      {erroSimam && (
        <div className="erro">
          {erroSimam}{' '}
          <button className="link" onClick={() => setTentativa((t) => t + 1)} disabled={carregandoFiltros}>Tentar de novo</button>
        </div>
      )}
      <form className={`panel form-consulta ${carregandoFiltros ? 'carregando' : ''}`} onSubmit={consultar}>
        {carregandoFiltros && !filtros.length && [0, 1, 2, 3].map((i) => <div key={i} className="campo esqueleto" />)}
        {filtros.map((f, i) => (
          <label key={f.chave} className={`campo ${CLASSE[f.chave] ?? ''}`} htmlFor={`c-${f.chave}`}>
            <span><b className="passo">{i + 1}</b>{f.rotulo}</span>
            <select id={`c-${f.chave}`} value={f.valor} disabled={!f.habilitado || etapa >= 0}
              onChange={(e) => escolher(f.chave, e.target.value)}>
              <option value="">Selecione{f.chave === 'ano' ? ' um ano' : f.chave === 'periodo' ? ' um período' : ''}…</option>
              {f.opcoes.map((o) => <option key={o.valor} value={o.valor}>{o.rotulo}</option>)}
            </select>
          </label>
        ))}
        <div className="inteiro acoes-consulta">
          <button className="btn" type="submit" disabled={etapa >= 0 || !completo}>{etapa >= 0 ? 'Consultando…' : 'Consultar e importar'}</button>
          <label className="check" htmlFor="c-auto">
            <input id="c-auto" type="checkbox" checked={baixarAuto} onChange={(e) => setBaixarAuto(e.target.checked)} />
            Gerar também a planilha Excel
          </label>
          {!completo && !carregandoFiltros && !semOpcoes.length && <span className="hint">Preencha todos os filtros na ordem numerada.</span>}
        </div>
        {semOpcoes.length > 0 && !carregandoFiltros && (
          <div className="inteiro aviso-simam">
            O SIMAM não trouxe nenhuma opção para <b>{semOpcoes.map((f) => f.rotulo).join(', ')}</b> com as escolhas atuais.
            Tente outro <b>{filtros[0]?.rotulo || 'Tipo'}</b> (para prefeituras e câmaras, use <b>Município</b>).
          </div>
        )}
        {etapa >= 0 && (
          <ol className="etapas inteiro">
            {ETAPAS.map((t, i) => <li key={t} className={i < etapa ? 'feita' : i === etapa ? 'atual' : ''}>{t}</li>)}
          </ol>
        )}
      </form>
      </>
      )}

      {resultado && !d && (
        <div className="panel sem-simam consulta-falhou">
          <div className="panel-head"><h2>A consulta não trouxe o demonstrativo</h2></div>
          <p className="erro">{resultado.mensagem || 'O SIMAM não devolveu dados para esses filtros.'}</p>
          <p className="hint" style={{ margin: 0 }}>Confira se a entidade publicou o RGF desse período no SIMAM. Se o problema continuar, baixe o CSV no site do TCE-PR e importe manualmente.</p>
          <div className="acoes-consulta">
            <button className="btn" onClick={consultar}>Tentar de novo</button>
            {irPara && <button className="btn ghost" onClick={() => irPara('importacao')}>Importar o CSV manualmente</button>}
          </div>
        </div>
      )}

      {resultado && d && (
        <div className="panel">
          <div className="panel-head">
            <div>
              <h2>{d ? d.entidade : 'Sem resultado'}</h2>
              {d && <div className="hint">{d.municipio} · 12 meses até {MESES[d.mes - 1]}/{d.ano} · Poder {d.poder === 'EXECUTIVO' ? 'Executivo' : 'Legislativo'}</div>}
            </div>
            {d && <span className="pill" style={{ '--c': FAIXAS[d.faixa].cor }}>{FAIXAS[d.faixa].nome}</span>}
          </div>
          {resultado.importacao && (
            <p className="hint" style={{ marginTop: 0 }}>Importação #{resultado.importacao.id}: {resultado.importacao.mensagem}</p>
          )}
          {!d ? <div className="vazio">{resultado.mensagem}</div> : (
            <>
              <div className="destaques">
                <div className="destaque"><span>Despesa sobre a RCL</span><strong>{pct(d.percentual)}</strong><small>limite máximo {pct(d.limiteMaximo)}</small></div>
                <div className="destaque"><span>Uso do limite</span><strong style={{ color: FAIXAS[d.faixa].cor }}>{pct((d.percentual / d.limiteMaximo) * 100, 1)}</strong><small>{FAIXAS[d.faixa].descricao}</small></div>
                <div className="destaque"><span>Margem até o limite</span><strong>{moedaCheia((d.rclAjustada * d.limiteMaximo) / 100 - d.despesaTotal)}</strong><small>sobre a RCL ajustada</small></div>
              </div>
              <div className="medidor" aria-hidden="true">
                <span style={{ width: `${Math.min(100, (d.percentual / d.limiteMaximo) * 100)}%`, background: FAIXAS[d.faixa].cor }} />
                <i style={{ left: '90%' }} /><i style={{ left: '95%' }} />
              </div>
              <div className="medidor-leg"><span>0%</span><span>alerta 90%</span><span>prudencial 95%</span><span>limite 100%</span></div>
              <div className="tabela-wrap">
                <table className="demonstrativo">
                  <tbody>
                    {linhas.map(([rot, val, forte]) => (
                      <tr key={rot} className={forte ? 'forte' : ''}><td>{rot}</td><td className="n">{val}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="acoes-consulta" style={{ marginTop: 14 }}>
                <button className="btn" onClick={() => baixarExcel()}>Baixar Excel (.xlsx)</button>
                <button className="btn ghost" onClick={() => verNoDashboard(d)}>Ver no dashboard</button>
                <button className="btn ghost" onClick={() => perguntar(`Analise a situação da ${d.entidade} (id ${d.entidadeId}) e sugira o que fazer.`)}>Pedir análise ao assistente</button>
              </div>
              {aviso && <p className="hint">{aviso}</p>}
            </>
          )}
        </div>
      )}
    </>
  )
}
