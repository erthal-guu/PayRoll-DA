/**
 * Entrega um arquivo ao usuário. Dentro do claude.ai (demo) usa o recurso de downloads
 * da página; no app normal usa um link de download comum.
 * @returns {Promise<'salvo'|'recusado'>}
 */
export async function salvarArquivo(blob, nome) {
  if (window.claude?.use) {
    const downloads = await window.claude.use('downloads').catch(() => null)
    if (downloads) {
      try {
        await downloads.save({ filename: nome, data: blob })
        return 'salvo'
      } catch (e) {
        if (e?.code === 'declined') return 'recusado'
        throw new Error('Não foi possível baixar o arquivo aqui. Rode o projeto localmente para baixar o Excel.')
      }
    }
  }
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
