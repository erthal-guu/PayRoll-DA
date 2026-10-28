import { FAIXAS, pct } from '../formato.js'

/** Entidades mais próximas do limite. A barra mostra o uso do limite (100% = limite máximo). */
export default function Ranking({ itens, selecionada, selecionar }) {
  const ESCALA = 110 // eixo vai até 110% do limite
  return (
    <div className="panel">
      <div className="panel-head"><h2>Mais perto do limite</h2><p className="hint">O traço marca o limite máximo</p></div>
      {!itens?.length ? <div className="vazio">Nenhuma entidade no filtro atual.</div> : (
        <div className="ranking">
          {itens.map((e, i) => {
            const uso = (e.percentual / e.limiteMaximo) * 100
            return (
              <button key={e.entidadeId} className="rank-item" aria-pressed={selecionada === e.entidadeId}
                onClick={() => selecionar(selecionada === e.entidadeId ? null : e)}>
                <span className="rank-pos">{i + 1}</span>
                <div style={{ minWidth: 0 }}>
                  <div className="rank-nome">{e.entidade} <small>· {e.poder === 'EXECUTIVO' ? 'limite 54%' : 'limite 6%'}</small></div>
                  <div className="trilho">
                    <span style={{ width: `${Math.min(uso, ESCALA) / ESCALA * 100}%`, background: FAIXAS[e.faixa].cor }} />
                    <i style={{ left: `${100 / ESCALA * 100}%` }} />
                  </div>
                </div>
                <div className="rank-pct" title="Despesa com pessoal sobre a RCL">{pct(e.percentual)}</div>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
