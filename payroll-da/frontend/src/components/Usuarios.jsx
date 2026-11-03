import { useEffect, useState } from 'react'
import { api } from '../api/index.js'

const VAZIO = { nome: '', email: '', senha: '', perfil: 'PREFEITURA', codIbge: '', ativo: true }

export default function Usuarios({ usuario, municipios }) {
  const [lista, setLista] = useState([])
  const [novo, setNovo] = useState(VAZIO)
  const [editando, setEditando] = useState(null)
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState(null)
  const [aviso, setAviso] = useState(null)

  const atualizar = () => api.usuarios().then(setLista).catch((e) => setErro(e.message))
  useEffect(() => { if (usuario.perfil === 'ADMIN') atualizar() }, [])

  const campo = (k) => ({ value: novo[k], onChange: (e) => setNovo({ ...novo, [k]: e.target.value }) })

  async function salvar(ev) {
    ev.preventDefault()
    setErro(null)
    setAviso(null)
    const municipio = municipios.find((m) => m.codIbge === novo.codIbge)?.municipio
      || (editando?.codIbge === novo.codIbge ? editando.municipio : undefined)
    try {
      if (editando) {
        await api.editarUsuario(editando.id, { ...novo, municipio })
        setAviso('Usuário atualizado.')
      } else {
        await api.criarUsuario({ ...novo, municipio })
        setAviso('Usuário criado.')
      }
      setNovo(VAZIO)
      setEditando(null)
      atualizar()
    } catch (e) { setErro(e.message) }
  }

  function editar(u) {
    setErro(null)
    setAviso(null)
    setEditando(u)
    setNovo({ nome: u.nome, email: u.email, senha: '', perfil: u.perfil, codIbge: u.codIbge || '', ativo: u.ativo })
  }

  function cancelar() {
    setEditando(null)
    setNovo(VAZIO)
  }

  const proprio = editando?.id === usuario.id

  async function remover(u) {
    if (!window.confirm(`Remover o usuário ${u.nome}?`)) return
    setErro(null)
    try { await api.removerUsuario(u.id); atualizar() } catch (e) { setErro(e.message) }
  }

  async function trocarSenha(ev) {
    ev.preventDefault()
    setErro(null)
    setAviso(null)
    try {
      await api.trocarSenha(senha)
      setSenha('')
      setAviso('Senha alterada. Entre de novo com a senha nova.')
      window.dispatchEvent(new Event('payroll:sessao-expirada'))
    } catch (e) { setErro(e.message) }
  }

  return (
    <>
      <div>
        <h1>Usuários</h1>
        <div className="sub">Administradores veem todos os municípios e importam dados. Usuários de prefeitura veem só o próprio município.</div>
      </div>
      {erro && <div className="erro">{erro}</div>}
      {aviso && <div className="panel">{aviso}</div>}

      {usuario.perfil === 'ADMIN' && (
        <div className="grade">
          <form className="panel form-usuario" onSubmit={salvar}>
            <div className="panel-head"><h2>{editando ? 'Editar usuário' : 'Novo usuário'}</h2></div>
            <label className="campo">Nome<input required {...campo('nome')} /></label>
            <label className="campo">E-mail<input type="email" required {...campo('email')} /></label>
            <label className="campo">{editando ? 'Nova senha (deixe em branco para manter)' : 'Senha (mínimo 6 caracteres)'}<input type="password" required={!editando} minLength={6} autoComplete="new-password" {...campo('senha')} /></label>
            <label className="campo">Perfil
              <select disabled={proprio} {...campo('perfil')}>
                <option value="PREFEITURA">Prefeitura</option>
                <option value="ADMIN">Administrador</option>
              </select>
            </label>
            {novo.perfil === 'PREFEITURA' && (
              <label className="campo">Município
                <select required {...campo('codIbge')}>
                  <option value="">Escolha…</option>
                  {municipios.map((m) => <option key={m.codIbge} value={m.codIbge}>{m.municipio}</option>)}
                </select>
              </label>
            )}
            {editando && (
              <label className="campo-check">
                <input type="checkbox" disabled={proprio} checked={novo.ativo} onChange={(e) => setNovo({ ...novo, ativo: e.target.checked })} />
                Ativo (usuário inativo não consegue entrar)
              </label>
            )}
            {proprio && <span className="hint">Você não pode mudar o próprio perfil nem se desativar.</span>}
            <div className="acoes-form">
              <button className="btn">{editando ? 'Salvar alterações' : 'Criar usuário'}</button>
              {editando && <button type="button" className="btn ghost" onClick={cancelar}>Cancelar</button>}
            </div>
          </form>

          <div className="panel">
            <div className="panel-head"><h2>Cadastrados</h2></div>
            <div className="tabela-wrap">
              <table>
                <thead><tr><th>Nome</th><th>E-mail</th><th>Perfil</th><th>Município</th><th>Situação</th><th /></tr></thead>
                <tbody>
                  {lista.map((u) => (
                    <tr key={u.id}>
                      <td>{u.nome}</td>
                      <td>{u.email}</td>
                      <td>{u.perfil === 'ADMIN' ? 'Administrador' : 'Prefeitura'}</td>
                      <td>{u.municipio || (u.perfil === 'ADMIN' ? 'Todos' : u.codIbge)}</td>
                      <td>{u.ativo ? 'Ativo' : 'Inativo'}</td>
                      <td>
                        <span className="acoes-usuario">
                          <button className="link" onClick={() => editar(u)}>Editar</button>
                          {u.id !== usuario.id && <button className="link" onClick={() => remover(u)}>Remover</button>}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      <form className="panel form-usuario" onSubmit={trocarSenha}>
        <div className="panel-head"><h2>Minha senha</h2><span className="hint">{usuario.email}</span></div>
        <label className="campo">Nova senha<input type="password" required minLength={6} autoComplete="new-password" value={senha} onChange={(e) => setSenha(e.target.value)} /></label>
        <button className="btn">Trocar senha</button>
      </form>
    </>
  )
}
