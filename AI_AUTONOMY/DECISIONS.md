# DECISIONS

## 2026-08-14
- **Arquitectura Visual Inicial (WAVE 1):** Se decide eliminar el sistema de "varias paletas" por una sola paleta adaptativa minimalista basada en escalas de grises (blanco, negro, gris premium) y mantener colores sutiles sólo para semántica (alerta, éxito, foco, prioridad).
- **Componentes:** Se decide crear una nueva capa de componentes base (`com.ordia.app.ui.components.designsystem`) para desacoplar el sistema de diseño nuevo del Material Design por defecto y poder implementar el concepto de "Zero UI" y minimalismo.
- **Formas:** Se reducirá el radio de borde de las tarjetas, botones, y otros elementos a valores más sutiles para lograr una apariencia más madura y menos caricaturesca (evitando bordes redondeados excesivos).