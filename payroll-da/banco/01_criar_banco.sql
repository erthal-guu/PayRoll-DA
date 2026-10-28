-- Passo 1: cria o usuário e o banco do Payroll DA.
-- Rode conectado como superusuário (postgres), em qualquer banco:
--   psql -U postgres -f banco/01_criar_banco.sql
-- No pgAdmin: abra o Query Tool no banco "postgres" e execute.

CREATE USER payroll WITH PASSWORD 'payroll';

CREATE DATABASE "PayRoll-DA"
    OWNER payroll
    ENCODING 'UTF8'
    TEMPLATE template0;
