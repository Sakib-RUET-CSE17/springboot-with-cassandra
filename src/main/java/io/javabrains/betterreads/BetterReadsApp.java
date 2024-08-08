package io.javabrains.betterreads;

import java.nio.file.Path;

import io.javabrains.betterreads.author.Author;
import io.javabrains.betterreads.author.AuthorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.javabrains.betterreads.connection.DataStaxAstraProperties;

import javax.annotation.PostConstruct;

/**
 * Main application class with main method that runs the Spring Boot app
 */

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterReadsApp {

    private AuthorRepository authorRepository;

    @Autowired
    public void setRepo(AuthorRepository authorRepository){
        this.authorRepository = authorRepository;
    }

	public static void main(String[] args) {
		SpringApplication.run(BetterReadsApp.class, args);
	}

    @PostConstruct
    public void start(){
        System.out.println("BetterReadsApp saving author");
        Author author = new Author();
        author.setId("id");
        author.setName("Name");
        author.setPersonalName("personalName");
        authorRepository.save(author);
        System.out.println("BetterReadsApp saved author");
    }

    /**
     * This is necessary to have the Spring Boot app use the Astra secure bundle 
     * to connect to the database
     */
	@Bean
    public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
        Path bundle = astraProperties.getSecureConnectBundle().toPath();
        return builder -> builder.withCloudSecureConnectBundle(bundle);
    }
	

}
