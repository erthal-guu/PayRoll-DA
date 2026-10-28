import { Fragment, useEffect, useRef, useState } from 'react'
import { api } from '../api/index.js'

const SUGESTOES_INICIAIS = [
  'Resuma a situação do período atual',
  'Quais câmaras passaram do limite prudencial?',
  'Quais prefeituras estão mais perto do limite e qual a margem delas?',
  'O que uma prefeitura acima do limite precisa fazer?',
]

/** Markdown mínimo: parágrafos, listas com "-" ou "1." e **negrito**. */
function Texto({ conteudo }) {
  const negrito = (t) => t.split(/(\*\*[^*]+\*\*)/g).map((p, i) =>
    p.startsWith('**') && p.endsWith('**') ? <strong key={i}>{p.slice(2, -2)}</strong> : <Fragment key={i}>{p}</Fragment>)
  const blocos = conteudo.replace(/^#+\s*/gm, '').split(/\n{2,}/)
  return blocos.map((b, i) => {
    const linhas = b.split('\n').filter(Boolean)
    if (linhas.length && linhas.every((l) => /^\s*([-*•]|\d+[.)])\s+/.test(l))) {
      return <ul key={i}>{linhas.map((l, j) => <li key={j}>{negrito(l.replace(/^\s*([-*•]|\d+[.)])\s+/, ''))}</li>)}</ul>
    }
    return <p key={i}>{linhas.map((l, j) => <Fragment key={j}>{j > 0 && <br />}{negrito(l)}</Fragment>)}</p>
  })
}

export default function Assistente({ contexto, aplicar, pedido }) {
  const [aberto, setAberto] = useState(false)
  const [mensagens, setMensagens] = useState([])
  const [entrada, setEntrada] = useState('')
  const [pensando, setPensando] = useState(false)
  const [erro, setErro] = useState(null)
  const [conversaId, setConversaId] = useState(null)
  const [historico, setHistorico] = useState(null) // lista de conversas salvas quando o painel de histórico está aberto
  const controle = useRef(null)
  const fim = useRef(null)

  useEffect(() => { fim.current?.scrollIntoView({ block: 'end' }) }, [mensagens, pensando])
  useEffect(() => {
    if (pedido?.texto) { setAberto(true); enviar(pedido.texto) }
  }, [pedido])

  async function enviar(texto) {
    const t = texto.trim()
    if (!t || pensando) return
    setErro(null)
    setEntrada('')
    const historico = [...mensagens, { papel: 'user', texto: t }]
    setMensagens([...historico, { papel: 'assistant', texto: '', sugestoes: [] }])
    setPensando(true)
    controle.current = new AbortController()
    const atualizarUltima = (campos) => setMensagens((ms) => ms.map((m, i) => (i === ms.length - 1 ? { ...m, ...campos } : m)))
    try {
      const r = await api.chat({
        conversaId,
        mensagens: historico.map(({ papel, texto: tx }) => ({ papel, texto: tx })).filter((m) => m.texto),
        contexto, signal: controle.current.signal,
        onText: (parcial) => atualizarUltima({ texto: parcial }),
      })
      if (r.cancelado && !r.texto) setMensagens((ms) => ms.slice(0, -1))
      else {
        atualizarUltima({ texto: r.texto || 'Sem resposta.', sugestoes: r.sugestoes || [] })
        if (r.conversaId) setConversaId(r.conversaId)
      }
    } catch (e) {
      setMensagens((ms) => ms.slice(0, -1))
      setErro(e.message)
    } finally {
      setPensando(false)
    }
  }

  function novaConversa() {
    setMensagens([]); setErro(null); setConversaId(null); setHistorico(null)
  }

  async function abrirHistorico() {
    setErro(null)
    if (historico) { setHistorico(null); return }
    try { setHistorico(await api.conversas()) } catch (e) { setErro(e.message) }
  }

  async function abrirConversa(c) {
    setErro(null)
    try {
      const ms = await api.conversa(c.id)
      setMensagens(ms.map((m) => ({ papel: m.papel, texto: m.texto, sugestoes: m.sugestoes || [] })))
      setConversaId(c.id)
      setHistorico(null)
    } catch (e) { setErro(e.message) }
  }

  async function apagarConversa(c) {
    if (!window.confirm(`Apagar a conversa "${c.titulo}"?`)) return
    try {
      await api.removerConversa(c.id)
      setHistorico((h) => h.filter((x) => x.id !== c.id))
      if (c.id === conversaId) novaConversa()
    } catch (e) { setErro(e.message) }
  }

  if (!aberto) {
    return <button className="fab" onClick={() => setAberto(true)} aria-label="Abrir assistente">Assistente IA</button>
  }

  return (
    <aside className="assistente" aria-label="Assistente">
      <header>
        <div><strong>Assistente IA</strong><small>Análise da despesa e sugestões para o painel</small></div>
        <div className="assistente-acoes">
          <button className="link" onClick={abrirHistorico} disabled={pensando} aria-pressed={Boolean(historico)}>Histórico</button>
          {(mensagens.length > 0 || historico) && <button className="link" onClick={novaConversa} disabled={pensando}>Nova conversa</button>}
          <button className="fechar" onClick={() => setAberto(false)} aria-label="Fechar assistente">×</button>
        </div>
      </header>
      <div className="conversa">
        {historico && (
          <div className="historico">
            <strong>Conversas salvas</strong>
            {!historico.length && <p className="hint">Nenhuma conversa ainda. As novas ficam salvas aqui automaticamente.</p>}
            {historico.map((c) => (
              <div key={c.id} className={`historico-item ${c.id === conversaId ? 'atual' : ''}`}>
                <button className="historico-abrir" onClick={() => abrirConversa(c)}>
                  <span>{c.titulo}</span>
                  <small>{new Date(c.atualizadaEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })} · {c.mensagens} mensagens</small>
                </button>
                <button className="historico-apagar" onClick={() => apagarConversa(c)} aria-label={`Apagar ${c.titulo}`} title="Apagar">×</button>
              </div>
            ))}
          </div>
        )}
        {!historico && !mensagens.length && (
          <div className="boas-vindas">
            <p>Pergunte sobre os dados do painel ou peça uma análise. Eu consulto os números antes de responder e sugiro filtros que você aplica com um clique.</p>
            {SUGESTOES_INICIAIS.map((s) => <button key={s} className="sugestao-inicial" onClick={() => enviar(s)}>{s}</button>)}
          </div>
        )}
        {!historico && mensagens.map((m, i) => (
          <div key={i} className={`msg ${m.papel}`}>
            {m.papel === 'user' ? m.texto
              : m.texto ? <Texto conteudo={m.texto} />
              : <span className="hint">Consultando os dados…</span>}
            {m.sugestoes?.length > 0 && (
              <div className="sugestoes">
                {m.sugestoes.map((s, j) => (
                  <button key={j} className="btn ghost" title={s.motivo} onClick={() => aplicar(s)}>{s.rotulo}</button>
                ))}
              </div>
            )}
          </div>
        ))}
        {erro && <div className="erro">{erro}</div>}
        <div ref={fim} />
      </div>
      <form className="entrada" onSubmit={(e) => { e.preventDefault(); enviar(entrada) }}>
        <label htmlFor="chat-entrada" className="sr">Mensagem</label>
        <textarea id="chat-entrada" rows={2} placeholder="Ex.: por que a Câmara de Maringá está em alerta?" value={entrada}
          onChange={(e) => setEntrada(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); enviar(entrada) } }} />
        {pensando
          ? <button type="button" className="btn ghost" onClick={() => controle.current?.abort()}>Parar</button>
          : <button type="submit" className="btn" disabled={!entrada.trim()}>Enviar</button>}
      </form>
    </aside>
  )
}
