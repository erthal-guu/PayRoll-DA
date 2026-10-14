import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { FAIXAS, periodoCurto, periodoTxt } from '../formato.js'

export default function EvolucaoFaixas({ dados, periodoAtual, setPeriodo }) {
  const linhas = (dados || []).map((p) => ({ ...p, rotulo: periodoCurto(p), ...p.faixas }))
  const ehAtual = (p) => periodoAtual && p.ano === periodoAtual.ano && p.mes === periodoAtual.mes
  return (
    <div className="panel">
      <div className="panel-head"><h2>Evolução por faixa</h2><p className="hint">Clique num mês para mudar o período</p></div>
      <div style={{ width: '100%', height: 250 }}>
        <ResponsiveContainer>
          <BarChart data={linhas} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}
            onClick={(e) => e?.activePayload && setPeriodo(e.activePayload[0].payload)}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="rotulo" tickLine={false} axisLine={false} interval="preserveStartEnd" minTickGap={14} />
            <YAxis tickLine={false} axisLine={false} allowDecimals={false} />
            <Tooltip cursor={{ fill: 'rgba(0,196,255,.08)' }} content={({ payload }) => payload?.length ? (
              <div className="tooltip">
                <strong>{periodoTxt(payload[0].payload)}</strong>
                {Object.entries(FAIXAS).reverse().map(([k, f]) => (
                  <div key={k}><span style={{ color: f.cor }}>■</span> {f.nome}: {payload[0].payload[k]}</div>
                ))}
              </div>) : null} />
            {Object.entries(FAIXAS).map(([k, f]) => (
              <Bar key={k} dataKey={k} stackId="f" fill={f.cor} cursor="pointer" isAnimationActive={false}
                shape={(props) => <rect x={props.x} y={props.y} width={props.width} height={Math.max(props.height, 0)}
                  fill={f.cor} opacity={ehAtual(props.payload) ? 1 : 0.45} />} />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
