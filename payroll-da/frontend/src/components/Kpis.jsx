import { moeda, pct } from '../formato.js'
import { useContador } from '../useContador.js'

const ICONES = {
  entidades: 'M4 21V8l8-5 8 5v13h-6v-6h-4v6H4Zm2-2h2v-6h8v6h2V9.1l-6-3.7-6 3.7V19Z',
  despesa: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 15.9V19h-2v-1.1c-1.7-.3-3-1.4-3.1-3h2c.1.8.9 1.4 2.1 1.4 1.3 0 2-.6 2-1.4 0-.7-.5-1.1-2.2-1.5-2-.5-3.4-1.2-3.4-3 0-1.4 1.1-2.5 2.6-2.8V6.5h2v1.1c1.5.3 2.6 1.4 2.7 2.8h-2c-.1-.7-.8-1.2-1.8-1.2-1.1 0-1.7.5-1.7 1.2 0 .6.5 1 2.2 1.4 2 .5 3.4 1.2 3.4 3.1 0 1.5-1.1 2.6-2.8 3Z',
  uso: 'M12 4a9 9 0 0 0-9 9h2a7 7 0 1 1 14 0h2a9 9 0 0 0-9-9Zm4.2 3.4-4.9 4.9a1.5 1.5 0 1 0 1.4 1.4l4.9-4.9-1.4-1.4ZM3 15v2h18v-2H3Z',
  alerta: 'M12 2 1 21h22L12 2Zm0 4.2L19.5 19h-15L12 6.2ZM11 10v5h2v-5h-2Zm0 6v2h2v-2h-2Z',
}

function Kpi({ rotulo, valor, formatar, det, icone, perigo, cor, barra, i }) {
  const animado = useContador(valor)
  return (
    <div className={`panel kpi ${perigo ? 'perigo' : ''}`} style={{ '--i': i, '--cor': cor }}>
      <div className="kpi-topo">
        <span className="kpi-icone" aria-hidden="true"><svg viewBox="0 0 24 24" width="20" height="20"><path d={ICONES[icone]} fill="currentColor" /></svg></span>
        <span className="rotulo">{rotulo}</span>
      </div>
      <div className="valor">{formatar(animado)}</div>
      <div className="det">{det}</div>
      {barra != null && <div className="barrinha" aria-hidden="true"><span style={{ width: `${Math.min(100, Math.max(0, barra))}%` }} /></div>}
    </div>
  )
}

export default function Kpis({ resumo }) {
  if (!resumo) return null
  const fora = resumo.faixas.ACIMA_LIMITE + resumo.faixas.PRUDENCIAL
  const inteiro = (n) => Math.round(n).toLocaleString('pt-BR')
  const itens = [
    { rotulo: 'Entidades analisadas', valor: resumo.entidades, formatar: inteiro, det: 'prefeituras e câmaras no filtro', icone: 'entidades', cor: '#3b5bfd' },
    { rotulo: 'Despesa total com pessoal', valor: Number(resumo.despesaTotal), formatar: moeda, det: 'soma dos últimos 12 meses', icone: 'despesa', cor: '#7c4dff' },
    { rotulo: 'Uso médio do limite', valor: Number(resumo.usoMedioLimite), formatar: (n) => (Number(resumo.rclTotal) > 0 ? pct(n, 1) : '—'), det: Number(resumo.rclTotal) > 0 ? '100% = limite máximo da LRF' : 'sem a RCL não há como calcular', icone: 'uso', cor: '#12b981', barra: Number(resumo.usoMedioLimite) },
    { rotulo: 'Acima do prudencial', valor: fora, formatar: inteiro, det: `${resumo.faixas.ACIMA_LIMITE} acima do limite máximo`, icone: 'alerta', perigo: fora > 0, cor: fora > 0 ? '#ef4444' : '#f5a524',
      barra: resumo.entidades ? (fora / resumo.entidades) * 100 : 0 },
  ]
  return (
    <div className="kpis">
      {itens.map((k, i) => <Kpi key={k.rotulo} {...k} i={i} />)}
    </div>
  )
}
