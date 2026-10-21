// Implementação em memória da mesma API do backend (mesmos parâmetros e formatos de resposta).
import { gerarLinhas, LIMITES } from '../mock/dados.js'

const espera = (ms = 180) => new Promise((r) => setTimeout(r, ms))
const semAcento = (s) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

let proximoId = 1
const entidades = new Map() // chave codIbge|nome -> entidade
let registros = [] // despesa_pessoal
const importacoes = []

function idEntidade(l) {
  const chave = `${l.codIbge}|${l.entidade}`
  if (!entidades.has(chave)) {
    entidades.set(chave, { id: entidades.size + 1, codIbge: l.codIbge, municipio: l.municipio, nome: l.entidade, poder: l.poder })
  }
  return entidades.get(chave)
}

function gravar(linhas, importacaoId) {
  const indice = new Map(registros.map((r, i) => [`${r.entidade.id}|${r.ano}|${r.mes}`, i]))
  for (const l of linhas) {
    const entidade = idEntidade(l)
    const percentual = l.rclAjustada ? (l.despesaTotal * 100) / l.rclAjustada : 0
    const reg = { ...l, entidade, percentual, limiteMaximo: LIMITES[l.poder], importacaoId }
    const chave = `${entidade.id}|${l.ano}|${l.mes}`
    if (indice.has(chave)) registros[indice.get(chave)] = reg
    else { indice.set(chave, registros.length); registros.push(reg) }
  }
}

gravar(gerarLinhas(), null)

export function faixaDe(percentual, limite) {
  if (percentual >= limite) return 'ACIMA_LIMITE'
  if (percentual >= limite * 0.95) return 'PRUDENCIAL'
  if (percentual >= limite * 0.9) return 'ALERTA'
  return 'REGULAR'
}

const faixasVazias = () => ({ REGULAR: 0, ALERTA: 0, PRUDENCIAL: 0, ACIMA_LIMITE: 0 })

function listarPeriodos() {
  const vistos = new Set()
  return registros
    .filter((r) => !vistos.has(r.ano * 100 + r.mes) && vistos.add(r.ano * 100 + r.mes))
    .map((r) => ({ ano: r.ano, mes: r.mes }))
    .sort((a, b) => b.ano - a.ano || b.mes - a.mes)
}

function filtrar(f, { periodo = true, faixa = true } = {}) {
  let ano = f.ano, mes = f.mes
  if (periodo && (!ano || !mes)) {
    const ult = listarPeriodos()[0]
    if (!ult) return { lista: [], ano, mes }
    ano = ano || ult.ano
    mes = mes || ult.mes
  }
  const busca = f.busca ? semAcento(f.busca) : null
  const lista = registros.filter((r) =>
    (!periodo || (r.ano === +ano && r.mes === +mes)) &&
    (!f.poder || r.entidade.poder === f.poder) &&
    (!f.codIbge || r.entidade.codIbge === f.codIbge) &&
    (!faixa || !f.faixa || faixaDe(r.percentual, r.limiteMaximo) === f.faixa) &&
    (!busca || semAcento(r.entidade.nome).includes(busca) || semAcento(r.entidade.municipio).includes(busca)))
  return { lista, ano: +ano, mes: +mes }
}

const soma = (lista, campo) => lista.reduce((t, r) => t + r[campo], 0)

export const api = {
  modo: 'mock',

  async periodos() { await espera(); return listarPeriodos() },

  async municipios() {
    await espera()
    const m = new Map()
    entidades.forEach((e) => m.set(e.codIbge, e.municipio))
    return [...m].map(([codIbge, municipio]) => ({ codIbge, municipio })).sort((a, b) => a.municipio.localeCompare(b.municipio))
  },

  async resumo(f) {
    await espera()
    const { lista, ano, mes } = filtrar(f, { faixa: false })
    const faixas = faixasVazias()
    lista.forEach((r) => faixas[faixaDe(r.percentual, r.limiteMaximo)]++)
    return {
      periodo: { ano, mes },
      entidades: lista.length,
      despesaTotal: soma(lista, 'despesaTotal'),
      rclTotal: soma(lista, 'rclAjustada'),
      usoMedioLimite: lista.length ? lista.reduce((t, r) => t + (r.percentual * 100) / r.limiteMaximo, 0) / lista.length : 0,
      faixas,
      composicao: {
        pessoalAtivo: soma(lista, 'pessoalAtivo'), inativosPensionistas: soma(lista, 'inativosPensionistas'),
        outrasDespesas: soma(lista, 'outrasDespesas'), naoComputadas: soma(lista, 'naoComputadas'),
      },
    }
  },

  async entidades(f, { ordem = 'usoLimite', direcao = 'desc', pagina = 0, tamanho = 20 } = {}) {
    await espera()
    const { lista } = filtrar(f)
    const usoLimite = (r) => r.percentual / r.limiteMaximo
    const chave = {
      usoLimite, percentual: (r) => r.percentual, despesaTotal: (r) => r.despesaTotal,
      entidade: (r) => r.entidade.nome, municipio: (r) => r.entidade.municipio,
    }[ordem] ?? usoLimite
    const sinal = direcao === 'asc' ? 1 : -1
    const ordenada = [...lista].sort((a, b) => {
      const va = chave(a), vb = chave(b)
      return (typeof va === 'string' ? va.localeCompare(vb) : va - vb) * sinal || a.entidade.nome.localeCompare(b.entidade.nome)
    })
    return {
      itens: ordenada.slice(pagina * tamanho, (pagina + 1) * tamanho).map((r) => ({
        entidadeId: r.entidade.id, codIbge: r.entidade.codIbge, municipio: r.entidade.municipio, entidade: r.entidade.nome,
        poder: r.entidade.poder, despesaTotal: r.despesaTotal, rclAjustada: r.rclAjustada, percentual: r.percentual,
        limiteMaximo: r.limiteMaximo, faixa: faixaDe(r.percentual, r.limiteMaximo),
      })),
      total: lista.length, pagina, tamanho,
    }
  },

  async evolucaoEntidade(id) {
    await espera()
    return registros.filter((r) => r.entidade.id === id)
      .sort((a, b) => a.ano - b.ano || a.mes - b.mes)
      .map((r) => ({ ano: r.ano, mes: r.mes, despesaTotal: r.despesaTotal, rclAjustada: r.rclAjustada, percentual: r.percentual, limiteMaximo: r.limiteMaximo }))
  },

  async evolucaoFaixas(f) {
    await espera()
    const { lista } = filtrar(f, { periodo: false, faixa: false })
    const porPeriodo = new Map()
    for (const r of lista) {
      const k = r.ano * 100 + r.mes
      if (!porPeriodo.has(k)) porPeriodo.set(k, { ano: r.ano, mes: r.mes, faixas: faixasVazias() })
      porPeriodo.get(k).faixas[faixaDe(r.percentual, r.limiteMaximo)]++
    }
    return [...porPeriodo.entries()].sort((a, b) => a[0] - b[0]).map(([, v]) => v)
  },

  async importacoes() { await espera(100); return [...importacoes].reverse() },

  async importacao(id) { await espera(100); return importacoes.find((i) => i.id === id) },

  /** No modo mock o CSV é lido de verdade no navegador, com as mesmas regras do backend. */
  async uploadCsv(arquivo) {
    const imp = novaImportacao('UPLOAD', arquivo.name)
    processarNoNavegador(imp, arquivo)
    return { ...imp }
  },

  async importarUrl(url) {
    await espera()
    if (!/^https:\/\/([a-z0-9-]+\.)*tce\.pr\.gov\.br\//i.test(url || '')) {
      throw new Error('Use um endereço https do TCE-PR (domínio tce.pr.gov.br).')
    }
    const imp = novaImportacao('URL', url)
    setTimeout(() => Object.assign(imp, {
      status: 'FALHOU', concluidaEm: new Date().toISOString(),
      mensagem: 'No modo de demonstração o download não é feito. Rode o backend para baixar do TCE-PR.',
    }), 900)
    return { ...imp }
  },

  async errosImportacao(id) { await espera(100); return importacoes.find((i) => i.id === id)?.erros ?? [] },

  async demonstrativo(id, ano, mes) {
    await espera(80)
    const d = demonstrativoDe(id, ano, mes)
    if (!d) throw new Error('Sem dados dessa entidade no período.')
    return d
  },

  async entidadesDoMunicipio(codIbge) {
    await espera(80)
    return [...entidades.values()].filter((e) => e.codIbge === codIbge)
      .map((e) => ({ id: e.id, nome: e.nome, poder: e.poder }))
      .sort((a, b) => a.poder.localeCompare(b.poder) || a.nome.localeCompare(b.nome))
  },

  /** Simula o botão Consultar: "baixa" o CSV do município, importa e devolve o demonstrativo. */
  async consultar({ codIbge, entidadeId, ano, mes }) {
    await espera(500)
    const doMunicipio = registros.filter((r) => r.entidade.codIbge === codIbge && r.ano === ano && r.mes === mes)
    const imp = novaImportacao('URL', `TCE-PR · SIM-AM · ${doMunicipio[0]?.entidade.municipio ?? codIbge} · ${String(mes).padStart(2, '0')}/${ano} (simulado)`)
    gravar(doMunicipio.map(({ entidade, percentual, limiteMaximo, importacaoId, ...linha }) => linha), imp.id)
    Object.assign(imp, {
      linhasLidas: doMunicipio.length, linhasImportadas: doMunicipio.length,
      status: doMunicipio.length ? 'CONCLUIDA' : 'FALHOU',
      mensagem: doMunicipio.length ? `${doMunicipio.length} registros importados.` : 'O TCE-PR não retornou dados para esse período.',
      concluidaEm: new Date().toISOString(),
    })
    const d = demonstrativoDe(entidadeId, ano, mes)
    return {
      importacao: { ...imp }, demonstrativo: d, excelUrl: null,
      mensagem: d ? 'Consulta concluída.' : 'Não há dados dessa entidade para o período escolhido.',
    }
  },

  /** Simula os filtros em cascata do formulário do SIMAM (cada escolha libera o próximo). */
  async simamFiltros(sel = {}) {
    await espera(150)
    const mun = await api.municipios()
    const ents = sel.municipio ? await api.entidadesDoMunicipio(sel.municipio) : []
    const per = listarPeriodos()
    const anos = [...new Set(per.map((p) => p.ano))]
    const meses = per.filter((p) => p.ano === +sel.ano).map((p) => p.mes).sort((a, b) => a - b)
    const nomesMes = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro']
    const f = (chave, rotulo, habilitado, opcoes) => ({ chave, id: `ddl${chave}`, rotulo, habilitado, valor: sel[chave] ?? '', opcoes })
    return [
      f('tipo', 'Tipo', true, [{ valor: '1', rotulo: 'Município' }, { valor: '2', rotulo: 'Consórcio Intermunicipal' }]),
      f('municipio', 'Município', sel.tipo === '1', mun.map((m) => ({ valor: m.codIbge, rotulo: m.municipio.toUpperCase() }))),
      f('entidade', 'Entidade', Boolean(sel.municipio), ents.map((e) => ({ valor: String(e.id), rotulo: e.nome.toUpperCase() }))),
      f('relatorio', 'Relatório', Boolean(sel.entidade), [{ valor: '4', rotulo: 'RGF - Demonstrativo da Despesa com Pessoal' }]),
      f('ano', 'Ano', Boolean(sel.relatorio), anos.map((a) => ({ valor: String(a), rotulo: String(a) }))),
      f('periodo', 'Período', Boolean(sel.ano), meses.map((m) => ({ valor: String(m), rotulo: nomesMes[m - 1] }))),
    ]
  },

  async consultarSimam(sel) {
    return api.consultar({ codIbge: sel.municipio, entidadeId: Number(sel.entidade), ano: Number(sel.ano), mes: Number(sel.periodo) })
  },

  async excel({ entidadeId, ano, mes }) {
    const d = demonstrativoDe(entidadeId, ano, mes)
    if (!d) throw new Error('Sem dados para esse período.')
    const { gerarExcel, nomeExcel } = await import('./excelNavegador.js')
    return { blob: gerarExcel(d, await api.evolucaoEntidade(entidadeId)), nome: nomeExcel(d) }
  },

  async chat(pedido) {
    const { conversarDemo } = await import('./assistenteDemo.js')
    return conversarDemo(api, pedido)
  },
}

function demonstrativoDe(entidadeId, ano, mes) {
  const r = registros.find((x) => x.entidade.id === entidadeId && x.ano === ano && x.mes === mes)
  if (!r) return null
  return {
    entidadeId, codIbge: r.entidade.codIbge, municipio: r.entidade.municipio, entidade: r.entidade.nome, poder: r.entidade.poder,
    ano, mes, despesaBruta: r.despesaBruta, pessoalAtivo: r.pessoalAtivo, inativosPensionistas: r.inativosPensionistas,
    outrasDespesas: r.outrasDespesas, naoComputadas: r.naoComputadas, despesaTotal: r.despesaTotal, rclAjustada: r.rclAjustada,
    percentual: r.percentual, limiteMaximo: r.limiteMaximo, faixa: faixaDe(r.percentual, r.limiteMaximo),
  }
}

function novaImportacao(origem, referencia) {
  const imp = {
    id: proximoId++, origem, referencia, status: 'PROCESSANDO', linhasLidas: 0, linhasImportadas: 0, linhasComErro: 0,
    mensagem: null, iniciadaEm: new Date().toISOString(), concluidaEm: null, erros: [],
  }
  importacoes.push(imp)
  return imp
}

const CAMPOS = {
  municipio: 'municipio', codibge: 'codIbge', entidade: 'entidade', poder: 'poder', ano: 'ano', mes: 'mes',
  despesabruta: 'despesaBruta', pessoalativo: 'pessoalAtivo', inativospensionistas: 'inativosPensionistas',
  outrasdespesaspessoal: 'outrasDespesas', despesasnaocomputadas: 'naoComputadas', despesatotalpessoal: 'despesaTotal',
  rclajustada: 'rclAjustada',
}
const OBRIGATORIOS = ['municipio', 'codIbge', 'entidade', 'poder', 'ano', 'mes', 'despesaTotal', 'rclAjustada']

function lerCsv(texto) {
  const linhas = []
  let campo = '', linha = [], aspas = false
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i]
    if (aspas) {
      if (c === '"' && texto[i + 1] === '"') { campo += '"'; i++ } else if (c === '"') aspas = false
      else campo += c
    } else if (c === '"') aspas = true
    else if (c === ';') { linha.push(campo); campo = '' }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && texto[i + 1] === '\n') i++
      linha.push(campo); campo = ''
      if (linha.some((v) => v.trim())) linhas.push(linha)
      linha = []
    } else campo += c
  }
  if (campo || linha.length) { linha.push(campo); if (linha.some((v) => v.trim())) linhas.push(linha) }
  return linhas
}

function numeroBr(v) {
  let s = String(v).replace('R$', '').replace('%', '').replace(/\s/g, '')
  const negativo = s.startsWith('(') && s.endsWith(')')
  s = s.replace(/[()]/g, '')
  if (s.includes(',')) s = s.replace(/\./g, '').replace(',', '.')
  const n = Number(s)
  if (s === '' || Number.isNaN(n)) throw new Error(`valor numérico inválido: "${v}"`)
  return negativo ? -n : n
}

async function processarNoNavegador(imp, arquivo) {
  try {
    const buffer = await arquivo.arrayBuffer()
    let texto
    try {
      texto = new TextDecoder('utf-8', { fatal: true }).decode(buffer)
    } catch {
      texto = new TextDecoder('iso-8859-1').decode(buffer) // padrão dos arquivos do TCE-PR
    }
    const [cabecalho, ...dados] = lerCsv(texto.replace(/^﻿/, ''))
    if (!cabecalho) throw new Error('O arquivo está vazio.')
    const idx = {}
    cabecalho.forEach((h, i) => {
      const campo = CAMPOS[semAcento(h).replace(/[\s_-]/g, '')]
      if (campo) idx[campo] = i
    })
    const faltando = OBRIGATORIOS.filter((c) => idx[c] === undefined)
    if (faltando.length) throw new Error(`O arquivo não tem as colunas obrigatórias: ${faltando.join(', ')}.`)

    const validas = []
    dados.forEach((cols, n) => {
      imp.linhasLidas++
      try {
        const v = (c) => (idx[c] === undefined ? '' : (cols[idx[c]] ?? '').trim())
        const codIbge = v('codIbge').replace(/\D/g, '')
        if (codIbge.length !== 7) throw new Error(`Código IBGE inválido: "${v('codIbge')}" (esperado 7 dígitos)`)
        const p = semAcento(v('poder'))
        const poder = p.startsWith('exec') || p.includes('prefeitura') ? 'EXECUTIVO'
          : p.startsWith('legis') || p.includes('camara') ? 'LEGISLATIVO' : null
        if (!poder) throw new Error(`Poder não reconhecido: "${v('poder')}"`)
        const mes = parseInt(v('mes'), 10)
        if (!(mes >= 1 && mes <= 12)) throw new Error(`Mês fora do intervalo 1 a 12: ${v('mes')}`)
        const total = numeroBr(v('despesaTotal'))
        const opc = (c, padrao) => (v(c) ? numeroBr(v(c)) : padrao)
        validas.push({
          codIbge, municipio: v('municipio'), entidade: v('entidade'), poder, ano: parseInt(v('ano'), 10), mes,
          despesaBruta: opc('despesaBruta', total), pessoalAtivo: opc('pessoalAtivo', 0),
          inativosPensionistas: opc('inativosPensionistas', 0), outrasDespesas: opc('outrasDespesas', 0),
          naoComputadas: opc('naoComputadas', 0), despesaTotal: total, rclAjustada: numeroBr(v('rclAjustada')),
        })
      } catch (e) {
        imp.linhasComErro++
        if (imp.erros.length < 200) imp.erros.push({ linha: n + 2, mensagem: e.message })
      }
    })
    await espera(600)
    gravar(validas, imp.id)
    Object.assign(imp, {
      linhasImportadas: validas.length,
      status: imp.linhasComErro ? 'COM_ERROS' : 'CONCLUIDA',
      mensagem: `${validas.length} registros importados${imp.linhasComErro ? `, ${imp.linhasComErro} linhas com erro` : ''}.`,
      concluidaEm: new Date().toISOString(),
    })
  } catch (e) {
    Object.assign(imp, { status: 'FALHOU', mensagem: e.message, concluidaEm: new Date().toISOString() })
  }
}
