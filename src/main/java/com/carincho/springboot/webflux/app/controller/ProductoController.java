package com.carincho.springboot.webflux.app.controller;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.thymeleaf.spring6.context.webflux.ReactiveDataDriverContextVariable;

import com.carincho.springboot.webflux.app.models.documents.Categoria;
import com.carincho.springboot.webflux.app.models.documents.Producto;
import com.carincho.springboot.webflux.app.models.services.ProductoService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SessionAttributes("producto")//Guarda la session el objecto producto de forma momentanea 
@Controller
public class ProductoController {


	@Autowired
	private ProductoService service;
	
	private static final Logger log = LoggerFactory.getLogger(ProductoController.class);
	
	@Value("${config.upload.path}")
	private String path;
	
	
	@ModelAttribute("categorias")
	public Flux<Categoria>categorias() {
		
		return service.findAllCategoria();
		
	}
	
	@GetMapping({"/listar", "/"})
	public Mono<String> listar(Model model) {
		
//		Flux<Producto>productos = service.findAll().map(producto -> {
//			producto.setNombre(producto.getNombre().toUpperCase());
//			
//			return producto;
//			
//		});//Por debajo se va a suscribir que va a ser mostrar los datos en una plantilla tymeleaf
		
		
		Flux<Producto>productos = service.findAllByName();
//		
		productos.subscribe(producto -> log.info(producto.getNombre()));//Otro subscriptor otro observador
		
		model.addAttribute("productos", productos );
		model.addAttribute("titulo", "Lista de productos");
		
		return Mono.just("listar");
	}
	
	@GetMapping("/form")
	public Mono<String>crear(Model model) {
		
		model.addAttribute("producto", new Producto());
		model.addAttribute("titulo", "Formulario de Producto");
		model.addAttribute("boton", "Crear");
		
		return Mono.just("form");
		
	}
	
	/**
	 * 
	 * BindingResultUtils tiene que ir despues del objeto del formulario
	 * 
	 * @RequestPart FilePart file es el mismo nombre que en la vista en name="file" en caso contrario
	 * en la anotacion indicarle el nombre
	 * 
	 */
	@PostMapping("/form")
	public Mono<String> guardar(@Valid Producto producto, BindingResult result, Model model, @RequestPart FilePart file,  SessionStatus status) {
		
		if(result.hasErrors()) {
			
			model.addAttribute("titulo", "Errores en formulario Producto");
			model.addAttribute("boton", "Guardar");
			return Mono.just("form");
			
		} else {
			
			
		}
		
		status.setComplete();//Finalizo la sesion
		
		
		Mono<Categoria> categoria = service.findCategoriaById(producto.getCategoria().getId());
		
		return categoria.flatMap(c -> {
			if(producto.getCreateAt() == null) {
				producto.setCreateAt(new Date());
				
			}
			
			if(!file.filename().isEmpty()) {
				
				producto.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
				.replace(" ", "")
				.replace(":", "")
				.replace("\\", "")
				);
			}
			
			producto.setCategoria(c);
			return service.save(producto);
		}).doOnNext(p -> {
						log.info("Categoria asignada: " + p.getCategoria().getNombre() + "Id Cat: " + p.getCategoria().getId());
						log.info("Producto guardado: " + p.getNombre() + "ID: " + p.getId());
					})
				.flatMap(p -> {
					
					if(!file.filename().isEmpty()) {
						return file.transferTo(new File(path + producto.getFoto()));
					}
					return Mono.empty();
					
				})
				.thenReturn("redirect:/listar?success=producto+guardado+con+exito"); //then(Mono.just("\"redirect:/listar\"")) Otra alternativa
		
	}
	
	@GetMapping("/eliminar/{id}")
	public Mono<String>eliminar(@PathVariable String id) {
		
		return service.findById(id)
				.defaultIfEmpty(new Producto())
				.flatMap(p -> {
					if(p.getId() == null) {
						return Mono.error(new InterruptedException("No existe el producto a eliminar"));
					}
					return Mono.just(p);
					
				})
				.flatMap(p -> {
					log.info("Eliminando Producto: " + p.getNombre());
					log.info("Eliminando Producto ID: " + p.getId());
			
				return service.delete(p);
			})
				.then(Mono.just("redirect:/listar?producto+eliminado+con+exito"))
				.onErrorResume(ex -> Mono.just("redirect:/listar?error=no+existe+el+producto+a+eliminar"));//Si el flux esta vacio;
		
	}
	
	@GetMapping("/form/{id}")
	public Mono<String>editar(@PathVariable String id, Model model) {
		
		Mono<Producto> productoMono = service.findById(id).doOnNext(p -> {
			
			log.info("Producto: " + p.getNombre());
		})
				.defaultIfEmpty(new Producto());//Si el flux esta vacio
		
		model.addAttribute("titulo", "Editar Producto");
		model.addAttribute("producto", productoMono);
		model.addAttribute("boton", "Editar");
		
		return Mono.just("form");
		
	}
	
	/**
	 * 
	 * otra forma para editar
	 * los datos del mode.attibute se guardan en otro hilo no se guardan en la sesion por lo que el @SessionAttributes no se guardan
	 * Se tiene que pasar en el hidden
	 * Esto se realiza dentro de otro contexto fuera del metodo handler
	 * 
	 */
	@GetMapping("/form-v2/{id}")
	public Mono<String>editarV2(@PathVariable String id, Model model) {
		
		return service.findById(id).doOnNext(p -> {
			
			log.info("Producto: " + p.getNombre());
			model.addAttribute("boton", "Editar");
			model.addAttribute("titulo", "Editar Producto");
			model.addAttribute("producto", p);
		})
				.defaultIfEmpty(new Producto())
				.flatMap(p -> {
					if(p.getId() == null) {
						return Mono.error(new InterruptedException("No existe el producto"));
					}
					return Mono.just(p);
					
				})
				.thenReturn("form")
				.onErrorResume(ex -> Mono.just("redirect:/listar?error=no+existe+el+producto"));//Si el flux esta vacio
		
		
		
		
	}
	
	@GetMapping("/ver/{id}")
	public Mono<String>ver(Model model, @PathVariable String id) {
		
		return service.findById(id)
				.doOnNext(p -> {
					model.addAttribute("producto", p);
					model.addAttribute("titulo", "Detalle Producto");
					
				})
				.switchIfEmpty(Mono.just(new Producto()))// se puede usar switch pasando el mono o default pasando producto
				.flatMap(p -> {
					if(p.getId() == null) {
						return Mono.error(new InterruptedException("No existe el producto"));
					}
					return Mono.just(p);
					
				})
				.then(Mono.just("ver"))
				.onErrorResume(ex -> Mono.just("redirect:/listar?error=no+existe+el+producto"));
	}
	
	@GetMapping("/uploads/img/{nombreFoto:.+}") // :.+ es una expresion regular en el pathVariable para la extension de la imagen
	public Mono<ResponseEntity<Resource>>verFoto(@PathVariable String nombreFoto) throws MalformedURLException {
		
		Path ruta = Paths.get(path).resolve(nombreFoto).toAbsolutePath();
		Resource imagen = new UrlResource(ruta.toUri());
		
		return Mono.just(
				ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + imagen.getFilename() + "\"")
						.body(imagen)
				
				);
	}
	
	
	
	/**
	 * Manejo contrapresion Data Driven
	 * 
	 * Reactive DAta driver un controlador que maneja internamente eventos con datos del stream
	 * 
	 * Una forma de manejar la contrapresion con thymeleaf
	 */
	@GetMapping({"/listar-datadriver"})
	public String listarDataDriver(Model model) {
		
		Flux<Producto>productos = service.findAllByName().delayElements(Duration.ofSeconds(1));
		
		productos.subscribe(producto -> log.info(producto.getNombre()));//Otro subscriptor otro observador
		
		model.addAttribute("productos",new ReactiveDataDriverContextVariable(productos, 1));// le indicamos que muestre por lotes
		model.addAttribute("titulo", "Lista de productos");
		
		return "listar";
	}
	/**
	 * Manejo contrapresion chunked
	 * 
	 * 
	 */
	@GetMapping({"/listar-full"})
	public String listarfull(Model model) {
		
//		Flux<Producto>productos = service.findAll().map(producto -> {
//			producto.setNombre(producto.getNombre().toUpperCase());
//			
//			return producto;
//			
//		}).repeat(5000);
		
		Flux<Producto>productos = service.findAllByNameRepeat();
		
		
		model.addAttribute("productos",new ReactiveDataDriverContextVariable(productos, 1));// le indicamos que muestre por lotes
		model.addAttribute("titulo", "Lista de productos");
		
		return "listar";
	}
	/**
	 * Manejo contrapresion chunked
	 * 
	 * 
	 */
	@GetMapping({"/listar-chunked"})
	public String listarChunked(Model model) {
		
//		Flux<Producto>productos = service.findAll().map(producto -> {
//			producto.setNombre(producto.getNombre().toUpperCase());
//			
//			return producto;
//			
//		}).repeat(5000);
		
		Flux<Producto>productos = service.findAllByNameRepeat();
		
		
		model.addAttribute("productos",new ReactiveDataDriverContextVariable(productos, 1));// le indicamos que muestre por lotes
		model.addAttribute("titulo", "Lista de productos");
		
		return "listar-chunked";
	}
	
}
