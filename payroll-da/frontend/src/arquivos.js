/**
 * Entrega um arquivo ao usuário com um link de download comum.
 * @returns {Promise<'salvo'|'recusado'>}
 */
export async function salvarArquivo(blob, nome) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nome
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
  return 'salvo'
}
