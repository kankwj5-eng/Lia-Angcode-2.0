# SSH remoto

AngCode puede delegar tareas pesadas a otra máquina usando OpenSSH del runtime.

Herramientas:

- `ssh.exec`
- `ssh.upload`
- `ssh.download`

Todas requieren `SSH_REMOTE` además de red/proceso.

## Seguridad

- `BatchMode=yes`: nunca abre un prompt de contraseña.
- `StrictHostKeyChecking=yes`: no acepta host keys nuevas silenciosamente.
- `known_hosts` debe existir dentro del workspace (por defecto `.agent/known_hosts`).
- La clave privada opcional también debe estar dentro del workspace.
- La ejecución remota usa una ruta de ejecutable absoluta y argumentos escapados individualmente.
- Las descargas terminan en `artifacts/`.

El enrolamiento/trust de una host key queda fuera de la autonomía del agente: el usuario debe proporcionar un `known_hosts` ya verificado.
