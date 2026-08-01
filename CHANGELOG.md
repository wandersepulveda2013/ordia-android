# 3.0.0

## Asistente de organización personal (evolución asistida, 10 bloques)

- Analizador de lenguaje natural en español: repetición (`cada lunes y jueves`, `todos los días`), recordatorio "N antes", duración y confianza; captura segura a bandeja.
- Preview de interpretación con chips (repetición/recordatorio) y revisión antes de guardar.
- Plan automático del día con motivos y conflictos, log de automatizaciones, respaldo con deshacer (`undoLastAutomation`) y selección por bloque.
- Replanificación del día (replan), botón "Replanificar día" y selección de bloques compartida.
- Tarjeta "Qué hago ahora" (WhatNowEngine) con razonamientos y prioridad por agenda.
- Sincronización de recordatorios ante cambios de zona horaria, hora y fecha; guardas para tareas canceladas.
- Rutinas adaptables: respetan "ya ejecutada hoy", registran automatización y se pueden deshacer.
- Resumen del día y ritmo semanal (SummaryEngine determinista): completadas, pendientes + minutos, atrasadas, bandeja y tendencia semanal.
- Subtareas inteligentes: autocompletar el padre, reabrir al desmarcar, límite de profundidad y deshacer.
- Aprendizaje local opt-in: perfil de horarios del planificador (percentiles, ventana 28 días) desde preferencias, nunca en la nube.

- Atención contextual local y opcional.
- Confirmación obligatoria antes de crear elementos.
- Integración con Compartir y Procesar texto.
- Bandeja estructurada desde notificaciones autorizadas.
- Deduplicación, límites diarios, pausa temporal y bloqueo de contenido sensible.
- Base 2.0 auditada: guardianes, respaldo, recordatorios y actualización segura.

# Registro de cambios

## 2.0.0 — auditoría adversarial final

- Autoactualizador aislado en el manifiesto debug/sideload; la variante release/tienda no incluye sus permisos ni componentes.
- Eliminada la ruta de instalación directa desde la notificación de DownloadManager.
- Restauración bloqueada ante copias incompletas, UTF-8 inválido, claves duplicadas, relaciones imposibles o datos posteriores al JSON raíz.
- Backup versión 3 con preferencias y progreso del guardián.
- Exportación e importación serializadas con Mutex.
- Cancelación esperada y reconstrucción de recordatorios futuros tras restaurar.
- Nombre de APK canónico y rechazo de duplicados confundibles por mayúsculas.
- Tamaño esperado persistido y comparado contra DownloadManager y bytes copiados.
- Cálculo de versionCode idéntico y acotado en Gradle y CI.
- GitHub CLI con timeout; fallos tardíos no revierten builds o commits válidos.
- Rechazo de enlaces simbólicos y análisis de manifiestos por variante, Kotlin, PowerShell, XML y YAML.
- Documentación corregida para eliminar afirmaciones obsoletas sobre --clobber, checksum y DataStore.

## 2.0.0 — revisión auditada

### Endurecimiento final de auditoría

- APK validada e instalada desde almacenamiento privado mediante FileProvider.
- Limpieza automática de versiones obsoletas y cancelación de descargas sustituidas.
- Descargas periódicas sin redes medidas; descargas manuales explícitas permitidas.
- URLs restringidas por host, puerto y ausencia de credenciales embebidas.
- DataStore tolerante a IOException y fecha diaria consistente.
- Vínculo limitado a 500 XP equivalentes; imposible evolucionar solo mediante toques.
- Experiencia monotónica derivada de registros reales.
- Overlay sincronizado con el refugio, horas silenciosas y “Reducir movimiento”.
- Navegación lateral oculta en editores y detalles.
- Aplicador con validación del remoto, detached HEAD, keystore huérfano y credenciales.
- Push explícito a la rama original.

### Seguridad y confiabilidad

- Validación de SHA-256, paquete, `versionCode`, certificado y tamaño antes de instalar.
- Eliminación automática de APK inválidas.
- Notificación de actualización dirigida a Ajustes en lugar del navegador.
- Publicación bloqueada cuando GitHub no dispone de la misma firma estable.
- CI dividido entre compilación de solo lectura y publicación con permisos mínimos.
- Checksum verificado nuevamente antes de crear una Release.
- Cancelación real de WorkManager al desactivar actualizaciones.

### Guardián

- Corrección de experiencia duplicada y energía inicial.
- Pruebas deterministas con reloj y zona fija.
- Horas de silencio funcionales, incluida la transición nocturna.
- Posiciones flotantes y métricas adaptadas a pantallas pequeñas.

### Aplicador

- Rollback al SHA original.
- Contraseñas locales protegidas con DPAPI.
- Push omitido si los secretos de firma no fueron confirmados.
- Pruebas, lint y compilación obligatorios antes del commit.
- Consumo único de intents compartidos y de navegación para evitar duplicados al recrear la actividad.
- Emparejamiento estricto entre APK y archivo de checksum dentro de la Release.

## 2.0.0

### Experiencia y diseño

- Panel de inicio renovado con mejor jerarquía y acciones rápidas.
- Sistema visual coherente en modo claro y oscuro.
- Navegación adaptativa y editores con más espacio útil.
- Tareas con búsqueda, filtros, orden y metadatos más legibles.
- Estadísticas ampliadas y menú «Más» reorganizado.
- Sustitución de componentes Material deprecados incluidos en el lote.

### Guardianes virtuales

- Seis especies procedurales sin descargas externas.
- Cinco etapas de evolución.
- Personalidad derivada del patrón real de uso.
- Vínculo, experiencia, energía y estados contextuales.
- Refugio interactivo y cambio de identidad.
- Mascota flotante arrastrable con accesos rápidos.
- Recompensas ligadas a tareas, hábitos, notas y enfoque.
- Límite diario de vínculo para evitar progreso artificial.
- Sin enfermedades, muerte o pérdida de progreso por ausencia.

### Actualizaciones

- Comprobación periódica con WorkManager.
- Descarga controlada con DownloadManager.
- Prevención de descargas duplicadas de la misma versión.
- Validación de HTTPS y origen de la APK.
- Aviso de instalación protegido por Android.
- GitHub Actions con pruebas, artefactos y Releases.
- VersionCode creciente y firma de desarrollo configurable.

### Seguridad y calidad

- Tráfico HTTP no cifrado deshabilitado.
- Receptor de descarga no exportado.
- Instalador con copia de seguridad, rollback y protección de cambios locales.
- Prueba unitaria del motor de evolución.
