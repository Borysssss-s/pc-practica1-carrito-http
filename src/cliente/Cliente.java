package cliente;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import http.Peticion;
import http.Respuesta;

/**
 * Cliente de consola del carrito de la compra.
 *
 * Se conecta al servidor por HTTP (puerto 10000) o por HTTPS (puerto 20000, con
 * autenticación mutua mediante certificados X.509) y envía una petición por cada
 * operación elegida en el menú. Para cada respuesta muestra por pantalla la línea de
 * estado, todas las cabeceras recibidas, las cookies y el código HTML.
 *
 * El carrito no se guarda en el servidor: viaja en la cookie "carrito", que el cliente
 * almacena, reenvía en cada petición y persiste en un fichero entre ejecuciones.
 */
public class Cliente {
	
	/**
	 * Si es true, se muestra por pantalla cada petición que envía el cliente.
	 */
	static public final boolean DEBUG = true;
	
	/**
	 * Nombre del servidor al que se conecta el cliente.
	 */
	static public final String HOST = "localhost";
	/**
	 * Puerto en el que escucha el servidor para HTTP.
	 */
	static public final int PUERTO_HTTP = 10000;
	/**
	 * Puerto en el que escucha el servidor para HTTPS.
	 */
	static public final int PUERTO_HTTPS = 20000;
	
	/**
	 * Almacén PKCS12 con el certificado y la clave privada del cliente.
	 */
	static public final String KSTORE = "cert/cliente.pfx";
	/**
	 * Contraseña del almacén del cliente.
	 */
	static public final String KS_PWD = "client";
	/**
	 * Contraseña de la clave privada del cliente dentro del almacén.
	 */
	static public final String CERT_PWD = "client";
	/**
	 * Certificado de la CA en la que confía el cliente para validar al servidor.
	 */
	static public final String CA_CERT = "cert/cacert.pem";
	
	/**
	 * Fichero donde se guardan las cookies para conservarlas entre ejecuciones.
	 */
	static public final String COOKIES_FILE = "cookies.txt";
	
	/**
	 * Cookies almacenadas por el cliente (nombre y valor), que se reenvían al servidor.
	 */
	static private final Map<String, String> cookies = new LinkedHashMap<>();
	/**
	 * Contexto SSL, creado la primera vez que se usa HTTPS y reutilizado después.
	 */
	static private SSLContext ctx;
	/**
	 * Indica si las peticiones se hacen por HTTPS (true) o por HTTP (false).
	 */
	static private boolean seguro = false;
	
	/**
	 * Crea un socket TCP normal, sin cifrar, para HTTP.
	 *
	 * @param address nombre o dirección del servidor
	 * @param port puerto del servidor
	 * @return el socket conectado
	 * @throws Exception si no se puede establecer la conexión
	 */
	static protected Socket instanceClientHttp (String address, int port) throws Exception {
		
		return new Socket(address, port);
	}
	
	/**
	 * Crea un socket SSL para HTTPS con autenticación mutua.
	 *
	 * La primera vez construye el contexto SSL (TLSv1.2): carga el certificado y la clave
	 * del cliente desde {@link #KSTORE} para presentarse al servidor, y el certificado de
	 * la CA desde {@link #CA_CERT} para validar el certificado del servidor.
	 *
	 * @param address nombre o dirección del servidor
	 * @param port puerto del servidor
	 * @return el socket SSL conectado
	 * @throws Exception si fallan la lectura de los almacenes o la conexión
	 */
	static protected Socket instanceClientHttps (String address, int port) throws Exception {
		
		SSLSocketFactory fac;
		KeyStore ks, kst;
		KeyManagerFactory kmf;
		TrustManagerFactory tmf;
		
		if (ctx == null) {
			ks = KeyStore.getInstance("PKCS12");
			
			try (FileInputStream f = new FileInputStream (KSTORE)) {
				ks.load(f, KS_PWD.toCharArray());
			}
			
			kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, CERT_PWD.toCharArray());
			
			kst = KeyStore.getInstance("PKCS12");
			kst.load(null, null);
			
			try (FileInputStream f = new FileInputStream (CA_CERT)) {
				kst.setCertificateEntry("CA", CertificateFactory.getInstance("X.509").generateCertificate(f));
				
			}
			
			tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(kst);
			
			ctx = SSLContext.getInstance("TLSv1.2");
			ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		}
		
		fac = ctx.getSocketFactory();
		return fac.createSocket(address, port);
	}
	
	/**
	 * Envía una petición por un socket ya conectado y muestra la respuesta.
	 *
	 * Construye la petición con las cookies actuales, la envía, lee la respuesta, guarda
	 * las cookies que reciba, la muestra por pantalla y cierra el socket. El socket tiene
	 * un tiempo de espera de 10 segundos.
	 *
	 * @param s socket ya conectado al servidor (HTTP o HTTPS)
	 * @param metodo método HTTP (GET o POST)
	 * @param ruta ruta del recurso, con su cadena de consulta si la tiene
	 * @param cuerpo cuerpo de la petición, o null si no lleva
	 */
	static protected void clientHttp (Socket s, String metodo, String ruta, String cuerpo) {
		BufferedReader	is;
		Peticion		p;
		Respuesta		r;

		try {
			s.setSoTimeout (10000);
			p = Peticion.nueva(metodo, ruta, HOST + ":" + s.getPort(), cabeceraCookie(), cuerpo);
			
			if (DEBUG) System.out.println("CLIENT SENDING <" + metodo + " " + ruta + ">");
			
			p.enviar(s.getOutputStream());

			is = new BufferedReader(new InputStreamReader (s.getInputStream(), StandardCharsets.ISO_8859_1));
			r = Respuesta.leer(is);
			guardarCookies(r);
			mostrar(r);
			
			s.close();

		} catch (Exception e) { 
			e.printStackTrace(); 
		}
	}
	
	/**
	 * Realiza una petición al servidor usando HTTP o HTTPS según el modo actual.
	 *
	 * @param metodo método HTTP (GET o POST)
	 * @param ruta ruta del recurso, con su cadena de consulta si la tiene
	 * @param cuerpo cuerpo de la petición, o null si no lleva
	 */
	static protected void peticion (String metodo, String ruta, String cuerpo) {
		Socket s;

		try {
			s = seguro ? instanceClientHttps(HOST, PUERTO_HTTPS)
			           : instanceClientHttp(HOST, PUERTO_HTTP);
			clientHttp(s, metodo, ruta, cuerpo);

		} catch (Exception e) { 
			e.printStackTrace(); 
		}
	}	

	/**
	 * Construye el valor de la cabecera Cookie con todas las cookies almacenadas.
	 *
	 * @return las cookies con formato "nombre=valor; nombre=valor", o null si no hay ninguna
	 */
	static private String cabeceraCookie () {
		if (cookies.isEmpty()) return null;
		
		StringBuilder sb = new StringBuilder();
		
		for (Map.Entry<String, String> e : cookies.entrySet()) {
			if (sb.length() > 0) sb.append("; ");
			
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		
		return sb.toString();
	}
	
	
	/**
	 * Actualiza las cookies del cliente con las cabeceras Set-Cookie de una respuesta.
	 *
	 * Solo se usa el par nombre=valor. Si el valor está vacío, la cookie se elimina (así
	 * el servidor indica que debe borrarse). Si la respuesta traía cookies, se guarda
	 * el estado en el fichero.
	 *
	 * @param r respuesta recibida del servidor
	 */
	static private void guardarCookies (Respuesta r) {
		for (String c : r.getCookies()) {
			String par = c.split (";")[0].trim();
			int i = par.indexOf('=');
		
			if (i > 0) {
				String nombre = par.substring(0, i);
				String valor = par.substring(i + 1);
				
				if (valor.isEmpty())
					cookies.remove(nombre);
				else cookies.put(nombre, valor);
			}
		}
		
		if (!r.getCookies().isEmpty())
			persistirCookies();
	}
	
	
	/**
	 * Muestra por pantalla la respuesta completa: línea de estado, cabeceras, cookies y
	 * código HTML. Se añade un salto de línea entre etiquetas solo para facilitar la
	 * lectura; el cuerpo recibido no se modifica.
	 *
	 * @param r respuesta recibida del servidor
	 */
	static private void mostrar (Respuesta r) {
		System.out.println("\n<<< HTTP/1.1 " + r.getCodigo() + " " + r.getFrase());

		for (Map.Entry<String, String> h : r.getCabeceras().entrySet())
			System.out.println(h.getKey() + ": " + h.getValue());

		for (String c : r.getCookies())
			System.out.println("Set-Cookie: " + c);

		System.out.println();
		System.out.println(r.getCuerpo().replace("><",">\n<"));
	}
		

	
	/**
	 * Pide por teclado el identificador de un producto.
	 *
	 * @param sc lector de la entrada estándar
	 * @return el identificador codificado para poder enviarse en la URL o en el cuerpo
	 */
	static private String pedirId (Scanner sc) {
		System.out.print("Id del producto: ");
		
		return URLEncoder.encode(sc.nextLine().trim(), StandardCharsets.UTF_8);
	}
	
	/**
	 * Carga las cookies guardadas en {@link #COOKIES_FILE}, si el fichero existe.
	 * Cada línea tiene el formato "nombre=valor".
	 */
	static private void cargarCookies () {
		Path f = Paths.get(COOKIES_FILE);
		if (!Files.exists(f)) return;
		
		try {
			for (String linea : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				int i = linea.indexOf('=');
				if (i > 0) cookies.put(linea.substring(0, i), linea.substring(i + 1));
			}
		} catch (IOException e) {
			System.err.println("No se pudieron leer las cookies: " + e.getMessage());
		}
	}
	
	/**
	 * Guarda en {@link #COOKIES_FILE} las cookies actuales, una por línea.
	 */
	static private void persistirCookies () {
		List<String> lineas = new ArrayList<>();
		
		for (Map.Entry<String, String> e : cookies.entrySet())
			lineas.add(e.getKey() + "=" + e.getValue());
		
		try {
			Files.write(Paths.get(COOKIES_FILE), lineas, StandardCharsets.UTF_8);
		
		} catch (IOException e) {
			System.err.println("No se pudieron guardar las cookies: " + e.getMessage());
		}
	}
	
	/**
	 * Punto de entrada del cliente.
	 *
	 * Carga las cookies guardadas y muestra el menú en bucle hasta que se elige salir. Cada
	 * opción realiza una petición al servidor; la opción 6 alterna entre HTTP y HTTPS.
	 *
	 * @param args no se utiliza
	 */
	public static void main(String[] args) {
		
		Scanner sc = new Scanner (System.in);
		
		cargarCookies();
		
		while (true) {
			System.out.println("\n=== Carrito (" + (seguro ? "HTTPS" : "HTTP") + ") ===");
			System.out.println("1. Inicio (catálogo)");
			System.out.println("2. Consultar productos");
			System.out.println("3. Consultar carrito");
			System.out.println("4. Añadir producto");
			System.out.println("5. Eliminar producto");
			System.out.println("6. Cambiar entre HTTP y HTTPS");
			System.out.println("7. Vaciar carrito");
			System.out.println("8. Modificar cantidad");
			System.out.println("0. Salir");
			System.out.print("Opción: ");
			
			switch (sc.nextLine().trim()) {
			
			case "1": peticion("GET", "/", null); break;
			case "2": peticion("GET", "/producto?id=" + pedirId(sc), null); break;
			case "3": peticion("GET", "/carrito", null); break;
			case "4": peticion("POST", "/carrito/anadir", "id=" + pedirId(sc)); break;
			case "5": peticion("POST", "/carrito/eliminar", "id=" + pedirId(sc)); break;
			case "6": seguro = !seguro; break;
			case "7": peticion ("POST", "/carrito/vaciar", null); break;
			case "8": {
				String id = pedirId(sc);
				System.out.print("Nueva cantidad (0 para quitarlo): ");
				String cantidad = URLEncoder.encode(sc.nextLine().trim(), StandardCharsets.UTF_8);
				peticion("POST", "/carrito/modificar", "id=" + id + "&cantidad=" + cantidad);
				break;
			}
			case "0": return;
			default: System.out.println("Opción no válida");
			}
			
		}
	}
	
	
	
}