import { FAIXAS, PODERES, periodoTxt } from '../formato.js'

export default function Filtros({ filtros, setFiltros, periodos, municipios, nomeEntidade, limparEntidade }) {
  const muda = (campo) => (e) => setFiltros((f) => ({ ...f, [campo]: e.target.value || null }))
  const valorPeriodo = filtros.ano ? `${filtros.ano}-${filtros.mes}` : ''
  const nomeMunicipio = municipios.find((m) => m.codIbge === filtros.codIbge)?.municipio

  const chips = [
    filtros.poder && { rotulo: PODERES[filtros.poder], limpar: () => setFiltros((f) => ({ ...f, poder: null })) },
    filtros.codIbge && { rotulo: nomeMunicipio, limpar: () => setFiltros((f) => ({ ...f, codIbge: null })) },
    filtros.faixa && { rotulo: `Faixa: ${FAIXAS[filtros.faixa].nome}`, limpar: () => setFiltros((f) => ({ ...f, faixa: null })) },
    filtros.busca && { rotulo: `Busca: "${filtros.busca}"`, limpar: () => setFiltros((f) => ({ ...f, busca: null })) },
    nomeEntidade && { rotulo: `Detalhe: ${nomeEntidade}`, limpar: limparEntidade },
  ].filter(Boolean)

  return (
    <div className="panel barra-filtros">
      <div className="filtros">
        <label className="campo" htmlFor="f-periodo">Período (12 meses até)
          <select id="f-periodo" value={valorPeriodo} onChange={(e) => {
            const [ano, mes] = e.target.value.split('-').map(Number)
            setFiltros((f) => ({ ...f, ano, mes }))
          }}>
            {periodos.map((p) => <option key={`${p.ano}-${p.mes}`} value={`${p.ano}-${p.mes}`}>{periodoTxt(p)}</option>)}
          </select>
        </label>
        <div className="campo">Poder
          <div className="seg" role="group" aria-label="Poder">
            {[[null, 'Todos'], ['EXECUTIVO', 'Executivo'], ['LEGISLATIVO', 'Legislativo']].map(([v, r]) => (
              <button key={r} aria-pressed={filtros.poder === v} onClick={() => setFiltros((f) => ({ ...f, poder: v }))}>{r}</button>
            ))}
          </div>
        </div>
        <label className="campo" htmlFor="f-municipio">Município
          <select id="f-municipio" value={filtros.codIbge || ''} onChange={muda('codIbge')}>
            <option value="">Todos os municípios</option>
            {municipios.map((m) => <option key={m.codIbge} value={m.codIbge}>{m.municipio}</option>)}
          </select>
        </label>
        <label className="campo" htmlFor="f-busca" style={{ flex: '1 1 200px' }}>Buscar entidade
          <input id="f-busca" key={filtros.busca || ''} type="search" placeholder="Ex.: câmara, Londrina" defaultValue={filtros.busca || ''}
            onKeyDown={(e) => e.key === 'Enter' && setFiltros((f) => ({ ...f, busca: e.currentTarget.value.trim() || null }))}
            onBlur={(e) => setFiltros((f) => ({ ...f, busca: e.target.value.trim() || null }))} />
        </label>
      </div>
      {chips.length > 0 && (
        <div className="chips">
          {chips.map((c) => (
            <span className="chip" key={c.rotulo}>{c.rotulo}<button aria-label={`Remover ${c.rotulo}`} onClick={c.limpar}>×</button></span>
          ))}
          <button className="link" onClick={() => { setFiltros((f) => ({ ano: f.ano, mes: f.mes })); limparEntidade() }}>Limpar tudo</button>
        </div>
      )}
    </div>
  )
}
