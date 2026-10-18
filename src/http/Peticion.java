package http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Petición HTTP/1.1, ya sea recibida por el servidor o construida por el cliente.
 *
 * Guarda la línea de petición (método, destino y versión), las cabeceras (con el nombre en
 * minúsculas) y el cuerpo. Incluye lo necesario para leer una petición de un flujo, extraer
 * sus cookies y parámetros, construir una nueva y enviarla.
 */
public class Peticion {
	
	/**
	 * Método HTTP (GET, POST...).
	 */
	private String metodo;
	/**
	 * Destino de la petición: la ruta y, si la hay, la cadena de consulta (por ejemplo "/producto?id=pc").
	 */
	private String id;
	/**
	 * Versión del protocolo (por ejemplo "HTTP/1.1").
	 */
	private String version;
	/**
	 * Cabeceras de la petición, con el nombre en minúsculas.
	 */
	private Map<String, String> cabeceras = new LinkedHashMap<>();
	/**
	 * Cuerpo de la petición (vacío si no lleva).
	 */
	private String cuerpo = "";
	/**
	 * Parámetros de la consulta y del cuerpo, ya decodificados. Se calculan la primera vez que se piden.
	 */
	private Map<String, String> parametros;
	
	/**
	 * Tamaño máximo admitido para el cuerpo, en caracteres.
	 */
	private static final int MAX_CUERPO = 65536;
	
	/**
	 * Lee una petición completa de un flujo de entrada.
	 *
	 * Lee la línea de petición, las cabeceras hasta la línea vacía y, si hay Content-Length,
	 * el cuerpo con exactamente esa longitud.
	 *
	 * @param in flujo del que se lee la petición
	 * @return la petición leída, o null si el flujo está cerrado y no hay datos
	 * @throws IOException si falla la lectura
	 * @throws IllegalArgumentException si la petición no es válida: línea de petición o cabecera
	 * incorrecta, Content-Length no numérico, cuerpo demasiado grande o incompleto
	 */
	public static Peticion leer(BufferedReader in) throws IOException {
		String linea = in.readLine();
		if (linea == null) return null;
		
		String[] partes = linea.split(" ");
		if (partes.length != 3 || !partes[2].startsWith("HTTP/")) {
			throw new IllegalArgumentException("Línea de petición incorrecta");
		}
		
		Peticion p = new Peticion();
		p.metodo = partes[0];
		p.id = partes[1];
		p.version = partes[2];
		
		while ((linea = in.readLine()) != null && !linea.isEmpty()) {
			int i = linea.indexOf(":");
			if (i <= 0)
				throw new IllegalArgumentException("Cabecera incorrecta");
			
			p.cabeceras.put(linea.substring(0,i).trim().toLowerCase(), linea.substring(i + 1).trim());
		}
		
		String cl = p.cabeceras.get("content-length");
		if (cl != null) {
			int n;
			try {
				n = Integer.parseInt(cl);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Content-Length");
			}
			
			if (n < 0 || n > MAX_CUERPO) {
				throw new IllegalArgumentException("Cuerpo demasiado grande");
			}
			
			char[] buf = new char[n];
			int leidos = 0;
			
			while (leidos < n) {
				int r = in.read(buf, leidos, n - leidos);
				if (r == -1) {
					throw new IllegalArgumentException("Cuerpo incompleto");
				}
				leidos += r;
			}
			
			p.cuerpo = new String(buf);
			
		}
		
		return p;
	}	
	
	/**
	 * Obtiene el valor de una cookie de la cabecera Cookie.
	 *
	 * @param nombre nombre de la cookie
	 * @return su valor, o null si la cabecera no existe o no contiene esa cookie
	 */
	public String getCookie(String nombre) {
		String c = cabeceras.get("cookie");
		if (c == null) return null;
		
		for (String par : c.split(";")) {
			par = par.trim();
			int i = par.indexOf("=");
			
			if (i > 0 && par.substring(0, i).equals(nombre)) 
				return par.substring( i + 1);
		}
		
		return null;
	}
	
	/**
	 * Obtiene la ruta del recurso, sin la cadena de consulta.
	 *
	 * @return el destino de la petición hasta el carácter "?" (o completo si no lo hay)
	 */
	public String getRuta() {
		int i = id.indexOf("?");
		return i < 0 ? id : id.substring(0, i);
	}
	
	/**
	 * Obtiene un parámetro de la petición.
	 *
	 * Se buscan en la cadena de consulta y, si el método es POST, también en el cuerpo
	 * (formato application/x-www-form-urlencoded).
	 *
	 * @param nombre nombre del parámetro
	 * @return su valor decodificado, o null si no existe
	 */
	public String getParametro(String nombre) {
		if (parametros == null) {
			parametros = new HashMap<>();
			int i = id.indexOf("?");
			
			if (i >= 0) {
				parsear(id.substring(i + 1));
			}
			
			if (metodo.equals("POST")) {
				parsear(cuerpo);
			}
		}
		return parametros.get(nombre);
	}
	
	/**
	 * Analiza una cadena "clave=valor&clave=valor" y añade los parámetros decodificados
	 * al conjunto de parámetros de la petición.
	 *
	 * @param s cadena con los parámetros codificados
	 */
	public void parsear(String s) {
		
		for (String par : s.split("&")) {
			if (par.isEmpty()) continue;
			
			int j = par.indexOf("=");
			
			String k = j < 0 ? par : par.substring(0,j);
			String v = j < 0 ? "" : par.substring(j + 1);
			
			parametros.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
					       URLDecoder.decode(v, StandardCharsets.UTF_8));
		}
	}

	/**
	 * @return el método HTTP
	 */
	public String getMetodo() {
		return metodo;
	}

	/**
	 * @return el destino de la petición (ruta y cadena de consulta)
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return la versión del protocolo
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @return vista de solo lectura de las cabeceras, con el nombre en minúsculas
	 */
	public Map<String, String> getCabeceras() {
		return Collections.unmodifiableMap(cabeceras);
	}

	/**
	 * @return el cuerpo de la petición (vacío si no lleva)
	 */
	public String getCuerpo() {
		return cuerpo;
	}

	/**
	 * @return vista de solo lectura de todos los parámetros de la petición
	 */
	public Map<String, String> getParametros() {
		getParametro("");
		return Collections.unmodifiableMap(parametros);
	}

	/**
	 * Construye una petición HTTP/1.1 nueva, con Connection: close.
	 *
	 * Añade las cabeceras Host y, si procede, Cookie. Si hay cuerpo, añade también Content-Type
	 * (application/x-www-form-urlencoded) y Content-Length. En un POST sin cuerpo, Content-Length: 0.
	 *
	 * @param metodo método HTTP (GET o POST)
	 * @param ruta destino de la petición, con su cadena de consulta si la tiene
	 * @param host valor de la cabecera Host (nombre y puerto)
	 * @param cookie valor de la cabecera Cookie, o null si no hay cookies
	 * @param cuerpo cuerpo de la petición, o null si no lleva
	 * @return la petición construida
	 */
	public static Peticion nueva(String metodo, String ruta, String host, String cookie, String cuerpo) {
		Peticion p = new Peticion();
		
		p.metodo = metodo;
		p.id = ruta;
		p.version = "HTTP/1.1";
		p.cabeceras.put("host", host);
		p.cabeceras.put("connection", "close");
		
		if (cookie != null && !cookie.isEmpty())
			p.cabeceras.put("cookie", cookie);
		
		if (cuerpo != null && !cuerpo.isEmpty()) {
			p.cuerpo = cuerpo;
			p.cabeceras.put("content-type", "application/x-www-form-urlencoded");
			p.cabeceras.put("content-length",
					String.valueOf(cuerpo.getBytes(StandardCharsets.ISO_8859_1).length));
		
		} else if (metodo.equals("POST")) {
			p.cabeceras.put("content-length", "0");
		}
		
		return p;
	}

	/**
	 * Escribe la petición en un flujo de salida: línea de petición, cabeceras, línea vacía y cuerpo,
	 * con saltos de línea CRLF.
	 *
	 * @param out flujo en el que se escribe
	 * @throws IOException si falla la escritura
	 */
	public void enviar(OutputStream out) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(metodo).append(' ').append(id).append(' ').append(version).append("\r\n");
		
		for (Map.Entry<String, String> h : cabeceras.entrySet())
			sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
		
		sb.append("\r\n");
		sb.append(cuerpo);
		out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
		out.flush();
	}
}