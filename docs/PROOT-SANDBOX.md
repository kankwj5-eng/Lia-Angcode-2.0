# Sandbox PRoot-Distro

El runtime AngCode incluye `proot-distro` en los perfiles de bootstrap para convertir PRoot en una capacidad administrada.

Herramientas:

- `sandbox.list`
- `sandbox.install`
- `sandbox.exec`
- `sandbox.remove`

`sandbox.install` y `sandbox.remove` requieren `SANDBOX_MANAGE`.

`sandbox.exec` no recibe una cadena de shell. Recibe:

- nombre del contenedor;
- ruta absoluta del ejecutable dentro del guest;
- lista de argumentos.

La ejecución usa `--isolated --minimal` y enlaza únicamente el workspace actual como `/workspace`.

Ejemplo conceptual:

```text
sandbox.exec(
  name="debian",
  executable="/usr/bin/python3",
  args=["/workspace/test.py"]
)
```

Esto permite dar a un agente un Linux completo sin entregarle automáticamente todo el almacenamiento Android.
