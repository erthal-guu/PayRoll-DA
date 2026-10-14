// Gera um CSV de exemplo no formato aceito pelo backend (ISO-8859-1, separador ";", números no padrão BR).
// Uso: npm run gerar-csv
import { writeFileSync } from 'node:fs'
import { gerarLinhas, COLUNAS_CSV } from '../mock/dados.js'

const destino = process.argv[2] ?? 'exemplo_despesa_pessoal.csv'
const br = (n) => n.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const campo = (v) => (typeof v === 'number' && !Number.isInteger(v) ? br(v) : String(v))
const esc = (s) => (/[;"\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s)

const linhas = [COLUNAS_CSV.map(([h]) => h).join(';')]
for (const l of gerarLinhas()) {
  linhas.push(COLUNAS_CSV.map(([, k]) => esc(campo(k === 'poder' ? (l.poder === 'EXECUTIVO' ? 'Executivo' : 'Legislativo') : l[k]))).join(';'))
}
writeFileSync(destino, Buffer.from(linhas.join('\r\n') + '\r\n', 'latin1'))
console.log(`${linhas.length - 1} linhas gravadas em ${destino}`)
