import { useEffect, useState } from 'react'
import { api } from '../api/index.js'
import { pct } from '../formato.js'

const real = (n) => (Number(n) || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const num = (v) => (v === '' || v == null ? null : Number(String(v).replace(',', '.')))
const NIVEIS_PADRAO = [
  { nome: 'I - Magistério', percSobreBase: '0' },
  { nome: 'II - Licenciatura', percSobreBase: '16' },
  { nome: 'III - Pós-graduação', percSobreBase: '19,5' },
]
const PLANO_VAZIO = { vencimentoBase: '', percClasse: '2', classeFinal: '30', lei: '', niveis: NIVEIS_PADRAO }

/** Tabela de vencimentos do magistério do município frente ao Piso Salarial Profissional Nacional (PSPN). */
export default function Professores({ usuario, municipios, codIbgeInicial }) {
  const admin = usuario.perfil === 'ADMIN'
  const anoAtual = new Date().getFullYear()
  const [codIbge, setCodIbge] = useState(admin ? codIbgeInicial || '' : usuario.codIbge)
  const [ano, setAno] = useState(anoAtual)
  const [carga, setCarga] = useState(20)
  const [analise, setAnalise] = useState(null)
  const [planos, setPlanos] = useState([])
  const [pisos, setPisos] = useState([])
  const [plano, setPlano] = useState(PLANO_VAZIO)
  const [piso, setPiso] = useState({ valor20h: '', atoLegal: '' })
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState(null)
  const [aviso, setAviso] = useState(null)

  const nomeMunicipio = municipios.find((m) => m.codIbge === codIbge)?.municipio
    || planos.find((p) => p.codIbge === codIbge)?.municipio || (admin ? '' : usuario.municipio)

  const atualizarListas = () => {
    api.professoresPlanos().then(setPlanos).catch(() => null)
    api.professoresPisos().then(setPisos).catch(() => null)
  }
  useEffect(atualizarListas, [])

  useEffect(() => {
    const p = pisos.find((x) => x.ano === Number(ano))
    setPiso({ valor20h: p ? String(p.valor20h).replace('.', ',') : '', atoLegal: p?.atoLegal || '' })
  }, [pisos, ano])

  useEffect(() => {
    if (!codIbge || !ano) { setAnalise(null); return }
    let ativo = true
    setCarregando(true); setErro(null)
    api.professores(codIbge, ano, carga)
      .then((a) => {
        if (!ativo) return
        setAnalise(a)
        setPlano(a ? {
          vencimentoBase: String(a.plano.vencimentoBase).replace('.', ','), percClasse: String(a.plano.percClasse).replace('.', ','),
          classeFinal: String(a.plano.classeFinal), lei: a.plano.lei || '',
          niveis: a.plano.niveis.map((n) => ({ nome: n.nome, percSobreBase: String(n.percSobreBase).replace('.', ',') })),
        } : PLANO_VAZIO)
      })
      .catch((e) => ativo && setErro(e.message))
      .finally(() => ativo && setCarregando(false))
    return () => { ativo = false }
  }, [codIbge, ano, carga])

  async function salvarPiso(ev) {
    ev.preventDefault(); setErro(null); setAviso(null)
    try {
      await api.salvarPiso(ano, { valor20h: num(piso.valor20h), atoLegal: piso.atoLegal })
      setAviso(`Piso de ${ano} salvo.`)
      atualizarListas()
      if (analise) setAnalise(await api.professores(codIbge, ano, carga))
    } catch (e) { setErro(e.message) }
  }

  async function salvarPlano(ev) {
    ev.preventDefault(); setErro(null); setAviso(null)
    try {
      const a = await api.salvarPlanoProfessores(codIbge, ano, {
        municipio: nomeMunicipio, cargaHoraria: Number(carga), vencimentoBase: num(plano.vencimentoBase),
        percClasse: num(plano.percClasse), classeFinal: Number(plano.classeFinal), lei: plano.lei,
        niveis: plano.niveis.map((n) => ({ nome: n.nome, percSobreBase: num(n.percSobreBase) ?? 0 })),
      })
      setAnalise(a)
      setAviso('Tabela de vencimentos salva.')
      atualizarListas()
    } catch (e) { setErro(e.message) }
  }

  const mudaNivel = (i, k) => (e) => setPlano((p) => ({ ...p, niveis: p.niveis.map((n, j) => (j === i ? { ...n, [k]: e.target.value } : n)) }))

  return (
    <>
      <div>
        <h1>Professores</h1>
        <div className="sub">Tabela de vencimentos do magistério comparada ao Piso Salarial Profissional Nacional (Lei Federal 11.738/2008).</div>
      </div>
      {erro && <div className="erro">{erro}</div>}
      {aviso && <div className="panel">{aviso}</div>}

      <div className="panel barra-filtros">
        <div className="filtros">
          {admin && (
            <label className="campo" htmlFor="p-municipio">Município
              <select id="p-municipio" value={codIbge} onChange={(e) => setCodIbge(e.target.value)}>
                <option value="">Escolha…</option>
                {municipios.map((m) => <option key={m.codIbge} value={m.codIbge}>{m.municipio}</option>)}
              </select>
            </label>
          )}
          <label className="campo" htmlFor="p-ano">Ano
            <input id="p-ano" type="number" min="2000" max="2100" value={ano} onChange={(e) => setAno(e.target.value)} />
          </label>
          <label className="campo" htmlFor="p-carga">Carga horária
            <select id="p-carga" value={carga} onChange={(e) => setCarga(Number(e.target.value))}>
              {[20, 30, 40].map((h) => <option key={h} value={h}>{h} horas</option>)}
            </select>
          </label>
        </div>
        {planos.length > 0 && (
          <p className="hint">Já cadastrados: {planos.map((p) => (
            <button key={`${p.codIbge}-${p.ano}-${p.cargaHoraria}`} className="link" style={{ marginRight: 10 }}
              onClick={() => { setCodIbge(p.codIbge); setAno(p.ano); setCarga(p.cargaHoraria) }}>
              {p.municipio} {p.ano} ({p.cargaHoraria} h)
            </button>))}
          </p>
        )}
      </div>

      {!codIbge && <div className="panel vazio">Escolha um município para ver a tabela dos professores.</div>}

      {codIbge && (
        <div className={`resultado-professores ${carregando ? 'carregando' : ''}`}>
          {analise ? <Resultado analise={analise} /> : !carregando && (
            <div className="panel vazio">Ainda não há tabela de vencimentos de {nomeMunicipio} para {ano} ({carga} horas).
              {admin ? ' Cadastre abaixo.' : ''}</div>
          )}
        </div>
      )}

      {admin && codIbge && (
        <div className="grade">
          <form className="panel form-usuario" onSubmit={salvarPlano}>
            <div className="panel-head"><h2>Plano de carreira de {nomeMunicipio} · {ano} · {carga} h</h2></div>
            <label className="campo">Vencimento inicial (classe 1-2 do primeiro nível), R$
              <input required inputMode="decimal" value={plano.vencimentoBase} onChange={(e) => setPlano({ ...plano, vencimentoBase: e.target.value })} placeholder="2.236,79" />
            </label>
            <label className="campo">Aumento por classe (% sobre a anterior)
              <input required inputMode="decimal" value={plano.percClasse} onChange={(e) => setPlano({ ...plano, percClasse: e.target.value })} />
            </label>
            <label className="campo">Última classe
              <input required type="number" min="2" max="60" value={plano.classeFinal} onChange={(e) => setPlano({ ...plano, classeFinal: e.target.value })} />
            </label>
            {plano.niveis.map((n, i) => (
              <div key={i} className="linha-nivel">
                <label className="campo">Nível<input required value={n.nome} onChange={mudaNivel(i, 'nome')} /></label>
                <label className="campo">% sobre o inicial<input inputMode="decimal" value={n.percSobreBase} onChange={mudaNivel(i, 'percSobreBase')} /></label>
                <button type="button" className="link" aria-label={`Remover ${n.nome}`} disabled={plano.niveis.length === 1}
                  onClick={() => setPlano((p) => ({ ...p, niveis: p.niveis.filter((_, j) => j !== i) }))}>Remover</button>
              </div>
            ))}
            <button type="button" className="link" style={{ alignSelf: 'flex-start' }}
              onClick={() => setPlano((p) => ({ ...p, niveis: [...p.niveis, { nome: '', percSobreBase: '0' }] }))}>Adicionar nível</button>
            <label className="campo">Lei do reajuste (opcional)
              <input value={plano.lei} onChange={(e) => setPlano({ ...plano, lei: e.target.value })} placeholder="Ex.: Lei nº 2036, de 24/02/2026" />
            </label>
            <div className="acoes-form"><button className="btn">Salvar tabela</button></div>
          </form>

          <form className="panel form-usuario" onSubmit={salvarPiso}>
            <div className="panel-head"><h2>Piso nacional de {ano}</h2></div>
            <label className="campo">Valor para 20 horas, R$
              <input required inputMode="decimal" value={piso.valor20h} onChange={(e) => setPiso({ ...piso, valor20h: e.target.value })} placeholder="2.565,32" />
            </label>
            <label className="campo">Ato legal (opcional)
              <input value={piso.atoLegal} onChange={(e) => setPiso({ ...piso, atoLegal: e.target.value })} placeholder="Ex.: Portaria MEC nº 82, de 29/01/2026" />
            </label>
            <span className="hint">Vale para todos os municípios. Para outras cargas horárias o piso é proporcional (40 h = o dobro).</span>
            <div className="acoes-form"><button className="btn">Salvar piso</button></div>
          </form>
        </div>
      )}
    </>
  )
}

function Resultado({ analise: a }) {
  const semPiso = a.piso == null
  return (
    <>
      <div className="kpis kpis-professores">
        <div className="panel kpi"><div className="rotulo">Piso nacional ({a.plano.cargaHoraria} h)</div>
          <div className="valor" style={{ fontSize: 22 }}>{semPiso ? '—' : real(a.piso)}</div>
          <div className="det">{semPiso ? `cadastre o piso de ${a.plano.ano}` : a.pisoAno.atoLegal || `PSPN ${a.plano.ano}`}</div></div>
        <div className="panel kpi"><div className="rotulo">Vencimento inicial do município</div>
          <div className="valor" style={{ fontSize: 22 }}>{real(a.plano.vencimentoBase)}</div>
          <div className="det">{a.plano.lei || 'classe 1-2 do primeiro nível'}</div></div>
        <div className={`panel kpi ${Number(a.indiceDefasagem) > 0 ? 'perigo' : ''}`}><div className="rotulo">Defasagem em relação ao piso</div>
          <div className="valor" style={{ fontSize: 22 }}>{semPiso ? '—' : pct(a.indiceDefasagem)}</div>
          <div className="det">{semPiso ? '' : Number(a.indiceDefasagem) > 0 ? 'quanto a tabela precisa subir para cumprir o piso' : 'a tabela cumpre o piso'}</div></div>
        <div className={`panel kpi ${a.celulasAbaixoDoPiso > 0 ? 'perigo' : ''}`}><div className="rotulo">Vencimentos abaixo do piso</div>
          <div className="valor" style={{ fontSize: 22 }}>{semPiso ? '—' : a.celulasAbaixoDoPiso}</div>
          <div className="det">de {a.classes.length * a.niveis.length} combinações de nível e classe</div></div>
      </div>
      <TabelaSalarial titulo="Tabela de vencimentos atual" classes={a.classes} niveis={a.niveis} campo="atual"
        nota={semPiso ? null : 'Em destaque, os vencimentos abaixo do piso nacional.'} />
      {!semPiso && (
        <>
          <TabelaSalarial titulo="Tabela em conformidade com o piso" classes={a.classes} niveis={a.niveis} campo="conformidade"
            nota="Mesma estrutura do plano de carreira, partindo do piso nacional." />
          <TabelaSalarial titulo="Defasagem salarial mensal" classes={a.classes} niveis={a.niveis} campo="defasagem"
            nota="Diferença entre a tabela atual e a tabela em conformidade." />
        </>
      )}
    </>
  )
}

function TabelaSalarial({ titulo, classes, niveis, campo, nota }) {
  return (
    <div className="panel">
      <div className="panel-head"><h2>{titulo}</h2>{nota && <p className="hint">{nota}</p>}</div>
      <div className="tabela-wrap fixa">
        <table className="tabela-salarial">
          <thead><tr><th>Nível \ Classe</th>{classes.map((c) => <th key={c} className="n">{c}</th>)}</tr></thead>
          <tbody>
            {niveis.map((n) => (
              <tr key={n.nome}>
                <td><b>{n.nome}</b>{Number(n.percSobreBase) > 0 && <span className="hint"> +{pct(n.percSobreBase, 1)}</span>}</td>
                {n[campo].map((v, i) => (
                  <td key={i} className={`n ${campo === 'atual' && n.abaixoDoPiso[i] ? 'abaixo-piso' : ''} ${campo === 'defasagem' && Number(v) < 0 ? 'negativo' : ''}`}>{real(v)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
