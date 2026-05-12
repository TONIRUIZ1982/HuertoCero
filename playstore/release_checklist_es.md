# Checklist antes de subir a Play Store

- [ ] Crear cuenta de desarrollador de Google Play.
- [ ] Publicar la politica de privacidad en una URL accesible.
- [ ] Sustituir el contacto de privacidad por un email real.
- [ ] Activar Firebase Authentication, Firestore y Storage.
- [ ] Desplegar `firestore.rules` y `storage.rules`.
- [ ] Restringir la clave de Google Maps en Google Cloud.
- [ ] Probar registro, login, crear producto, subir imagen, favoritos, reservas, chat y reportes.
- [ ] Probar Mis productos: editar precio, cambiar descripcion y eliminar producto.
- [ ] Probar categorias y filtro del mapa.
- [ ] Probar perfil de vendedor y valoraciones.
- [ ] Crear icono 512x512 PNG.
- [ ] Crear feature graphic 1024x500.
- [ ] Hacer al menos 5 capturas.
- [ ] Generar Android App Bundle release (`.aab`).
- [ ] Completar Data Safety segun `data_safety_notes_es.md`.
- [ ] Declarar que la app contiene contenido generado por usuarios y tiene reportes.
- [ ] Revisar versionCode y versionName antes de cada subida.

Comando recomendado para generar bundle desde terminal:

```powershell
.\gradlew.bat bundleRelease
```

Android Studio tambien puede generar el bundle desde Build > Generate Signed Bundle / APK.
