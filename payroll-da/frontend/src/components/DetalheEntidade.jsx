import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api/index.js'
import { FAIXAS, faixaTexto, moedaCheia, pct, periodoCurto, periodoTxt } from '../formato.js'
import { useCarregar } from '../useCarregar.js'

export default function DetalheEntidade({ entidade, fechar }) {
  const { dados, carregando, erro } = useCarregar(() => api.evolucaoEntidade(entidade.entidadeId), [entidade.entidadeId])
  const limite = entidade.limiteMaximo
  const serie = (dados || []).map((p) => ({ ...p, rotulo: periodoCurto(p), percentual: Number(p.percentual) }))
  const max = Math.max(limite * 1.08, ...serie.map((p) => p.percentual)) || limite
  const min = Math.min(limite * 0.7, ...serie.map((p) => p.percentual))
  const ultimo = serie[serie.length - 1]

  return (
    <div className="panel">
      <div className="panel-head">
        <div>
          <h2>{entidade.entidade}</h2>
          <div className="hint">{entidade.municipio} · {entidade.poder === 'EXECUTIVO' ? 'Poder Executivo, limite 54% da RCL' : 'Poder Legislativo, limite 6% da RCL'}</div>
        </div>
        <button className="btn ghost" onClick={fechar}>Fechar detalhe</button>
      </div>
      {erro && <div className="erro">{erro}</div>}
      {ultimo && (
        <div className="kpis" style={{ marginBottom: 12 }}>
          <div className="kpi"><div className="rotulo">Despesa com pessoal</div><div className="valor" style={{ fontSize: 20 }}>{moedaCheia(ultimo.despesaTotal)}</div></div>
          <div className="kpi"><div className="rotulo">RCL ajustada</div><div className="valor" style={{ fontSize: 20 }}>{moedaCheia(ultimo.rclAjustada)}</div></div>
          <div className="kpi"><div className="rotulo">% da RCL em {periodoTxt(ultimo)}</div><div className="valor" style={{ fontSize: 20, color: FAIXAS[faixaTexto(ultimo.percentual, limite)].cor }}>{pct(ultimo.percentual)}</div></div>
          <div className="kpi"><div className="rotulo">Margem até o limite</div><div className="valor" style={{ fontSize: 20 }}>{moedaCheia(ultimo.rclAjustada * limite / 100 - ultimo.despesaTotal)}</div></div>
        </div>
      )}
      <div className={carregando ? 'carregando' : ''} style={{ width: '100%', height: 260 }}>
        <ResponsiveContainer>
          <LineChart data={serie} margin={{ top: 10, right: 90, left: -8, bottom: 0 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="rotulo" tickLine={false} axisLine={false} minTickGap={14} />
            <YAxis domain={[Math.floor(min), Math.ceil(max)]} tickLine={false} axisLine={false} tickFormatter={(v) => `${v}%`} width={48} />
            <ReferenceLine y={limite} stroke="var(--bad)" strokeDasharray="4 4" label={{ value: `Limite ${pct(limite, 0)}`, position: 'right', fill: 'var(--bad)', fontSize: 12 }} />
            <ReferenceLine y={limite * 0.95} stroke="var(--orange)" strokeDasharray="4 4" label={{ value: `Prudencial ${pct(limite * 0.95, 1)}`, position: 'right', fill: 'var(--orange)', fontSize: 12 }} />
            <ReferenceLine y={limite * 0.9} stroke="var(--warn)" strokeDasharray="4 4" label={{ value: `Alerta ${pct(limite * 0.9, 1)}`, position: 'right', fill: 'var(--warn)', fontSize: 12 }} />
            <Tooltip content={({ payload }) => payload?.length ? (
              <div className="tooltip"><strong>{periodoTxt(payload[0].payload)}</strong>
                {pct(payload[0].payload.percentual)} da RCL<br />{moedaCheia(payload[0].payload.despesaTotal)}</div>) : null} />
            <Line type="monotone" dataKey="percentual" stroke="var(--accent)" strokeWidth={2.5} dot={false} activeDot={{ r: 5 }} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
