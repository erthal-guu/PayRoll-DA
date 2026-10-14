// Gera dados DE EXEMPLO de despesa com pessoal para municípios do Paraná.
// Os nomes e códigos IBGE servem só de referência; os valores são fictícios.

const MUNICIPIOS = [
  ['4106902', 'Curitiba', 1773], ['4113700', 'Londrina', 555], ['4115200', 'Maringá', 409],
  ['4119905', 'Ponta Grossa', 358], ['4104808', 'Cascavel', 348], ['4125506', 'São José dos Pinhais', 329],
  ['4108304', 'Foz do Iguaçu', 285], ['4105805', 'Colombo', 232], ['4109401', 'Guarapuava', 182],
  ['4118204', 'Paranaguá', 145], ['4101804', 'Araucária', 151], ['4127700', 'Toledo', 150],
  ['4101408', 'Apucarana', 130], ['4119152', 'Pinhais', 127], ['4104204', 'Campo Largo', 136],
  ['4101507', 'Arapongas', 119], ['4100400', 'Almirante Tamandaré', 119], ['4128104', 'Umuarama', 117],
  ['4119509', 'Piraquara', 118], ['4103701', 'Cambé', 107], ['4107652', 'Fazenda Rio Grande', 148],
  ['4126256', 'Sarandi', 100], ['4118402', 'Paranavaí', 92], ['4108403', 'Francisco Beltrão', 97],
  ['4118501', 'Pato Branco', 91], ['4105508', 'Cianorte', 79], ['4127106', 'Telêmaco Borba', 75],
  ['4104907', 'Castro', 73], ['4122404', 'Rolândia', 71], ['4110706', 'Irati', 60],
]

export const LIMITES = { EXECUTIVO: 54, LEGISLATIVO: 6 }

function mulberry32(seed) {
  return function () {
    let t = (seed += 0x6d2b79f5)
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function periodosExemplo() {
  const lista = []
  for (let ano = 2025; ano <= 2026; ano++) {
    for (let mes = 1; mes <= 12; mes++) {
      if (ano === 2026 && mes > 8) break
      lista.push({ ano, mes })
    }
  }
  return lista
}

const r2 = (n) => Math.round(n * 100) / 100

/** Linhas no mesmo formato lógico do CSV importado pelo backend. */
export function gerarLinhas() {
  const rnd = mulberry32(2026)
  const periodos = periodosExemplo()
  const linhas = []
  for (const [codIbge, municipio, popMil] of MUNICIPIOS) {
    const rclBase = popMil * 1000 * (4300 + rnd() * 1600) // R$ por habitante/ano
    const entidades = [
      { nome: `Prefeitura Municipal de ${municipio}`, poder: 'EXECUTIVO', alvo: 42 + rnd() * 14, tendencia: (rnd() - 0.4) * 0.35 },
      { nome: `Câmara Municipal de ${municipio}`, poder: 'LEGISLATIVO', alvo: 2.6 + rnd() * 3.3, tendencia: (rnd() - 0.45) * 0.04 },
    ]
    periodos.forEach(({ ano, mes }, i) => {
      const rcl = rclBase * (1 + i * 0.006 + (rnd() - 0.5) * 0.01)
      for (const e of entidades) {
        const pct = Math.max(1, e.alvo + e.tendencia * i + (rnd() - 0.5) * (e.poder === 'EXECUTIVO' ? 0.9 : 0.12))
        const total = (rcl * pct) / 100
        const naoComputadas = total * (0.06 + rnd() * 0.04)
        const inativos = total * (e.poder === 'EXECUTIVO' ? 0.08 + rnd() * 0.05 : 0.02)
        const outras = total * (0.02 + rnd() * 0.03)
        const bruta = total + naoComputadas
        linhas.push({
          municipio, codIbge, entidade: e.nome, poder: e.poder, ano, mes,
          despesaBruta: r2(bruta), pessoalAtivo: r2(bruta - inativos - outras), inativosPensionistas: r2(inativos),
          outrasDespesas: r2(outras), naoComputadas: r2(naoComputadas), despesaTotal: r2(total), rclAjustada: r2(rcl),
        })
      }
    })
  }
  return linhas
}

/** Cabeçalho esperado pelo backend (ver tce.importacao.colunas no application.yml). */
export const COLUNAS_CSV = [
  ['municipio', 'municipio'], ['cod_ibge', 'codIbge'], ['entidade', 'entidade'], ['poder', 'poder'],
  ['ano', 'ano'], ['mes', 'mes'], ['despesa_bruta', 'despesaBruta'], ['pessoal_ativo', 'pessoalAtivo'],
  ['inativos_pensionistas', 'inativosPensionistas'], ['outras_despesas_pessoal', 'outrasDespesas'],
  ['despesas_nao_computadas', 'naoComputadas'], ['despesa_total_pessoal', 'despesaTotal'], ['rcl_ajustada', 'rclAjustada'],
]
