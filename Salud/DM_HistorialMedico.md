1. Visión General

Plataforma integral de seguimiento médico compuesta por una aplicación móvil, tarjeta RFID para acceso de emergencias y un panel administrativo. El diseño y desarrollo móvil se rige por altos estándares de ingeniería de software, enfocándose en rendimiento, accesibilidad y escalabilidad internacional.
2. Arquitectura de Software

    Patrón MVVM (Model-View-ViewModel): Separación estricta de responsabilidades.

        View (UI): Exclusivamente responsable de renderizar la interfaz y capturar interacciones. Debe ser pasiva y reaccionar a los estados expuestos por el ViewModel.

        ViewModel: Contiene la lógica de presentación. Gestiona el estado de la UI y se comunica con los repositorios de datos.

        Model (Data): Repositorios, fuentes de datos locales (bases de datos, XML) y remotas (API).

    Desarrollo Guiado por Pruebas (TDD): Construcción del sistema bajo la metodología TDD. Ningún componente visual o flujo de datos será aprobado sin sus respectivas pruebas unitarias y de interfaz previas.

3. Directrices de Interfaz y Experiencia (UI/UX)

    Minimalismo Visual: Interfaces limpias y no saturadas. Uso intensivo de espacio en blanco para guiar la atención del usuario y evitar fatiga cognitiva, crucial en aplicaciones de salud.

    Iconografía Reconocible: Uso exclusivo de iconografía estándar e intuitiva (ej. SF Symbols) que los nuevos usuarios puedan identificar inmediatamente sin necesidad de leer.

    Restricción de Emojis: Queda estrictamente prohibido el uso de emojis en cualquier pantalla, notificación o alerta para mantener la formalidad y claridad médica.

    Modo Oscuro Obligatorio: Implementación de tokens de color semánticos. La aplicación debe detectar y adaptarse automáticamente a las preferencias de Modo Claro / Modo Oscuro del sistema sin perder legibilidad.

4. Accesibilidad e Inclusión

    Soporte TalkBack / VoiceOver: Todos los elementos interactivos e informativos deben contar con etiquetas descriptivas (contentDescription o accessibilityLabel). La navegación mediante lectores de pantalla debe seguir un orden lógico y secuencial, permitiendo a personas con discapacidad visual utilizar el 100% de las funciones de la app.

    Accesibilidad Tipográfica: Jerarquía visual basada en los estilos de texto dinámicos del sistema (ej. Large Title, Body, Caption) para que la interfaz escale correctamente si el usuario aumenta el tamaño de letra en los ajustes de su teléfono.

5. Normativas de Desarrollo de UI

    Internacionalización Centralizada (i18n): Cero texto estático (hardcoded) en el código fuente. Absolutamente todos los textos de la interfaz deben estar referenciados a un archivo central strings.xml. Cualquier adaptación o traducción futura se hará modificando únicamente el XML.

    Componentes y Controles Nativos: La interfaz consumirá componentes nativos de iOS para garantizar familiaridad y rendimiento. Se exige el uso de Navigation Controllers, Tab Bars, Action Sheets y Modals propios del sistema operativo.


    y dime papi quiero mas cada vez que acabes una tarea y al emepzarla 