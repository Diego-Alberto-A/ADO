# Publicar Crosswalker

Guía para quien reciba este repositorio y vaya a subir la app a Google Play.
El código está listo; lo único que falta es la firma, que depende de la cuenta
de quien publica y por eso no puede venir incluida.

- **Versión:** 1.0 (`versionCode = 1`, `versionName = "1.0"`), etiquetada como `v1.0`.
- **applicationId:** `com.crosswalker.game`
- **minSdk:** 24 (Android 7.0) · **targetSdk:** 37
- **Permisos:** ninguno. El juego funciona sin red y no recoge ningún dato.

> El paquete Kotlin interno sigue siendo `com.example.crosswalker` (es el
> `namespace` de Gradle, visible solo en el código). Play solo mira el
> `applicationId`, así que no hace falta renombrarlo; cambiarlo obligaría a
> mover todos los archivos fuente sin ningún beneficio externo.

## 1. Requisitos

- JDK 11 o superior y el SDK de Android (API 37).
- Android Studio, o solo la línea de comandos con `local.properties` apuntando al SDK:
  ```
  sdk.dir=/ruta/a/Android/Sdk
  ```

Comprueba que compila antes de tocar la firma:

```bash
./gradlew testDebugUnitTest      # 50 pruebas unitarias
./gradlew assembleDebug
```

## 2. Crear el keystore de firma

**Este paso lo hace quien publica, una sola vez.** El keystore es la identidad
de la app en Google Play: si se pierde, no se pueden volver a publicar
actualizaciones nunca más. Guárdalo con copia de seguridad y fuera del repositorio.

```bash
keytool -genkeypair -v \
  -keystore crosswalker-release.jks \
  -alias crosswalker \
  -keyalg RSA -keysize 2048 -validity 10000
```

Después copia la plantilla y rellena los cuatro valores:

```bash
cp keystore.properties.example keystore.properties
```

```properties
storeFile=/ruta/absoluta/a/crosswalker-release.jks
storePassword=<la contraseña del keystore>
keyAlias=crosswalker
keyPassword=<la contraseña de la clave>
```

`keystore.properties`, `*.jks` y `*.keystore` están en `.gitignore`. No los subas
al repositorio ni los mandes por chat o correo.

Si no existe `keystore.properties`, el proyecto sigue compilando: el release sale
**sin firmar** (`app-release-unsigned.apk`), que no se puede instalar ni subir.
Es el comportamiento esperado, no un error de configuración.

## 3. Generar el artefacto

Google Play exige un Android App Bundle (`.aab`):

```bash
./gradlew bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

Para probar en un dispositivo real antes de subirlo, un APK firmado:

```bash
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Verifica la firma:

```bash
$ANDROID_HOME/build-tools/<versión>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

**Guarda `app/build/outputs/mapping/release/mapping.txt`** de cada versión que
publiques y súbelo a Play Console. El release va minificado con R8, así que sin
ese archivo los informes de errores llegan ilegibles.

## 4. Subir a Play Console

1. Crea la app en [Play Console](https://play.google.com/console) y sube el `.aab`.
2. **Firma de apps de Play:** acepta que Google gestione la clave de firma
   (recomendado). Tu keystore pasa a ser la clave de *carga*; sigue siendo
   imprescindible conservarlo.
3. **Seguridad de los datos:** declara que la app no recoge ni comparte datos.
   Es cierto: no hay permisos, no hay red, y lo único que se guarda es el avance
   de la campaña en `SharedPreferences` local (`stars_<n>`, `tutorial_done`).
4. **Clasificación de contenido:** rellena el cuestionario. Es un juego educativo
   sin violencia, compras ni publicidad.
5. **Público objetivo:** si lo diriges a menores de 13 años, Play activa requisitos
   extra (Families Policy). Tenlo en cuenta: el juego es claramente infantil.
6. **Ficha de Play:** hacen falta icono de 512×512, gráfico destacado de 1024×500
   y al menos dos capturas de pantalla. El icono del launcher está en
   `assets/game/res/mipmap-*`; las capturas hay que tomarlas del juego.
7. **Política de privacidad:** Play la exige por URL incluso sin recogida de datos.

## 5. Publicar una actualización

Sube `versionCode` en `app/build.gradle.kts` (debe ser estrictamente mayor que el
de la versión anterior) y `versionName` si corresponde. Firma con **el mismo**
keystore y vuelve a `bundleRelease`.

## Licencias de los recursos

El arte parte del [Kenney RPG Urban pack](https://kenney.nl) (CC0). Las licencias
de los recursos incluidos están en `assets/game/files/`, y el material fuente y
los créditos en `assets/source/`. Revísalo antes de publicar para que la
atribución de la ficha de Play sea correcta.
