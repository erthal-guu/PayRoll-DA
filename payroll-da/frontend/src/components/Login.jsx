import { useState } from 'react'
import { api } from '../api/index.js'

export default function Login({ aoEntrar }) {
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState(null)
  const [enviando, setEnviando] = useState(false)

  async function entrar(ev) {
    ev.preventDefault()
    setErro(null)
    setEnviando(true)
    try { aoEntrar(await api.entrar(email, senha)) } catch (e) { setErro(e.message) } finally { setEnviando(false) }
  }

  return (
    <div className="login">
      <form className="panel login-form" onSubmit={entrar}>
        <div className="logo"><span className="marca" aria-hidden="true" />Payroll <b>DA</b><small>Despesa com pessoal · LRF · PR</small></div>
        {erro && <div className="erro">{erro}</div>}
        <label className="campo">E-mail
          <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="campo">Senha
          <input type="password" autoComplete="current-password" required value={senha} onChange={(e) => setSenha(e.target.value)} />
        </label>
        <button className="btn" disabled={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</button>
      </form>
    </div>
  )
}
