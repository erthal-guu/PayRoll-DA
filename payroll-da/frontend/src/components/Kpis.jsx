import { moeda, pct } from '../formato.js'

export default function Kpis({ resumo }) {
  if (!resumo) return null
  const fora = resumo.faixas.ACIMA_LIMITE + resumo.faixas.PRUDENCIAL
  const itens = [
    ['Entidades analisadas', resumo.entidades.toLocaleString('pt-BR'), 'prefeituras e câmaras no filtro'],
    ['Despesa total com pessoal', moeda(resumo.despesaTotal), 'soma dos últimos 12 meses'],
    ['Uso médio do limite', pct(resumo.usoMedioLimite, 1), '100% = limite máximo da LRF'],
    ['Acima do prudencial', fora.toLocaleString('pt-BR'), `${resumo.faixas.ACIMA_LIMITE} acima do limite máximo`],
  ]
  return (
    <div className="kpis">
      {itens.map(([rotulo, valor, det], i) => (
        <div className="panel kpi" key={rotulo}>
          <div className="rotulo">{rotulo}</div>
          <div className="valor" style={i === 3 && fora > 0 ? { color: 'var(--bad)' } : undefined}>{valor}</div>
          <div className="det">{det}</div>
        </div>
      ))}
    </div>
  )
}
