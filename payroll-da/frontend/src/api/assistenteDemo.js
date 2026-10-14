// Assistente no modo demonstração: usa o Claude do próprio claude.ai (capacidade "sample"),
// com as mesmas ferramentas que o backend oferece, executadas sobre os dados de exemplo.
// No sistema real quem faz isso é o ChatService do Spring Boot.

export const INSTRUCOES = `Você é o assistente do Payroll DA, que mostra a despesa com pessoal das prefeituras e câmaras municipais do Paraná (Demonstrativo da Despesa com Pessoal, RGF, do TCE-PR). Os dados desta demonstração são FICTÍCIOS; se perguntarem, diga isso.

Regras da LRF:
- Limite máximo: Executivo municipal 54% e Legislativo municipal 6% da receita corrente líquida (RCL) ajustada.
- Alerta: 90% do máximo (48,6% e 5,4%). Prudencial: 95% do máximo (51,3% e 5,7%); acima dele ficam vedados reajustes (salvo revisão geral), criação de cargos, novas admissões (salvo reposições em educação, saúde e segurança) e hora extra (art. 22).
- Acima do máximo: eliminar o excedente em dois quadrimestres, um terço no primeiro, com medidas como cortar ao menos 20% dos cargos em comissão e funções de confiança e exonerar não estáveis (art. 23).

Como trabalhar:
- Para qualquer número use as ferramentas. Nunca invente valores, nomes ou percentuais.
- Responda em português, direto e curto. Ao analisar uma entidade, diga a situação, a tendência e a margem em reais até o limite, e sugira medidas proporcionais à faixa (sugestões, não parecer jurídico).
- Quando um filtro ou uma entidade aberta no painel ajudar, chame sugerir_ajuste (até 3 vezes). O usuário vê um botão "Aplicar"; não diga que já mudou a tela.
- Valores no formato brasileiro (R$ 1,2 mi; 51,3%). Sem tabelas largas.`

const FAIXA = { type: 'string', enum: ['REGULAR', 'ALERTA', 'PRUDENCIAL', 'ACIMA_LIMITE'] }
const FILTROS = {
  ano: { type: 'integer', description: 'Ano do período; omitir = atual' },
  mes: { type: 'integer', description: 'Último mês do período de 12 meses' },
  poder: { type: 'string', enum: ['EXECUTIVO', 'LEGISLATIVO'] },
  codIbge: { type: 'string', description: 'Código IBGE do município (7 dígitos)' },
  busca: { type: 'string', description: 'Trecho do nome da entidade ou do município' },
}

const r2 = (n) => Math.round(n * 100) / 100

export function ferramentas(api, filtrosAtuais, sugestoes) {
  const comPeriodo = (i) => ({ ano: i.ano ?? filtrosAtuais.ano, mes: i.mes ?? filtrosAtuais.mes, poder: i.poder, codIbge: i.codIbge, busca: i.busca, faixa: i.faixa })
  return [
    {
      name: 'resumo_painel',
      description: 'Totais do período: entidades, despesa total, uso médio do limite, quantas entidades em cada faixa e composição da despesa.',
      inputSchema: { type: 'object', properties: FILTROS },
      async execute(i) {
        const r = await api.resumo({ ...comPeriodo(i), faixa: undefined })
        return { ...r, despesaTotal: r2(r.despesaTotal), rclTotal: r2(r.rclTotal), usoMedioLimite: r2(r.usoMedioLimite) }
      },
    },
    {
      name: 'buscar_entidades',
      description: "Lista entidades com id, despesa total, RCL, percentual, limite e faixa. Use para perguntas como 'quais câmaras passaram do prudencial?'. Máximo 20 linhas.",
      inputSchema: {
        type: 'object',
        properties: {
          ...FILTROS, faixa: FAIXA,
          ordem: { type: 'string', enum: ['usoLimite', 'percentual', 'despesaTotal', 'entidade', 'municipio'] },
          direcao: { type: 'string', enum: ['asc', 'desc'] },
          limite: { type: 'integer' },
        },
      },
      async execute(i) {
        const limite = Math.min(Math.max(Number(i.limite) || 10, 1), 20)
        const p = await api.entidades(comPeriodo(i), { ordem: i.ordem || 'usoLimite', direcao: i.direcao || 'desc', tamanho: limite })
        return {
          total: p.total,
          itens: p.itens.map((e) => ({ id: e.entidadeId, entidade: e.entidade, municipio: e.municipio, codIbge: e.codIbge, poder: e.poder, despesaTotal: r2(e.despesaTotal), rclAjustada: r2(e.rclAjustada), percentual: r2(e.percentual), limite: e.limiteMaximo, faixa: e.faixa })),
        }
      },
    },
    {
      name: 'evolucao_entidade',
      description: 'Série dos últimos 12 períodos de uma entidade (pelo id): despesa, RCL, percentual e limite.',
      inputSchema: { type: 'object', properties: { entidadeId: { type: 'integer' } }, required: ['entidadeId'] },
      async execute(i) {
        const s = await api.evolucaoEntidade(Number(i.entidadeId))
        if (!s.length) throw new Error('Entidade não encontrada')
        return s.slice(-12).map((p) => ({ ano: p.ano, mes: p.mes, despesaTotal: r2(p.despesaTotal), rcl: r2(p.rclAjustada), percentual: r2(p.percentual) }))
      },
    },
    {
      name: 'sugerir_ajuste',
      description: 'Propõe um ajuste no painel que o usuário aplica com um botão: filtros, uma entidade para abrir no detalhe ou a tela. Não muda nada sozinho.',
      inputSchema: {
        type: 'object',
        properties: {
          rotulo: { type: 'string', description: "Texto curto do botão, ex.: 'Ver câmaras no prudencial'" },
          motivo: { type: 'string', description: 'Uma frase dizendo por que ajuda' },
          filtros: { type: 'object', properties: { ...FILTROS, faixa: FAIXA } },
          entidadeId: { type: 'integer', description: 'Entidade a abrir no detalhe' },
          tela: { type: 'string', enum: ['dashboard', 'consultar', 'importacao'] },
        },
        required: ['rotulo'],
      },
      execute(i) {
        if (sugestoes.length >= 3) return 'Limite de 3 sugestões atingido.'
        sugestoes.push({ rotulo: String(i.rotulo || 'Aplicar'), motivo: i.motivo ? String(i.motivo) : '', filtros: i.filtros || {}, entidadeId: i.entidadeId ? Number(i.entidadeId) : null, tela: i.tela || 'dashboard' })
        return 'Sugestão registrada; o usuário verá um botão para aplicar.'
      },
    },
  ]
}

const MENSAGENS_ERRO = {
  not_granted: 'O assistente precisa da sua permissão para usar o Claude nesta página.',
  sampling_disabled: 'O Claude não está disponível para esta conta.',
  rate_limited: 'Muitas perguntas seguidas. Espere um pouco e tente de novo.',
  tools_unavailable: 'Esta versão do app não consegue consultar os dados. Atualize o app do Claude.',
  refused: 'O assistente não respondeu a essa pergunta. Tente perguntar de outro jeito.',
}

/** Conversa usando o Claude do claude.ai. Lança Error com mensagem pronta para o usuário. */
export async function conversarDemo(api, { mensagens, contexto, onText, signal }) {
  const sample = window.claude?.use ? await window.claude.use('sample').catch(() => null) : null
  if (!sample) {
    throw new Error('No modo de demonstração o assistente funciona quando a página é aberta no claude.ai. '
      + 'No sistema real ele roda pelo backend, com a chave da Anthropic configurada no servidor.')
  }
  const sugestoes = []
  const ctx = `[Tela atual: ${contexto.tela}; filtros: ${JSON.stringify(contexto.filtros)}${contexto.entidadeId ? `; entidade aberta no detalhe: id ${contexto.entidadeId}` : ''}]`
  const turnos = mensagens.slice(-12).map((m, i, arr) => ({
    role: m.papel === 'assistant' ? 'assistant' : 'user',
    content: i === arr.length - 1 ? `${ctx}\n\n${m.texto}` : m.texto,
  }))
  try {
    const { text } = await sample([{ role: 'user', content: INSTRUCOES }, ...turnos], {
      tools: ferramentas(api, contexto.filtros, sugestoes), onText: onText && (({ text: t }) => onText(t)), signal,
    })
    return { texto: text, sugestoes }
  } catch (e) {
    if (e?.code === 'cancelled') return { texto: e.text || '', sugestoes, cancelado: true }
    throw new Error(MENSAGENS_ERRO[e?.code] || 'O assistente ficou indisponível. Tente de novo em instantes.')
  }
}
