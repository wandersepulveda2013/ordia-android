
### Decisiones - Wave 1
1. **Colores Base**: Se transicionó de la paleta crema/tinta a una estrictamente blanco y negro verdadero, cediendo todo el protagonismo al contenido.
2. **Componentes Foundation**: Se crearon `OrdiaButton` y `OrdiaCard` para evitar el síndrome de 'dashboard genérico de Material Design', suavizando esquinas y priorizando el espacio negativo (Zero UI).
3. **Manejo de Clicks en Surfaces**: Se resolvió usar `if (onClick != null)` condicionalmente al instanciar `Surface` en `OrdiaCard` para evitar la intercepción de eventos e indicar semántica interactiva solo cuando corresponde.
