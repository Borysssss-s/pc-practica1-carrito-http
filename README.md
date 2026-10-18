# Servidor y cliente HTTP/HTTPS con carrito de la compra

Práctica 1 de **Programación para las Comunicaciones**. Un servidor HTTP/HTTPS multihilo y un
cliente de consola, ambos en Java sobre sockets, sin librerías HTTP de alto nivel. El protocolo
HTTP/1.1 (petición, respuesta, cabeceras y cookies) está implementado a mano.

## Qué hace

- **Servidor** que escucha a la vez en el puerto **10000 (HTTP)** y en el **20000 (HTTPS)**, cada uno en su
  propio hilo, y atiende cada conexión en un hilo nuevo (E/S bloqueante, con tiempo de espera de 10 s).
- **HTTPS con autenticación mutua**: TLS 1.2 y certificados X.509 del servidor y del cliente firmados por
  una misma CA propia. El servidor exige certificado de cliente.
- **Carrito sin estado en el servidor**: el contenido viaja en la cookie `carrito` (`id:cantidad|id:cantidad`).
  Como la cookie la controla el cliente, el servidor la valida contra el catálogo en cada petición.
- **Catálogo** de productos cargado desde `src/catalogo.csv` (formato `id;nombre;precio`).
- **Cliente de consola** que muestra la respuesta completa (línea de estado, cabeceras, cookies y HTML).
  También se puede usar el servidor desde un navegador.

### Rutas

| Método | Ruta | Acción |
|---|---|---|
| GET | `/` | Inicio y catálogo |
| GET | `/carrito` | Consultar el carrito |
| GET | `/producto?id=...` | Consultar un producto |
| POST | `/carrito/anadir` | Añadir una unidad (`id=...`) |
| POST | `/carrito/eliminar` | Quitar una unidad (`id=...`) |
| POST | `/carrito/modificar` | Fijar la cantidad (`id=...&cantidad=...`, de 0 a 99) |
| POST | `/carrito/vaciar` | Vaciar el carrito |

Códigos de estado: 200, 400 (cantidad no válida), 404 (ruta o producto desconocido) y 405 (con `Allow`).

### Aportaciones opcionales

Vaciar el carrito, modificar cantidades, límite de 99 unidades por producto, validación de cookies
manipuladas y persistencia de las cookies del cliente en `cookies.txt`.

## Estructura

```
src/
  servidor/   Servidor, ServicioCarrito
  http/       Peticion, Respuesta
  carrito/    Carrito, Catalogo, Producto
  cliente/    Cliente
  catalogo.csv
cert/         certificados (ver más abajo)
doc/          memoria técnica
```

## Requisitos

- JDK 11 o superior
- `openssl` y `keytool` (para generar los certificados)

## Compilar y ejecutar

Todo se ejecuta **desde la raíz del repositorio** (la carpeta que contiene `src/` y `cert/`), porque el
código usa rutas relativas.

Compilar (PowerShell):

```powershell
javac -d bin (Get-ChildItem -Recurse src -Filter *.java).FullName
```

Compilar (Linux/macOS/Git Bash):

```bash
javac -d bin $(find src -name "*.java")
```

Ejecutar, el servidor primero y el cliente en otra terminal:

```
java -cp bin servidor.Servidor
java -cp bin cliente.Cliente
```

En el menú del cliente, la opción **6** alterna entre HTTP y HTTPS. Desde un navegador:
`http://localhost:10000/` y `https://localhost:20000/` (este último necesita el certificado de cliente
instalado y la CA como autoridad de confianza).

## Certificados

Por seguridad, **este repositorio no incluye claves privadas ni los almacenes `.pfx`**. Solo se
publica el certificado de la CA (`cert/cacert.pem`). Para ejecutar HTTPS hay que generar los certificados
y dejarlos en `cert/`, con estos nombres:

| Fichero | Contenido |
|---|---|
| `cacert.pem` | Certificado de la CA (CN=ca, OU=ppc, O=umu, C=es) |
| `server.pfx` | Certificado y clave del servidor (CN=localhost, con SAN `localhost` y `127.0.0.1`) |
| `cliente.pfx` | Certificado y clave del cliente (CN = nombre del alumno) |

Pasos generales:

1. Crear la CA con `openssl req -x509` (clave y certificado autofirmado).
2. Crear cada par de claves en un almacén PKCS12 con `keytool -genkey` y generar la petición con
   `keytool -certreq`.
3. Firmar cada petición con la CA usando `openssl ca` (con las extensiones del servidor, que incluyen el SAN,
   y las del cliente, con `clientAuth`).
4. Importar en cada almacén primero la CA (`keytool -import -trustcacerts`) y después el certificado firmado.

Las contraseñas de los almacenes son constantes del código (`Servidor.java` y `Cliente.java`) y son
de práctica.

## Autoría

Práctica realizada con apoyo de herramientas de IA (Claude) para revisión de código, depuración, guía en
los comandos de certificados, y borradores de la memoria y de los diagramas. Detalle completo en la
memoria técnica.