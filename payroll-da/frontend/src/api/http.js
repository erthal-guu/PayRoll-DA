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

async function req(caminho, opcoes) {
  let resp
  try {
    resp = await fetch(BASE + caminho, opcoes)
  } catch {
    throw new Error(`Não foi possível conectar à API em ${BASE}. Confira se o backend está rodando.`)
  }
  if (resp.status === 204) return null
  const corpo = await resp.json().catch(() => null)
  if (!resp.ok) throw new Error(corpo?.mensagem || `A API respondeu ${resp.status}.`)
  return corpo
}

const filtros = (f) => ({ ano: f.ano, mes: f.mes, poder: f.poder, codIbge: f.codIbge, faixa: f.faixa, busca: f.busca })

export const api = {
  modo: 'api',
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
  async excel({ entidadeId, ano, mes }) {
    const resp = await fetch(`${BASE}/api/consultas/excel${query({ entidadeId, ano, mes })}`)
    if (!resp.ok) throw new Error('Não foi possível gerar o Excel.')
    const nome = /filename\*?=(?:UTF-8'')?"?([^";]+)/i.exec(resp.headers.get('Content-Disposition') || '')?.[1]
    return { blob: await resp.blob(), nome: nome ? decodeURIComponent(nome) : `RGF_pessoal_${ano}-${mes}.xlsx` }
  },
  /** O backend responde de uma vez (sem streaming); onText recebe a resposta completa. */
  async chat({ mensagens, contexto, onText, signal }) {
    const r = await req('/api/chat', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mensagens, contexto }), signal,
    })
    onText?.(r.texto)
    return r
  },
}
