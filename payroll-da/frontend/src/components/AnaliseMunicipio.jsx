import { Bar, CartesianGrid, ComposedChart, Legend, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api/index.js'
import { FAIXAS, MESES, faixaTexto, moeda, moedaCheia, pct } from '../formato.js'
import { useCarregar } from '../useCarregar.js'

const n = (v) => Number(v) || 0
const variacao = (atual, anterior) => (anterior ? ((atual - anterior) / anterior) * 100 : null)
const rotuloPeriodo = (p) => `12 meses até ${MESES[p.mes - 1]}/${p.ano}`

/** Último ponto de cada ano (o fechamento do ano, ou o mais recente se o ano ainda não fechou). */
function porAno(serie) {
  const anos = new Map()
  serie.forEach((p) => { if (!anos.has(p.ano) || anos.get(p.ano).mes < p.mes) anos.set(p.ano, p) })
  return [...anos.values()].sort((a, b) => a.ano - b.ano)
}

/** Crescimento acumulado do primeiro ao último ano e média das variações anuais. */
function crescimento(anos, campo) {
  if (anos.length < 2 || !n(anos[0][campo])) return null
  const acumulado = variacao(n(anos[anos.length - 1][campo]), n(anos[0][campo]))
  const anuais = anos.slice(1).map((p, i) => variacao(n(p[campo]), n(anos[i][campo]))).filter((v) => v != null)
  return { acumulado, media: anuais.reduce((s, v) => s + v, 0) / anuais.length, de: anos[0].ano, ate: anos[anos.length - 1].ano }
}

/**
 * Análise da prefeitura do município, na ordem do relatório de análise financeira: RCL ajustada e despesa total com
 * pessoal (ano atual contra o mesmo período do ano anterior), série histórica e índice da LRF frente aos limites.
 */
export default function AnaliseMunicipio({ codIbge, nomeMunicipio, periodo, versao, irPara }) {
  const entidades = useCarregar(() => api.entidadesDoMunicipio(codIbge), [codIbge, versao])
  const prefeitura = entidades.dados?.find((e) => e.poder === 'EXECUTIVO')
  const evolucao = useCarregar(() => (prefeitura ? api.evolucaoEntidade(prefeitura.id) : []), [prefeitura?.id, versao])

  if (entidades.dados && !prefeitura) return null
  const serie = (evolucao.dados || []).map((p) => ({ ...p, despesaTotal: n(p.despesaTotal), rclAjustada: n(p.rclAjustada), percentual: n(p.percentual) }))
  if (!serie.length) return null

  const atual = serie.find((p) => periodo && p.ano === periodo.ano && p.mes === periodo.mes) || serie[serie.length - 1]
  const anterior = serie.find((p) => p.ano === atual.ano - 1 && p.mes === atual.mes)
  const limite = n(atual.limiteMaximo)
  const vRcl = anterior && variacao(atual.rclAjustada, anterior.rclAjustada)
  const vDtp = anterior && variacao(atual.despesaTotal, anterior.despesaTotal)
  const anos = porAno(serie)
  // a série histórica usa os anos fechados (dezembro); o ano corrente entra só no gráfico
  const fechados = anos.filter((p) => p.mes === 12)
  const historico = fechados.length >= 2 ? fechados : anos
  const cRcl = crescimento(historico, 'rclAjustada')
  const cDtp = crescimento(historico, 'despesaTotal')
  const faixa = faixaTexto(atual.percentual, limite)
  const semRcl = !atual.rclAjustada

  return (
    <div className="panel analise-municipio">
      <div className="panel-head">
        <div>
          <h2>Análise de {nomeMunicipio}</h2>
          <div className="hint">{prefeitura.nome} · {rotuloPeriodo(atual)}{anterior ? ` comparado a ${rotuloPeriodo(anterior)}` : ''}</div>
        </div>
        {irPara && <button className="btn ghost" onClick={() => irPara('professores')}>Ver tabela dos professores</button>}
      </div>

      <div className="destaques">
        <Comparativo titulo="Receita corrente líquida ajustada" atual={atual.rclAjustada} anterior={anterior?.rclAjustada} variacao={vRcl} />
        <Comparativo titulo="Despesa total com pessoal" atual={atual.despesaTotal} anterior={anterior?.despesaTotal} variacao={vDtp}
          alerta={vDtp != null && vRcl != null && vDtp > vRcl ? 'cresceu mais que a receita' : null} />
        <div className="destaque">
          <span>Índice da LRF (despesa ÷ RCL)</span>
          <strong style={{ color: FAIXAS[faixa].cor }}>{semRcl ? '—' : pct(atual.percentual)}</strong>
          <small>{semRcl ? 'sem a RCL não há como calcular' : `${FAIXAS[faixa].nome} · limites: alerta ${pct(limite * 0.9, 1)}, prudencial ${pct(limite * 0.95, 1)}, máximo ${pct(limite, 0)}`}</small>
        </div>
      </div>

      {(cRcl || cDtp) && (
        <p className="hint texto-analise">
          {cRcl && <>De {cRcl.de} a {cRcl.ate}, a receita corrente líquida ajustada cresceu {pct(cRcl.acumulado)} (média de {pct(cRcl.media)} ao ano). </>}
          {cDtp && <>A despesa total com pessoal cresceu {pct(cDtp.acumulado)} (média de {pct(cDtp.media)} ao ano). </>}
          {cRcl && cDtp && cDtp.acumulado > cRcl.acumulado && <b>A despesa com pessoal cresceu mais que a receita no período.</b>}
        </p>
      )}

      <div className="grade">
        <div>
          <h3 className="subtitulo">Receita e despesa com pessoal por ano</h3>
          <div style={{ width: '100%', height: 240 }}>
            <ResponsiveContainer>
              <ComposedChart data={anos} margin={{ top: 6, right: 6, left: 6, bottom: 0 }}>
                <CartesianGrid vertical={false} />
                <XAxis dataKey="ano" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} tickFormatter={moeda} width={78} />
                <Tooltip formatter={(v) => moedaCheia(v)} labelFormatter={(a) => `Ano ${a}`} />
                <Legend />
                <Bar dataKey="rclAjustada" name="RCL ajustada" fill="#3b5bfd" radius={[6, 6, 0, 0]} />
                <Bar dataKey="despesaTotal" name="Despesa com pessoal" fill="#7c4dff" radius={[6, 6, 0, 0]} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </div>
        <div>
          <h3 className="subtitulo">Índice da LRF por ano</h3>
          <div style={{ width: '100%', height: 240 }}>
            <ResponsiveContainer>
              <ComposedChart data={anos.map((p) => ({ ...p, percentual: p.percentual }))} margin={{ top: 6, right: 70, left: -8, bottom: 0 }}>
                <CartesianGrid vertical={false} />
                <XAxis dataKey="ano" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} tickFormatter={(v) => `${v}%`} width={48}
                  domain={[(min) => Math.floor(Math.min(min, limite * 0.8)), (max) => Math.ceil(Math.max(max, limite * 1.05))]} />
                <ReferenceLine y={limite} stroke="var(--bad)" strokeDasharray="4 4" label={{ value: `Máximo ${pct(limite, 0)}`, position: 'right', fill: 'var(--bad)', fontSize: 12 }} />
                <ReferenceLine y={limite * 0.95} stroke="var(--orange)" strokeDasharray="4 4" label={{ value: `Prudencial ${pct(limite * 0.95, 1)}`, position: 'right', fill: 'var(--orange)', fontSize: 12 }} />
                <ReferenceLine y={limite * 0.9} stroke="var(--warn)" strokeDasharray="4 4" label={{ value: `Alerta ${pct(limite * 0.9, 1)}`, position: 'right', fill: 'var(--warn)', fontSize: 12 }} />
                <Tooltip formatter={(v) => pct(v)} labelFormatter={(a) => `Ano ${a}`} />
                <Line type="monotone" dataKey="percentual" name="Índice da LRF" stroke="#3b5bfd" strokeWidth={2.5} dot={{ r: 4 }} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  )
}

function Comparativo({ titulo, atual, anterior, variacao: v, alerta }) {
  return (
    <div className="destaque">
      <span>{titulo}</span>
      <strong>{moedaCheia(atual)}</strong>
      <small>
        {anterior != null ? <>ano anterior {moedaCheia(anterior)} · evolução <b className={v > 0 ? 'sobe' : 'desce'}>{v > 0 ? '+' : ''}{pct(v)}</b></> : 'sem o mesmo período do ano anterior na base'}
        {alerta && <><br /><b style={{ color: 'var(--bad)' }}>{alerta}</b></>}
      </small>
    </div>
  )
}
