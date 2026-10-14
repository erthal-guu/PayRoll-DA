export const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']

export const periodoTxt = (p) => (p ? `${MESES[p.mes - 1]}/${p.ano}` : '')
export const periodoCurto = (p) => `${MESES[p.mes - 1]}/${String(p.ano).slice(2)}`

export function moeda(n) {
  const v = Number(n) || 0
  if (Math.abs(v) >= 1e9) return `R$ ${(v / 1e9).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} bi`
  if (Math.abs(v) >= 1e6) return `R$ ${(v / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`
  if (Math.abs(v) >= 1e3) return `R$ ${(v / 1e3).toLocaleString('pt-BR', { maximumFractionDigits: 0 })} mil`
  return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export const moedaCheia = (n) => (Number(n) || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 })

export const pct = (n, casas = 2) =>
  `${(Number(n) || 0).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas })}%`

export const FAIXAS = {
  REGULAR: { nome: 'Regular', descricao: 'abaixo de 90% do limite', cor: 'var(--good)' },
  ALERTA: { nome: 'Alerta', descricao: 'de 90% a 95% do limite', cor: 'var(--warn)' },
  PRUDENCIAL: { nome: 'Prudencial', descricao: 'de 95% até o limite', cor: 'var(--orange)' },
  ACIMA_LIMITE: { nome: 'Acima do limite', descricao: 'limite máximo atingido', cor: 'var(--bad)' },
}

/** Mesma regra do backend: alerta a partir de 90% do limite, prudencial a partir de 95%. */
export function faixaTexto(percentual, limite) {
  if (percentual >= limite) return 'ACIMA_LIMITE'
  if (percentual >= limite * 0.95) return 'PRUDENCIAL'
  if (percentual >= limite * 0.9) return 'ALERTA'
  return 'REGULAR'
}

export const PODERES ={ EXECUTIVO: 'Executivo', LEGISLATIVO: 'Legislativo' }
