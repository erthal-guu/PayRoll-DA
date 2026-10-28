# Colocar o Payroll DA no ar

O jeito mais simples: **uma VPS com Docker** (Hetzner, DigitalOcean, Contabo, Oracle Free... uns R$ 25 a 40/mês, 2 GB de RAM bastam).

## 1. Pré-requisitos
- Um servidor Linux com Docker e o plugin `docker compose`.
- Um domínio (ou subdomínio) com registro **A** apontando para o IP do servidor.
- Portas 80 e 443 abertas no firewall.
- O servidor precisa **alcançar `simam.tce.pr.gov.br`** (a Consulta automática depende disso). Teste: `curl -I https://simam.tce.pr.gov.br`. Alguns sites do governo bloqueiam IP de datacenter/exterior; se isso acontecer, use um servidor no Brasil.

## 2. Subir
```bash
git clone https://github.com/erthal-guu/PayRoll-DA.git
cd PayRoll-DA/payroll-da
cp .env.example .env
nano .env        # DOMINIO, DB_PASSWORD, ADMIN_EMAIL, ADMIN_SENHA, GROQ_API_KEY
docker compose -f docker-compose.prod.yml up -d --build
```
Pronto: o banco é criado vazio e as tabelas são criadas sozinhas (Flyway) na primeira subida. O administrador é criado com `ADMIN_EMAIL`/`ADMIN_SENHA` (só quando não há nenhum usuário). O HTTPS é emitido sozinho pelo Caddy.

Abra `https://SEU_DOMINIO` e entre. Os dados vêm da **Consulta automática** ou da tela **Importar CSV**.

## 3. O que é cada variável (arquivo `.env`)
| Variável | Para quê |
|---|---|
| `DOMINIO` | endereço do site; também vira a origem permitida (CORS) e o endereço da API no front |
| `DB_PASSWORD` | senha do Postgres (o banco não fica exposto na internet) |
| `ADMIN_EMAIL`, `ADMIN_SENHA` | primeiro administrador |
| `GROQ_API_KEY` | chave do assistente; fica só no servidor |
| `GROQ_MODELO` | opcional, troca o modelo |

Segredos ficam só no `.env` do servidor. O `.env` está no `.gitignore`.

## 4. Operação
- Atualizar: `git pull && docker compose -f docker-compose.prod.yml up -d --build`
- Logs: `docker compose -f docker-compose.prod.yml logs -f api`
- Backup: `docker compose -f docker-compose.prod.yml exec db pg_dump -U payroll PayRoll-DA > backup.sql`
- Restaurar: `docker compose -f docker-compose.prod.yml exec -T db psql -U payroll PayRoll-DA < backup.sql`
- Trocar a senha do admin: na tela **Usuários** (depois do primeiro login).

## 5. Alternativa sem servidor próprio (Render / Railway / Fly)
- **Banco:** crie um Postgres gerenciado e use os dados dele em `DB_URL` (`jdbc:postgresql://HOST:5432/BANCO`), `DB_USER`, `DB_PASSWORD`.
- **API:** serviço Docker apontando para a pasta `backend/` (usa `backend/Dockerfile`), com as variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_SENHA`, `GROQ_API_KEY` e `CORS_ORIGENS=https://endereço-do-front`. A porta vem de `PORT`.
- **Front:** site estático (`npm ci && npm run build`, publicar `dist/`), com `VITE_API_URL=https://endereço-da-api` definido **antes do build**.
- Os planos grátis dormem e podem demorar a responder a consulta ao SIMAM; para uso real, prefira o plano pago ou a VPS.
