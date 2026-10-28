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
      <aside className="login-arte">
        <div className="logo"><span className="marca" aria-hidden="true" /><span>Payroll <b>DA</b><small>Despesa com pessoal · LRF · PR</small></span></div>
        <div>
          <h2>Acompanhe a despesa com pessoal <em>antes</em> de estourar o limite.</h2>
          <p>Dados do SIMAM do TCE-PR organizados por município, com alertas da Lei de Responsabilidade Fiscal.</p>
        </div>
        <div className="login-cards">
          <div><strong>54%</strong><span>limite do Executivo</span></div>
          <div><strong>6%</strong><span>limite do Legislativo</span></div>
          <div><strong>95%</strong><span>limite prudencial</span></div>
        </div>
      </aside>
      <div className="login-lado">
        <form className="login-form" onSubmit={entrar}>
          <div className="logo"><span className="marca" aria-hidden="true" /><span>Payroll <b>DA</b><small>Despesa com pessoal · LRF · PR</small></span></div>
          <div>
            <h1>Entrar</h1>
            <p className="sub">Use o e-mail e a senha cadastrados pelo administrador.</p>
          </div>
          {erro && <div className="erro">{erro}</div>}
          <label className="campo">E-mail
            <input type="email" autoComplete="username" required placeholder="voce@prefeitura.pr.gov.br" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label className="campo">Senha
            <input type="password" autoComplete="current-password" required placeholder="••••••" value={senha} onChange={(e) => setSenha(e.target.value)} />
          </label>
          <button className="btn" disabled={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</button>
        </form>
      </div>
    </div>
  )
}
