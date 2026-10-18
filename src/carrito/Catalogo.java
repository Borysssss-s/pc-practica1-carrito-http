package carrito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de productos, cargado desde un fichero de texto.
 *
 * Cada línea del fichero tiene el formato "id;nombre;precio". Se ignoran las líneas
 * vacías y las que empiezan por "#". Los identificadores solo pueden contener los
 * caracteres a-z, 0-9, "_" y "-", y no pueden repetirse.
 */
public class Catalogo {
	
	/**
	 * Productos del catálogo, indexados por identificador y en orden de lectura.
	 */
	private final Map<String, Producto> productos = new LinkedHashMap<>();
	
	/**
	 * Carga el catálogo desde un fichero.
	 *
	 * @param ruta ruta del fichero (UTF-8)
	 * @throws IOException si no se puede leer el fichero, si alguna línea no es válida
	 * (formato, identificador, identificador repetido o precio) o si el catálogo queda vacío
	 */
	public Catalogo(String ruta) throws IOException {
		List<String> lineas = Files.readAllLines(Paths.get(ruta), StandardCharsets.UTF_8);
		
		for (int i = 0; i < lineas.size(); i++) {
			String linea = lineas.get(i).trim();
			
			if (linea.isEmpty() || linea.startsWith("#")) continue;
			
			String[] campos = linea.split(";");
			
			if (campos.length != 3) {
				throw new IOException(ruta + ", linea " + (i + 1) + ": se esperaba id;nombre;precio");
			}
			
			String id = campos[0].trim();
			
			if (!id.matches("[a-z0-9_-]+")) {
				throw new IOException(ruta + ", línea " + (i + 1) + ": id no válido (solo a-z, 0-9, _ y -)");
			}
			if (productos.containsKey(id)) {
				throw new IOException(ruta + ", línea " + (i + 1) + ": id repetido");
			}
			
			double precio;
			
			try {
				precio = Double.parseDouble(campos[2].trim());
			} catch (NumberFormatException e) {
				throw new IOException(ruta + ", línea " + (i + 1) + ": precio no válido");
			}
			
			productos.put(id, new Producto(id, campos[1].trim(), precio));
		}
		
		if (productos.isEmpty()) {
			throw new IOException(ruta + ": el catálogo está vacío");
		}
	}
	
	/**
	 * Busca un producto por su identificador.
	 *
	 * @param id identificador del producto
	 * @return el producto, o null si no existe
	 */
	public Producto buscar(String id) {
		return productos.get(id);
	}
	
	/**
	 * Devuelve todos los productos del catálogo.
	 *
	 * @return vista de solo lectura de los productos, en el orden del fichero
	 */
	public Collection<Producto> todos() {
		return Collections.unmodifiableCollection(productos.values());
	}
	

}