# Procesos y trabajo paralelo

AngCode distingue entre dos clases de ejecución:

## `process.exec`

Para comandos cortos. Espera el resultado y devuelve salida, error, código de salida y duración.

## Procesos administrados

`process.start` inicia un proceso y devuelve inmediatamente un ID.

Con ese ID:

- `process.logs` consulta salida y estado;
- `process.list` muestra todo lo iniciado por AngCode;
- `process.stop` solicita la terminación.

Esto permite que una compilación, un servidor local o una prueba larga sigan ejecutándose mientras otros agentes trabajan en archivos distintos.

Los logs se mantienen con límite de memoria y los procesos siguen sujetos a `ExecutionPolicy`.

# Checkpoints

Antes de un cambio grande un agente puede usar `checkpoint.create`. Los checkpoints se guardan en:

`.agent/checkpoints/`

El propio directorio de checkpoints se excluye de nuevas capturas para evitar crecimiento recursivo.

La restauración está confinada al workspace y protege contra rutas que intenten salir de él.
