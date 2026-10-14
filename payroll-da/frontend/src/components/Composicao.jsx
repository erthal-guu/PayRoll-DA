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
      <ul className="legenda" style={{ marginTop: 12 }}>
        {PARTES.map(([k, nome, cor]) => (
          <li key={k} style={{ display: 'grid', gridTemplateColumns: '12px 1fr auto auto', gap: 10, alignItems: 'center', padding: '4px 8px', fontSize: 14 }}>
            <span className="cor" style={{ background: cor }} />
            <span>{nome}</span>
            <span style={{ fontFamily: 'var(--f-num)' }}>{moeda(c[k])}</span>
            <span className="hint" style={{ width: 56, textAlign: 'right' }}>{pct(total ? (c[k] / total) * 100 : 0, 1)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
