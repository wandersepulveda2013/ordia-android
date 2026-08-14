# Estado Actual

## Auditoría Inicial (WAVE 0)
- **Arquitectura actual**: MVVM con Clean Architecture, Room (KSP), Jetpack Compose.
- **Deuda**: Componentes UI dispersos sin prefijo centralizado, colores no 100% semánticos, animaciones básicas, KSP caching issues en CI.
- **Riesgos Técnicos**:
  1. Concurrencia en base de datos local con múltiples inserciones rápidas.
  2. Implementación de un custom keyboard (IME) complejo y seguro.
  3. Rendimiento con listas inmensas (notas complejas/tareas).
  4. WorkManager triggers precisos en Doze Mode.
  5. Parseo NLP robusto sin APIs de pago y en local.

**Próxima Acción**: Ejecutar WAVE 1 (Foundation + Design System).
