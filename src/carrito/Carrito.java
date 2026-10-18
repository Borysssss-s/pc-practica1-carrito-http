package carrito;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carrito de la compra.
 *
 * Es un objeto sin estado propio en el servidor: se reconstruye en cada petición a partir
 * de la cookie "carrito" y se vuelve a serializar en la respuesta. Guarda, para cada
 * producto, el número de unidades, respetando el orden de inserción.
 */
public class Carrito {
	
	/**
	 * Número máximo de unidades permitidas de un mismo producto.
	 */
	public static final int MAX_CANTIDAD = 99;
	
	/**
	 * Unidades de cada producto, indexadas por su identificador.
	 */
	private Map<String, Integer> items = new LinkedHashMap<>();

	/**
	 * Reconstruye un carrito a partir del valor de la cookie "carrito".
	 *
	 * El formato es "id:cantidad|id:cantidad". Como la cookie la controla el cliente, no se
	 * confía en ella: se descartan las entradas mal formadas, los productos que no están
	 * en el catálogo y las cantidades que no estén entre 1 y {@link #MAX_CANTIDAD}.
	 *
	 * @param valor valor de la cookie, o null si no se recibió
	 * @param catalogo catálogo con el que se validan los identificadores
	 * @return el carrito reconstruido (vacío si la cookie es nula o no es válida)
	 */
	public static Carrito desdeCookie(String valor, Catalogo catalogo) {
		Carrito c = new Carrito();
		
		if (valor == null || valor.isEmpty()) return c;
		
		for (String par : valor.split("\\|")) {
			String[] p = par.split(":");
			
			if (p.length != 2 || catalogo.buscar(p[0]) == null) continue;
			
			try {
				int n = Integer.parseInt(p[1]);
				if (n > 0 && n <= MAX_CANTIDAD) c.items.put(p[0], n);
				
			} catch (NumberFormatException e) {
				
			}
		}
		
		return c;
	}
	
	/**
	 * Serializa el carrito al formato de la cookie "carrito".
	 *
	 * @return el contenido como "id:cantidad|id:cantidad", o cadena vacía si no hay productos
	 */
	public String aCookie() {
		StringBuilder sb = new StringBuilder();
		
		for (var e : items.entrySet()) {
			
			if (sb.length() > 0) {
				sb.append("|");
			}
			
			sb.append(e.getKey()).append(":").append(e.getValue());
		}
		
		return sb.toString();
	}
	
	/**
	 * Añade una unidad de un producto.
	 *
	 * @param id identificador del producto
	 * @return true si se añadió; false si ya se había alcanzado {@link #MAX_CANTIDAD}
	 */
	public boolean anadir(String id) {
		int n = items.getOrDefault(id, 0);
		
		if (n >= MAX_CANTIDAD) return false;
		
		items.put(id, n + 1);
		
		return true;
	}
	
	/**
	 * Quita una unidad de un producto. Si solo quedaba una, el producto desaparece del carrito.
	 *
	 * @param id identificador del producto
	 * @return true si el producto estaba en el carrito; false en caso contrario
	 */
	public boolean eliminar(String id) {
		Integer n = items.get(id);
		
		if (n == null) {
			return false;
		}
		if (n <= 1) {
			items.remove(id);
		} else {
			items.put(id, n - 1);
		}
		return true;
	}
	
	/**
	 * Calcula el número total de unidades del carrito.
	 *
	 * @return suma de las unidades de todos los productos
	 */
	public int total() {
		int total = 0;
		
		for (int n : items.values()) {
			total += n;
		}
		
		return total;
	}
	
	/**
	 * Establece directamente la cantidad de un producto.
	 *
	 * @param id identificador del producto
	 * @param cantidad nueva cantidad; si es 0 o menor, el producto se elimina
	 */
	public void fijar(String id, int cantidad) {
		if (cantidad <= 0) items.remove(id);
		else items.put(id, cantidad);
	}

	/**
	 * Devuelve el contenido del carrito.
	 *
	 * @return vista de solo lectura de las unidades por producto
	 */
	public Map<String, Integer> getItems() {
		return Collections.unmodifiableMap(items);
	}
	
	
}