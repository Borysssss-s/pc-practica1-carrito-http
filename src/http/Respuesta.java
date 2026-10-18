package http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Respuesta HTTP/1.1, ya sea construida por el servidor para enviarla o leída por el cliente.
 *
 * Guarda el código y la frase de estado, las cabeceras, las cookies (cabeceras Set-Cookie,
 * que pueden repetirse) y el cuerpo.
 */
public class Respuesta {
	
	/**
	 * Código de estado (200, 404...).
	 */
	private int codigo;
	/**
	 * Frase asociada al código de estado ("OK", "Not Found"...).
	 */
	private String frase;
	/**
	 * Cuerpo de la respuesta (código HTML).
	 */
	private String cuerpo;
	/**
	 * Cabeceras adicionales de la respuesta, sin contar las cookies.
	 */
	private Map<String, String> cabeceras = new LinkedHashMap<>();
	/**
	 * Valores de las cabeceras Set-Cookie.
	 */
	private List<String> cookies = new ArrayList<>();
	
	/**
	 * Crea una respuesta.
	 *
	 * @param codigo código de estado
	 * @param frase frase asociada al código
	 * @param cuerpo cuerpo de la respuesta
	 */
	public Respuesta(int codigo, String frase, String cuerpo) {
		this.codigo = codigo;
		this.frase = frase;
		this.cuerpo = cuerpo;
	}
	
	/**
	 * Crea una respuesta de error con una página HTML mínima.
	 *
	 * @param codigo código de estado de error (400, 404, 405...)
	 * @param frase frase asociada al código
	 * @return la respuesta de error
	 */
	public static Respuesta error(int codigo, String frase) {
		return new Respuesta(codigo, frase, "<html><body><h1>" + codigo + " " + frase + "</h1></body></html>");
	}
	
	/**
	 * Añade una cabecera a la respuesta.
	 *
	 * @param nombre nombre de la cabecera
	 * @param valor valor de la cabecera
	 */
	public void addCabecera(String nombre, String valor) {
		cabeceras.put(nombre, valor);
	}
	
	/**
	 * Añade una cabecera Set-Cookie a la respuesta.
	 *
	 * @param valor contenido de la cabecera (nombre=valor y atributos)
	 */
	public void addSetCookie(String valor) {
		cookies.add(valor);
	}
	
	/**
	 * Escribe la respuesta en un flujo de salida.
	 *
	 * Envía la línea de estado y las cabeceras (Connection: close, Date, Server, Content-Type,
	 * Content-Length con el tamaño del cuerpo en bytes UTF-8, las adicionales y los Set-Cookie),
	 * una línea vacía y el cuerpo.
	 *
	 * @param out flujo en el que se escribe
	 * @throws IOException si falla la escritura
	 */
	public void enviar(OutputStream out) throws IOException {
		
		byte[] body = cuerpo.getBytes(StandardCharsets.UTF_8);
		String fecha = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")));
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("HTTP/1.1 ").append(codigo).append(" ").append(frase).append("\r\n");
		sb.append("Connection: close\r\n");
		sb.append("Date: ").append(fecha).append("\r\n");
		sb.append("Server: ServidorCarrito/1.0\r\n");
		sb.append("Content-Type: text/html; charset=UTF-8\r\n");
		sb.append("Content-Length: ").append(body.length).append("\r\n");
		
		for (var h : cabeceras.entrySet()) {
			sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
		}
		
		for (String c : cookies) {
			sb.append("Set-Cookie: ").append(c).append("\r\n");
		}
		
		sb.append("\r\n");
		
		out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
		out.write(body);
		out.flush();
	}



	/**
	 * Lee una respuesta completa de un flujo de entrada.
	 *
	 * Lee la línea de estado, las cabeceras (las Set-Cookie se guardan aparte) y el cuerpo.
	 * Si hay Content-Length, lee esa longitud; si no, lee hasta el final del flujo. El cuerpo se
	 * decodifica de ISO-8859-1 a UTF-8, porque así se ha leído el flujo.
	 *
	 * @param in flujo del que se lee la respuesta (leído en ISO-8859-1)
	 * @return la respuesta leída
	 * @throws IOException si la respuesta está vacía o la línea de estado, una cabecera o
	 * Content-Length no son válidos
	 */
	public static Respuesta leer(BufferedReader in) throws IOException {
		String linea = in.readLine();
		if (linea == null) throw new IOException("Respuesta vacía");

		String[] partes = linea.split(" ", 3);
		
		if (partes.length < 2 || !partes[0].startsWith("HTTP/"))
			throw new IOException("Línea de estado incorrecta: " + linea);
		int codigo;
		
		try {
			codigo = Integer.parseInt(partes[1]);
		} catch (NumberFormatException e) {
			throw new IOException("Código de estado incorrecto: " + linea);
		}
		
		Respuesta r = new Respuesta(codigo, partes.length > 2 ? partes[2] : "", "");

		int longitud = -1;
		
		while ((linea = in.readLine()) != null && !linea.isEmpty()) {
			int i = linea.indexOf(':');
			
			if (i <= 0) throw new IOException("Cabecera incorrecta: " + linea);
			
			String nombre = linea.substring(0, i).trim();
			String valor = linea.substring(i + 1).trim();
			
			if (nombre.equalsIgnoreCase("Set-Cookie")) {
				r.cookies.add(valor);
				
			} else {
				r.cabeceras.put(nombre, valor);
				
				if (nombre.equalsIgnoreCase("Content-Length")) {
					try {
						longitud = Integer.parseInt(valor);
						
					} catch (NumberFormatException e) {
						throw new IOException("Content-Length incorrecto");
					}
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[4096];
		int pendiente = longitud;
		
		while (pendiente != 0) {
			int n = in.read(buf, 0, pendiente < 0 ? buf.length : Math.min(buf.length, pendiente));
			
			if (n == -1) break;
			
			sb.append(buf, 0, n);
			
			if (pendiente > 0) pendiente -= n;
		}

		r.cuerpo = new String(sb.toString().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
		return r;
	}
	
	/**
	 * @return el código de estado
	 */
	public int getCodigo() {
		return codigo;
	}

	/**
	 * @return la frase asociada al código de estado
	 */
	public String getFrase() {
		return frase;
	}

	/**
	 * @return el cuerpo de la respuesta
	 */
	public String getCuerpo() {
		return cuerpo;
	}

	/**
	 * @return vista de solo lectura de las cabeceras (sin las cookies)
	 */
	public Map<String, String> getCabeceras() {
		return Collections.unmodifiableMap(cabeceras);
	}

	/**
	 * @return vista de solo lectura de los valores de Set-Cookie
	 */
	public List<String> getCookies() {
		return Collections.unmodifiableList(cookies);
	}
}