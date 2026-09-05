-- Ejecutar como usuario administrador de PostgreSQL.
-- Cambia la contraseña antes de usarlo en producción.
CREATE USER isla_spider_bot WITH PASSWORD 'CAMBIA_ESTA_CLAVE';
CREATE DATABASE isla_spider_bot OWNER isla_spider_bot ENCODING 'UTF8';

