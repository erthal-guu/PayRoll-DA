// Chamadas reais à API Spring Boot.
const BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

function query(params) {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, v)
  })
  const s = q.toString()
  return s ? `?${s}` : ''
}

const CHAVE_TOKEN = 'payroll.token'

function lerToken() {
  try { return localStorage.getItem(CHAVE_TOKEN) } catch { return null }
}

function gravarToken(token) {
  try { token ? localStorage.setItem(CHAVE_TOKEN, token) : localStorage.removeItem(CHAVE_TOKEN) } catch { /* sem armazenamento */ }
}

/** Adiciona o token de login aos cabeçalhos. */
function comToken(opcoes = {}) {
  const token = lerToken()
  return token ? { ...opcoes, headers: { ...opcoes.headers, Authorization: `Bearer ${token}` } } : opcoes
}

async function req(caminho, opcoes = {}, limiteMs = 0) {
  let resp
  let estourou = false
  const relogio = limiteMs ? new AbortController() : null
  const timer = relogio && setTimeout(() => { estourou = true; relogio.abort() }, limiteMs)
  try {
    resp = await fetch(BASE + caminho, comToken(relogio ? { ...opcoes, signal: relogio.signal } : opcoes))
  } catch {
    if (estourou) throw new Error(`Passaram ${Math.round(limiteMs / 1000)} segundos sem resposta do backend. Veja a última linha "SIMAM:" no console do backend.`)
    throw new Error(`Não foi possível conectar à API em ${BASE}. Confira se o backend está rodando.`)
  } finally {
    if (timer) clearTimeout(timer)
  }
  if (resp.status === 401 && caminho !== '/api/auth/login') {
    gravarToken(null)
    window.dispatchEvent(new Event('payroll:sessao-expirada'))
  }
  if (resp.status === 204) return null
  const corpo = await resp.json().catch(() => null)
  if (!resp.ok) throw new Error(corpo?.mensagem || `A API respondeu ${resp.status}.`)
  return corpo
}

const filtros = (f) => ({ ano: f.ano, mes: f.mes, poder: f.poder, codIbge: f.codIbge, faixa: f.faixa, busca: f.busca })

const json = (corpo) => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) })

export const api = {
  temToken: () => Boolean(lerToken()),
  async entrar(email, senha) {
    const r = await req('/api/auth/login', json({ email, senha }))
    gravarToken(r.token)
    return r.usuario
  },
  eu: () => req('/api/auth/eu'),
  async sair() {
    await req('/api/auth/logout', { method: 'POST' }).catch(() => null)
    gravarToken(null)
  },
  trocarSenha: (senha) => req('/api/auth/senha', json({ senha })),
  usuarios: () => req('/api/usuarios'),
  criarUsuario: (u) => req('/api/usuarios', json(u)),
  removerUsuario: (id) => req(`/api/usuarios/${id}`, { method: 'DELETE' }),
  periodos: () => req('/api/dashboard/periodos'),
  municipios: () => req('/api/dashboard/municipios'),
  // o resumo mostra a distribuição por faixa, por isso ignora o filtro de faixa
  resumo: (f) => req('/api/dashboard/resumo' + query({ ...filtros(f), faixa: undefined })),
  entidades: (f, opc = {}) => req('/api/dashboard/entidades' + query({ ...filtros(f), ...opc })),
  evolucaoEntidade: (id) => req(`/api/dashboard/entidades/${id}/evolucao`),
  evolucaoFaixas: (f) => req('/api/dashboard/evolucao-faixas' + query({ poder: f.poder, codIbge: f.codIbge, busca: f.busca })),
  importacoes: () => req('/api/importacoes'),
  importacao: (id) => req(`/api/importacoes/${id}`),
  errosImportacao: (id) => req(`/api/importacoes/${id}/erros`),
  uploadCsv: (arquivo) => {
    const dados = new FormData()
    dados.append('arquivo', arquivo)
    return req('/api/importacoes/upload', { method: 'POST', body: dados })
  },
  importarUrl: (url) => req('/api/importacoes/url', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url }),
  }),
  demonstrativo: (id, ano, mes) => req(`/api/dashboard/entidades/${id}/demonstrativo${query({ ano, mes })}`),
  entidadesDoMunicipio: (codIbge) => req(`/api/dashboard/municipios/${codIbge}/entidades`),
  consultar: (pedido) => req('/api/consultas', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(pedido),
  }),
  simamFiltros: (selecao) => req('/api/simam/filtros', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ selecao }),
  }, 170000),
  consultarSimam: (selecao) => req('/api/simam/consultar', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ selecao }),
  }, 170000),
  async simamDiagnostico() {
    const resp = await fetch(`${BASE}/api/simam/diagnostico`, comToken())
    if (!resp.ok) throw new Error('Não foi possível gerar o diagnóstico.')
    return { blob: await resp.blob(), nome: 'simam-diagnostico.zip' }
  },
  async excel({ entidadeId, ano, mes }) {
    const resp = await fetch(`${BASE}/api/consultas/excel${query({ entidadeId, ano, mes })}`, comToken())
    if (!resp.ok) throw new Error('Não foi possível gerar o Excel.')
    const nome = /filename\*?=(?:UTF-8'')?"?([^";]+)/i.exec(resp.headers.get('Content-Disposition') || '')?.[1]
    return { blob: await resp.blob(), nome: nome ? decodeURIComponent(nome) : `RGF_pessoal_${ano}-${mes}.xlsx` }
  },
  conversas: () => req('/api/chat/conversas'),
  conversa: (id) => req(`/api/chat/conversas/${id}`),
  removerConversa: (id) => req(`/api/chat/conversas/${id}`, { method: 'DELETE' }),
  /** O backend responde de uma vez (sem streaming); onText recebe a resposta completa. */
  async chat({ conversaId, mensagens, contexto, onText, signal }) {
    const r = await req('/api/chat', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ conversaId, mensagens, contexto }), signal,
    })
    onText?.(r.texto)
    return r
  },
}
