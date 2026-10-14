// Gera no navegador a mesma planilha que o backend gera com Apache POI (usada no modo mock).
import * as XLSX from 'xlsx'
import { MESES } from '../formato.js'

const SITUACAO = {
  ACIMA_LIMITE: 'acima do limite máximo da LRF', PRUDENCIAL: 'acima do limite prudencial',
  ALERTA: 'acima do limite de alerta', REGULAR: 'dentro dos limites',
}

export function gerarExcel(d, evolucao) {
  const L = d.limiteMaximo
  const moeda = '#,##0.00', pct = '0.00%'
  const linhas = [
    ['RGF - Demonstrativo da Despesa com Pessoal'],
    [`${d.entidade} · ${d.municipio} (IBGE ${d.codIbge})`],
    [`Período: 12 meses até ${MESES[d.mes - 1]}/${d.ano} · Poder ${d.poder === 'EXECUTIVO' ? 'Executivo' : 'Legislativo'}`],
    [],
    ['Despesa com pessoal', 'Valor (R$)'],
    ['Despesa bruta com pessoal (I)', d.despesaBruta, moeda],
    ['   Pessoal ativo', d.pessoalAtivo, moeda],
    ['   Pessoal inativo e pensionistas', d.inativosPensionistas, moeda],
    ['   Outras despesas de pessoal (terceirização)', d.outrasDespesas, moeda],
    ['Despesas não computadas (II)', d.naoComputadas, moeda],
    ['Despesa total com pessoal - DTP (III = I - II)', d.despesaTotal, moeda],
    [],
    ['Receita corrente líquida ajustada (IV)', d.rclAjustada, moeda],
    ['DTP sobre a RCL ajustada (III / IV)', d.percentual / 100, pct],
    ['Limite máximo', L / 100, pct],
    ['Limite prudencial (95% do máximo)', (L * 0.95) / 100, pct],
    ['Limite de alerta (90% do máximo)', (L * 0.9) / 100, pct],
    ['Margem até o limite máximo (R$)', (d.rclAjustada * L) / 100 - d.despesaTotal, moeda],
    [],
    [`Situação: ${SITUACAO[d.faixa]}`],
  ]
  const ws = XLSX.utils.aoa_to_sheet(linhas.map((l) => l.slice(0, 2)))
  linhas.forEach((l, i) => {
    if (l[2]) ws[XLSX.utils.encode_cell({ r: i, c: 1 })].z = l[2]
  })
  ws['!cols'] = [{ wch: 52 }, { wch: 22 }]

  const ev = XLSX.utils.aoa_to_sheet([
    ['Período', 'Despesa total com pessoal', 'RCL ajustada', '% da RCL', 'Limite máximo'],
    ...evolucao.map((p) => [`${MESES[p.mes - 1]}/${p.ano}`, p.despesaTotal, p.rclAjustada, p.percentual / 100, p.limiteMaximo / 100]),
  ])
  evolucao.forEach((_, i) => {
    ;[[1, moeda], [2, moeda], [3, pct], [4, pct]].forEach(([c, z]) => { ev[XLSX.utils.encode_cell({ r: i + 1, c })].z = z })
  })
  ev['!cols'] = [{ wch: 12 }, { wch: 26 }, { wch: 26 }, { wch: 12 }, { wch: 14 }]

  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Demonstrativo')
  XLSX.utils.book_append_sheet(wb, ev, 'Evolução')
  const dados = XLSX.write(wb, { type: 'array', bookType: 'xlsx' })
  return new Blob([dados], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
}

export const nomeExcel = (d) =>
  `RGF_pessoal_${d.municipio.normalize('NFD').replace(/\p{M}/gu, '').replace(/[^A-Za-z0-9]+/g, '_')}_${d.ano}-${String(d.mes).padStart(2, '0')}.xlsx`
