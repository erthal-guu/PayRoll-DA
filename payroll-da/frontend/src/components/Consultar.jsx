import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/index.js'
import { salvarArquivo } from '../arquivos.js'
import { FAIXAS, MESES, moedaCheia, pct } from '../formato.js'

const MESES_LONGOS = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro']
const ETAPAS = ['Buscando o CSV no TCE-PR', 'Importando no banco de dados', 'Gerando a planilha Excel']

export default function Consultar({ periodos, municipios, aoConsultar, verNoDashboard, perguntar }) {
  const [codIbge, setCodIbge] = useState('')
  const [entidades, setEntidades] = useState([])
  const [entidadeId, setEntidadeId] = useState('')
  const anos = useMemo(() => [...new Set(periodos.map((p) => p.ano))], [periodos])
  const [ano, setAno] = useState(null)
  const [mes, setMes] = useState(null)
  const [baixarAuto, setBaixarAuto] = useState(true)
  const [etapa, setEtapa] = useState(-1)
  const [resultado, setResultado] = useState(null)
  const [erro, setErro] = useState(null)
  const [aviso, setAviso] = useState(null)

  useEffect(() => {
    if (!ano && periodos[0]) { setAno(periodos[0].ano); setMes(periodos[0].mes) }
  }, [periodos])

  // começa por Curitiba quando existir, senão pelo primeiro município com dados
  useEffect(() => {
    if (!municipios.length || municipios.some((m) => m.codIbge === codIbge)) return
    setCodIbge((municipios.find((m) => m.codIbge === '4106902') ?? municipios[0]).codIbge)
  }, [municipios])

  useEffect(() => {
    if (!codIbge) return
    api.entidadesDoMunicipio(codIbge).then((l) => { setEntidades(l); setEntidadeId(l[0]?.id ?? '') }).catch((e) => setErro(e.message))
  }, [codIbge])

  const mesesDoAno = periodos.filter((p) => p.ano === ano).map((p) => p.mes).sort((a, b) => a - b)

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
    ev.preventDefault()
    setErro(null); setAviso(null); setResultado(null)
    try {
      setEtapa(0)
      const pedido = { codIbge, entidadeId: Number(entidadeId), ano, mes }
      const consulta = api.consultar(pedido)
      setTimeout(() => setEtapa((e) => (e === 0 ? 1 : e)), 350)
      const r = await consulta
      setEtapa(2)
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
        <h1>Consultar relatório da LRF</h1>
        <div className="sub">Busca o demonstrativo no TCE-PR, importa no banco e gera a planilha Excel</div>
      </div>
      {erro && <div className="erro">{erro}</div>}

      <form className="panel form-consulta" onSubmit={consultar}>
        <label className="campo" htmlFor="c-tipo">Tipo
          <select id="c-tipo" defaultValue="municipio"><option value="municipio">Município</option></select>
        </label>
        <label className="campo largo" htmlFor="c-municipio">Município
          <select id="c-municipio" value={codIbge} onChange={(e) => setCodIbge(e.target.value)}>
            {municipios.map((m) => <option key={m.codIbge} value={m.codIbge}>{m.municipio.toUpperCase()}</option>)}
          </select>
        </label>
        <label className="campo inteiro" htmlFor="c-entidade">Entidade
          <select id="c-entidade" value={entidadeId} onChange={(e) => setEntidadeId(e.target.value)}>
            {entidades.map((e) => <option key={e.id} value={e.id}>{e.nome.toUpperCase()}</option>)}
          </select>
        </label>
        <label className="campo inteiro" htmlFor="c-relatorio">Relatório
          <select id="c-relatorio" defaultValue="rgf-pessoal"><option value="rgf-pessoal">RGF - Demonstrativo da Despesa com Pessoal</option></select>
        </label>
        <label className="campo" htmlFor="c-ano">Ano
          <select id="c-ano" value={ano ?? ''} onChange={(e) => {
            const a = Number(e.target.value); setAno(a)
            const ms = periodos.filter((p) => p.ano === a).map((p) => p.mes)
            if (!ms.includes(mes)) setMes(Math.max(...ms))
          }}>
            {anos.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </label>
        <label className="campo" htmlFor="c-periodo">Período
          <select id="c-periodo" value={mes ?? ''} onChange={(e) => setMes(Number(e.target.value))}>
            {mesesDoAno.map((m) => <option key={m} value={m}>{MESES_LONGOS[m - 1]}</option>)}
          </select>
        </label>
        <div className="inteiro acoes-consulta">
          <button className="btn" type="submit" disabled={etapa >= 0 || !entidadeId}>{etapa >= 0 ? 'Consultando…' : 'Consultar'}</button>
          <label className="check" htmlFor="c-auto">
            <input id="c-auto" type="checkbox" checked={baixarAuto} onChange={(e) => setBaixarAuto(e.target.checked)} />
            Baixar o Excel ao terminar
          </label>
        </div>
        {etapa >= 0 && (
          <ol className="etapas inteiro">
            {ETAPAS.map((t, i) => <li key={t} className={i < etapa ? 'feita' : i === etapa ? 'atual' : ''}>{t}</li>)}
          </ol>
        )}
      </form>

      {resultado && (
        <div className="panel">
          <div className="panel-head">
            <div>
              <h2>{d ? d.entidade : 'Sem resultado'}</h2>
              {d && <div className="hint">{d.municipio} · 12 meses até {MESES[d.mes - 1]}/{d.ano} · Poder {d.poder === 'EXECUTIVO' ? 'Executivo' : 'Legislativo'}</div>}
            </div>
            {d && <span className="pill" style={{ color: FAIXAS[d.faixa].cor, border: `1px solid ${FAIXAS[d.faixa].cor}` }}>{FAIXAS[d.faixa].nome}</span>}
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
