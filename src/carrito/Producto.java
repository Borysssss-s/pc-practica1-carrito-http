package carrito;

/**
 * Producto del catálogo, con su identificador, nombre y precio.
 */
public class Producto {

	/**
	 * Identificador único del producto y su nombre.
	 */
	private String id, nombre;
	/**
	 * Precio del producto en euros.
	 */
	private double precio;

	/**
	 * Crea un producto.
	 *
	 * @param id identificador único
	 * @param nombre nombre del producto
	 * @param precio precio en euros
	 */
	public Producto(String id, String nombre, double precio) {
		this.id = id;
		this.nombre = nombre;
		this.precio = precio;
	}

	/**
	 * @return el identificador del producto
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return el nombre del producto
	 */
	public String getNombre() {
		return nombre;
	}

	/**
	 * @return el precio del producto en euros
	 */
	public double getPrecio() {
		return precio;
	}

}