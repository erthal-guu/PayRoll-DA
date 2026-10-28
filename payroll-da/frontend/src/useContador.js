import { useEffect, useRef, useState } from 'react'

/** Anima um número do valor anterior até o novo (efeito de contagem nos cards). */
export function useContador(alvo, duracao = 700) {
  const [valor, setValor] = useState(0)
  const anterior = useRef(0)

  useEffect(() => {
    const inicio = anterior.current
    const fim = Number(alvo) || 0
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      anterior.current = fim
      setValor(fim)
      return undefined
    }
    let quadro
    const t0 = performance.now()
    const passo = (agora) => {
      const t = Math.min(1, (agora - t0) / duracao)
      const v = inicio + (fim - inicio) * (1 - (1 - t) ** 3)
      setValor(v)
      anterior.current = v
      if (t < 1) quadro = requestAnimationFrame(passo)
    }
    quadro = requestAnimationFrame(passo)
    return () => cancelAnimationFrame(quadro)
  }, [alvo, duracao])

  return valor
}
