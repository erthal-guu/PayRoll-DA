import { Fragment, useEffect, useRef, useState } from 'react'
import { api } from '../api/index.js'

const STATUS = {
  PROCESSANDO: ['Processando', 'var(--accent)'],
  CONCLUIDA: ['Concluída', 'var(--good)'],
  COM_ERROS: ['Concluída com erros', 'var(--warn)'],
  FALHOU: ['Falhou', 'var(--bad)'],
}

export default function Importacao({ aoImportar }) {
  const [lista, setLista] = useState([])
  const [arrastando, setArrastando] = useState(false)
  const [url, setUrl] = useState('')
  const [erro, setErro] = useState(null)
  const [aberta, setAberta] = useState(null)
  const [errosLinha, setErrosLinha] = useState([])
  const entrada = useRef(null)
  const processando = lista.some((i) => i.status === 'PROCESSANDO')

  const atualizar = () => api.importacoes().then(setLista).catch((e) => setErro(e.message))
  useEffect(() => { atualizar() }, [])
  useEffect(() => {
    if (!processando) return
    const t = setInterval(async () => {
      const nova = await api.importacoes().catch(() => null)
      if (!nova) return
      setLista(nova)
      if (!nova.some((i) => i.status === 'PROCESSANDO')) aoImportar()
    }, 800)
    return () => clearInterval(t)
  }, [processando])

  async function enviar(arquivo) {
    if (!arquivo) return
    setErro(null)
    try { await api.uploadCsv(arquivo); atualizar() } catch (e) { setErro(e.message) }
  }

  async function baixar(ev) {
    ev.preventDefault()
    setErro(null)
    try { await api.importarUrl(url); atualizar() } catch (e) { setErro(e.message) }
  }

  async function verErros(imp) {
    if (aberta === imp.id) { setAberta(null); return }
    setAberta(imp.id)
    setErrosLinha(await api.errosImportacao(imp.id).catch(() => []))
  }

  return (
    <>
      <div>
        <h1>Importar CSV</h1>
        <div className="sub">Importação manual: você baixa o CSV no SIMAM e envia para a base. Para o sistema buscar sozinho, use a <b>Consulta automática</b>.</div>
      </div>
      {erro && <div className="erro">{erro}</div>}
      <div className="grade">
        <div className="panel">
          <div className="panel-head"><h2>Enviar arquivo CSV</h2></div>
          <div className={`drop ${arrastando ? 'ativo' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setArrastando(true) }}
            onDragLeave={() => setArrastando(false)}
            onDrop={(e) => { e.preventDefault(); setArrastando(false); enviar(e.dataTransfer.files[0]) }}>
            <p style={{ margin: '0 0 12px' }}>Arraste o CSV aqui ou</p>
            <button className="btn" onClick={() => entrada.current.click()}>Escolher arquivo</button>
            <input ref={entrada} id="arquivo-csv" type="file" accept=".csv,text/csv" hidden onChange={(e) => { enviar(e.target.files[0]); e.target.value = '' }} />
          </div>
          <ol className="passos-ajuda">
            <li>No SIMAM, abra <i>Relatórios da LRF</i>, escolha os filtros e clique em <b>Consultar</b>.</li>
            <li>Na tela do relatório, use <b>Exportar → CSV (comma delimited)</b>.</li>
            <li>Envie o arquivo aqui. Também aceitamos o CSV consolidado (<code>municipio</code>, <code>cod_ibge</code>, <code>entidade</code>, <code>poder</code>, <code>ano</code>, <code>mes</code>, <code>despesa_total_pessoal</code>, <code>rcl_ajustada</code>, separador <code>;</code>).</li>
          </ol>
          <p className="hint" style={{ marginBottom: 0 }}>Reimportar o mesmo período atualiza os valores, sem duplicar.</p>
        </div>
        <div className="panel">
          <div className="panel-head"><h2>Baixar dos dados abertos do TCE-PR</h2></div>
          <form className="form-url" onSubmit={baixar}>
            <label htmlFor="url-tce" className="hint" style={{ flexBasis: '100%' }}>Endereço do arquivo (.csv ou .zip)</label>
            <input id="url-tce" type="url" placeholder="https://servicos.tce.pr.gov.br/..." value={url} onChange={(e) => setUrl(e.target.value)} />
            <button className="btn" type="submit">Importar</button>
          </form>
          <p className="hint" style={{ marginBottom: 0 }}>O backend só aceita endereços https dos domínios do TCE-PR. Se o arquivo vier em .zip, o primeiro CSV é extraído automaticamente.</p>
        </div>
      </div>
      <div className="panel">
        <div className="panel-head"><h2>Histórico</h2></div>
        {!lista.length ? <div className="vazio">Nenhuma importação ainda.</div> : (
          <div className="tabela-wrap">
            <table>
              <thead><tr><th>#</th><th>Origem</th><th>Arquivo</th><th>Status</th><th className="n">Importadas</th><th className="n">Com erro</th><th>Resultado</th></tr></thead>
              <tbody>
                {lista.map((i) => (
                  <Fragment key={i.id}>
                    <tr onClick={() => i.linhasComErro > 0 && verErros(i)} style={{ cursor: i.linhasComErro ? 'pointer' : 'default' }}>
                      <td>{i.id}</td>
                      <td>{{ UPLOAD: 'Manual', SIMAM: 'Automática' }[i.origem] ?? 'URL'}</td>
                      <td style={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' }} title={i.referencia}>{i.referencia}</td>
                      <td><span className="pill" style={{ '--c': STATUS[i.status][1] }}>{STATUS[i.status][0]}</span></td>
                      <td className="n">{i.linhasImportadas.toLocaleString('pt-BR')}</td>
                      <td className="n">{i.linhasComErro > 0 ? <button className="link">{i.linhasComErro} ver</button> : 0}</td>
                      <td style={{ whiteSpace: 'normal', minWidth: 220 }}>{i.mensagem ?? 'Lendo o arquivo…'}</td>
                    </tr>
                    {aberta === i.id && (
                      <tr><td colSpan={7} style={{ whiteSpace: 'normal', background: 'var(--bg)' }}>
                        {errosLinha.slice(0, 50).map((e) => <div key={e.linha} className="hint">Linha {e.linha}: {e.mensagem}</div>)}
                      </td></tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
