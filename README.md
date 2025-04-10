# wakamiti-openai-plugin

## Descripción

El proyecto **wakamiti-openai-plugin** es un servicio diseñado para generar archivos de características (`.feature`)
basados en documentación de API. Utiliza la API de OpenAI para generar contenido dinámico y estructurado en función de
los esquemas de OpenAPI proporcionados. Este servicio es útil para automatizar la creación de pruebas o documentación
basada en especificaciones de API.


## Funcionamiento del servicio

### Entradas

1. **API Key de OpenAI**: Clave necesaria para autenticar las solicitudes a la API de OpenAI.
2. **Documentación de API (esquema OpenAPI)**: Puede ser un archivo JSON o una URL que contenga la especificación
   OpenAPI.
3. **Ruta de destino**: Carpeta donde se generarán los archivos `.feature`.
4. **Idioma**: Idioma en el que se generarán los archivos de características (por ejemplo, `es` para español o `en` para
   inglés).

### Salidas

- **Archivos `.feature`**: Archivos generados en lenguaje Gherkin que contienen escenarios de prueba funcionales y de
  validación basados en las operaciones definidas en el esquema OpenAPI.

### Pasos que sigue el servicio

1. **Inicialización**:
    - Se configura el cliente de OpenAI utilizando la API Key proporcionada.
    - Se analiza el esquema OpenAPI para fragmentarlo en operaciones individuales.

2. **Fragmentación del esquema OpenAPI**:
    - Se identifican las rutas, métodos HTTP, parámetros de entrada/salida, validaciones y códigos de respuesta.
    - Cada operación se convierte en un fragmento independiente con su propio identificador (`operationId`).

3. **Generación del contenido**:
    - Para cada operación, se crea un archivo `.feature` en la ruta de destino.
    - Se utiliza un prompt predefinido para generar escenarios de prueba en lenguaje Gherkin, siguiendo las mejores
      prácticas y restricciones definidas.

4. **Escritura de los archivos**:
    - Los escenarios generados se escriben en los archivos `.feature`.
    - Cada archivo incluye:
        - Un caso principal (caso feliz).
        - Casos funcionales adicionales que terminan en éxito.
        - Validaciones de entradas válidas e inválidas.
        - Casos de error (códigos 4xx).
        - Sugerencias de dudas para hacerle al analista funcional.

5. **Finalización**:
    - Se asegura que todos los archivos se hayan generado correctamente.
    - Se cierra el cliente de OpenAI.
