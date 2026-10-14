import { useEffect, useState } from 'react'

/** Executa uma chamada assíncrona sempre que as dependências mudam; descarta respostas antigas. */
export function useCarregar(funcao, deps) {
  const [estado, setEstado] = useState({ dados: null, carregando: true, erro: null })
  useEffect(() => {
    let ativo = true
    setEstado((e) => ({ ...e, carregando: true, erro: null }))
    Promise.resolve()
      .then(funcao)
      .then((dados) => ativo && setEstado({ dados, carregando: false, erro: null }))
      .catch((erro) => ativo && setEstado((e) => ({ ...e, carregando: false, erro: erro.message })))
    return () => { ativo = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  return estado
}
