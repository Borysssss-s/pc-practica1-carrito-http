package servidor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import carrito.Catalogo;
import http.Peticion;
import http.Respuesta;

// Servidor principal

/**
 * Servidor principal de la práctica: atiende peticiones HTTP y HTTPS del carrito de la compra.
 *
 * Escucha en dos puertos a la vez, cada uno en su propio hilo: HTTP (sin cifrar) y HTTPS
 * (TLS 1.2 con autenticación mutua). Cada conexión aceptada se atiende en un hilo nuevo,
 * con E/S bloqueante. El servidor no guarda estado entre peticiones: el carrito viaja en una cookie.
 */
public class Servidor {
	
	/**
	 * Si es true, se muestra por pantalla cada petición recibida.
	 */
	static public final boolean DEBUG = true;

	/**
	 * Puerto de escucha para HTTP.
	 */
	static public final int	PUERTO_HTTP	= 10000;
	/**
	 * Puerto de escucha para HTTPS.
	 */
	static public final int	PUERTO_HTTPS = 20000;

	/**
	 * Fichero con el catálogo de productos.
	 */
	static public final String CATALOGO	= "src/catalogo.csv";
	/**
	 * Almacén PKCS12 con el certificado y la clave privada del servidor.
	 */
	static public final String SV_STORE	= "cert/server.pfx";
	/**
	 * Contraseña del almacén del servidor.
	 */
	static public final String SV_PWD = "password";
	/**
	 * Contraseña de la clave privada del servidor dentro del almacén.
	 */
	static public final String SV_CERT_PWD = "password";
	/**
	 * Certificado de la CA en la que confía el servidor para validar a los clientes.
	 */
	static public final String CA_CERT = "cert/cacert.pem";

	/**
	 * Servicio que atiende las peticiones. Se crea en {@link #main(String[])} y lo comparten todos los hilos.
	 */
	static private ServicioCarrito	servicio;
	
	/**
	 * Arranca el servidor HTTP: abre un socket en el puerto indicado y acepta conexiones
	 * indefinidamente (esta llamada no termina mientras el servidor esté activo).
	 *
	 * @param port puerto en el que se escucha
	 */
	static public void instanceServerHttp (int port) {
		ServerSocket	s;

		try {
			s = new ServerSocket(port);
			System.out.println("Escuchando HTTP en el puerto " + port + "...");
			aceptar(s);

		} catch (Exception e) { 
			e.printStackTrace(); 
		}
	}
	
	/**
	 * Arranca el servidor HTTPS con autenticación mutua.
	 *
	 * Crea el contexto SSL (TLSv1.2): carga de {@link #SV_STORE} el certificado y la clave del
	 * servidor, y de {@link #CA_CERT} la CA en la que confía. Exige certificado de cliente
	 * ({@code setNeedClientAuth(true)}), de forma que solo se atiende a clientes con un certificado
	 * firmado por esa CA. Después acepta conexiones indefinidamente.
	 *
	 * @param port puerto en el que se escucha
	 */
	static public void instanceServerHttps (int port) {
		SSLContext				ctx;
		SSLServerSocketFactory	fac;
		SSLServerSocket			s;
		KeyStore				ksm, kst;
		KeyManagerFactory		kmf;
		TrustManagerFactory		tmf;

		try {
			ksm = KeyStore.getInstance("PKCS12");
			
			try (FileInputStream f = new FileInputStream(SV_STORE)) {
				ksm.load(f, SV_PWD.toCharArray());
			}
			
			kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ksm, SV_CERT_PWD.toCharArray());

			kst = KeyStore.getInstance("PKCS12");
			kst.load(null, null);
			
			try (FileInputStream f = new FileInputStream(CA_CERT)) {
				Certificate ca = CertificateFactory.getInstance("X.509").generateCertificate(f);
				kst.setCertificateEntry("CA", ca);
			}
			
			tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(kst);

			ctx = SSLContext.getInstance("TLSv1.2");
			ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			fac = ctx.getServerSocketFactory();
			s = (SSLServerSocket) fac.createServerSocket(port);
			s.setNeedClientAuth(true);

			System.out.println("Escuchando HTTPS en el puerto " + port + "...");
			aceptar(s);

		} catch (Exception e) { 
			e.printStackTrace(); 
		}
	}

	/**
	 * Acepta conexiones de un socket de servidor mientras esté abierto.
	 *
	 * Por cada conexión crea un hilo nuevo que la atiende con {@link #serverHttp(Socket)}.
	 *
	 * @param s socket de servidor (HTTP o HTTPS) en el que se aceptan las conexiones
	 */
	static protected void aceptar (ServerSocket s) {
		while (!s.isClosed()) {
			try {
				final Socket s1 = s.accept();
				new Thread(() -> serverHttp (s1)).start();

			} catch (IOException e) {
				if (!s.isClosed())
					System.err.println("Error en accept: " + e.getMessage());
			}
		}
	}
	
	/**
	 * Atiende una conexión: lee la petición, genera la respuesta y la envía.
	 *
	 * El socket tiene un tiempo de espera de 10 segundos. Si la petición es incorrecta se
	 * responde 400 (Bad Request). Si la conexión falla, se muestra el error por la salida de
	 * errores. En todos los casos el socket se cierra al terminar.
	 *
	 * @param s socket de la conexión aceptada
	 */
	static protected void serverHttp (Socket s) {
		BufferedReader	is;
		OutputStream	os;
		Respuesta		resp;

		try {
			s.setSoTimeout(10000);
			is = new BufferedReader(new InputStreamReader (s.getInputStream(), StandardCharsets.ISO_8859_1));
			os = s.getOutputStream();

			try {
				Peticion p = Peticion.leer(is);
				
				if (p == null) return;
				
				if (DEBUG) System.out.println ("SERVER RECEIVED <" + p.getMetodo() + " " + p.getId() + ">");
				
				resp = servicio.atender(p);
				
			} catch (IllegalArgumentException e) {
				resp = Respuesta.error (400, "Bad Request");
			}
			
			resp.enviar(os);

		} catch (Exception e) {
			System.err.println ("Conexion finalizada con error: " + e.getMessage());
			
		} finally {
			try { 
				s.close(); 
				
			} catch (IOException e) { }
		}
	}
	
	/**
	 * Punto de entrada del servidor.
	 *
	 * Carga el catálogo y lanza los dos hilos de escucha: uno para HTTP y otro para HTTPS.
	 *
	 * @param args no se utiliza
	 * @throws Exception si no se puede cargar el catálogo
	 */
	public static void main(String[] args) throws Exception {
		
		servicio = new ServicioCarrito(new Catalogo (CATALOGO));

		new Thread(() -> instanceServerHttp (PUERTO_HTTP)).start();
		new Thread(() -> instanceServerHttps (PUERTO_HTTPS)).start();
			
		
	}
	
}