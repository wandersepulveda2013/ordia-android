# Ordia Mega Evolution - Decisiones Arquitectónicas y de Diseño

## Wave 1: Foundation & Design System
- **Minimalismo Extremo vs. Funcionalidad:** Para cumplir la regla "A veces menos es más" sin quitar features, se unificó la base visual del esquema de color. En lugar de tener tarjetas de múltiples colores pastel que ensucian la vista, la estructura principal es puramente blanca y negra (o grises oscuros).
- **Preservación de Acentos:** Inicialmente se intentó sobreescribir el mapa `accentSwatches` con blanco/negro en todos los casos, pero tras revisión, esto destruía una feature requerida (personalización de acento). La decisión final fue usar blanco/negro para las superficies y fondos principales, pero permitir que los acentos (para iconos, checks, botones primarios) sigan usando la paleta semántica si el usuario así lo desea.
- **Componentes Abstraídos (OrdiaPrimitives):** Se añadieron `OrdiaButton`, `OrdiaCard`, `OrdiaInput`, `OrdiaDialog` y `OrdiaTask` para centralizar el control visual. Esto evita tener que buscar a través de toda la app cada vez que queramos ajustar un padding o un radio de borde en el futuro.
