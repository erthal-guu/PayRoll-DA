import { FAIXAS, moeda, pct } from '../formato.js'

const COLUNAS = [
  ['entidade', 'Entidade', false],
  ['municipio', 'Município', false],
  ['despesaTotal', 'Despesa com pessoal', true],
  ['percentual', '% da RCL', true],
  ['usoLimite', 'Uso do limite', true],
]

export default function TabelaEntidades({ pagina, ordem, setOrdem, irPara, selecionada, selecionar, carregando }) {
  if (!pagina) return null
  const ordenar = (campo) => setOrdem((o) => ({ campo, direcao: o.campo === campo && o.direcao === 'desc' ? 'asc' : 'desc' }))
  const totalPaginas = Math.max(1, Math.ceil(pagina.total / pagina.tamanho))
  const inicio = pagina.total ? pagina.pagina * pagina.tamanho + 1 : 0
  const fim = Math.min(pagina.total, (pagina.pagina + 1) * pagina.tamanho)

  return (
    <div className="panel">
      <div className="panel-head"><h2>Entidades</h2><p className="hint">Clique numa linha para ver a evolução</p></div>
      <div className={`tabela-wrap ${carregando ? 'carregando' : ''}`}>
        <table>
          <thead>
            <tr>
              {COLUNAS.map(([campo, rotulo, num]) => (
                <th key={campo} className={num ? 'n' : ''}>
                  <button onClick={() => ordenar(campo)} aria-sort={ordem.campo === campo ? (ordem.direcao === 'asc' ? 'ascending' : 'descending') : undefined}>
                    {rotulo} {ordem.campo === campo ? (ordem.direcao === 'asc' ? '↑' : '↓') : ''}
                  </button>
                </th>
              ))}
              <th>Situação</th>
            </tr>
          </thead>
          <tbody>
            {pagina.itens.map((e) => (
              <tr key={e.entidadeId} aria-selected={selecionada === e.entidadeId} tabIndex={0}
                onClick={() => selecionar(e)} onKeyDown={(ev) => ev.key === 'Enter' && selecionar(e)}>
                <td>{e.entidade}</td>
                <td>{e.municipio}</td>
                <td className="n">{moeda(e.despesaTotal)}</td>
                <td className="n">{pct(e.percentual)}</td>
                <td className="n">{pct((e.percentual / e.limiteMaximo) * 100, 1)}</td>
                <td><span className="pill" style={{ color: FAIXAS[e.faixa].cor, background: 'var(--panel-2)', border: `1px solid ${FAIXAS[e.faixa].cor}` }}>{FAIXAS[e.faixa].nome}</span></td>
              </tr>
            ))}
            {!pagina.itens.length && <tr><td colSpan={6} className="vazio">Nenhuma entidade encontrada com esses filtros.</td></tr>}
          </tbody>
        </table>
      </div>
      <div className="paginacao">
        <span>{inicio}–{fim} de {pagina.total} entidades</span>
        <div>
          <button className="btn ghost" disabled={pagina.pagina === 0} onClick={() => irPara(pagina.pagina - 1)}>Anterior</button>
          <button className="btn ghost" disabled={pagina.pagina + 1 >= totalPaginas} onClick={() => irPara(pagina.pagina + 1)}>Próxima</button>
        </div>
      </div>
    </div>
  )
}
