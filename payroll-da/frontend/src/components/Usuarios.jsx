import { useEffect, useState } from 'react'
import { api } from '../api/index.js'

const iniciais = (nome) => (nome || '?').trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase()

const VAZIO = { nome: '', email: '', senha: '', perfil: 'PREFEITURA', codIbge: '', ativo: true }

/**
 * Administrador: lista dos usuários; "Novo usuário" e "Editar" abrem o mesmo modal, que também troca a senha.
 * Prefeitura: os próprios dados e "Alterar senha", que abre um modal só com a senha.
 */
export default function Usuarios({ usuario, municipios }) {
  const [lista, setLista] = useState([])
  const [form, setForm] = useState(VAZIO)
  // null: modal fechado; 'novo': criando; um usuário: editando; 'senha': prefeitura trocando a própria senha
  const [modal, setModal] = useState(null)
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState(null)
  const [erroModal, setErroModal] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [salvando, setSalvando] = useState(false)

  const admin = usuario.perfil === 'ADMIN'
  const atualizar = () => api.usuarios().then(setLista).catch((e) => setErro(e.message))
  useEffect(() => { if (admin) atualizar() }, [])

  useEffect(() => {
    if (!modal) return undefined
    const esc = (e) => e.key === 'Escape' && fechar()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [modal])

  const editando = modal && typeof modal === 'object' ? modal : null
  const proprio = editando?.id === usuario.id
  const campo = (k) => ({ value: form[k], onChange: (e) => setForm({ ...form, [k]: e.target.value }) })

  function abrirNovo() {
    setAviso(null); setErroModal(null)
    setForm(VAZIO)
    setModal('novo')
  }

  function editar(u) {
    setAviso(null); setErroModal(null)
    setForm({ nome: u.nome, email: u.email, senha: '', perfil: u.perfil, codIbge: u.codIbge || '', ativo: u.ativo })
    setModal(u)
  }

  function abrirSenha() {
    setAviso(null); setErroModal(null)
    setSenha('')
    setModal('senha')
  }

  function fechar() {
    setModal(null)
    setErroModal(null)
  }

  async function salvar(ev) {
    ev.preventDefault()
    setErroModal(null)
    setSalvando(true)
    const municipio = municipios.find((m) => m.codIbge === form.codIbge)?.municipio
      || (editando?.codIbge === form.codIbge ? editando.municipio : undefined)
    try {
      if (editando) {
        await api.editarUsuario(editando.id, { ...form, municipio })
        if (proprio && form.senha) {
          // a troca da própria senha encerra a sessão: entra de novo com a senha nova
          window.dispatchEvent(new Event('payroll:sessao-expirada'))
          return
        }
        setAviso(`${form.nome} atualizado.`)
      } else {
        await api.criarUsuario({ ...form, municipio })
        setAviso(`${form.nome} criado.`)
      }
      fechar()
      atualizar()
    } catch (e) {
      setErroModal(e.message)
    } finally {
      setSalvando(false)
    }
  }

  async function remover(u) {
    if (!window.confirm(`Remover o usuário ${u.nome}?`)) return
    setErro(null); setAviso(null)
    try { await api.removerUsuario(u.id); atualizar() } catch (e) { setErro(e.message) }
  }

  async function trocarSenha(ev) {
    ev.preventDefault()
    setErroModal(null)
    setSalvando(true)
    try {
      await api.trocarSenha(senha)
      window.dispatchEvent(new Event('payroll:sessao-expirada'))
    } catch (e) {
      setErroModal(e.message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <>
      <div>
        <h1>{admin ? 'Usuários' : 'Minha conta'}</h1>
        <div className="sub">Administradores veem todos os municípios e importam dados. Usuários de prefeitura veem só o próprio município.</div>
      </div>
      {erro && <div className="erro">{erro}</div>}
      {aviso && <div className="aviso-ok">{aviso}</div>}

      {admin ? (
        <div className="panel lista-usuarios">
          <div className="panel-head">
            <div className="titulo-lista"><h2>Cadastrados</h2><span className="hint">{lista.length} {lista.length === 1 ? 'usuário' : 'usuários'}</span></div>
            <button className="btn" onClick={abrirNovo}>+ Novo usuário</button>
          </div>
          <div className="tabela-wrap">
            <table>
              <thead><tr><th>Usuário</th><th>Perfil</th><th>Município</th><th>Situação</th><th className="n">Ações</th></tr></thead>
              <tbody>
                {lista.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <span className="usuario-cel">
                        <span className="avatar">{iniciais(u.nome)}</span>
                        <span className="quem"><b>{u.nome}{u.id === usuario.id && <em> (você)</em>}</b><small>{u.email}</small></span>
                      </span>
                    </td>
                    <td><span className={`etiqueta ${u.perfil === 'ADMIN' ? 'admin' : 'prefeitura'}`}>{u.perfil === 'ADMIN' ? 'Administrador' : 'Prefeitura'}</span></td>
                    <td>{u.municipio || (u.perfil === 'ADMIN' ? 'Todos' : u.codIbge)}</td>
                    <td><span className={`situacao ${u.ativo ? 'ativo' : 'inativo'}`}>{u.ativo ? 'Ativo' : 'Inativo'}</span></td>
                    <td className="n">
                      <span className="acoes-usuario">
                        <button className="btn-mini" onClick={() => editar(u)}>Editar</button>
                        {u.id !== usuario.id && <button className="btn-mini perigo" onClick={() => remover(u)}>Remover</button>}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="panel minha-conta">
          <span className="usuario-cel">
            <span className="avatar">{iniciais(usuario.nome)}</span>
            <span className="quem"><b>{usuario.nome}</b><small>{usuario.email}</small></span>
          </span>
          <span className="etiqueta prefeitura">Prefeitura{usuario.municipio ? ` · ${usuario.municipio}` : ''}</span>
          <button className="btn ghost" onClick={abrirSenha}>Alterar senha</button>
        </div>
      )}

      {modal && (
        <div className="modal-fundo" onMouseDown={(e) => e.target === e.currentTarget && fechar()}>
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-titulo">
            {modal === 'senha' ? (
              <form onSubmit={trocarSenha}>
                <div className="modal-topo">
                  <h2 id="modal-titulo">Alterar senha</h2>
                  <button type="button" className="modal-fechar" aria-label="Fechar" onClick={fechar}>×</button>
                </div>
                <div className="modal-corpo">
                  <label className="campo">Nova senha (mínimo 6 caracteres)
                    <input type="password" required minLength={6} autoFocus autoComplete="new-password" value={senha} onChange={(e) => setSenha(e.target.value)} />
                  </label>
                  <span className="hint">Depois de trocar, você entra de novo com a senha nova.</span>
                  {erroModal && <div className="erro">{erroModal}</div>}
                </div>
                <div className="modal-acoes">
                  <button type="button" className="btn ghost" onClick={fechar}>Cancelar</button>
                  <button className="btn" disabled={salvando}>{salvando ? 'Salvando…' : 'Trocar senha'}</button>
                </div>
              </form>
            ) : (
              <form onSubmit={salvar}>
                <div className="modal-topo">
                  {editando ? (
                    <span className="usuario-cel">
                      <span className="avatar">{iniciais(editando.nome)}</span>
                      <span className="quem"><b id="modal-titulo">Editar usuário</b><small>{editando.email}</small></span>
                    </span>
                  ) : <h2 id="modal-titulo">Novo usuário</h2>}
                  <button type="button" className="modal-fechar" aria-label="Fechar" onClick={fechar}>×</button>
                </div>
                <div className="modal-corpo grade-form">
                  <label className="campo">Nome<input required autoFocus {...campo('nome')} /></label>
                  <label className="campo">E-mail<input type="email" required {...campo('email')} /></label>
                  <label className="campo">Perfil
                    <select disabled={proprio} {...campo('perfil')}>
                      <option value="PREFEITURA">Prefeitura</option>
                      <option value="ADMIN">Administrador</option>
                    </select>
                  </label>
                  {form.perfil === 'PREFEITURA' ? (
                    <label className="campo">Município
                      <select required {...campo('codIbge')}>
                        <option value="">Escolha…</option>
                        {municipios.map((m) => <option key={m.codIbge} value={m.codIbge}>{m.municipio}</option>)}
                      </select>
                    </label>
                  ) : <div className="campo campo-info">Município<span>Todos (administrador)</span></div>}
                  <div className="secao-senha inteiro">
                    <b>{editando ? 'Alterar senha' : 'Senha de acesso'}</b>
                    <label className="campo">{editando ? 'Nova senha (deixe em branco para manter a atual)' : 'Senha (mínimo 6 caracteres)'}
                      <input type="password" required={!editando} minLength={6} autoComplete="new-password" {...campo('senha')} />
                    </label>
                    {proprio && form.senha && <span className="hint">Ao trocar a sua própria senha, você entra de novo com a senha nova.</span>}
                  </div>
                  {editando && (
                    <label className="campo-check inteiro">
                      <input type="checkbox" disabled={proprio} checked={form.ativo} onChange={(e) => setForm({ ...form, ativo: e.target.checked })} />
                      Ativo (usuário inativo não consegue entrar)
                    </label>
                  )}
                  {proprio && <span className="hint inteiro">Você não pode mudar o próprio perfil nem se desativar.</span>}
                  {erroModal && <div className="erro inteiro">{erroModal}</div>}
                </div>
                <div className="modal-acoes">
                  <button type="button" className="btn ghost" onClick={fechar}>Cancelar</button>
                  <button className="btn" disabled={salvando}>{salvando ? 'Salvando…' : editando ? 'Salvar alterações' : 'Criar usuário'}</button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </>
  )
}
