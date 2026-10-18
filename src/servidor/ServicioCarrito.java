package servidor;

import java.util.Locale;
import java.util.Map;

import carrito.Carrito;
import carrito.Catalogo;
import carrito.Producto;
import http.Peticion;
import http.Respuesta;

/**
 * Lógica de la aplicación del carrito de la compra.
 *
 * Recibe una petición HTTP y devuelve la respuesta correspondiente. Es independiente del tipo
 * de conexión (HTTP o HTTPS). No guarda estado: el carrito se reconstruye en cada petición a
 * partir de la cookie "carrito" y se devuelve actualizado en la respuesta.
 *
 * Rutas atendidas:
 * <ul>
 * <li>GET /: inicio y catálogo</li>
 * <li>GET /carrito: consultar el carrito</li>
 * <li>GET /producto?id=...: consultar un producto</li>
 * <li>POST /carrito/anadir: añadir una unidad de un producto</li>
 * <li>POST /carrito/eliminar: quitar una unidad de un producto</li>
 * <li>POST /carrito/modificar: fijar la cantidad de un producto (de 0 a el máximo permitido)</li>
 * <li>POST /carrito/vaciar: vaciar el carrito</li>
 * </ul>
 */
public class ServicioCarrito {
	
	/**
	 * Catálogo de productos con el que se valida cada identificador.
	 */
	private final Catalogo catalogo;
	
	/**
	 * Crea el servicio.
	 *
	 * @param catalogo catálogo de productos disponibles
	 */
	public ServicioCarrito(Catalogo catalogo) {
		this.catalogo = catalogo;
	}
	
	/**
	 * Atiende una petición y genera la respuesta.
	 *
	 * Reconstruye el carrito desde la cookie, aplica la operación que corresponde a la ruta y
	 * devuelve una página HTML con el resultado. Si cambia el carrito, la respuesta incluye la
	 * cookie actualizada; al vaciarlo, una cookie caducada para que el cliente la borre.
	 *
	 * Respuestas de error: 404 si la ruta o el producto no existen, 405 (con la cabecera Allow) si
	 * el método no está permitido para esa ruta, y 400 si la cantidad de "modificar" no es válida.
	 *
	 * @param p petición recibida
	 * @return la respuesta que se envía al cliente
	 */
	public Respuesta atender(Peticion p) {
		String ruta = p.getRuta();
		String metodo = p.getMetodo();
		
		Carrito carrito = Carrito.desdeCookie(p.getCookie("carrito"), catalogo);
		String operacion;
		boolean vaciar = false;
		Producto ultimo = null;
		
		if (ruta.equals("/")) {
			if (!metodo.equals("GET")) return noPermitido("GET");
			
			operacion = "Inicio";
		
		} else if (ruta.equals("/carrito")) {
			if (!metodo.equals("GET")) return noPermitido("GET");
			
			operacion = "Consultar carrito";
		
		} else if (ruta.equals("/producto")) {
			if (!metodo.equals("GET")) return noPermitido("GET");
			
			ultimo = producto(p);
			if (ultimo == null) return Respuesta.error(404, "Not Found");
			operacion = "Consultar producto";
		
		} else if (ruta.equals("/carrito/anadir")) {
			if (!metodo.equals("POST")) return noPermitido("POST");
			
			ultimo = producto(p);
			if (ultimo == null) return Respuesta.error(404, "Not Found");
			boolean anadido = carrito.anadir(ultimo.getId());
			operacion = anadido ? "Añadir producto"
					            : "Añadir producto (máximo de " + Carrito.MAX_CANTIDAD + " unidades alcanzado)";
			
		} else if (ruta.equals("/carrito/eliminar")) {
			if (!metodo.equals("POST")) return noPermitido("POST");
			
			ultimo = producto(p);
			if (ultimo == null) return Respuesta.error(404, "Not Found");
			boolean quitado = carrito.eliminar(ultimo.getId());
			operacion = quitado ? "Eliminar producto" : "Eliminar producto (no estaba en el carrito)";
		
		} else if (ruta.equals("/carrito/vaciar")) {
			if (!metodo.equals("POST")) return noPermitido("POST");
			
			carrito = new Carrito();
			operacion = "Vaciar carrito";
			vaciar = true;
			
		} else if (ruta.equals("/carrito/modificar")) {
			if (!metodo.equals("POST")) return noPermitido("POST");
			
			ultimo = producto(p);
			if (ultimo == null) return Respuesta.error(404, "Not Found");
			
			// La cantidad llega de fuera: se valida antes de usarla
			int cantidad;
			try {
				cantidad = Integer.parseInt(p.getParametro("cantidad"));
				
			} catch (NumberFormatException e) {
				return Respuesta.error(400, "Bad Request");
			}
			
			if (cantidad < 0 || cantidad > Carrito.MAX_CANTIDAD) 
				return Respuesta.error(400, "Bad Request");
			
			if (!carrito.getItems().containsKey(ultimo.getId())) {
				operacion = "Modificar cantidad (no estaba en el carrito)";
			
			} else {
				carrito.fijar(ultimo.getId(), cantidad);
				operacion = "Modificar cantidad";
			}
			
		} else {
			return Respuesta.error(404, "Not Found");
		}
		
		Respuesta r = new Respuesta(200, "OK", pagina(operacion, ultimo, carrito));
		
		if (vaciar) r.addSetCookie("carrito=; Path=/; Expires=Thu, 01-Jan-1970 00:00:00 GMT");
		else if (carrito.total() > 0 || p.getCookie("carrito") != null)
			r.addSetCookie("carrito=" + carrito.aCookie() + "; Path=/");
		
		return r;
	}
	
	/**
	 * Obtiene el producto indicado en el parámetro "id" de la petición.
	 *
	 * @param p petición recibida
	 * @return el producto, o null si falta el parámetro o no está en el catálogo
	 */
	private Producto producto(Peticion p) {
		String id = p.getParametro("id");
		
		return id == null ? null : catalogo.buscar(id);
	}
	
	/**
	 * Crea una respuesta 405 (Method Not Allowed) con la cabecera Allow.
	 *
	 * @param permitido método que sí admite la ruta
	 * @return la respuesta de error
	 */
	private static Respuesta noPermitido(String permitido) {
		Respuesta r = Respuesta.error(405, "Method Not Allowed");
		r.addCabecera("Allow", permitido);
		
		return r;
	}
	
	/**
	 * Genera la página HTML de respuesta.
	 *
	 * Muestra la operación realizada, el último producto consultado, el contenido del carrito
	 * (con un formulario para modificar cada cantidad), el botón de vaciar y el catálogo con
	 * botones para añadir y eliminar, de modo que se pueda usar desde un navegador.
	 *
	 * @param operacion descripción de la operación realizada
	 * @param ultimo último producto consultado, o null si no hay
	 * @param carrito carrito actual
	 * @return el código HTML de la página
	 */
	private String pagina(String operacion, Producto ultimo, Carrito carrito) {
		StringBuilder h = new StringBuilder();
		h.append("<html><head><meta charset=\"UTF-8\"><title>Carrito</title></head><body>");
		h.append("<h1>Carrito de la compra</h1>");
		h.append("<p><b>Operación:</b> ").append(esc(operacion)).append("</p>");
		h.append("<p><b>Último producto:</b> ").append(ultimo == null ? "ninguno" : esc(ultimo.getNombre())).append("</p>");
		h.append("<p><b>Productos en el carrito:</b> ").append(carrito.total()).append("</p>");

		h.append("<ul>");

		for (Map.Entry<String, Integer> e : carrito.getItems().entrySet()) {
			Producto pr = catalogo.buscar(e.getKey());
			h.append("<li>").append(esc(pr.getNombre())).append(" x").append(e.getValue())
			 .append(" <form method=\"POST\" action=\"/carrito/modificar\" style=\"display:inline\">")
			 .append("<input type=\"hidden\" name=\"id\" value=\"").append(pr.getId()).append("\">")
			 .append("<input type=\"number\" name=\"cantidad\" min=\"0\" max=\"").append(Carrito.MAX_CANTIDAD)
			 .append("\" value=\"").append(e.getValue()).append("\"> ")
			 .append("<button type=\"submit\">Modificar</button></form></li>");
		}
		h.append("</ul>");

		// Botón para vaciar el carrito, debajo de la lista
		h.append("<div><form method=\"POST\" action=\"/carrito/vaciar\">"
			   + "<button type=\"submit\">Vaciar carrito</button></form></div>");

		// Catálogo con botones, para validar desde el navegador
		h.append("<h2>Catálogo</h2><ul>");

		for (Producto pr : catalogo.todos()) {
			h.append("<li>").append(esc(pr.getNombre()))
			 .append(" [").append(pr.getId()).append("]")
			 .append(" (").append(String.format(Locale.US, "%.2f", pr.getPrecio())).append(" €) ")
			 .append("<a href=\"/producto?id=").append(pr.getId()).append("\">ver</a> ")
			 .append(formulario("/carrito/anadir", pr.getId(), "Añadir"))
			 .append(formulario("/carrito/eliminar", pr.getId(), "Eliminar"))
			 .append("</li>");
		}

		h.append("</ul><p><a href=\"/carrito\">Ver carrito</a></p></body></html>");

		return h.toString();
	}
	
	/**
	 * Genera un formulario HTML con un solo botón que envía un POST con el identificador de un producto.
	 *
	 * @param accion ruta a la que se envía el formulario
	 * @param id identificador del producto
	 * @param texto texto del botón
	 * @return el código HTML del formulario
	 */
	private static String formulario(String accion, String id, String texto) {
		return "<form method=\"POST\" action=\"" + accion + "\" style=\"display:inline\">"
			     + "<input type=\"hidden\" name=\"id\" value=\"" + id + "\">"
			     + "<button type=\"submit\">" + texto + "</button></form> ";
	}
	
	/**
	 * Escapa los caracteres especiales de HTML (&amp;, &lt; y &gt;) para que un texto se muestre tal cual.
	 *
	 * @param s texto original
	 * @return el texto escapado
	 */
	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}