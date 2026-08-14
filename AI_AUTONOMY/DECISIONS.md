# Decisiones Arquitectónicas y de Diseño
- Adoptar `HorizontalDivider` y `Icons.AutoMirrored.Outlined` para la versión de Compose 1.7.0+.
- Refactorizar `Theme.kt` para usar escala de grises (blanco, negro, gris) como base para la UI, eliminando tonos crema/amarillentos de fondo. Los acentos del usuario (OrdiaGold, OrdiaSage) se preservan como `accentSwatches`.
- Construir componentes `OrdiaButton`, `OrdiaCard`, `OrdiaSurface` etc., en el paquete `ui/components/` para unificar el aspecto y evitar código espagueti y estilos Material genéricos.
