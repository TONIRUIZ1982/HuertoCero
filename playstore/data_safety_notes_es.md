# Notas para Data Safety de Play Console

Estas notas sirven para rellenar el formulario de Seguridad de los datos. Ajustalas si cambias funciones antes de publicar.

## Datos recogidos

- Email o identificador de usuario: autenticacion.
- Fotos y videos: imagenes de productos subidas por vendedores.
- Mensajes: chat entre comprador y vendedor.
- Ubicacion: coordenadas del punto de venta/producto en el mapa.
- Actividad en la app: favoritos, reservas, productos publicados y reportes.
- Valoraciones: puntuaciones asociadas a vendedores.

## Uso de datos

- Funcionalidad de la app.
- Comunicacion entre usuarios.
- Seguridad, prevencion de abuso y moderacion.
- Gestion de cuenta.

## Comparticion

No vender datos. Los datos se procesan mediante Firebase y Google Maps como proveedores tecnicos.

## Seguridad

Configura reglas de Firestore y Storage antes de publicar. Restringe la clave de Google Maps por paquete Android y huella SHA-1/SHA-256.

## Eliminacion

Debe existir una via de contacto real para solicitar eliminacion de cuenta/datos. Publica esa via en la ficha de Play Store y en la politica de privacidad.
