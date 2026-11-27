# El juego no usa reflexión: los drawables se resuelven con referencias R
# explícitas (ver CraftArt.kt) y el JSON de la campaña se parsea a mano con
# org.json, así que no hace falta conservar modelos por nombre.
# Compose, AndroidX y Lifecycle traen sus propias reglas consumer.

# Conserva los nombres de archivo y línea en los stack traces de producción.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
