import { moeda, pct } from '../formato.js'

const PARTES = [
  ['pessoalAtivo', 'Pessoal ativo', '#3b5bfd'],
  ['inativosPensionistas', 'Inativos e pensionistas', '#7c4dff'],
  ['outrasDespesas', 'Terceirização e outras', '#f5a524'],
  ['naoComputadas', 'Não computadas (deduções)', '#94a3b8'],
]

export default function Composicao({ resumo }) {
  if (!resumo) return null
  const c = resumo.composicao
  const total = PARTES.reduce((t, [k]) => t + Number(c[k] || 0), 0)
  return (
    <div className="panel">
      <div className="panel-head"><h2>Composição da despesa bruta</h2><p className="hint">{moeda(total)}</p></div>
      <div className="empilhada">
        {PARTES.map(([k, , cor]) => <div key={k} style={{ width: `${total ? (c[k] / total) * 100 : 0}%`, background: cor }} />)}
      </div>
      <ul className="composicao">
        {PARTES.map(([k, nome, cor]) => {
          const parte = total ? (c[k] / total) * 100 : 0
          return (
            <li key={k}>
              <div className="composicao-linha"><span className="cor" style={{ background: cor }} />{nome}</div>
              <div>
                <div className="composicao-valor">{moeda(c[k])}</div>
                <div className="composicao-pct">{pct(parte, 1)} do total</div>
              </div>
              <div className="trilho"><span style={{ width: `${parte}%`, background: cor }} /></div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
