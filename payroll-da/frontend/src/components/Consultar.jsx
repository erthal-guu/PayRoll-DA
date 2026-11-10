import { useEffect, useState } from 'react'
import { api } from '../api/index.js'
import { salvarArquivo } from '../arquivos.js'
import { FAIXAS, MESES, moedaCheia, pct } from '../formato.js'

const ETAPAS = ['Abrindo o formulário do SIMAM (TCE-PR)', 'Aplicando os filtros e gerando o relatório', 'Importando para a base de dados']
const CLASSE = { tipo: '', municipio: 'largo', entidade: 'inteiro', relatorio: 'inteiro' }

const sem = (t) => t.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
// o sistema sabe ler o Demonstrativo da Despesa com Pessoal (RGF)
const relatorioAceito = (o) => sem(o.rotulo).includes('despesa com pessoal')
/**
 * Relatórios da análise financeira do município (os mesmos do relatório de análise do PSPN). RCL ajustada, despesa total
 * com pessoal e índice da LRF vêm do mesmo demonstrativo do RGF no SIMAM; o FUNDEB vem do RREO, que o sistema ainda
 * não lê (fica visível, mas desabilitado).
 */
const TIPOS = [
  { id: 'rcl', rotulo: 'RREO – Receita Corrente Líquida Ajustada', simam: relatorioAceito },
  { id: 'dtp', rotulo: 'RGF – Despesa Total com Pessoal', simam: relatorioAceito },
  { id: 'lrf', rotulo: 'LRF – Índice de Responsabilidade Fiscal', simam: relatorioAceito },
  // no SIMAM, as transferências do FUNDEB estão no demonstrativo de Manutenção e Desenvolvimento do Ensino (MDE)
  { id: 'fundeb', rotulo: 'RREO – Transferências do FUNDEB', simam: (o) => sem(o.rotulo).includes('desenvolvimento do ensino'), pendente: 'leitura em desenvolvimento' },
]
// Tipo fica fixo em Município, a entidade é a do próprio município (ex.: "MUNICÍPIO DE CANTAGALO") e o período não é
// escolhido: o backend importa todos os meses publicados no ano
const OCULTOS = ['tipo', 'entidade', 'periodo']
const entidadeDoMunicipio = (opcoes) => opcoes.find((o) => /^(municipio|prefeitura municipal) de /.test(sem(o.rotulo).trim()))

export default function Consultar({ aoConsultar, verNoDashboard, perguntar, irPara }) {
  const [selecao, setSelecao] = useState({})
  const [filtros, setFiltros] = useState([])
  const [carregandoFiltros, setCarregandoFiltros] = useState(true)
  const [baixarAuto, setBaixarAuto] = useState(false)
  const [etapa, setEtapa] = useState(-1)
  const [resultado, setResultado] = useState(null)
  const [erro, setErro] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [erroSimam, setErroSimam] = useState(null)
  const [tentativa, setTentativa] = useState(0)
  const [tipo, setTipo] = useState('')

  // cada escolha pode liberar o próximo filtro no SIMAM, então a lista é relida a cada mudança
  useEffect(() => {
    let atual = true
    setCarregandoFiltros(true)
    api.simamFiltros(selecao)
      .then((l) => { if (atual) { setFiltros(l); setErroSimam(null) } })
      .catch((e) => { if (atual) setErroSimam(e.message) })
      .finally(() => { if (atual) setCarregandoFiltros(false) })
    return () => { atual = false }
  }, [selecao, tentativa])

  // Tipo e Entidade não aparecem na tela: o sistema escolhe Município e a entidade do município selecionado
  useEffect(() => {
    const tipo = filtros.find((f) => f.chave === 'tipo')
    const municipio = tipo?.opcoes.find((o) => sem(o.rotulo).trim() === 'municipio')
    if (tipo?.habilitado && municipio && tipo.valor !== municipio.valor && selecao.tipo !== municipio.valor) {
      escolher('tipo', municipio.valor)
      return
    }
    const entidade = filtros.find((f) => f.chave === 'entidade')
    const doMunicipio = entidade?.habilitado && entidadeDoMunicipio(entidade.opcoes)
    if (doMunicipio && !entidade.valor && selecao.entidade !== doMunicipio.valor) {
      escolher('entidade', doMunicipio.valor)
      return
    }
    // trocou o município: reaplica o relatório já escolhido
    const relatorio = filtros.find((f) => f.chave === 'relatorio')
    if (tipo && relatorio?.habilitado && !relatorio.valor && !selecao.relatorio) escolherTipo(tipo)
  }, [filtros])

  /** O campo Relatório mostra os tipos da análise; cada um aponta para o relatório do SIMAM que traz o dado. */
  function escolherTipo(id) {
    setTipo(id)
    const t = TIPOS.find((x) => x.id === id)
    const opcao = t && filtros.find((f) => f.chave === 'relatorio')?.opcoes.find(t.simam)
    escolher('relatorio', opcao ? opcao.valor : '')
  }

  function escolher(chave, valor) {
    const ordem = filtros.map((f) => f.chave)
    const novo = {}
    // campos anteriores entram com o valor que está na tela, inclusive o que o SIMAM já traz escolhido (ex.: Tipo = Município)
    filtros.slice(0, ordem.indexOf(chave)).forEach((f) => { if (f.valor) novo[f.chave] = f.valor })
    if (valor) novo[chave] = valor
    setSelecao(novo)
    setResultado(null)
  }

  // só contam os campos que o SIMAM liberou e que têm opções; os outros não são exigidos
  const exigidos = filtros.filter((f) => f.habilitado && f.opcoes.length > 0 && f.chave !== 'periodo')
  // campo liberado mas vazio, logo depois de campos já preenchidos: o SIMAM não tem nada para essas escolhas
  const semOpcoes = filtros.filter((f, i) => f.habilitado && f.opcoes.length === 0
    && filtros.slice(0, i).every((a) => !a.habilitado || !a.opcoes.length || a.valor))
  const temTipos = filtros.some((f) => f.chave === 'relatorio' && f.opcoes.some(relatorioAceito))
  const completo = exigidos.length > 0 && exigidos.every((f) => f.valor) && semOpcoes.length === 0 && (!temTipos || Boolean(tipo))
  const rotulo = (chave) => filtros.find((f) => f.chave === chave)?.opcoes.find((o) => o.valor === selecao[chave])?.rotulo

  async function baixarExcel(r = resultado) {
    setAviso(null)
    try {
      const { blob, nome } = await api.excel({ entidadeId: r.demonstrativo.entidadeId, ano: r.demonstrativo.ano, mes: r.demonstrativo.mes })
      const st = await salvarArquivo(blob, nome)
      setAviso(st === 'salvo' ? `Planilha ${nome} gerada.` : 'Download cancelado.')
    } catch (e) {
      setAviso(e.message)
    }
  }

  async function baixarDiagnostico() {
    try {
      const { blob, nome } = await api.simamDiagnostico()
      await salvarArquivo(blob, nome)
    } catch (e) { setAviso(e.message) }
  }

  async function consultar(ev) {
    ev?.preventDefault()
    setErro(null); setAviso(null); setResultado(null)
    try {
      setEtapa(0)
      const consulta = api.consultarSimam(selecao)
      setTimeout(() => setEtapa((e) => (e === 0 ? 1 : e)), 400)
      setTimeout(() => setEtapa((e) => (e === 1 ? 2 : e)), 1500)
      const r = await consulta
      setResultado(r)
      aoConsultar()
      if (r.demonstrativo && baixarAuto) await baixarExcel(r)
    } catch (e) {
      setErro(e.message)
    } finally {
      setEtapa(-1)
    }
  }

  const d = resultado?.demonstrativo
  const linhas = d ? [
    ['Despesa bruta com pessoal (I)', moedaCheia(d.despesaBruta), true],
    ['Pessoal ativo', moedaCheia(d.pessoalAtivo)],
    ['Pessoal inativo e pensionistas', moedaCheia(d.inativosPensionistas)],
    ['Outras despesas de pessoal (terceirização)', moedaCheia(d.outrasDespesas)],
    ['Despesas não computadas (II)', moedaCheia(d.naoComputadas), true],
    ['Despesa total com pessoal (III = I − II)', moedaCheia(d.despesaTotal), true],
    ['Receita corrente líquida ajustada (IV)', moedaCheia(d.rclAjustada), true],
    ['Despesa total sobre a RCL (III / IV)', pct(d.percentual), true],
    ['Limite máximo', pct(d.limiteMaximo)],
    ['Limite prudencial (95%)', pct(d.limiteMaximo * 0.95)],
    ['Limite de alerta (90%)', pct(d.limiteMaximo * 0.9)],
    ['Margem até o limite máximo', moedaCheia((d.rclAjustada * d.limiteMaximo) / 100 - d.despesaTotal), true],
  ] : []

  return (
    <>
      <div>
        <h1>Analisar município</h1>
        <div className="sub">Escolha os filtros do SIMAM: o sistema busca o relatório no TCE-PR e grava na base, sem você baixar nada.</div>
      </div>
      {erro && <div className="erro"><b>A consulta falhou.</b> {erro}</div>}

      {erroSimam && !filtros.length ? (
        <div className="panel sem-simam">
          <div className="panel-head"><h2>Não consegui abrir o SIMAM do TCE-PR</h2></div>
          <p className="erro">{erroSimam}</p>
          <p className="hint">A análise do município acessa <code>simam.tce.pr.gov.br</code> a partir do backend. Confira se o computador
            onde o backend roda tem internet, se o site do TCE-PR abre no navegador e se nenhum firewall ou proxy está bloqueando o Java.</p>
          <div className="acoes-consulta">
            <button className="btn" onClick={() => setTentativa((t) => t + 1)} disabled={carregandoFiltros}>
              {carregandoFiltros ? 'Tentando…' : 'Tentar de novo'}
            </button>
            {irPara && <button className="btn ghost" onClick={() => irPara('importacao')}>Importar o CSV manualmente</button>}
          </div>
        </div>
      ) : (
      <>
      {erroSimam && (
        <div className="erro">
          {erroSimam}{' '}
          <button className="link" onClick={() => setTentativa((t) => t + 1)} disabled={carregandoFiltros}>Tentar de novo</button>
        </div>
      )}
      <form className={`panel form-consulta ${carregandoFiltros ? 'carregando' : ''}`} onSubmit={consultar}>
        {carregandoFiltros && !filtros.length && [0, 1, 2, 3].map((i) => <div key={i} className="campo esqueleto" />)}
        {filtros.filter((f) => !OCULTOS.includes(f.chave)
          || (f.chave === 'entidade' && f.habilitado && f.opcoes.length > 0 && !entidadeDoMunicipio(f.opcoes))
          ).map((f) => (f.chave !== 'relatorio' || !f.opcoes.some(relatorioAceito) ? f : {
            ...f, valor: f.valor ? tipo : '',
            opcoes: TIPOS.map((t) => {
              const falta = t.pendente || (!f.opcoes.some(t.simam) && 'não disponível no SIMAM para esta entidade')
              return { valor: t.id, rotulo: falta ? `${t.rotulo} (${falta})` : t.rotulo, desabilitado: Boolean(falta) }
            }),
          })).map((f, i) => (
          <label key={f.chave} className={`campo ${CLASSE[f.chave] ?? ''}`} htmlFor={`c-${f.chave}`}>
            <span><b className="passo">{i + 1}</b>{f.rotulo}</span>
            <select id={`c-${f.chave}`} value={f.valor} disabled={!f.habilitado || etapa >= 0}
              onChange={(e) => (f.chave === 'relatorio' && f.opcoes.some((o) => o.valor === 'rcl') ? escolherTipo(e.target.value) : escolher(f.chave, e.target.value))}>
              <option value="">Selecione{f.chave === 'ano' ? ' um ano' : ''}…</option>
              {f.opcoes.map((o) => <option key={o.valor} value={o.valor} disabled={o.desabilitado}>{o.rotulo}</option>)}
            </select>
          </label>
        ))}
        {filtros.some((f) => f.chave === 'relatorio' && f.opcoes.some(relatorioAceito)) && (
          <div className="inteiro hint">RCL ajustada, despesa total com pessoal e índice da LRF vêm do mesmo demonstrativo do RGF no SIMAM; o sistema usa a entidade do próprio município. Todos os meses publicados no ano escolhido são importados, o que pode levar alguns minutos.</div>
        )}
        <div className="inteiro acoes-consulta">
          <button className="btn" type="submit" disabled={etapa >= 0 || !completo}>{etapa >= 0 ? 'Analisando…' : 'Analisar e importar'}</button>
          <label className="check" htmlFor="c-auto">
            <input id="c-auto" type="checkbox" checked={baixarAuto} onChange={(e) => setBaixarAuto(e.target.checked)} />
            Gerar também a planilha Excel
          </label>
          {!completo && !carregandoFiltros && !semOpcoes.length && <span className="hint">Preencha todos os filtros na ordem numerada.</span>}
        </div>
        {semOpcoes.length > 0 && !carregandoFiltros && (
          <div className="inteiro aviso-simam">
            O SIMAM não trouxe nenhuma opção para <b>{semOpcoes.map((f) => f.rotulo).join(', ')}</b> com as escolhas atuais.
            Tente outra entidade ou outro ano.
          </div>
        )}
        <div className="inteiro hint">
          Algo diferente do site do TCE-PR? <button type="button" className="link" onClick={baixarDiagnostico}>Baixar o diagnóstico do SIMAM</button> e envie para o suporte.
        </div>
        {etapa >= 0 && (
          <ol className="etapas inteiro">
            {ETAPAS.map((t, i) => <li key={t} className={i < etapa ? 'feita' : i === etapa ? 'atual' : ''}>{t}</li>)}
          </ol>
        )}
      </form>
      </>
      )}

      {resultado && !d && (
        <div className="panel sem-simam consulta-falhou">
          <div className="panel-head"><h2>A consulta não trouxe o demonstrativo</h2></div>
          <p className="erro">{resultado.mensagem || 'O SIMAM não devolveu dados para esses filtros.'}</p>
          <p className="hint" style={{ margin: 0 }}>Confira se a entidade publicou o RGF desse período no SIMAM. Se o problema continuar, baixe o CSV no site do TCE-PR e importe manualmente.</p>
          <div className="acoes-consulta">
            <button className="btn" onClick={consultar}>Tentar de novo</button>
            {irPara && <button className="btn ghost" onClick={() => irPara('importacao')}>Importar o CSV manualmente</button>}
          </div>
        </div>
      )}

      {resultado && d && (
        <div className="panel">
          <div className="panel-head">
            <div>
              <h2>{d ? d.entidade : 'Sem resultado'}</h2>
              {tipo && <div className="hint"><b>{TIPOS.find((t) => t.id === tipo)?.rotulo}</b></div>}
              {d && <div className="hint">{d.municipio} · último período importado: 12 meses até {MESES[d.mes - 1]}/{d.ano} · Poder {d.poder === 'EXECUTIVO' ? 'Executivo' : 'Legislativo'}</div>}
            </div>
            {d && <span className="pill" style={{ '--c': FAIXAS[d.faixa].cor }}>{FAIXAS[d.faixa].nome}</span>}
          </div>
          {resultado.importacao && (
            <p className="hint" style={{ marginTop: 0 }}>Importação #{resultado.importacao.id}: {resultado.importacao.mensagem}</p>
          )}
          {resultado.mensagem && resultado.mensagem !== 'Consulta concluída.' && (
            <p className="hint" style={{ marginTop: 0 }}>{resultado.mensagem}</p>
          )}
          {!d ? <div className="vazio">{resultado.mensagem}</div> : (
            <>
              {d.rclAjustada === 0 && (
                <p className="hint" style={{ marginTop: 0 }}>O demonstrativo do SIMAM não traz a receita corrente líquida (RCL), então o percentual e a margem não puderam ser calculados. A despesa com pessoal foi importada normalmente.</p>
              )}
              <div className="destaques">
                {tipo === 'rcl' && <div className="destaque"><span>Receita corrente líquida ajustada</span><strong>{moedaCheia(d.rclAjustada)}</strong><small>últimos 12 meses</small></div>}
                {tipo === 'dtp' && <div className="destaque"><span>Despesa total com pessoal</span><strong>{moedaCheia(d.despesaTotal)}</strong><small>últimos 12 meses</small></div>}
                <div className="destaque"><span>{tipo === 'lrf' ? 'Índice da LRF (despesa ÷ RCL)' : 'Despesa sobre a RCL'}</span><strong>{pct(d.percentual)}</strong><small>limite máximo {pct(d.limiteMaximo)}</small></div>
                <div className="destaque"><span>Uso do limite</span><strong style={{ color: FAIXAS[d.faixa].cor }}>{pct((d.percentual / d.limiteMaximo) * 100, 1)}</strong><small>{FAIXAS[d.faixa].descricao}</small></div>
                {(!tipo || tipo === 'lrf') && <div className="destaque"><span>Margem até o limite</span><strong>{moedaCheia((d.rclAjustada * d.limiteMaximo) / 100 - d.despesaTotal)}</strong><small>sobre a RCL ajustada</small></div>}
              </div>
              <div className="medidor" aria-hidden="true">
                <span style={{ width: `${Math.min(100, (d.percentual / d.limiteMaximo) * 100)}%`, background: FAIXAS[d.faixa].cor }} />
                <i style={{ left: '90%' }} /><i style={{ left: '95%' }} />
              </div>
              <div className="medidor-leg"><span>0%</span><span>alerta 90%</span><span>prudencial 95%</span><span>limite 100%</span></div>
              <div className="tabela-wrap">
                <table className="demonstrativo">
                  <tbody>
                    {linhas.map(([rot, val, forte]) => (
                      <tr key={rot} className={forte ? 'forte' : ''}><td>{rot}</td><td className="n">{val}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="acoes-consulta" style={{ marginTop: 14 }}>
                <button className="btn" onClick={() => baixarExcel()}>Baixar Excel (.xlsx)</button>
                <button className="btn ghost" onClick={() => verNoDashboard(d)}>Ver no dashboard</button>
                <button className="btn ghost" onClick={() => perguntar(`Analise a situação da ${d.entidade} (id ${d.entidadeId}) e sugira o que fazer.`)}>Pedir análise ao assistente</button>
              </div>
              {aviso && <p className="hint">{aviso}</p>}
            </>
          )}
        </div>
      )}
    </>
  )
}
