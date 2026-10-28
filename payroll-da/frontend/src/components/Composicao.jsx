import { moeda, pct } from '../formato.js'

const PARTES = [
  ['pessoalAtivo', 'Pessoal ativo', 'var(--accent)'],
  ['inativosPensionistas', 'Inativos e pensionistas', '#7c8cff'],
  ['outrasDespesas', 'Terceirização e outras', 'var(--warn)'],
  ['naoComputadas', 'Não computadas (deduções)', 'var(--muted)'],
]

export default function Composicao({ resumo }) {
  if (!resumo) return null
  const c = resumo.composicao
  const total = PARTES.reduce((t, [k]) => t + Number(c[k] || 0), 0)
  return (
    <div className="panel">
      <div className="panel-head"><h2>Composição da despesa bruta</h2><p className="hint">{moeda(total)}</p></div>
      <div style={{ display: 'flex', height: 14, borderRadius: 7, overflow: 'hidden', background: 'var(--line)' }}>
        {PARTES.map(([k, , cor]) => <div key={k} style={{ width: `${total ? (c[k] / total) * 100 : 0}%`, background: cor }} />)}
      </div>
      <ul className="composicao">
        {PARTES.map(([k, nome, cor]) => {
          const parte = total ? (c[k] / total) * 100 : 0
          return (
            <li key={k}>
              <div className="composicao-linha">
                <span className="cor" style={{ background: cor }} />
                <span>{nome}</span>
                <span className="composicao-valor">{moeda(c[k])}</span>
                <span className="hint composicao-pct">{pct(parte, 1)}</span>
              </div>
              <div className="trilho"><span style={{ width: `${parte}%`, background: cor }} /></div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
