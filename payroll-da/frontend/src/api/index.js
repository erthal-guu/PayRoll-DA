// Escolhe a fonte de dados: mock no navegador ou API Spring Boot (ver arquivo .env).
import { api as mock } from './mock.js'
import { api as http } from './http.js'

export const api = import.meta.env.VITE_USAR_MOCK === 'false' ? http : mock
