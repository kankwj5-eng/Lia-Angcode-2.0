# Automatizaciones locales

AngCode usa AndroidX WorkManager para misiones periódicas locales.

## Propiedades

Cada automatización guarda:

- ID y nombre;
- misión;
- workspace fijo por nombre;
- intervalo de 15 minutos a 7 días;
- requisito opcional de red;
- requisito opcional de carga;
- estado activo/pausado;
- último resultado.

## Seguridad

Una automatización nunca recibe permisos elevados automáticamente.

No hereda:

- root;
- Shizuku;
- ADB remoto;
- SSH remoto;
- lectura/escritura de portapapeles;
- acciones visibles Android;
- GitHub write.

Si una misión necesita esos permisos debe ejecutarse manualmente y ser aprobada por el usuario.

El workspace queda fijado al crear la automatización. El Worker valida que continúe dentro de `files/workspaces`.

## Concurrencia

WorkManager usa trabajo periódico único por ID. Además, AngCode mantiene un lock por ID en proceso para impedir que una ejecución manual y una periódica se solapen.

Un fallo de una iteración se registra como `error`, pero no destruye la automatización periódica: el siguiente ciclo vuelve a intentarlo.

## Ejecución larga

El Worker se promueve a foreground mientras el modelo trabaja y muestra una notificación cancelable.
