# Cancelación de misión

AngCode usa un token atómico compartido. El loop comprueba cancelación antes y después de inferencias y herramientas. El coordinador deja de programar workers y no fusiona el worktree cuando la misión fue cancelada.

La UI muestra **Detener misión** solo durante la ejecución. Con llama-server también se apaga el servidor para liberar RAM y ayudar a desbloquear la llamada activa. Este control operativo es independiente del diseño final de la mascota.
