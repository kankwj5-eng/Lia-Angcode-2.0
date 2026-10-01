# Tool Packs en la interfaz

La pantalla Herramientas muestra ahora cada Tool Pack empaquetado con AngCode.

Para cada pack se ve:

- nombre y descripción;
- paquetes requeridos;
- herramientas activas / herramientas declaradas;
- estado del runtime.

Cuando el runtime está instalado y `apt` o `apt-get` han sido descubiertos, el usuario puede pulsar **Instalar pack**.

Ese gesto crea un contexto administrativo explícito y llama a `package.install` con:

- `PROCESS_EXECUTE`
- `NETWORK`
- `PACKAGE_MANAGE`

Los agentes normales no reciben ese permiso por defecto.
