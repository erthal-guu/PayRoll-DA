import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { FAIXAS } from '../formato.js'

export default function FaixasDonut({ resumo, faixa, setFaixa }) {
  if (!resumo) return null
  const dados = Object.entries(FAIXAS).map(([k, f]) => ({ chave: k, nome: f.nome, valor: resumo.faixas[k] ?? 0, cor: f.cor }))
  const total = dados.reduce((t, d) => t + d.valor, 0)
  const alternar = (k) => setFaixa(faixa === k ? null : k)
  return (
    <div className="panel">
      <div className="panel-head"><h2>Situação frente ao limite da LRF</h2><p className="hint">Clique numa faixa para filtrar</p></div>
      <div className="donut-wrap">
        <div style={{ width: 180, height: 180, position: 'relative' }}>
          <ResponsiveContainer>
            <PieChart>
              <Pie data={dados} dataKey="valor" nameKey="nome" innerRadius={60} outerRadius={88} paddingAngle={3} cornerRadius={6}
                stroke="var(--panel)" onClick={(d) => alternar(d.chave)} animationDuration={900}>
                {dados.map((d) => (
                  <Cell key={d.chave} fill={d.cor} cursor="pointer" opacity={!faixa || faixa === d.chave ? 1 : 0.3} />
                ))}
              </Pie>
              <Tooltip content={({ payload }) => payload?.[0] ? (
                <div className="tooltip"><strong>{payload[0].name}</strong>{payload[0].value} entidades</div>) : null} />
            </PieChart>
          </ResponsiveContainer>
          <div className="donut-centro"><div><strong>{total}</strong><span className="hint">entidades</span></div></div>
        </div>
        <ul className="legenda">
          {dados.map((d) => (
            <li key={d.chave}>
              <button aria-pressed={faixa === d.chave} onClick={() => alternar(d.chave)}>
                <span className="cor" style={{ background: d.cor }} />
                <span>{d.nome}<small>{FAIXAS[d.chave].descricao}</small></span>
                <span className="n">{d.valor}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
