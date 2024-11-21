package com.carincho.springboot.webflux.app;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import com.carincho.springboot.webflux.app.models.documents.Categoria;
import com.carincho.springboot.webflux.app.models.documents.Producto;
import com.carincho.springboot.webflux.app.models.services.ProductoService;

import reactor.core.publisher.Flux;

@SpringBootApplication
public class SpringBootWebfluxApplication implements CommandLineRunner{
	
	@Autowired
	private ProductoService service;
	@Autowired
	private ReactiveMongoTemplate mongoTemplate;
	
	private static final Logger log = LoggerFactory.getLogger(SpringBootWebfluxApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SpringBootWebfluxApplication.class, args);
	}
	
	/**
	 * 
	 * thenMany Se usa para incluir otro flujo una vez que haya concluido el principal, para Flux
	 * then para Mono
	 */

	@Override
	public void run(String... args) throws Exception {
		
		mongoTemplate.dropCollection("productos").subscribe();//borrar la coleccion
		mongoTemplate.dropCollection("categorias").subscribe();
		
		Categoria electronico = new Categoria("Electronico");
		Categoria deporte = new Categoria("Deporte");
		Categoria computacion = new Categoria("Computacion");
		Categoria muebles = new Categoria("Muebles");
		
		Flux.just(electronico, deporte, computacion, muebles)
		.flatMap(service::saveCategoria)
		.doOnNext(c -> {
			
			log.info("Categoria creada: " + c.getNombre() + " , ID: " + c.getId());
		}).thenMany(Flux.just(new Producto("Tv Panasonic", 4000.00, electronico),
				new Producto("Tv sony", 10000.99, electronico),
				new Producto("Drone dji", 20000.0, electronico),
				new Producto("IPhone 16 Pro Max", 31000.99, computacion),
				new Producto("DVD sony", 1500.0, electronico),
				new Producto("Mac air M3", 35000.87, computacion),
				new Producto("Apple watch ultra", 18000.0,computacion),
				new Producto("Mesa cocina", 2000.0, muebles),
				new Producto("Airpods pro 2", 5499.99, computacion),
				new Producto("Teatro en casa sony 5.1", 15000.0, electronico),
				new Producto("Sonos Studio", 9999.999, electronico),
				new Producto("Power Block sport", 9000.0, deporte)
				).flatMap(producto -> {
				
					producto.setCreateAt(new Date());
					return service.save(producto);
					
				}))//Se usa para sacar producto por que save regresa un Mono);
				.subscribe(producto -> log.info("Insert: " + producto.getId() + " " + producto.getNombre()));
	}

}
