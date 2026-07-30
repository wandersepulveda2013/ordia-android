# Guardianes virtuales de Ordia 2.0

## Principio

El guardián acompaña el progreso sin castigar el descanso: no enferma, no muere y no pierde evolución por ausencia.

## Especies y etapas

Especies: Lumi, Moss, Orbit, Ember, Tide y Nova. Se dibujan de forma procedural, sin descargar imágenes.

Etapas: Chispa, Cría, Joven, Compañero y Ascendido.

## Experiencia verificable

La actividad se deriva de registros reales:

- tareas completadas;
- minutos de sesiones de enfoque terminadas;
- rachas de hábitos;
- notas activas.

El snapshot persistido conserva el máximo ya alcanzado para la mascota flotante. Se usa la cifra mayor entre actividad derivada y persistida; nunca se suman como fuentes duplicadas.

El vínculo directo aporta una bonificación limitada: `bond / 4`, con un máximo de 500 XP. Por tanto, cuidar al guardián ayuda, pero no permite alcanzar todas las evoluciones tocando la pantalla.

## Protección contra abuso y datos anómalos

- solo las primeras doce interacciones del día aumentan vínculo;
- cada sesión de enfoque aporta como máximo 180 minutos;
- cada hábito aporta como máximo 30 días de racha al cálculo;
- completar, desmarcar y volver a completar no incrementa un contador separado;
- restaurar datos vuelve a reconstruir la experiencia desde Room.

## Dinámicas

Estados: tranquilo, feliz, concentrado, con sueño, curioso, orgulloso, juguetón y preocupado.

Interacciones: acariciar, jugar, dar energía, conversar y descansar.

El refugio muestra:

- experiencia de actividad y bonificación del vínculo;
- etapa siguiente y XP restante;
- energía;
- personalidad derivada del uso;
- tres señales de cuidado diario;
- una acción sugerida basada en la situación actual.

## Overlay y accesibilidad

La mascota flotante:

- es arrastrable y se mantiene dentro de la pantalla;
- respeta `Reducir movimiento`;
- detiene animaciones durante horas silenciosas;
- reduce tamaño y opacidad por la noche;
- no inspecciona ni captura contenido de otras aplicaciones;
- abre únicamente acciones explícitas de Ordia.

Los fallos de `WindowManager` se contienen para no cerrar la aplicación.
